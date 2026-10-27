"""`test_api_agent.py` 中 streaming 相关测试。"""

import uuid

import pytest
from langchain_core.messages import AIMessage, AIMessageChunk

from app.services import view_state
from tests.support.api_agent_fixtures import _events, build_client


@pytest.fixture
def client(session_factory, monkeypatch):
    return build_client(session_factory, monkeypatch)


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
    assert state["changesetId"]
    assert isinstance(state["ops"], list)
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


def test_state_update_includes_the_saved_view_spec(client, monkeypatch):
    class ViewWritingAgent:
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
            yield {"model": {"messages": [AIMessage(content="已经把图按域分组。")]}}

    def build_with_view(self, session, settings, run, *, checkpointer=None):  # noqa: ARG001, ARG002
        view_state.upsert_view_state(
            session,
            graph_id=run.graph_id,
            user_id=run.owner_id,
            spec={"layout": {"kind": "domain-grouped", "options": {}}},
            owner_id=run.owner_id,
        )
        return ViewWritingAgent()

    monkeypatch.setattr(
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        build_with_view,
    )
    conv = client.post("/api/v1/conversations", json={"title": "视图事件"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "按域分组"},
    ) as response:
        body = "".join(response.iter_text())

    state = next(event for event in _events(body) if event["type"] == "state.update")
    assert state["view"] == {"layout": {"kind": "domain-grouped", "options": {}}}


def test_streaming_agent_emits_reasoning_and_text_blocks(client, monkeypatch):
    """增量流里思考在前、正文在后，中间靠 block.end 收口。"""

    class StreamingAgent:
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
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
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        lambda *a, **k: StreamingAgent(),
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
        def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
            raise RuntimeError("模型挂了")
            yield  # pragma: no cover

    monkeypatch.setattr(
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        lambda *a, **k: BrokenAgent(),
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


def test_agent_endpoints_forward_x_turn_id(client, monkeypatch):
    captured: list[dict] = []

    def fake_stream(*args, **kwargs):  # noqa: ARG001
        captured.append(kwargs)
        return iter(["data: {}\n\n"])

    monkeypatch.setattr("app.api.agent.runtime.stream_run", fake_stream)
    conv = client.post("/api/v1/conversations", json={"title": "turn id"}).json()
    turn_id = uuid.uuid4()
    headers = {"X-Turn-Id": str(turn_id)}

    assert (
        client.post(
            f"/api/v1/conversations/{conv['id']}/messages",
            json={"text": "继续"},
            headers=headers,
        ).status_code
        == 200
    )
    assert (
        client.post(
            f"/api/v1/conversations/{conv['id']}/resume",
            json={"answer": "确认"},
            headers=headers,
        ).status_code
        == 200
    )
    assert (
        client.post(
            f"/api/v1/conversations/{conv['id']}/messages", json={"text": "新的一轮"}
        ).status_code
        == 200
    )

    assert [item["turn_id"] for item in captured] == [turn_id, turn_id, None]
