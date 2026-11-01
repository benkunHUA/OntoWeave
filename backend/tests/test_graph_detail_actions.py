from __future__ import annotations

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.services import embedding
from app.services.action import presentation
from app.services.graph import publication as graph_publication
from app.services.graph import search as graph_search
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


def test_graph_detail_returns_action_summaries_after_publish(session: Session):
    ids = graph_with_close_ticket_action(session)
    graph_publication.publish_graph(
        session,
        graph_id=ids["graph_id"],
        owner_id=ids["owner"],
        embedder=embedding.HashingEmbedder(),
    )

    detail = graph_search.get_graph_detail(session, graph_id=ids["graph_id"], owner_id=ids["owner"])
    expected = presentation.summary_to_dict(
        presentation.load_action_summary(
            session,
            ids["action_id"],
            owner_id=ids["owner"],
            graph_id=ids["graph_id"],
        )
    )

    assert detail["actions"] == [expected]


def test_graph_detail_actions_respect_owner(session: Session):
    ids = graph_with_close_ticket_action(session)
    graph_publication.publish_graph(
        session,
        graph_id=ids["graph_id"],
        owner_id=ids["owner"],
        embedder=embedding.HashingEmbedder(),
    )

    with pytest.raises(NotFoundError):
        presentation.load_action_summaries(session, ids["graph_id"], owner_id=uuid.uuid4())
