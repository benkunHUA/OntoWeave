"""`test_element_edit.py` 中 creates 相关测试。"""

import uuid
from typing import get_args

from app.services import changeset, element_edit, graph, ontology
from tests.support.element_edit_fixtures import _graph


def test_apply_edits_builds_a_graph_in_one_batch(session):
    """一次保存里建类型、复用、建关系：与模型走工具写入是同一套规则。"""
    owner = uuid.uuid4()
    g = _graph(session, owner)
    pool_type = ontology.create_object_type(session, owner_id=owner, name="供应商")
    ontology.transition_object_type(session, pool_type.id, "confirmed", owner_id=owner)

    cs = element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.ObjectTypeCreate(name="客户", description="下订单的一方"),
            element_edit.ObjectTypeReuse(object_type_id=pool_type.id),
        ],
        owner_id=owner,
    )

    names = {obj.name for obj in ontology.list_object_types(session, owner_id=owner)}
    assert names == {"客户", "供应商"}
    members = set(graph.list_graph_objects(session, g.id))
    assert {pool_type.id} <= members

    customer = next(
        obj for obj in ontology.list_object_types(session, owner_id=owner) if obj.name == "客户"
    )
    assert customer.id in members

    element_edit.apply_edits(
        session,
        graph_id=g.id,
        ops=[
            element_edit.RelationCreate(
                source_type_id=customer.id,
                target_type_id=pool_type.id,
                name="由客户指定",
            )
        ],
        owner_id=owner,
    )

    assert [rel.name for rel in graph.get_graph_snapshot(session, g.id).relations] == ["由客户指定"]
    assert [(op.op, op.payload["kind"]) for op in changeset.list_ops(session, cs.id)] == [
        ("create", "object_type"),
        ("create", "relation"),
    ]


def test_every_create_op_has_a_handler():
    """新建类 op 必须登记实现：漏登记要在测试里炸，而不是等用户保存时 500。"""
    ops = [
        op
        for op in get_args(element_edit.EditOp)
        if op not in (element_edit.ElementUpdate, element_edit.ElementDelete)
    ]
    handlers = element_edit.write_handlers()

    assert set(ops) <= set(handlers)
    assert set(handlers) <= set(ops)
