"""`test_api_agent.py` 中 interrupts 相关测试。"""

import pytest
from langchain_core.messages import AIMessage, ToolMessage

from tests.support.api_agent_fixtures import _events, build_client


@pytest.fixture
def client(session_factory, monkeypatch):
    return build_client(session_factory, monkeypatch)


def test_interrupt_is_reported_as_paused(client, monkeypatch):
    """中断和完成都是 run.end，前端靠 paused 区分，不能含糊。"""

    class InterruptingAgent:
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
            from langgraph.types import Interrupt

            yield {"__interrupt__": (Interrupt(value={"question": "客户还是供应商？"}),)}

    monkeypatch.setattr(
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        lambda *a, **k: InterruptingAgent(),
    )
    conv = client.post("/api/v1/conversations", json={"title": "中断"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "张三下了个订单"},
    ) as response:
        body = "".join(response.iter_text())

    events = _events(body)
    interrupt = next(e for e in events if e["type"] == "interrupt")
    assert interrupt["question"] == "客户还是供应商？"
    assert interrupt["blockId"]
    assert events[-1]["type"] == "run.end"
    assert events[-1]["paused"] is True
    history = client.get(f"/api/v1/conversations/{conv['id']}/messages").json()
    assert [(item["role"], item["content"]) for item in history] == [
        ("user", "张三下了个订单"),
        ("assistant", "客户还是供应商？"),
    ]
    # 中断以块的形式落库，刷新后前端据此重建等待回答的状态。
    assert history[-1]["blocks"] == [
        {
            "id": interrupt["blockId"],
            "kind": "interrupt",
            "question": "客户还是供应商？",
            "options": [],
            "freeText": {"wrap": "raw"},
            "details": [],
        }
    ]


def test_ask_user_options_become_resume_payloads(client, monkeypatch):
    class AskingAgent:
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
            from langgraph.types import Interrupt

            yield {
                "__interrupt__": (
                    Interrupt(
                        value={"question": "客户还是供应商？", "options": ["客户", "供应商"]}
                    ),
                )
            }

    monkeypatch.setattr(
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        lambda *a, **k: AskingAgent(),
    )
    conv = client.post("/api/v1/conversations", json={"title": "选项"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "张三"},
    ) as response:
        body = "".join(response.iter_text())
    interrupt = next(e for e in _events(body) if e["type"] == "interrupt")
    assert interrupt["options"] == [
        {"label": "客户", "answer": "客户"},
        {"label": "供应商", "answer": "供应商"},
    ]


def test_resume_resolves_the_tool_block_left_by_ask_user(client, monkeypatch):
    """ask_user 是在工具内部挂起的，续跑拿到 ToolMessage 后必须把上一轮那块收口。

    否则前端一直显示成"还在等你回答"，刷新也救不回来。
    """
    from langgraph.types import Interrupt

    call_id = "call_ask_1"
    asked = {"question": "客户还是供应商？", "options": ["客户", "供应商"]}

    class InterruptingAgent:
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
            yield {
                "model": {
                    "messages": [
                        AIMessage(
                            content="",
                            tool_calls=[
                                {
                                    "name": "ask_user",
                                    "args": asked,
                                    "id": call_id,
                                    "type": "tool_call",
                                }
                            ],
                        )
                    ]
                }
            }
            yield {"__interrupt__": (Interrupt(value=asked),)}

    class ResumingAgent:
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
            yield {"tools": {"messages": [ToolMessage(content="客户", tool_call_id=call_id)]}}
            yield {"model": {"messages": [AIMessage(content="行，按客户来。")]}}

    agents = iter([InterruptingAgent(), ResumingAgent()])
    monkeypatch.setattr(
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        lambda *a, **k: next(agents),
    )

    conv = client.post("/api/v1/conversations", json={"title": "挂起收口"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "张三下了个订单"},
    ) as response:
        body = "".join(response.iter_text())
    assert _events(body)[-1]["paused"] is True

    history = client.get(f"/api/v1/conversations/{conv['id']}/messages").json()
    hung = next(block for block in history[-1]["blocks"] if block["kind"] == "tool")
    assert hung["callId"] == call_id
    assert hung["status"] == "running"

    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/resume",
        json={"answer": "客户"},
    ) as response:
        body = "".join(response.iter_text())
    assert _events(body)[-1]["type"] == "run.end"

    resolved = next(
        block
        for message in client.get(f"/api/v1/conversations/{conv['id']}/messages").json()
        for block in message["blocks"] or []
        if block["kind"] == "tool" and block["callId"] == call_id
    )
    assert resolved["status"] == "ok"
    assert resolved["result"] == "客户"
