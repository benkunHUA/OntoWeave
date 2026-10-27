"""`test_changeset.py` 中 lifecycle 相关测试。"""

import uuid

import pytest

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.services import changeset, conversation, ontology
from tests.support.changeset_fixtures import _graph_id


def test_confirm_promotes_created_elements(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    changeset.confirm_changeset(session, cs.id)

    assert changeset.get_changeset(session, cs.id).status == "confirmed"
    assert ontology.get_object_type(session, obj.id).status == "confirmed"


def test_reject_deletes_created_elements(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    changeset.reject_changeset(session, cs.id)

    assert changeset.get_changeset(session, cs.id).status == "rejected"
    with pytest.raises(NotFoundError):
        ontology.get_object_type(session, obj.id)


def test_reject_restores_updated_element(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    changeset.record_update(
        session,
        changeset_id=cs.id,
        element_kind="object_type",
        element_id=obj.id,
        patch={"name": "终端客户", "description": "重点客户"},
    )
    assert obj.name == "终端客户"
    assert obj.description == "重点客户"

    changeset.reject_changeset(session, cs.id)

    restored = ontology.get_object_type(session, obj.id)
    assert restored.name == "客户"
    assert restored.description is None


def test_confirm_reapplies_the_recorded_after_snapshot(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    changeset.record_update(
        session,
        changeset_id=cs.id,
        element_kind="object_type",
        element_id=obj.id,
        patch={"name": "终端客户"},
    )

    obj.name = "外部误改"
    changeset.confirm_changeset(session, cs.id)

    assert ontology.get_object_type(session, obj.id).name == "终端客户"


def test_confirm_deletes_element_flagged_for_deletion(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="delete",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    # 草稿阶段不真删
    assert ontology.get_object_type(session, obj.id) is not None

    changeset.confirm_changeset(session, cs.id)

    with pytest.raises(NotFoundError):
        ontology.get_object_type(session, obj.id)


def test_cannot_confirm_twice(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    changeset.confirm_changeset(session, cs.id)
    with pytest.raises(InvalidTransitionError):
        changeset.confirm_changeset(session, cs.id)


def test_confirm_rejects_dangling_target(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=uuid.uuid4(),
        payload={"kind": "object_type"},
    )
    with pytest.raises(NotFoundError):
        changeset.confirm_changeset(session, cs.id)


def test_reject_cannot_restore_field_outside_whitelist(session):
    """before 里的键名是调用方可控的，不能拿它直接写属性。"""
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    # record_op 阶段就该挡住
    with pytest.raises(InvariantViolationError):
        changeset.record_op(
            session,
            changeset_id=cs.id,
            op="update",
            target_id=obj.id,
            payload={"kind": "object_type", "before": {"status": "approved"}},
        )


def test_reject_still_refuses_tampered_op(session):
    """就算绕过 record_op 直接塞进数据库，reject 也要拒绝。"""
    from app.models import ChangesetOp

    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    session.add(
        ChangesetOp(
            changeset_id=cs.id,
            op="update",
            target_id=obj.id,
            payload={"kind": "object_type", "before": {"status": "approved"}},
        )
    )
    session.flush()

    with pytest.raises(InvariantViolationError):
        changeset.reject_changeset(session, cs.id)

    assert ontology.get_object_type(session, obj.id).status == "draft"
    assert changeset.get_changeset(session, cs.id).status == "open"


def test_confirm_refuses_element_not_in_draft(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed")
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    with pytest.raises(InvalidTransitionError):
        changeset.confirm_changeset(session, cs.id)
    # 校验不通过时不应该改动任何东西
    assert changeset.get_changeset(session, cs.id).status == "open"


def test_deleting_a_type_and_its_children_does_not_double_delete(session):
    """同一次改动里既新建对象类型、又新建它上面的关系和属性，撤销/确认时删一遍就够。

    库里 relation / property 对 object_type 是 ON DELETE CASCADE：先删父行的话
    子行会被级联带走，ORM 再删一次就是 0 匹配。分阶段「先子后父」就不会。
    """
    import warnings

    from sqlalchemy.exc import SAWarning

    owner = uuid.uuid4()
    cs = changeset.create_changeset(
        session, graph_id=conversation.create_conversation(session, owner_id=owner).resource_id
    )
    spot = ontology.create_object_type(session, owner_id=owner, name="景点")
    trip = ontology.create_object_type(session, owner_id=owner, name="行程")
    rel = ontology.create_relation(
        session, owner_id=owner, source_type_id=trip.id, target_type_id=spot.id, name="包含"
    )
    prop = ontology.create_property(session, owner_type_id=spot.id, name="描述")
    for target, kind in ((spot.id, "object_type"), (rel.id, "relation"), (prop.id, "property")):
        changeset.record_op(
            session, changeset_id=cs.id, op="create", target_id=target, payload={"kind": kind}
        )

    with warnings.catch_warnings():
        warnings.simplefilter("error", SAWarning)
        changeset.reject_changeset(session, cs.id, owner_id=owner)
        session.flush()
