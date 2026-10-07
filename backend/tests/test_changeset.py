import uuid

import pytest

from app.errors import InvalidTransitionError, NotFoundError
from app.services import changeset, conversation, ontology


def _graph_id(session) -> uuid.UUID:
    return conversation.create_conversation(session, owner_id=uuid.uuid4(), title="测试").graph_id


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
    before = {"name": "客户", "description": None}

    obj.name = "终端客户"
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="update",
        target_id=obj.id,
        payload={"kind": "object_type", "before": before},
    )

    changeset.reject_changeset(session, cs.id)

    assert ontology.get_object_type(session, obj.id).name == "客户"


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
