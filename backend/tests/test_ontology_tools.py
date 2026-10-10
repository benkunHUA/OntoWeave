import uuid

import pytest

from app.agent import ontology_tools as tools
from app.errors import InvariantViolationError, NotFoundError
from app.services import changeset, conversation, embedding, graph, ontology


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


def _changeset(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="写工具")
    return conv, changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)


def test_propose_type_creates_draft_and_records_op(session):
    owner = uuid.uuid4()
    _, emb, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    result = tools.propose_type(
        session, changeset_id=cs.id, owner_id=owner, name="供应商", embedder=emb
    )
    obj = ontology.get_object_type(session, uuid.UUID(result["id"]))
    assert obj.status == "draft"
    assert obj.embedding is not None
    assert [op.op for op in changeset.list_ops(session, cs.id)] == ["create"]


def test_propose_type_adds_created_type_to_graph(session):
    owner = uuid.uuid4()
    conv, cs = _changeset(session, owner)
    emb = embedding.HashingEmbedder()

    result = tools.propose_type(
        session, changeset_id=cs.id, owner_id=owner, name="机场", embedder=emb
    )

    assert uuid.UUID(result["id"]) in graph.list_graph_objects(session, conv.graph_id)


def test_propose_type_reuses_existing_type(session):
    owner = uuid.uuid4()
    _, emb, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    result = tools.propose_type(
        session, changeset_id=cs.id, owner_id=owner, name="客户", embedder=emb
    )
    assert result["id"] == str(customer.id)
    assert result["reused"] is True
    assert changeset.list_ops(session, cs.id) == []


def test_propose_type_adds_reused_type_to_the_current_graph(session):
    owner = uuid.uuid4()
    _, emb, customer, *_ = _setup(session, owner)
    current, cs = _changeset(session, owner)

    tools.propose_type(session, changeset_id=cs.id, owner_id=owner, name="客户", embedder=emb)

    assert customer.id in graph.list_graph_objects(session, current.graph_id)


def test_propose_type_reports_near_duplicates(session):
    owner = uuid.uuid4()
    _, emb, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    result = tools.propose_type(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        name="客户信息",
        embedder=emb,
        reuse_threshold=0.999,
    )
    assert result["similar"]
    assert result["reused"] is False


def test_propose_relation_requires_existing_endpoints(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)
    with pytest.raises(NotFoundError):
        tools.propose_relation(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            source_type_id=customer.id,
            target_type_id=uuid.uuid4(),
            name="供应",
        )


def test_propose_relation_adds_endpoints_and_relation_to_graph(session):
    owner = uuid.uuid4()
    conv, cs = _changeset(session, owner)
    source = ontology.create_object_type(session, owner_id=owner, name="飞机")
    target = ontology.create_object_type(session, owner_id=owner, name="机场")

    result = tools.propose_relation(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="停靠",
    )

    graph_objects = graph.list_graph_objects(session, conv.graph_id)
    assert source.id in graph_objects
    assert target.id in graph_objects
    assert uuid.UUID(result["id"]) in graph.list_graph_relations(session, conv.graph_id)


def test_update_element_requires_a_patch(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)
    with pytest.raises(InvariantViolationError):
        tools.update_element(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            element_kind="object_type",
            element_id=customer.id,
            patch={},
        )


def test_update_element_rejects_field_outside_whitelist(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)
    with pytest.raises(InvariantViolationError):
        tools.update_element(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            element_kind="object_type",
            element_id=customer.id,
            patch={"status": "approved"},
        )


def test_update_element_applies_and_records_snapshot(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    tools.update_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
        patch={"name": "终端客户"},
    )
    assert ontology.get_object_type(session, customer.id).name == "终端客户"
    assert changeset.list_ops(session, cs.id)[0].payload == {
        "kind": "object_type",
        "before": {"name": "客户"},
        "after": {"name": "终端客户"},
    }


def test_remove_element_only_marks(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    tools.remove_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
    )
    assert ontology.get_object_type(session, customer.id) is not None
    assert changeset.list_ops(session, cs.id)[0].op == "delete"
