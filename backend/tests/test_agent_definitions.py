import uuid

import pytest

from app.agent.definitions.base import AgentRunContext
from app.agent.definitions.ontology import OntologyAgentDefinition, OntologyRunContext
from app.agent.definitions.registry import get_definition
from app.config import Settings
from app.errors import InvariantViolationError
from app.services import conversation
from app.vocabulary import ConversationKind


def test_registry_resolves_modeling_definition():
    definition = get_definition(ConversationKind.MODELING)

    assert isinstance(definition, OntologyAgentDefinition)
    assert get_definition("modeling") is definition


def test_registry_rejects_unknown_kind():
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind: fake"):
        get_definition("fake")


def test_ontology_run_context_extends_agent_run_context():
    context = OntologyRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind=ConversationKind.MODELING,
        resource_id=uuid.uuid4(),
        graph_id=uuid.uuid4(),
        changeset_id=uuid.uuid4(),
    )

    assert isinstance(context, AgentRunContext)


def test_ontology_definition_kind_is_conversation_kind():
    assert OntologyAgentDefinition.kind == ConversationKind.MODELING
    assert OntologyAgentDefinition.kind == "modeling"


def test_ontology_prepare_run_rejects_another_kind(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="数据连接",
        kind=ConversationKind.DATA_CONNECTION,
        resource_id=uuid.uuid4(),
    )

    with pytest.raises(InvariantViolationError, match="cannot run conversation kind"):
        OntologyAgentDefinition().prepare_run(session, conversation=conv, owner_id=owner)


def test_ontology_run_rejects_resource_graph_mismatch(session):
    definition = OntologyAgentDefinition()
    run = OntologyRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind=ConversationKind.MODELING,
        resource_id=uuid.uuid4(),
        graph_id=uuid.uuid4(),
        changeset_id=uuid.uuid4(),
    )

    with pytest.raises(InvariantViolationError, match="resource_id must match graph_id"):
        definition.build_graph(session, Settings(), run, checkpointer=None)
    with pytest.raises(InvariantViolationError, match="resource_id must match graph_id"):
        definition.build_state_update(session, Settings(), run)


def test_ontology_build_graph_rejects_a_generic_run_context(session):
    run = AgentRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind=ConversationKind.MODELING,
        resource_id=uuid.uuid4(),
    )

    with pytest.raises(InvariantViolationError, match="requires OntologyRunContext"):
        OntologyAgentDefinition().build_graph(session, Settings(), run, checkpointer=None)


def test_ontology_run_rejects_another_kind(session):
    graph_id = uuid.uuid4()
    run = OntologyRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind=ConversationKind.DATA_CONNECTION,
        resource_id=graph_id,
        graph_id=graph_id,
        changeset_id=uuid.uuid4(),
    )

    with pytest.raises(InvariantViolationError, match="cannot run conversation kind"):
        OntologyAgentDefinition().build_state_update(session, Settings(), run)
