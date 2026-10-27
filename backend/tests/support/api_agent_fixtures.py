"""`test_api_agent.py` 的共享 fixture、假客户端与构造 helper。"""

import json

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, ToolMessage
from sqlalchemy.orm import Session, sessionmaker


class StubAgent:
    """假的编译产物：只按约定吐 update chunk，用来单独验证 SSE 这层的管道。"""

    def stream(self, payload, config=None, stream_mode=None, subgraphs=False):  # noqa: ARG002
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


def build_client(session_factory: sessionmaker[Session], monkeypatch) -> TestClient:
    monkeypatch.setattr(
        "app.agent.definitions.ontology.OntologyAgentDefinition.build_graph",
        lambda *a, **k: StubAgent(),
    )
    # 关掉检查点：这几个用例验证的是 SSE 管道，不需要连库建检查点。
    from app.main import _create_app

    return TestClient(_create_app(session_factory, enable_agent_checkpoints=False))


@pytest.fixture
def client(session_factory: sessionmaker[Session], monkeypatch) -> TestClient:
    return build_client(session_factory, monkeypatch)
