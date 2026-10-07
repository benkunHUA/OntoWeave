import uuid

import pytest

from app.agent import ontology_tools as tools
from app.errors import NotFoundError
from app.services import conversation, embedding, graph, ontology


def _setup(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="工具测试")
    emb = embedding.HashingEmbedder()
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    for obj in (customer, order):
        embedding.save_embedding(session, object_type_id=obj.id, embedder=emb)
    rel = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    graph.add_object_to_graph(session, conv.graph_id, customer.id, owner_id=owner)
    graph.add_object_to_graph(session, conv.graph_id, order.id, owner_id=owner)
    graph.add_relation_to_graph(session, conv.graph_id, rel.id, owner_id=owner)
    return conv, emb, customer, order, rel


def test_search_ontology_returns_similar_with_scores(session):
    owner = uuid.uuid4()
    _, emb, *_ = _setup(session, owner)
    hits = tools.search_ontology(session, query="客户公司", embedder=emb, owner_id=owner)
    assert hits[0]["name"] == "客户"
    assert "similarity" in hits[0]


def test_search_ontology_only_returns_own(session):
    owner, other = uuid.uuid4(), uuid.uuid4()
    emb = embedding.HashingEmbedder()
    mine = ontology.create_object_type(session, owner_id=owner, name="客户")
    outsider = ontology.create_object_type(session, owner_id=other, name="客户")
    for obj in (mine, outsider):
        embedding.save_embedding(session, object_type_id=obj.id, embedder=emb)
    hits = tools.search_ontology(session, query="客户", embedder=emb, owner_id=owner)
    assert [hit["id"] for hit in hits] == [str(mine.id)]


def test_get_subgraph_walks_one_hop(session):
    owner = uuid.uuid4()
    conv, _, customer, _, _ = _setup(session, owner)
    result = tools.get_subgraph(
        session, graph_id=conv.graph_id, object_type_id=customer.id, owner_id=owner
    )
    assert sorted(node["name"] for node in result["nodes"]) == ["客户", "订单"]
    assert [rel["name"] for rel in result["relations"]] == ["下订单"]


def test_get_subgraph_rejects_unknown_node(session):
    owner = uuid.uuid4()
    conv, *_ = _setup(session, owner)
    with pytest.raises(NotFoundError):
        tools.get_subgraph(
            session, graph_id=conv.graph_id, object_type_id=uuid.uuid4(), owner_id=owner
        )


def test_list_types_only_returns_own(session):
    owner, other = uuid.uuid4(), uuid.uuid4()
    ontology.create_object_type(session, owner_id=owner, name="mine")
    ontology.create_object_type(session, owner_id=other, name="other")
    assert [item["name"] for item in tools.list_types(session, owner_id=owner)] == ["mine"]
