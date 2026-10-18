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
from app.models import Conversation, Graph
from app.services import changeset, conversation, graph, ontology

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
    ).resource_id


def _delete_conversation_and_graph(
    session: Session, *, conversation_id: uuid.UUID, graph_id: uuid.UUID
) -> None:
    session.execute(delete(Conversation).where(Conversation.id == conversation_id))
    session.execute(delete(Graph).where(Graph.id == graph_id))
    session.commit()


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
        "resource_id"
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
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)

    assert changeset.get_changeset(session, cs.id, owner_id=owner).id == cs.id
    with pytest.raises(NotFoundError):
        changeset.get_changeset(session, cs.id, owner_id=uuid.uuid4())


def test_concurrent_confirm_only_one_wins(engine):
    factory = build_session_factory(engine)
    owner = _default_user()
    with factory() as setup:
        conv = conversation.create_conversation(session=setup, owner_id=owner, title="并发确认")
        cs = changeset.create_changeset(session=setup, graph_id=conv.resource_id, owner_id=owner)
        setup.commit()
        conversation_id = conv.id
        graph_id = conv.resource_id
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
            _delete_conversation_and_graph(
                cleanup, conversation_id=conversation_id, graph_id=graph_id
            )


def test_record_op_waits_for_the_changeset_lock(engine):
    factory = build_session_factory(engine)
    owner = _default_user()
    with factory() as setup:
        conv = conversation.create_conversation(session=setup, owner_id=owner, title="并发记录")
        cs = changeset.create_changeset(session=setup, graph_id=conv.resource_id, owner_id=owner)
        setup.commit()
        conversation_id = conv.id
        graph_id = conv.resource_id
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
            _delete_conversation_and_graph(
                cleanup, conversation_id=conversation_id, graph_id=graph_id
            )


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


def test_edit_endpoint_updates_elements(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="客户")
    prop = ontology.create_property(session, owner_type_id=obj.id, name="客户名", datatype="text")
    graph.add_object_to_graph(session, graph_id, obj.id, owner_id=_default_user())
    session.commit()

    resp = api_client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {
                    "op": "update",
                    "element_kind": "object_type",
                    "element_id": str(obj.id),
                    "patch": {"name": "终端客户", "description": "重点客户"},
                },
                {
                    "op": "update",
                    "element_kind": "property",
                    "element_id": str(prop.id),
                    "patch": {"name": "客户全称", "datatype": "decimal"},
                },
            ]
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "open"
    assert [op["op"] for op in body["ops"]] == ["update", "update"]

    snapshot = api_client.get(f"{API}/graphs/{graph_id}/snapshot").json()
    node = next(item for item in snapshot["nodes"] if item["id"] == str(obj.id))
    assert node["name"] == "终端客户"
    assert node["description"] == "重点客户"
    field = next(item for item in node["properties"] if item["id"] == str(prop.id))
    assert field["name"] == "客户全称"
    assert field["datatype"] == "decimal"


def test_edit_endpoint_adds_and_removes_properties(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="客户")
    old_prop = ontology.create_property(session, owner_type_id=obj.id, name="旧字段")
    graph.add_object_to_graph(session, graph_id, obj.id, owner_id=_default_user())
    session.commit()

    resp = api_client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {
                    "op": "create_property",
                    "owner_type_id": str(obj.id),
                    "name": "等级",
                    "datatype": "integer",
                },
                {
                    "op": "delete",
                    "element_kind": "property",
                    "element_id": str(old_prop.id),
                },
            ]
        },
    )
    assert resp.status_code == 200
    ops = resp.json()["ops"]
    assert [op["op"] for op in ops] == ["create", "delete"]

    snapshot = api_client.get(f"{API}/graphs/{graph_id}/snapshot").json()
    node = next(item for item in snapshot["nodes"] if item["id"] == str(obj.id))
    names = sorted(field["name"] for field in node["properties"])
    assert names == ["旧字段", "等级"]

    # 确认后：新增字段保留并转正，标记删除的旧字段被真删。
    confirmed = api_client.post(f"{API}/changesets/{resp.json()['id']}/confirm")
    assert confirmed.status_code == 200
    snapshot = api_client.get(f"{API}/graphs/{graph_id}/snapshot").json()
    node = next(item for item in snapshot["nodes"] if item["id"] == str(obj.id))
    assert [field["name"] for field in node["properties"]] == ["等级"]


def test_edit_endpoint_reuses_open_changeset(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="客户")
    session.commit()
    cid = api_client.post(f"{API}/graphs/{graph_id}/changesets").json()["id"]

    resp = api_client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {
                    "op": "update",
                    "element_kind": "object_type",
                    "element_id": str(obj.id),
                    "patch": {"name": "终端客户"},
                }
            ]
        },
    )
    assert resp.status_code == 200
    assert resp.json()["id"] == cid


def test_edit_endpoint_rejects_unknown_field(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=_default_user(), name="客户")
    session.commit()

    resp = api_client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {
                    "op": "update",
                    "element_kind": "object_type",
                    "element_id": str(obj.id),
                    "patch": {"status": "confirmed"},
                }
            ]
        },
    )
    assert resp.status_code == 409
    assert resp.json()["error"] == "invariant_violation"


def test_edit_endpoint_requires_at_least_one_edit(api_client, session):
    graph_id = _graph_id(session)
    resp = api_client.post(f"{API}/graphs/{graph_id}/edits", json={"edits": []})
    assert resp.status_code == 422


def test_edit_endpoint_rejects_unknown_op(api_client, session):
    graph_id = _graph_id(session)
    resp = api_client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={"edits": [{"op": "explode", "element_id": str(uuid.uuid4())}]},
    )
    assert resp.status_code == 422


def test_edit_endpoint_updates_relation(api_client, session):
    graph_id = _graph_id(session)
    owner = _default_user()
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    graph.add_object_to_graph(session, graph_id, customer.id, owner_id=owner)
    graph.add_object_to_graph(session, graph_id, order.id, owner_id=owner)
    graph.add_relation_to_graph(session, graph_id, rel.id, owner_id=owner)
    session.commit()

    resp = api_client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={
            "edits": [
                {
                    "op": "update",
                    "element_kind": "relation",
                    "element_id": str(rel.id),
                    "patch": {
                        "cardinality": "one_to_many",
                        "forward_label": "订单",
                        "reverse_label": "客户",
                    },
                }
            ]
        },
    )
    assert resp.status_code == 200

    snapshot = api_client.get(f"{API}/graphs/{graph_id}/snapshot").json()
    edge = next(item for item in snapshot["relations"] if item["id"] == str(rel.id))
    assert edge["cardinality"] == "one_to_many"
    assert edge["forward_label"] == "订单"
    assert edge["reverse_label"] == "客户"
