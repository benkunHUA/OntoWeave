from __future__ import annotations

import uuid

import pytest

from app.errors import NotFoundError
from app.services import embedding
from app.services.graph import publication as graph_publication
from app.services.graph import search as graph_search
from tests.support.graph_fixtures import _published_graph


def test_search_graphs_only_returns_current_users_published_graphs(session):
    owner = uuid.uuid4()
    mine, _, _, _ = _published_graph(session, owner)
    unpublished, _, _, _ = _published_graph(session, owner)
    other, _, _, _ = _published_graph(session, uuid.uuid4())
    embedder = embedding.HashingEmbedder()

    graph_publication.publish_graph(session, graph_id=mine.id, owner_id=owner, embedder=embedder)
    graph_publication.publish_graph(
        session,
        graph_id=other.id,
        owner_id=other.owner_id,
        embedder=embedder,
    )

    results = graph_search.search_graphs(
        session,
        owner_id=owner,
        query="客户下订单金额",
        embedder=embedder,
        limit=5,
    )

    assert [item["graph_id"] for item in results] == [mine.id]
    assert unpublished.id not in [item["graph_id"] for item in results]
    assert results[0]["graph_card"] == mine.graph_card
    assert 0.0 <= results[0]["similarity"] <= 1.0


def test_graph_detail_is_type_level_only(session):
    owner = uuid.uuid4()
    row, _, _, relation = _published_graph(session, owner)
    graph_publication.publish_graph(
        session,
        graph_id=row.id,
        owner_id=owner,
        embedder=embedding.HashingEmbedder(),
    )

    detail = graph_search.get_graph_detail(session, graph_id=row.id, owner_id=owner)

    assert detail["graph_id"] == str(row.id)
    assert {item["name"] for item in detail["object_types"]} == {"客户", "订单"}
    assert detail["relations"][0]["id"] == str(relation.id)
    assert "items" not in detail
    with pytest.raises(NotFoundError):
        graph_search.get_graph_detail(session, graph_id=row.id, owner_id=uuid.uuid4())


def test_search_graphs_ignores_vectors_from_another_embedding_model(session):
    owner = uuid.uuid4()
    row, _, _, _ = _published_graph(session, owner)
    graph_publication.publish_graph(
        session,
        graph_id=row.id,
        owner_id=owner,
        embedder=embedding.HashingEmbedder(),
    )

    class OtherModelEmbedder(embedding.HashingEmbedder):
        model = "other-model"

    results = graph_search.search_graphs(
        session,
        owner_id=owner,
        query="客户",
        embedder=OtherModelEmbedder(),
    )

    assert results == []
