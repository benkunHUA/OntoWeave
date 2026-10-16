import json
import uuid

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, AIMessageChunk, ToolMessage
from sqlalchemy.orm import Session, sessionmaker

from app.services import view_state


class StubAgent:
    """假的编译产物：只按约定吐 update chunk，用来单独验证 SSE 这层的管道。"""

    def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
        yield {
            "model": {
                "messages": [
                    AIMessage(
                        content="",
                        tool_calls=[
                            {"name": "list_types", "args": {}, "id": "c1", "type": "tool_call"}
                        ],
                    )
                ]
            }
        }
        yield {"tools": {"messages": [ToolMessage(content="[]", tool_call_id="c1")]}}
        yield {"model": {"messages": [AIMessage(content="图里现在还没有类型。")]}}


def _events(text: str) -> list[dict]:
    out = []
    for block in text.split("\n\n"):
        for line in block.splitlines():
            if line.startswith("data: "):
                out.append(json.loads(line[len("data: ") :]))
    return out


@pytest.fixture
def client(session_factory: sessionmaker[Session], monkeypatch) -> TestClient:
    monkeypatch.setattr(
        "app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: StubAgent()
    )
    # 关掉检查点：这几个用例验证的是 SSE 管道，不需要连库建检查点。
    from app.main import _create_app

    return TestClient(_create_app(session_factory, enable_agent_checkpoints=False))


def test_message_endpoint_streams_events(client):
    conv = client.post("/api/v1/conversations", json={"title": "SSE 测试"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "图里有什么？"},
    ) as response:
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("text/event-stream")
        body = "".join(response.iter_text())

    events = _events(body)
    kinds = [event["type"] for event in events]
    assert kinds[0] == "run.start"
    assert "tool.start" in kinds
    assert "tool.end" in kinds
    assert "block.delta" in kinds
    assert "state.update" in kinds
    assert kinds[-1] == "run.end"
    assert events[-1]["paused"] is False

    tool_start = next(event for event in events if event["type"] == "tool.start")
    assert tool_start["name"] == "list_types"
    assert tool_start["callId"] == "c1"
    tool_end = next(event for event in events if event["type"] == "tool.end")
    assert tool_end["status"] == "ok"
    delta = next(event for event in events if event["type"] == "block.delta")
    assert delta["text"] == "图里现在还没有类型。"
    state = next(event for event in events if event["type"] == "state.update")
    assert state["view"] == {}

    history = client.get(f"/api/v1/conversations/{conv['id']}/messages").json()
    assert [(item["role"], item["content"]) for item in history] == [
        ("user", "图里有什么？"),
        ("assistant", "图里现在还没有类型。"),
    ]
    # 工具调用也进了助手消息的 blocks，刷新后还能看到。
    assistant = history[-1]
    assert [block["kind"] for block in assistant["blocks"]] == ["tool", "text"]
    assert assistant["blocks"][0]["name"] == "list_types"
    assert assistant["blocks"][0]["status"] == "ok"


