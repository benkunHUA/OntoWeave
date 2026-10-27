from __future__ import annotations

import json
import uuid
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from sqlalchemy.orm import Session, sessionmaker

from app.agent import llm
from app.agent.definitions.query import QueryAgentDefinition
from app.agent.tools.query.subagent import _QueryRunner, build_query_subagent
from app.config import Settings
from app.main import create_app
from app.models import Graph
from app.schemas.query import QueryAgentResult, QueryAgentStatus, QueryEvidence
from app.services import conversation

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def test_query_conversation_has_random_resource_and_no_graph(session):
    owner = uuid.uuid4()

    conv = conversation.create_conversation(session, owner_id=owner, title="新问答", kind="query")

    assert conv.kind == "query"
    assert session.get(Graph, conv.resource_id) is None


def test_query_conversation_api_creates_a_query_session(client):
    response = client.post(f"{API}/query/conversations")

    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "query"
    assert body["title"] == "新问答"

    listed = client.get(f"{API}/conversations", params={"kind": "query"})
    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [body["id"]]


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


def test_query_runner_uses_persistent_child_thread_and_projects_result():
    conversation_id = uuid.uuid4()
    graph_id = uuid.uuid4()
    child = FakeChild()
    runner = _QueryRunner(child, conversation_id)
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": {"graph_id": str(graph_id), "object_types": [], "relations": []},
        "rewritten_query": "查询订单",
    }

    result = runner(
        {"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]},
        {"configurable": {"thread_id": "parent", "checkpoint_ns": "old"}},
    )

    child_state, child_config = child.calls[0]
    assert child_config["configurable"]["thread_id"] == f"{conversation_id}:query:{graph_id}"
    assert "checkpoint_ns" not in child_config["configurable"]
    assert child_state["graph_id"] == str(graph_id)
    assert child_state["turn_id"]
    assert result["messages"][0].content == "最终回答"
    assert result["structured_response"].status is QueryAgentStatus.OK
    assert "steps" not in result


def test_query_runner_loads_canonical_graph_detail_and_ignores_model_copy():
    conversation_id = uuid.uuid4()
    graph_id = uuid.uuid4()
    child = FakeChild()
    canonical = {
        "graph_id": str(graph_id),
        "object_types": [{"id": "type-1", "properties": [{"id": "prop-1"}]}],
        "relations": [{"id": "relation-1"}],
    }
    runner = _QueryRunner(
        child,
        conversation_id,
        graph_detail_loader=lambda _: canonical,
    )
    model_copy = {
        "graph_id": str(graph_id),
        "object_types": [{"name": "客户", "properties": ["name"]}],
        "relations": [{"name": "下单客户"}],
    }
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": model_copy,
        "rewritten_query": "查询订单",
    }

    runner({"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]})

    child_state, _ = child.calls[0]
    assert child_state["graph_detail"] == canonical
    child_payload = json.loads(child_state["messages"][0].content)
    assert child_payload["graph_detail"] == canonical


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


def test_query_runner_replaces_model_evidence_with_current_turn_tool_state():
    conversation_id = uuid.uuid4()
    graph_id = uuid.uuid4()
    child = EvidenceChild()
    runner = _QueryRunner(child, conversation_id)
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": {"graph_id": str(graph_id), "object_types": [], "relations": []},
        "rewritten_query": "查询订单",
    }

    result = runner({"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]})

    evidence = result["structured_response"].evidence
    assert len(evidence) == 1
    assert evidence[0].primary_key == "8"
    assert evidence[0].title == "订单 SO202601030008"
    assert result["structured_response"].limitations == []


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


def test_query_runner_keeps_limitations_when_blocked():
    child = BlockedChild()
    runner = _QueryRunner(child, uuid.uuid4())
    graph_id = uuid.uuid4()
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": {"graph_id": str(graph_id), "object_types": [], "relations": []},
        "rewritten_query": "查询订单",
    }

    result = runner({"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]})

    assert result["structured_response"].limitations == ["该图尚未建立实例索引"]


def test_query_runner_reuses_checkpoint_thread_for_followup():
    conversation_id = uuid.uuid4()
    graph_id = uuid.uuid4()
    child = FakeChild()
    runner = _QueryRunner(child, conversation_id)
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": {"graph_id": str(graph_id), "object_types": [], "relations": []},
        "rewritten_query": "查询订单",
    }
    message = {"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]}

    runner(message, {"configurable": {"thread_id": "parent"}})
    runner(
        {
            "messages": [
                HumanMessage(
                    content=json.dumps(
                        {**payload, "rewritten_query": "查询未发货订单"},
                        ensure_ascii=False,
                    )
                )
            ]
        },
        {"configurable": {"thread_id": "parent"}},
    )

    first_config = child.calls[0][1]["configurable"]
    second_config = child.calls[1][1]["configurable"]
    assert first_config["thread_id"] == second_config["thread_id"]
    assert "未发货" in child.calls[1][0]["rewritten_query"]


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


def test_query_runner_resets_thread_with_dangling_tool_calls():
    conversation_id = uuid.uuid4()
    graph_id = uuid.uuid4()
    checkpointer = FakeCheckpointer(
        [
            AIMessage(
                content="",
                tool_calls=[{"name": "query_objects", "id": "call-1", "args": {}}],
            )
        ]
    )
    child = FakeChild()
    runner = _QueryRunner(child, conversation_id, checkpointer)
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": {"graph_id": str(graph_id), "object_types": [], "relations": []},
        "rewritten_query": "查询订单",
    }

    runner({"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]})

    assert checkpointer.deleted == [f"{conversation_id}:query:{graph_id}"]
    assert len(child.calls) == 1


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


def test_query_runner_retries_once_after_dangling_history_error():
    conversation_id = uuid.uuid4()
    graph_id = uuid.uuid4()
    checkpointer = FakeCheckpointer()
    child = FlakyChild()
    runner = _QueryRunner(child, conversation_id, checkpointer)
    payload = {
        "graph_id": str(graph_id),
        "graph_detail": {"graph_id": str(graph_id), "object_types": [], "relations": []},
        "rewritten_query": "查询订单",
    }

    result = runner({"messages": [HumanMessage(content=json.dumps(payload, ensure_ascii=False))]})

    assert checkpointer.deleted == [f"{conversation_id}:query:{graph_id}"]
    assert len(child.calls) == 2
    assert result["structured_response"].answer == "重试成功"


def test_query_subagent_is_a_compiled_subagent_and_query_graph_compiles(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="查询", kind="query")
    settings = Settings(chat_api_key="fake-key")
    subagent = build_query_subagent(
        session,
        settings,
        conversation_id=conv.id,
        owner_id=owner,
        checkpointer=InMemorySaver(),
        model=llm.build_chat_model(settings),
    )
    assert subagent["name"] == "query_agent"
    assert hasattr(subagent["runnable"], "invoke")

    run = QueryAgentDefinition().prepare_run(session, conversation=conv, owner_id=owner)
    agent = QueryAgentDefinition().build_graph(session, settings, run, checkpointer=InMemorySaver())
    assert type(agent).__name__ == "CompiledStateGraph"
