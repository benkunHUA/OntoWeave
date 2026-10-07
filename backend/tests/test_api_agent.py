import json
import uuid

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, ToolMessage
from sqlalchemy.orm import Session, sessionmaker


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
    monkeypatch.setattr("app.agent.runtime.build_agent_for_run", lambda *a, **k: StubAgent())
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
    assert "message.delta" in kinds
    assert "state.update" in kinds
    assert kinds[-1] == "run.end"
    assert events[-1]["paused"] is False

    tool_start = next(event for event in events if event["type"] == "tool.start")
    assert tool_start["name"] == "list_types"
    delta = next(event for event in events if event["type"] == "message.delta")
    assert delta["text"] == "图里现在还没有类型。"


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


def test_interrupt_is_reported_as_paused(client, monkeypatch):
    """中断和完成都是 run.end，前端靠 paused 区分，不能含糊。"""

    class InterruptingAgent:
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            from langgraph.types import Interrupt

            yield {"__interrupt__": (Interrupt(value={"question": "客户还是供应商？"}),)}

    monkeypatch.setattr(
        "app.agent.runtime.build_agent_for_run", lambda *a, **k: InterruptingAgent()
    )
    conv = client.post("/api/v1/conversations", json={"title": "中断"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "张三下了个订单"},
    ) as response:
        body = "".join(response.iter_text())

    events = _events(body)
    assert next(e for e in events if e["type"] == "interrupt")["question"] == "客户还是供应商？"
    assert events[-1] == {"type": "run.end", "paused": True}


def test_message_endpoint_rejects_unknown_conversation(client):
    resp = client.post(f"/api/v1/conversations/{uuid.uuid4()}/messages", json={"text": "hi"})
    assert resp.status_code == 404


def test_stream_errors_are_reported_as_events(client, monkeypatch):
    class BrokenAgent:
        def stream(self, payload, config=None, stream_mode=None):  # noqa: ARG002
            raise RuntimeError("模型挂了")
            yield  # pragma: no cover

    monkeypatch.setattr("app.agent.runtime.build_agent_for_run", lambda *a, **k: BrokenAgent())
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
