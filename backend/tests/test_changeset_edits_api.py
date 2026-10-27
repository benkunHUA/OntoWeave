"""`test_changeset.py` 中 edits_api 相关测试。"""

import uuid

import pytest

from app.services import graph, ontology
from tests.support.changeset_fixtures import API, _default_user, _graph_id, build_client


@pytest.fixture
def api_client(session_factory):
    return build_client(session_factory)


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
