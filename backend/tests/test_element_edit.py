import uuid

import pytest

from app.errors import InvariantViolationError, NotFoundError
from app.models import Relation
from app.services import changeset, element_edit, embedding, graph, ontology


def _graph(session, owner_id: uuid.UUID):
    return graph.create_graph(session, owner_id=owner_id, name="测试图")


def test_apply_edits_creates_open_changeset_and_applies(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    prop = ontology.create_property(session, owner_type_id=obj.id, name="客户名", datatype="text")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementUpdate(
                element_kind="object_type",
                element_id=obj.id,
                patch={"name": "终端客户", "description": "重点客户"},
            ),
            element_edit.ElementUpdate(
                element_kind="property",
                element_id=prop.id,
                patch={"name": "客户全称", "datatype": "decimal"},
            ),
        ],
        owner_id=owner,
    )

    assert cs.status == changeset.OPEN
    ops = changeset.list_ops(session, cs.id)
    assert [op.op for op in ops] == ["update", "update"]
    assert obj.name == "终端客户"
    assert obj.description == "重点客户"
    assert prop.name == "客户全称"
    assert prop.datatype == "decimal"


def test_apply_edits_adds_and_removes_properties(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    old_prop = ontology.create_property(session, owner_type_id=obj.id, name="旧字段")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.PropertyCreate(owner_type_id=obj.id, name="新增字段", datatype="integer"),
            element_edit.ElementDelete(element_kind="property", element_id=old_prop.id),
        ],
        owner_id=owner,
    )

    ops = changeset.list_ops(session, cs.id)
    assert [op.op for op in ops] == ["create", "delete"]
    assert [op.payload["kind"] for op in ops] == ["property", "property"]

    names = sorted(prop.name for prop in ontology.list_properties(session, obj.id))
    assert names == ["新增字段", "旧字段"]

    # 撤销后：新增字段被删掉，标记删除的旧字段仍在（草稿阶段没真删）。
    changeset.reject_changeset(session, cs.id)
    assert [prop.name for prop in ontology.list_properties(session, obj.id)] == ["旧字段"]


def test_apply_edits_confirms_added_property(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[element_edit.PropertyCreate(owner_type_id=obj.id, name="等级", datatype="integer")],
        owner_id=owner,
    )
    created_id = changeset.list_ops(session, cs.id)[0].target_id

    changeset.confirm_changeset(session, cs.id)

    prop = ontology.list_properties(session, obj.id)[0]
    assert prop.id == created_id
    assert prop.status == "confirmed"


def test_apply_edits_reuses_existing_open_changeset(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    existing = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[element_edit.ElementUpdate("object_type", obj.id, {"name": "终端客户"})],
        owner_id=owner,
    )

    assert cs.id == existing.id
    assert len(changeset.list_ops(session, existing.id)) == 1


def test_apply_edits_requires_at_least_one_edit(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(session, graph_id=g.id, ops=[], owner_id=owner)


def test_apply_edits_rejects_unknown_field(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("object_type", obj.id, {"status": "confirmed"})],
            owner_id=owner,
        )


def test_apply_edits_hides_other_owners_elements(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    foreign = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="别人的对象")

    with pytest.raises(NotFoundError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("object_type", foreign.id, {"name": "改名"})],
            owner_id=owner,
        )


def test_create_property_hides_foreign_owner_type(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    foreign = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="别人的对象")
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)

    with pytest.raises(NotFoundError):
        element_edit.create_property(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            owner_type_id=foreign.id,
            name="偷偷加的字段",
        )


def test_record_update_refreshes_embedding_when_name_changes(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    emb = embedding.HashingEmbedder()
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed")
    embedding.refresh_object_type_embedding(session, object_type_id=obj.id, embedder=emb)
    before = list(obj.embedding or [])
    cs = changeset.create_changeset(session, graph_id=g.id, owner_id=owner)

    element_edit.record_update(
        session,
        changeset_id=cs.id,
        element_kind="object_type",
        element_id=obj.id,
        patch={"name": "终端客户"},
        owner_id=owner,
        embedder=emb,
    )

    assert obj.embedding is not None
    assert list(obj.embedding) != before


def test_update_rejects_unknown_datatype(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    prop = ontology.create_property(session, owner_type_id=obj.id, name="客户名")

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("property", prop.id, {"datatype": "bogus"})],
            owner_id=owner,
        )


def test_update_rejects_second_primary_key(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    obj = ontology.create_object_type(session, owner_id=owner, name="订单")
    ontology.create_property(session, owner_type_id=obj.id, name="订单号", is_primary_key=True)
    other = ontology.create_property(session, owner_type_id=obj.id, name="流水号")

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("property", other.id, {"is_primary_key": True})],
            owner_id=owner,
        )


def _relation(session, owner: uuid.UUID):
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    return ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )


def test_update_relation_fields(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    rel = _relation(session, owner)

    element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ElementUpdate(
                "relation",
                rel.id,
                {"cardinality": "one_to_many", "forward_label": "订单", "reverse_label": "客户"},
            )
        ],
        owner_id=owner,
    )

    assert rel.cardinality == "one_to_many"
    assert rel.forward_label == "订单"
    assert rel.reverse_label == "客户"


def test_update_relation_rejects_unknown_cardinality(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    rel = _relation(session, owner)

    with pytest.raises(InvariantViolationError):
        element_edit.apply_edits(
            session,
            graph_id=g.id,
            ops=[element_edit.ElementUpdate("relation", rel.id, {"cardinality": "lots"})],
            owner_id=owner,
        )


def test_delete_relation_confirms_away(session):
    owner = uuid.uuid4()
    g = _graph(session, owner)
    rel = _relation(session, owner)

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[element_edit.ElementDelete("relation", rel.id)],
        owner_id=owner,
    )

    # 草稿阶段只是标记，确认后才真删。
    assert session.get(Relation, rel.id) is not None
    changeset.confirm_changeset(session, cs.id, owner_id=owner)
    assert session.get(Relation, rel.id) is None
