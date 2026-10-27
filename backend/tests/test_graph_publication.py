from __future__ import annotations

import uuid

import pytest

from app.errors import InvalidRequestError, NotFoundError
from app.services import embedding, ontology, status
from app.services.graph import publication as graph_publication
from tests.support.graph_fixtures import _published_graph


def test_publish_graph_writes_card_embedding_and_timestamp(session):
    owner = uuid.uuid4()
    row, _, _, _ = _published_graph(session, owner)

    publication = graph_publication.publish_graph(
        session,
        graph_id=row.id,
        owner_id=owner,
        embedder=embedding.HashingEmbedder(),
    )

    assert publication.graph_id == row.id
    assert "客户" in publication.graph_card
    assert "下订单" in publication.graph_card
    assert publication.embedding_model == "local-hash"
    assert row.graph_card == publication.graph_card
    assert row.graph_embedding is not None
    assert row.published_at is not None


def test_publish_graph_rejects_draft_elements_without_writing(session):
    owner = uuid.uuid4()
    row, customer, _, _ = _published_graph(session, owner)
    ontology.transition_object_type(session, customer.id, status.APPROVED, owner_id=owner)
    draft = ontology.create_property(
        session, owner_type_id=customer.id, name="草稿字段", datatype="text"
    )
    del draft

    with pytest.raises(InvalidRequestError, match="unpublished elements"):
        graph_publication.publish_graph(
            session,
            graph_id=row.id,
            owner_id=owner,
            embedder=embedding.HashingEmbedder(),
        )

    assert row.graph_card is None
    assert row.published_at is None


def test_publication_and_detail_require_published_owned_graph(session):
    owner = uuid.uuid4()
    row, _, _, _ = _published_graph(session, owner)
    other = uuid.uuid4()

    with pytest.raises(InvalidRequestError, match="not been published"):
        graph_publication.get_publication(session, graph_id=row.id, owner_id=owner)
    with pytest.raises(NotFoundError):
        graph_publication.get_publication(session, graph_id=row.id, owner_id=other)

    graph_publication.publish_graph(
        session,
        graph_id=row.id,
        owner_id=owner,
        embedder=embedding.HashingEmbedder(),
    )
    with pytest.raises(NotFoundError):
        graph_publication.get_publication(session, graph_id=row.id, owner_id=other)
