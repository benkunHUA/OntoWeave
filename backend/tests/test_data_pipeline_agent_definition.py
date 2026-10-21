import uuid
from dataclasses import replace

import pytest
from sqlalchemy import select

from app.agent.definitions.base import AgentRunContext
from app.agent.definitions.data_pipeline import (
    DataPipelineAgentDefinition,
    DataPipelineRunContext,
)
from app.agent.definitions.registry import get_definition
from app.agent.prompts_data_pipeline import build_prompt
from app.agent.tools.data_pipeline import (
    DataPipelineToolContext,
    build_data_pipeline_tools,
)
from app.config import Settings
from app.errors import DataServiceUnavailableError, InvariantViolationError
from app.models import ConversationEvent
from app.services import conversation, conversation_event
from app.vocabulary import ConversationKind
from tests.pipeline_test_fakes import PIPELINE_ID, FakePipelineClient


def _conversation(session, owner):
    return conversation.create_conversation(
        session,
        owner_id=owner,
        title="订单清洗",
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=PIPELINE_ID,
    )


def test_registry_resolves_data_pipeline_definition():
    assert isinstance(get_definition(ConversationKind.DATA_PIPELINE), DataPipelineAgentDefinition)


def test_prepare_run_reloads_pipeline_draft_and_check(session, monkeypatch):
    client = FakePipelineClient()
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)

    run = DataPipelineAgentDefinition().prepare_run(session, conversation=conv, owner_id=owner)

    assert isinstance(run, DataPipelineRunContext)
    assert run.pipeline_state["name"] == "订单清洗"
    assert run.draft["revision"] == 3
    assert run.check_result["stage"] == "draft"
    assert run.turn_id is None
    assert [name for name, _ in client.calls] == ["get", "draft", "check"]


def test_prepare_run_rejects_wrong_conversation_kind(session, monkeypatch):
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient",
        lambda settings: FakePipelineClient(),
    )
    conv = conversation.create_conversation(
        session,
        owner_id=uuid.uuid4(),
        title="连接",
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=uuid.uuid4(),
    )

    with pytest.raises(InvariantViolationError, match="cannot run conversation kind"):
        DataPipelineAgentDefinition().prepare_run(
            session, conversation=conv, owner_id=conv.owner_id
        )


@pytest.mark.parametrize("failing_method", ["get_pipeline", "get_draft", "check_pipeline"])
def test_prepare_run_degrades_on_any_java_read_failure(session, monkeypatch, failing_method):
    client = FakePipelineClient()

    def fail(*_args, **_kwargs):
        raise DataServiceUnavailableError("java down")

    monkeypatch.setattr(client, failing_method, fail)
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)

    run = DataPipelineAgentDefinition().prepare_run(session, conversation=conv, owner_id=owner)

    assert run.load_error == "java down"
    assert run.pipeline_state is None
    assert run.draft is None
    assert run.check_result is None
    attempts = [name for name, _ in client.calls]
    assert ("get" in attempts) is (failing_method != "get_pipeline")
    assert ("draft" in attempts) is (failing_method != "get_draft")
    assert attempts.count("check") == (0 if failing_method == "check_pipeline" else 1)


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


def test_build_state_update_uses_internal_fields(session, monkeypatch):
    client = FakePipelineClient()
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    definition = DataPipelineAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)

    update = definition.build_state_update(session, Settings(), run)

    assert update["pipelineId"] == str(PIPELINE_ID)
    assert update["draftRevision"] == 3
    assert update["check"]["stage"] == "draft"
    assert update["loadError"] is None


def test_build_state_update_reloads_latest_revision_after_tool_write(session, monkeypatch):
    client = FakePipelineClient()
    current = {"revision": 3}

    def get_draft(pipeline_id, *, owner_id):  # noqa: ARG001
        client._record("draft", pipeline_id=pipeline_id, owner_id=owner_id)
        return {
            "pipelineId": str(pipeline_id),
            "revision": current["revision"],
            "definition": {"nodes": []},
        }

    def apply_command(pipeline_id, *, owner_id, payload):  # noqa: ARG001
        current["revision"] = 9
        return {
            "pipelineId": str(pipeline_id),
            "revision": 9,
            "definition": {"nodes": [], "edges": []},
        }

    def check_pipeline(pipeline_id, *, owner_id, revision):  # noqa: ARG001
        return {
            "pipelineId": str(pipeline_id),
            "revision": revision,
            "stage": "ready",
            "blockingIssues": [],
            "warnings": [],
            "nextActions": [],
        }

    monkeypatch.setattr(client, "get_draft", get_draft)
    monkeypatch.setattr(client, "apply_draft_command", apply_command)
    monkeypatch.setattr(client, "check_pipeline", check_pipeline)
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    definition = DataPipelineAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)
    ctx = DataPipelineToolContext(
        session=session,
        owner_id=owner,
        conversation_id=conv.id,
        pipeline_id=conv.resource_id,
        client=client,
        draft_revision=3,
        turn_id=uuid.uuid4(),
    )

    result = {item.name: item for item in build_data_pipeline_tools(ctx)}[
        "add_pipeline_node"
    ].invoke({"type": "filter", "name": "筛选"})
    update = definition.build_state_update(session, Settings(), run)

    assert result["revision"] == 9
    assert update["draftRevision"] == 9
    assert update["check"]["revision"] == 9


def test_build_state_update_appends_one_idempotent_turn_event(session, monkeypatch):
    client = FakePipelineClient()
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    definition = DataPipelineAgentDefinition()
    prepared = definition.prepare_run(session, conversation=conv, owner_id=owner)
    turn_id = uuid.uuid4()
    run = replace(prepared, turn_id=turn_id)

    first = definition.build_state_update(session, Settings(), run)
    second = definition.build_state_update(session, Settings(), run)

    rows = list(
        session.scalars(
            select(ConversationEvent).where(
                ConversationEvent.conversation_id == conv.id,
                ConversationEvent.event_type == "pipeline.agent.turn",
            )
        )
    )
    assert first["draftRevision"] == second["draftRevision"] == 3
    assert len(rows) == 1
    assert rows[0].turn_id == turn_id
    assert rows[0].payload["turnId"] == str(turn_id)
    assert rows[0].payload["draftRevision"] == 3
    assert rows[0].payload["loadError"] is None


def test_build_state_update_degrades_when_latest_read_fails(session, monkeypatch):
    client = FakePipelineClient()
    monkeypatch.setattr(
        "app.agent.definitions.data_pipeline.DataServiceClient", lambda settings: client
    )
    owner = uuid.uuid4()
    conv = _conversation(session, owner)
    definition = DataPipelineAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)

    def fail(*_args, **_kwargs):
        raise DataServiceUnavailableError("java down")

    monkeypatch.setattr(client, "get_draft", fail)
    update = definition.build_state_update(session, Settings(), run)

    assert update["draftRevision"] is None
    assert update["check"] is None
    assert update["loadError"] == "java down"


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
