import uuid

import pytest

from app.agent.definitions.data_connection import (
    DataConnectionAgentDefinition,
    DataConnectionRunContext,
)
from app.agent.definitions.registry import get_definition
from app.agent.prompts.data_connection import build_prompt
from app.agent.tools.data_connection import (
    DataConnectionToolContext,
    build_data_connection_tools,
)
from app.clients.data_service import mapping
from app.config import Settings
from app.errors import InvariantViolationError
from app.services import conversation
from app.services.conversation import event as conversation_event
from app.vocabulary import ConversationKind


def _context(session, fake_data_service, owner_id):
    conv = conversation.create_conversation(
        session,
        owner_id=owner_id,
        title="ERP",
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=fake_data_service.connection_id,
    )
    conversation_event.append_event(
        session,
        conversation_id=conv.id,
        event_id=uuid.uuid4(),
        event_type="sync.run.queued",
        summary="已进入队列",
        payload={},
    )
    return DataConnectionToolContext(
        session=session,
        owner_id=owner_id,
        conversation_id=conv.id,
        data_connection_id=fake_data_service.connection_id,
        client=fake_data_service,
    )


def test_data_connection_tools_are_read_only(session, fake_data_service):
    ctx = _context(session, fake_data_service, uuid.uuid4())
    tools = {item.name: item for item in build_data_connection_tools(ctx)}

    assert list(tools) == [
        "get_data_connection_state",
        "get_connection_events",
        "list_source_tables",
        "get_source_table_schema",
        "ask_user",
    ]
    state = tools["get_data_connection_state"].invoke({})
    assert state["missing_steps"] == ["RUN_SYNC"]
    assert state["connection"]["name"] == "ERP"

    events = tools["get_connection_events"].invoke({"limit": 5})
    assert events[0]["summary"] == "已进入队列"

    tables = tools["list_source_tables"].invoke({})
    assert tables["tables"][0]["schema"] == "demo"

    detail = tools["get_source_table_schema"].invoke({"schema_name": "demo", "table": "orders"})
    assert detail["primary_key_columns"] == ["id"]


def test_prompt_contains_live_state_and_events(session, fake_data_service):
    ctx = _context(session, fake_data_service, uuid.uuid4())
    state = mapping.state_summary(
        fake_data_service.get_state(ctx.data_connection_id, owner_id=ctx.owner_id)
    )
    events = conversation_event.list_recent_events(session, ctx.conversation_id)

    prompt = build_prompt(state, events)

    assert "RUN_SYNC" in prompt
    assert "已进入队列" in prompt
    assert "不能保存连接" in prompt


def test_registry_resolves_data_connection_definition():
    assert isinstance(get_definition("data_connection"), DataConnectionAgentDefinition)


def test_prepare_run_rejects_modeling_conversation(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="建模")

    with pytest.raises(InvariantViolationError, match="cannot run conversation kind"):
        DataConnectionAgentDefinition().prepare_run(
            session, conversation=conv, owner_id=conv.owner_id
        )


def test_build_graph_uses_read_only_tools_and_dynamic_prompt(
    session, fake_data_service, monkeypatch
):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="ERP",
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=fake_data_service.connection_id,
    )
    definition = DataConnectionAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)
    assert isinstance(run, DataConnectionRunContext)

    captured = {}
    monkeypatch.setattr(
        "app.agent.definitions.data_connection.DataServiceClient",
        lambda settings: fake_data_service,
    )

    def fake_build_agent(*args, **kwargs):
        captured["args"] = args
        captured["kwargs"] = kwargs
        return "agent"

    monkeypatch.setattr(
        "app.agent.definitions.data_connection.agent_graph.build_agent", fake_build_agent
    )

    assert (
        definition.build_graph(session, Settings(chat_api_key="test"), run, checkpointer=None)
        == "agent"
    )
    assert captured["kwargs"]["include_planning"] is False
    assert "RUN_SYNC" in captured["kwargs"]["system_prompt"]
    assert [item.name for item in captured["args"][1]] == [
        "get_data_connection_state",
        "get_connection_events",
        "list_source_tables",
        "get_source_table_schema",
        "ask_user",
    ]


def test_build_state_update_returns_connection_summary(session, fake_data_service, monkeypatch):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="ERP",
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=fake_data_service.connection_id,
    )
    definition = DataConnectionAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)
    monkeypatch.setattr(
        "app.agent.definitions.data_connection.DataServiceClient",
        lambda settings: fake_data_service,
    )

    update = definition.build_state_update(session, Settings(), run)

    assert update["dataConnection"]["missing_steps"] == ["RUN_SYNC"]
    assert update["dataConnection"]["connection"]["name"] == "ERP"
