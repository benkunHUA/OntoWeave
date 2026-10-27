"""`test_actions_api.py` 中 creation 相关测试。"""

import uuid

import pytest

from app.schemas.changeset import ElementKind
from app.services import element_kinds
from tests.support.actions_api_fixtures import (
    API,
    _create_action,
    _create_type,
    _edits,
    _graph_id,
    _ops,
    _snapshot_actions,
    build_client,
)


@pytest.fixture
def client(session_factory):
    return build_client(session_factory)


def test_element_kind_literal_covers_the_registry():
    """接口层的判别联合不能漏掉任何已登记的元素种类，也不能多写。"""
    assert set(ElementKind.__args__) == set(element_kinds.REGISTRY)


def test_create_action_type_lands_on_the_canvas_as_a_draft(client):
    graph_id = _graph_id(client)
    action_id = _create_action(client, graph_id, "指派工单")

    second = _edits(client, graph_id, {"op": "create_action_type", "name": "关闭工单"})
    assert _ops(second)[-1] == ("create", "action_type")

    briefs = {item["id"]: item for item in _snapshot_actions(client, graph_id)}
    assert briefs[action_id] == {
        "id": action_id,
        "name": "指派工单",
        "description": None,
        "status": "draft",
        "involves": [],
    }
    assert len(briefs) == 2


def test_action_brief_lists_what_the_action_touches(client):
    """involves = 对象引用参数指向的类型 + 规则作用的类型与关系。"""
    graph_id = _graph_id(client)
    customer_id = _create_type(client, graph_id, "客户")
    order_id = _create_type(client, graph_id, "订单")
    relation_id = _edits(
        client,
        graph_id,
        {
            "op": "create_relation",
            "source_type_id": customer_id,
            "target_type_id": order_id,
            "name": "下订单",
        },
    )["ops"][-1]["target_id"]
    action_id = _create_action(client, graph_id, "下订单")

    created = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "客户",
            "kind": "object_ref",
            "target_object_type_id": customer_id,
        },
    )
    customer_parameter_id = created["ops"][-1]["target_id"]
    created = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "订单",
            "kind": "object_ref",
            "target_object_type_id": order_id,
        },
    )
    order_parameter_id = created["ops"][-1]["target_id"]
    _edits(
        client,
        graph_id,
        {
            "op": "add_action_rule",
            "action_type_id": action_id,
            "rule_kind": "create_link",
            "target_relation_id": relation_id,
            "source_parameter_id": customer_parameter_id,
            "target_parameter_id": order_parameter_id,
        },
    )

    briefs = {item["id"]: item for item in _snapshot_actions(client, graph_id)}
    involves = briefs[action_id]["involves"]
    assert {(item["kind"], item["id"]) for item in involves} == {
        ("object_type", customer_id),
        ("object_type", order_id),
        ("relation", relation_id),
    }


def test_confirming_a_changeset_confirms_the_whole_action(client):
    graph_id = _graph_id(client)
    action_id = _create_action(client, graph_id, "关闭工单")
    changeset_id = client.get(f"{API}/graphs/{graph_id}/changesets").json()[0]["id"]

    confirmed = client.post(f"{API}/changesets/{changeset_id}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"
    assert client.get(f"{API}/actions/{action_id}").json()["status"] == "confirmed"


def test_list_actions_filters_by_query_and_status(client):
    graph_id = _graph_id(client)
    _create_action(client, graph_id, "指派工单")
    _create_action(client, graph_id, "关闭订单")

    listed = client.get(f"{API}/actions", params={"q": "工单"}).json()
    assert [item["name"] for item in listed] == ["指派工单"]
    assert client.get(f"{API}/actions", params={"q": "不存在"}).json() == []
    assert len(client.get(f"{API}/actions", params={"status": "draft"}).json()) == 2
    assert client.get(f"{API}/actions", params={"status": "confirmed"}).json() == []


def test_unknown_action_returns_404(client):
    response = client.get(f"{API}/actions/{uuid.uuid4()}")
    assert response.status_code == 404
    assert response.json()["error"] == "not_found"


@pytest.mark.parametrize(
    "edit",
    [
        {"op": "not_an_op"},
        {"op": "create_action_type"},
        {"op": "update", "element_kind": "banana", "element_id": str(uuid.uuid4())},
    ],
)
def test_malformed_edits_are_rejected(client, edit):
    """判别联合与形状在接口层就挡住：不在枚举里的 op / 种类一律 422。"""
    graph_id = _graph_id(client)
    response = client.post(f"{API}/graphs/{graph_id}/edits", json={"edits": [edit]})
    assert response.status_code == 422