def test_resume_endpoint_streams_events(client):
    conv = client.post("/api/v1/conversations", json={"title": "SSE 续跑"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/resume",
        json={"answer": "客户"},
    ) as response:
        body = "".join(response.iter_text())
    kinds = [event["type"] for event in _events(body)]
    assert kinds[0] == "run.start"
    assert kinds[-1] == "run.end"


def test_resume_with_tool_decision_stores_the_display_text(client):
    """工具审批的 answer 是 decisions 结构，入库的必须是人看得懂的那句话。"""
    conv = client.post("/api/v1/conversations", json={"title": "审批续跑"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/resume",
        json={"answer": {"decisions": [{"type": "approve"}]}, "text": "允许"},
    ) as response:
        body = "".join(response.iter_text())
    assert [event["type"] for event in _events(body)][0] == "run.start"
    history = client.get(f"/api/v1/conversations/{conv['id']}/messages").json()
    assert history[0]["role"] == "user"
    assert history[0]["content"] == "允许"


def test_interrupt_is_reported_as_paused(client, monkeypatch):
    """中断和完成都是 run.end，前端靠 paused 区分，不能含糊。"""

    class InterruptingAgent:
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            from langgraph.types import Interrupt

            yield {"__interrupt__": (Interrupt(value={"question": "客户还是供应商？"}),)}

    monkeypatch.setattr(
        "app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: InterruptingAgent()
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
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            from langgraph.types import Interrupt

            yield {
                "__interrupt__": (
                    Interrupt(
                        value={"question": "客户还是供应商？", "options": ["客户", "供应商"]}
                    ),
                )
            }

    monkeypatch.setattr(
        "app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: AskingAgent()
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
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
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
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            yield {"tools": {"messages": [ToolMessage(content="客户", tool_call_id=call_id)]}}
            yield {"model": {"messages": [AIMessage(content="行，按客户来。")]}}

    agents = iter([InterruptingAgent(), ResumingAgent()])
    monkeypatch.setattr(
        "app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: next(agents)
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


def test_state_update_includes_the_saved_view_spec(client, monkeypatch):
    class ViewWritingAgent:
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            yield {"model": {"messages": [AIMessage(content="已经把图按域分组。")]}}

    def build_with_view(session, settings, run, owner_id, *, checkpointer=None):  # noqa: ARG001
        view_state.upsert_view_state(
            session,
            graph_id=run.graph_id,
            user_id=owner_id,
            spec={"layout": {"kind": "domain-grouped", "options": {}}},
            owner_id=owner_id,
        )
        return ViewWritingAgent()

    monkeypatch.setattr("app.agent.runtime.assembly.build_agent_for_run", build_with_view)
    conv = client.post("/api/v1/conversations", json={"title": "视图事件"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "按域分组"},
    ) as response:
        body = "".join(response.iter_text())

    state = next(event for event in _events(body) if event["type"] == "state.update")
    assert state["view"] == {"layout": {"kind": "domain-grouped", "options": {}}}


def test_message_endpoint_rejects_unknown_conversation(client):
    resp = client.post(f"/api/v1/conversations/{uuid.uuid4()}/messages", json={"text": "hi"})
    assert resp.status_code == 404


def test_streaming_agent_emits_reasoning_and_text_blocks(client, monkeypatch):
    """增量流里思考在前、正文在后，中间靠 block.end 收口。"""

    class StreamingAgent:
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            meta = {"langgraph_node": "model", "langgraph_checkpoint_ns": "model:1"}
            yield (
                "messages",
                (
                    AIMessageChunk(
                        content="",
                        additional_kwargs={"reasoning_content": "先查一下有没有现成的"},
                    ),
                    meta,
                ),
            )
            yield ("messages", (AIMessageChunk(content="已经"), meta))
            yield ("messages", (AIMessageChunk(content="建好了。"), meta))
            # 子智能体的 token 不该混进主消息流。
            yield (
                "messages",
                (
                    AIMessageChunk(content="子智能体的碎碎念"),
                    {
                        "langgraph_node": "model",
                        "langgraph_checkpoint_ns": "model:1|view_designer:2",
                    },
                ),
            )
            yield ("updates", {"model": {"messages": [AIMessage(content="已经建好了。")]}})

    monkeypatch.setattr(
        "app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: StreamingAgent()
    )
    conv = client.post("/api/v1/conversations", json={"title": "流式"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "帮我建客户类型"},
    ) as response:
        body = "".join(response.iter_text())

    events = _events(body)
    kinds = [event["type"] for event in events]
    assert kinds.count("block.start") == 2
    started = [event["kind"] for event in events if event["type"] == "block.start"]
    assert started == ["reasoning", "text"]
    first_start = kinds.index("block.start")
    first_end = kinds.index("block.end")
    second_start = kinds.index("block.start", first_start + 1)
    assert first_start < first_end < second_start
    text = "".join(event["text"] for event in events if event["type"] == "block.delta")
    assert text == "先查一下有没有现成的已经建好了。"
    assert "子智能体的碎碎念" not in text

    history = client.get(f"/api/v1/conversations/{conv['id']}/messages").json()
    assistant = history[-1]
    assert assistant["content"] == "已经建好了。"
    assert [block["kind"] for block in assistant["blocks"]] == ["reasoning", "text"]
    assert assistant["blocks"][0]["text"] == "先查一下有没有现成的"
    assert assistant["blocks"][1]["text"] == "已经建好了。"


def test_stream_errors_are_reported_as_events(client, monkeypatch):
    class BrokenAgent:
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            raise RuntimeError("模型挂了")
            yield  # pragma: no cover

    monkeypatch.setattr(
        "app.agent.runtime.assembly.build_agent_for_run", lambda *a, **k: BrokenAgent()
    )
    conv = client.post("/api/v1/conversations", json={"title": "错误"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "试试"},
    ) as response:
        body = "".join(response.iter_text())

    events = _events(body)
    assert events[-1]["type"] == "run.error"
    assert "模型挂了" in events[-1]["message"]
