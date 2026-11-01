from __future__ import annotations

import uuid

import pytest
from sqlalchemy.orm import Session

from app.services import action, embedding, status
from app.services.action import execution
from app.services.graph import publication as graph_publication
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


def _publish(session: Session, ids: dict[str, uuid.UUID]) -> None:
    graph_publication.publish_graph(
        session,
        graph_id=ids["graph_id"],
        owner_id=ids["owner"],
        embedder=embedding.HashingEmbedder(),
    )


def test_action_context_contains_stable_snapshot_and_hash(session: Session):
    ids = graph_with_close_ticket_action(session)
    _publish(session, ids)

    first = execution.load_action_context(
        session,
        graph_id=ids["graph_id"],
        action_type_id=ids["action_id"],
        owner_id=ids["owner"],
        conversation_id=uuid.uuid4(),
    )
    second = execution.load_action_context(
        session,
        graph_id=ids["graph_id"],
        action_type_id=ids["action_id"],
        owner_id=ids["owner"],
        conversation_id=first.conversation_id,
    )

    assert first.definition_hash == second.definition_hash
    assert len(first.definition_hash) == 64
    assert first.snapshot["name"] == "关闭工单"
    assert first.snapshot["rules"][0]["assignments"][0]["target_property_name"] == "解决说明"


def test_action_context_requires_published_graph(session: Session):
    ids = graph_with_close_ticket_action(session)

    with pytest.raises(execution.ActionExecutionError) as raised:
        execution.load_action_context(
            session,
            graph_id=ids["graph_id"],
            action_type_id=ids["action_id"],
            owner_id=ids["owner"],
        )

    assert raised.value.code == "ACTION_NOT_PUBLISHED"


def test_action_context_rejects_unconfirmed_child_elements(session: Session):
    ids = graph_with_close_ticket_action(session)
    _publish(session, ids)
    parameter = action.list_parameters(session, ids["action_id"])[0]
    parameter.status = status.DRAFT
    session.flush()

    with pytest.raises(execution.ActionExecutionError) as raised:
        execution.load_action_context(
            session,
            graph_id=ids["graph_id"],
            action_type_id=ids["action_id"],
            owner_id=ids["owner"],
        )

    assert raised.value.code == "ACTION_NOT_EXECUTABLE"
    assert "parameter" in raised.value.details["elements"][0]


def test_action_context_rejects_action_outside_graph(session: Session):
    ids = graph_with_close_ticket_action(session)
    _publish(session, ids)
    outside = action.create_action_type(session, owner_id=ids["owner"], name="图外动作")

    with pytest.raises(execution.ActionExecutionError) as raised:
        execution.load_action_context(
            session,
            graph_id=ids["graph_id"],
            action_type_id=outside.id,
            owner_id=ids["owner"],
        )

    assert raised.value.code == "ACTION_NOT_IN_GRAPH"
