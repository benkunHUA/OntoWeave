"""`test_changeset.py` 中 store 相关测试。"""

import uuid

import pytest

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.services import changeset, conversation, ontology
from tests.support.changeset_fixtures import _graph_id


def test_create_changeset_is_open(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    assert cs.status == "open"
    assert cs.id is not None


def test_record_op_and_list(session):
    graph_id = _graph_id(session)
    cs = changeset.create_changeset(session, graph_id=graph_id)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    ops = changeset.list_ops(session, cs.id)
    assert len(ops) == 1
    assert ops[0].op == "create"
    assert ops[0].target_id == obj.id


def test_cannot_record_op_into_closed_changeset(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    changeset.confirm_changeset(session, cs.id)
    with pytest.raises(InvalidTransitionError):
        changeset.record_op(session, changeset_id=cs.id, op="create")


def test_missing_changeset_raises(session):
    with pytest.raises(NotFoundError):
        changeset.get_changeset(session, uuid.uuid4())


def test_list_changesets_filters_by_graph(session):
    graph_id = _graph_id(session)
    other = _graph_id(session)
    ours = changeset.create_changeset(session, graph_id=graph_id)
    changeset.create_changeset(session, graph_id=other)

    ids = [cs.id for cs in changeset.list_changesets(session, graph_id=graph_id)]
    assert ids == [ours.id]


def test_record_update_collects_before_and_applies_after(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    op = changeset.record_update(
        session,
        changeset_id=cs.id,
        element_kind="object_type",
        element_id=obj.id,
        patch={"name": "终端客户"},
    )

    assert ontology.get_object_type(session, obj.id).name == "终端客户"
    assert op.payload == {
        "kind": "object_type",
        "before": {"name": "客户"},
        "after": {"name": "终端客户"},
    }


def test_record_update_rejects_stale_before_snapshot(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    with pytest.raises(InvariantViolationError):
        changeset.record_op(
            session,
            changeset_id=cs.id,
            op="update",
            target_id=obj.id,
            payload={
                "kind": "object_type",
                "before": {"name": "旧名字"},
                "after": {"name": "终端客户"},
            },
        )

    assert ontology.get_object_type(session, obj.id).name == "客户"


def test_record_op_rejects_unknown_op(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    with pytest.raises(InvariantViolationError):
        changeset.record_op(
            session,
            changeset_id=cs.id,
            op="createe",
            target_id=obj.id,
            payload={"kind": "object_type"},
        )


def test_record_op_requires_target_and_kind(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    with pytest.raises(InvariantViolationError):
        changeset.record_op(session, changeset_id=cs.id, op="create")
    with pytest.raises(InvariantViolationError):
        changeset.record_op(session, changeset_id=cs.id, op="create", target_id=uuid.uuid4())
    with pytest.raises(InvariantViolationError):
        changeset.record_op(
            session,
            changeset_id=cs.id,
            op="create",
            target_id=uuid.uuid4(),
            payload={"kind": "not_a_kind"},
        )


def test_changeset_is_scoped_to_graph_owner(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="mine")
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)

    assert changeset.get_changeset(session, cs.id, owner_id=owner).id == cs.id
    with pytest.raises(NotFoundError):
        changeset.get_changeset(session, cs.id, owner_id=uuid.uuid4())
