"""`test_data_pipeline_agent_definition.py` 中 graph 相关测试。"""

import uuid
from dataclasses import replace

import pytest

from app.agent.definitions.base import AgentRunContext
from app.agent.definitions.data_pipeline import (
    DataPipelineAgentDefinition,
    DataPipelineRunContext,
)
from app.agent.prompts.data_pipeline import build_prompt
from app.agent.tools.data_pipeline import (
    DataPipelineToolContext,
    build_data_pipeline_tools,
)
from app.config import Settings
from app.errors import InvariantViolationError
from app.services.conversation import event as conversation_event
from app.vocabulary import ConversationKind
from tests.support.data_pipeline_agent_definition_fixtures import _conversation
from tests.support.pipeline_test_fakes import PIPELINE_ID, FakePipelineClient


def test_build_graph_uses_single_agent_and_all_tools(session, monkeypatch):
    client = FakePipelineClient()
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    definition = DataPipelineAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)
    captured = {}

    def fake_build_agent(*args, **kwargs):
        captured["args"] = args
        captured["kwargs"] = kwargs
        return "agent"

    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.agent_graph.build_agent", fake_build_agent
    )

    result = definition.build_graph(session, Settings(chat_api_key="test"), run, checkpointer=None)

    assert result == "agent"
    assert captured["kwargs"]["include_planning"] is False
    assert "subagents" not in captured["kwargs"] or captured["kwargs"]["subagents"] is None
    assert [item.name for item in captured["args"][1]] == [
        "get_pipeline_state",
        "get_pipeline_draft",
        "get_pipeline_check_result",
        "get_pipeline_events",
        "get_node_catalog",
        "list_pipeline_input_datasets",
        "get_dataset_schema",
        "get_output_sources",
        "add_pipeline_node",
        "update_pipeline_node",
        "delete_pipeline_node",
        "connect_pipeline_nodes",
        "disconnect_pipeline_nodes",
        "set_pipeline_output",
        "preview_pipeline_node",
        "build_pipeline",
        "ask_user",
    ]
    prompt = captured["kwargs"]["system_prompt"]
    assert "## 当前流程（每轮重新读取）" in prompt
    assert str(PIPELINE_ID) in prompt
    assert "## 本轮优先动作" in prompt


def test_build_graph_restores_answered_question_for_same_turn(session, monkeypatch):
    client = FakePipelineClient()
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    turn_id = uuid.uuid4()
    ctx = DataPipelineToolContext(
        session=session,
        owner_id=owner,
        conversation_id=conv.id,
        pipeline_id=conv.resource_id,
        client=client,
        draft_revision=3,
        turn_id=turn_id,
    )
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: "选择 A")
    assert {item.name: item for item in build_data_pipeline_tools(ctx)}["ask_user"].invoke(
        {"question": "选哪个？"}
    ) == "选择 A"

    definition = DataPipelineAgentDefinition()
    run = replace(
        definition.prepare_run(session, conversation=conv, owner_id=owner),
        turn_id=turn_id,
    )
    captured = {}

    def fake_build_agent(*args, **kwargs):  # noqa: ARG001
        captured["tools"] = {item.name: item for item in args[1]}
        return "agent"

    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.agent_graph.build_agent", fake_build_agent
    )
    definition.build_graph(session, Settings(chat_api_key="test"), run, checkpointer=None)
    monkeypatch.setattr(binding, "interrupt", lambda payload: "选择 B")

    result = captured["tools"]["ask_user"].invoke({"question": "还要问什么？"})

    assert result == {
        "error": "QUESTION_LIMIT_REACHED",
        "message": "每轮最多问一个问题",
    }


def test_prompt_contains_fixed_sections_rules_and_state(session):
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    conversation_event.append_event(
        session,
        conversation_id=conv.id,
        event_id=uuid.uuid4(),
        event_type="pipeline.draft.changed",
        summary="新增筛选节点",
        payload={},
    )
    events = conversation_event.list_recent_events(session, conv.id)
    state = {
        "pipelineId": str(PIPELINE_ID),
        "draftRevision": 3,
        "check": {"stage": "draft", "blockingIssues": [{"code": "NEEDS_OUTPUT"}]},
        "loadError": None,
    }

    prompt = build_prompt(state, events)

    assert "你是 OntoWeave 的数据整理助手" in prompt
    assert "## 当前流程（每轮重新读取）" in prompt
    assert "## 最近事件" in prompt
    assert "## 工作规则" in prompt
    assert "## 本轮优先动作" in prompt
    for rule in range(1, 8):
        assert f"{rule}. " in prompt
    assert str(PIPELINE_ID) in prompt
    assert "新增筛选节点" in prompt


def test_definition_rejects_mismatched_pipeline_resource(session):
    run = DataPipelineRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind=ConversationKind.DATA_PIPELINE,
        resource_id=uuid.uuid4(),
        turn_id=uuid.uuid4(),
        pipeline_id=uuid.uuid4(),
        pipeline_state=None,
        draft=None,
        check_result=None,
    )

    with pytest.raises(InvariantViolationError, match="resource_id must match pipeline_id"):
        DataPipelineAgentDefinition().build_graph(session, Settings(), run, checkpointer=None)


def test_agent_run_context_accepts_turn_id():
    turn_id = uuid.uuid4()
    run = AgentRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind=ConversationKind.DATA_PIPELINE,
        resource_id=uuid.uuid4(),
        turn_id=turn_id,
    )
    assert run.turn_id == turn_id
