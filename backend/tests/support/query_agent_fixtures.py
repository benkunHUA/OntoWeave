"""`test_query_agent.py` 的共享 fixture、假客户端与构造 helper。"""

from __future__ import annotations

from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage
from sqlalchemy.orm import Session, sessionmaker

from app.main import create_app
from app.schemas.query import QueryAgentResult, QueryAgentStatus, QueryEvidence

API = "/api/v1"


def build_client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return build_client(session_factory)


class FakeChild:
    def __init__(self) -> None:
        self.calls: list[tuple[dict, dict]] = []

    def invoke(self, state, config):
        self.calls.append((state, config))
        return {
            "messages": [AIMessage(content="中间消息"), AIMessage(content="最终回答")],
            "structured_response": QueryAgentResult(status=QueryAgentStatus.OK, answer="最终回答"),
            "steps": {"s0": []},
        }


class EvidenceChild(FakeChild):
    def invoke(self, state, config):
        self.calls.append((state, config))
        return {
            "messages": [AIMessage(content="答案")],
            "structured_response": QueryAgentResult(
                status=QueryAgentStatus.OK,
                answer="答案",
                evidence=[QueryEvidence(step_id="model")],
                limitations=["上一轮的限制"],
            ),
            "evidence": [
                {
                    "turn_id": "previous-turn",
                    "step_id": "s0",
                    "object_type_id": "cc78d0bd-155f-4b23-b2ce-748efadceb8c",
                    "primary_key": "112",
                    "title": "苏泽宁",
                    "properties": {},
                    "index_version_id": "3a9aafbe-2e31-4117-bacf-d4fa358581e2",
                    "status": "OK",
                },
                {
                    "turn_id": state["turn_id"],
                    "step_id": "s0",
                    "object_type_id": "cc78d0bd-155f-4b23-b2ce-748efadceb8c",
                    "primary_key": "8",
                    "title": "订单 SO202601030008",
                    "properties": {},
                    "index_version_id": "3a9aafbe-2e31-4117-bacf-d4fa358581e2",
                    "status": "OK",
                },
            ],
        }


class LabeledEvidenceChild(FakeChild):
    def invoke(self, state, config):
        self.calls.append((state, config))
        return {
            "messages": [AIMessage(content="答案")],
            "structured_response": QueryAgentResult(
                status=QueryAgentStatus.OK,
                answer="答案",
            ),
            "evidence": [
                {
                    "turn_id": state["turn_id"],
                    "step_id": "s0",
                    "object_type_id": "cc78d0bd-155f-4b23-b2ce-748efadceb8c",
                    "primary_key": "8",
                    "title": "订单 SO202601030008",
                    "properties": {"prop-order-no": "SO202601030008"},
                    "index_version_id": "3a9aafbe-2e31-4117-bacf-d4fa358581e2",
                    "status": "OK",
                }
            ],
        }


class BlockedChild(FakeChild):
    def invoke(self, state, config):
        self.calls.append((state, config))
        return {
            "messages": [AIMessage(content="索引未就绪")],
            "structured_response": QueryAgentResult(
                status=QueryAgentStatus.BLOCKED,
                limitations=["该图尚未建立实例索引"],
            ),
        }


class FakeCheckpointer:
    def __init__(self, messages: list | None = None) -> None:
        self.messages = messages or []
        self.deleted: list[str] = []

    def get_tuple(self, config):
        if not self.messages:
            return None
        return SimpleNamespace(checkpoint={"channel_values": {"messages": self.messages}})

    def delete_thread(self, thread_id: str) -> None:
        self.deleted.append(thread_id)


class FlakyChild(FakeChild):
    def invoke(self, state, config):
        self.calls.append((state, config))
        if len(self.calls) == 1:
            raise RuntimeError(
                "An assistant message with 'tool_calls' must be followed by tool messages"
            )
        return {
            "messages": [AIMessage(content="重试成功")],
            "structured_response": QueryAgentResult(status=QueryAgentStatus.OK, answer="重试成功"),
        }
