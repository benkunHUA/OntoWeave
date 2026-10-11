import uuid
from concurrent.futures import ThreadPoolExecutor
from threading import Event
from time import sleep

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import delete
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.db import build_session_factory
from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.main import create_app
from app.models import Graph
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


def test_concurrent_confirm_only_one_wins(engine):
    factory = build_session_factory(engine)
    owner = _default_user()
    with factory() as setup:
        conv = conversation.create_conversation(session=setup, owner_id=owner, title="并发确认")
        cs = changeset.create_changeset(session=setup, graph_id=conv.graph_id, owner_id=owner)
        setup.commit()
        graph_id = conv.graph_id
        changeset_id = cs.id

    started = Event()

    def confirm_in_second_session() -> str:
        with factory() as session:
            started.set()
            try:
                changeset.confirm_changeset(session, changeset_id, owner_id=owner)
                session.commit()
                return "confirmed"
            except InvalidTransitionError:
                session.rollback()
                return "conflict"

    try:
        with factory() as first:
            changeset.confirm_changeset(first, changeset_id, owner_id=owner)
            with ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(confirm_in_second_session)
                assert started.wait(timeout=2)
                sleep(0.2)
                assert not future.done()
                first.commit()
                assert future.result(timeout=5) == "conflict"
    finally:
        with factory() as cleanup:
            cleanup.execute(delete(Graph).where(Graph.id == graph_id))
            cleanup.commit()


def test_record_op_waits_for_the_changeset_lock(engine):
    factory = build_session_factory(engine)
    owner = _default_user()
    with factory() as setup:
        conv = conversation.create_conversation(session=setup, owner_id=owner, title="并发记录")
        cs = changeset.create_changeset(session=setup, graph_id=conv.graph_id, owner_id=owner)
        setup.commit()
        graph_id = conv.graph_id
        changeset_id = cs.id

    started = Event()

    def record_in_second_session() -> str:
        with factory() as session:
            started.set()
            try:
                changeset.record_op(
                    session,
                    changeset_id=changeset_id,
                    op="create",
                    target_id=uuid.uuid4(),
                    payload={"kind": "object_type"},
                    owner_id=owner,
                )
                session.commit()
                return "recorded"
            except InvalidTransitionError:
                session.rollback()
                return "closed"

    try:
        with factory() as first:
            changeset.confirm_changeset(first, changeset_id, owner_id=owner)
            with ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(record_in_second_session)
                assert started.wait(timeout=2)
                sleep(0.2)
                assert not future.done()
                first.commit()
                assert future.result(timeout=5) == "closed"
    finally:
        with factory() as cleanup:
            cleanup.execute(delete(Graph).where(Graph.id == graph_id))
            cleanup.commit()


def test_deleting_a_type_and_its_children_does_not_double_delete(session):
    """同一次改动里既新建对象类型、又新建它上面的关系和属性，撤销/确认时删一遍就够。

    库里 relation / property 对 object_type 是 ON DELETE CASCADE：先删父行的话
    子行会被级联带走，ORM 再删一次就是 0 匹配。分阶段「先子后父」就不会。
    """
    import warnings

    from sqlalchemy.exc import SAWarning

    owner = uuid.uuid4()
    cs = changeset.create_changeset(
        session, graph_id=conversation.create_conversation(session, owner_id=owner).graph_id
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
