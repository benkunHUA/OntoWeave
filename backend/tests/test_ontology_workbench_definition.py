"""映射工作台 definition、图装配与提示词测试。"""

from __future__ import annotations

import uuid
from dataclasses import replace

import pytest

from app.agent.definitions.ontology_workbench import OntologyWorkbenchAgentDefinition
from app.agent.definitions.registry import get_definition
from app.agent.prompts.ontology_workbench import build_prompt
from app.config import Settings
from app.errors import InvariantViolationError
from app.services import conversation, ontology
from app.vocabulary import ConversationKind
from tests.support.ontology_workbench_support import FakeWorkbenchClient, object_with_properties


def test_registry_and_prepare_run_use_workbench_kind(session):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="客户映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=obj.id,
    )

    definition = get_definition(ConversationKind.ONTOLOGY_WORKBENCH)
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)

    assert isinstance(definition, OntologyWorkbenchAgentDefinition)
    assert run.subject_kind == ontology.OBJECT_TYPE
    assert run.subject_id == obj.id


def test_prepare_run_rejects_another_conversation_kind(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="建模")

    with pytest.raises(InvariantViolationError, match="cannot run conversation kind"):
        OntologyWorkbenchAgentDefinition().prepare_run(session, conversation=conv, owner_id=owner)


def test_build_graph_reloads_state_and_injects_prompt(session, monkeypatch):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="客户映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=obj.id,
    )
    definition = OntologyWorkbenchAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)
    run = replace(run, turn_id=uuid.uuid4())
    client = FakeWorkbenchClient()
    monkeypatch.setattr(
        "app.agent.definitions.ontology_workbench.OntologyDataServiceClient",
        lambda settings: client,
    )
    captured = {}

    def fake_build_agent(*args, **kwargs):
        captured["tools"] = {item.name for item in args[1]}
        captured["prompt"] = kwargs["system_prompt"]
        return "agent"

    monkeypatch.setattr(
        "app.agent.definitions.ontology_workbench.agent_graph.build_agent", fake_build_agent
    )

    assert (
        definition.build_graph(session, Settings(chat_api_key="test"), run, checkpointer=None)
        == "agent"
    )
    assert "get_mapping" in captured["tools"]
    assert "update_mapping" in captured["tools"]
    assert "当前映射（每轮重新读取）" in captured["prompt"]
    assert str(obj.id) in captured["prompt"]


def test_prompt_states_confirmation_and_run_first_rules():
    prompt = build_prompt(
        {
            "subject_kind": "object_type",
            "subject_id": str(uuid.uuid4()),
            "binding": None,
            "mapping": None,
            "latest_run": {"status": "FAILED"},
            "load_error": None,
        }
    )

    assert "主键" in prompt and "标题键" in prompt
    assert "生成、换源、解绑、取消运行都必须先确认" in prompt
    assert "先看运行记录" in prompt or "先读取失败运行" in prompt
