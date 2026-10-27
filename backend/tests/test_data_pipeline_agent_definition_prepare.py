"""`test_data_pipeline_agent_definition.py` 中 prepare 相关测试。"""

import uuid

import pytest

from app.agent.definitions.data_pipeline import (
    DataPipelineAgentDefinition,
    DataPipelineRunContext,
)
from app.agent.definitions.registry import get_definition
from app.errors import DataServiceUnavailableError, InvariantViolationError
from app.services import conversation
from app.vocabulary import ConversationKind
from tests.support.data_pipeline_agent_definition_fixtures import _conversation
from tests.support.pipeline_test_fakes import FakePipelineClient


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
