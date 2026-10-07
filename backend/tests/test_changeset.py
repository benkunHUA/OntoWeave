import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.main import create_app
from app.services import changeset, conversation, ontology

API = "/api/v1"


@pytest.fixture
def api_client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def _default_user() -> uuid.UUID:
    """接口层全部按默认用户过滤，测试数据也要挂在这个用户下。"""
    return uuid.UUID(get_settings().default_user_id)


def _graph_id(session) -> uuid.UUID:
    """接口层按默认用户过滤，这里也用默认用户建图，保证接口看得见。"""
    return conversation.create_conversation(
        session, owner_id=_default_user(), title="测试"
    ).graph_id


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


def test_changeset_api_confirm_flow(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="客户")
    session.commit()

    cs = api_client.post(f"{API}/graphs/{graph_id}/changesets")
    assert cs.status_code == 201
    cid = cs.json()["id"]

    recorded = api_client.post(
        f"{API}/changesets/{cid}/ops",
        json={
            "op": "create",
            "target_id": str(obj.id),
            "payload": {"kind": "object_type"},
        },
    )
    assert recorded.status_code == 201

    detail = api_client.get(f"{API}/changesets/{cid}")
    assert detail.status_code == 200
    assert len(detail.json()["ops"]) == 1

    confirmed = api_client.post(f"{API}/changesets/{cid}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_changeset_api_reject_flow(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="临时")
    session.commit()

    cid = api_client.post(f"{API}/graphs/{graph_id}/changesets").json()["id"]
    api_client.post(
        f"{API}/changesets/{cid}/ops",
        json={
            "op": "create",
            "target_id": str(obj.id),
            "payload": {"kind": "object_type"},
        },
    )
    rejected = api_client.post(f"{API}/changesets/{cid}/reject")
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"


def test_recording_into_closed_changeset_returns_409(api_client):
    graph_id = api_client.post(f"{API}/conversations", json={"title": "接口测试"}).json()[
        "graph_id"
    ]
    cid = api_client.post(f"{API}/graphs/{graph_id}/changesets").json()["id"]
    api_client.post(f"{API}/changesets/{cid}/confirm")

    resp = api_client.post(f"{API}/changesets/{cid}/ops", json={"op": "create"})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invalid_transition"


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


def test_changeset_is_scoped_to_graph_owner(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="mine")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)

    assert changeset.get_changeset(session, cs.id, owner_id=owner).id == cs.id
    with pytest.raises(NotFoundError):
        changeset.get_changeset(session, cs.id, owner_id=uuid.uuid4())
