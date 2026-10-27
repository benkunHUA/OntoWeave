"""`test_data_pipeline_agent_definition.py` 中 state 相关测试。"""

import uuid
from dataclasses import replace

from sqlalchemy import select

from app.agent.definitions.data_pipeline import (
    DataPipelineAgentDefinition,
)
from app.agent.tools.data_pipeline import (
    DataPipelineToolContext,
    build_data_pipeline_tools,
)
from app.config import Settings
from app.errors import DataServiceUnavailableError
from app.models import ConversationEvent
from tests.support.data_pipeline_agent_definition_fixtures import _conversation
from tests.support.pipeline_test_fakes import PIPELINE_ID, FakePipelineClient


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


def test_build_state_update_upserts_turn_event_with_final_state(session, monkeypatch):
    client = FakePipelineClient()
    current_revision = {"value": 3}

    def get_draft(pipeline_id, *, owner_id):  # noqa: ARG001
        return {
            "pipelineId": str(pipeline_id),
            "revision": current_revision["value"],
            "definition": {"nodes": []},
        }

    monkeypatch.setattr(client, "get_draft", get_draft)
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
    current_revision["value"] = 9
    second = definition.build_state_update(session, Settings(), run)

    def fail(*_args, **_kwargs):
        raise DataServiceUnavailableError("java down")

    monkeypatch.setattr(client, "get_draft", fail)
    third = definition.build_state_update(session, Settings(), run)

    rows = list(
        session.scalars(
            select(ConversationEvent).where(
                ConversationEvent.conversation_id == conv.id,
                ConversationEvent.event_type == "pipeline.agent.turn",
            )
        )
    )
    assert first["draftRevision"] == 3
    assert second["draftRevision"] == 9
    assert third["draftRevision"] is None
    assert third["loadError"] == "java down"
    assert len(rows) == 1
    assert rows[0].turn_id == turn_id
    assert rows[0].payload["draftRevision"] is None
    assert rows[0].payload["loadError"] == "java down"
    assert rows[0].summary == "本轮数据整理未能读取最新状态"


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
