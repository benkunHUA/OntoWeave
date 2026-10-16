import uuid

import pytest

from app.agent.tools.ontology import ops
from app.errors import InvariantViolationError, NotFoundError
from app.services import changeset, conversation, embedding, graph, ontology


def _setup(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="工具测试")
    emb = embedding.HashingEmbedder()
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    for obj in (customer, order):
        # 只有确认过的类型才有向量、才会被检索到——和线上一致。
        ontology.transition_object_type(session, obj.id, "confirmed", owner_id=owner)
        embedding.refresh_object_type_embedding(session, object_type_id=obj.id, embedder=emb)
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


def test_get_subgraph_walks_one_hop(session):
    owner = uuid.uuid4()
    conv, _, customer, _, _ = _setup(session, owner)
    result = ops.get_subgraph(
        session, graph_id=conv.graph_id, object_type_id=customer.id, owner_id=owner
    )
    assert sorted(node["name"] for node in result["nodes"]) == ["客户", "订单"]
    assert [rel["name"] for rel in result["relations"]] == ["下订单"]


def test_get_subgraph_rejects_unknown_node(session):
    owner = uuid.uuid4()
    conv, *_ = _setup(session, owner)
    with pytest.raises(NotFoundError):
        ops.get_subgraph(
            session, graph_id=conv.graph_id, object_type_id=uuid.uuid4(), owner_id=owner
        )


def _changeset(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="写工具")
    return conv, changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)


def test_propose_type_creates_draft_and_records_op(session):
    owner = uuid.uuid4()
    _, emb, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    result = ops.propose_type(session, changeset_id=cs.id, owner_id=owner, name="供应商")
    obj = ontology.get_object_type(session, uuid.UUID(result["id"]))
    assert obj.status == "draft"
    assert obj.embedding is None
    assert [op.op for op in changeset.list_ops(session, cs.id)] == ["create"]


def test_propose_type_adds_created_type_to_graph(session):
    owner = uuid.uuid4()
    conv, cs = _changeset(session, owner)

    result = ops.propose_type(session, changeset_id=cs.id, owner_id=owner, name="机场")

    assert uuid.UUID(result["id"]) in graph.list_graph_objects(session, conv.graph_id)


def test_propose_type_creates_new_draft_even_when_similar_exists(session):
    owner = uuid.uuid4()
    _, emb, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    result = ops.propose_type(session, changeset_id=cs.id, owner_id=owner, name="客户信息")

    assert result["name"] == "客户信息"
    assert "reused" not in result
    assert [op.op for op in changeset.list_ops(session, cs.id)] == ["create"]


def test_reuse_type_adds_existing_type_to_the_current_graph(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    current, cs = _changeset(session, owner)

    result = ops.reuse_type(session, changeset_id=cs.id, owner_id=owner, object_type_id=customer.id)

    assert result["id"] == str(customer.id)
    assert result["reused"] is True
    assert customer.id in graph.list_graph_objects(session, current.graph_id)


def test_reuse_type_does_not_record_a_changeset_op(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    ops.reuse_type(session, changeset_id=cs.id, owner_id=owner, object_type_id=customer.id)

    assert changeset.list_ops(session, cs.id) == []


def test_reuse_type_rejects_draft_type(session):
    owner = uuid.uuid4()
    draft = ontology.create_object_type(session, owner_id=owner, name="还没定稿的客户")
    _, cs = _changeset(session, owner)

    with pytest.raises(InvariantViolationError):
        ops.reuse_type(session, changeset_id=cs.id, owner_id=owner, object_type_id=draft.id)


def test_reuse_type_rejects_another_owners_type(session):
    owner, other = uuid.uuid4(), uuid.uuid4()
    outsider = ontology.create_object_type(session, owner_id=other, name="别家的")
    _, cs = _changeset(session, owner)

    with pytest.raises(NotFoundError):
        ops.reuse_type(session, changeset_id=cs.id, owner_id=owner, object_type_id=outsider.id)


def test_propose_relation_requires_existing_endpoints(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)
    with pytest.raises(NotFoundError):
        ops.propose_relation(
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

    result = ops.propose_relation(
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
    _, emb, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)
    with pytest.raises(InvariantViolationError):
        ops.update_element(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            element_kind="object_type",
            element_id=customer.id,
            patch={},
            embedder=emb,
        )


def test_update_element_rejects_field_outside_whitelist(session):
    owner = uuid.uuid4()
    _, emb, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)
    with pytest.raises(InvariantViolationError):
        ops.update_element(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            element_kind="object_type",
            element_id=customer.id,
            patch={"status": "approved"},
            embedder=emb,
        )


def test_update_element_applies_and_records_snapshot(session):
    owner = uuid.uuid4()
    _, emb, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    ops.update_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
        patch={"name": "终端客户"},
        embedder=emb,
    )
    assert ontology.get_object_type(session, customer.id).name == "终端客户"
    assert changeset.list_ops(session, cs.id)[0].payload == {
        "kind": "object_type",
        "before": {"name": "客户"},
        "after": {"name": "终端客户"},
    }


def test_update_element_reembeds_object_type_on_description_change(session):
    owner = uuid.uuid4()
    _, emb, customer, *_ = _setup(session, owner)
    before = list(ontology.get_object_type(session, customer.id).embedding)
    _, cs = _changeset(session, owner)

    ops.update_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
        patch={"description": "会下订单并把货退回来的终端买家"},
        embedder=emb,
    )

    after = ontology.get_object_type(session, customer.id).embedding
    assert after is not None
    assert list(after) != before


def test_update_element_survives_embedding_failure(session, failing_embedder):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    ops.update_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
        patch={"name": "终端客户"},
        embedder=failing_embedder,
    )

    assert ontology.get_object_type(session, customer.id).name == "终端客户"


def test_remove_element_only_marks(session):
    owner = uuid.uuid4()
    _, _, customer, *_ = _setup(session, owner)
    _, cs = _changeset(session, owner)

    ops.remove_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
    )
    assert ontology.get_object_type(session, customer.id) is not None
    assert changeset.list_ops(session, cs.id)[0].op == "delete"
