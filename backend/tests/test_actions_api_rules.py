"""`test_actions_api.py` 中 rules 相关测试。"""

import pytest

from tests.support.actions_api_fixtures import (
    API,
    _confirm,
    _create_action,
    _create_type,
    _edits,
    _graph_id,
    _ops,
    build_client,
)


@pytest.fixture
def client(session_factory):
    return build_client(session_factory)


def test_shared_element_used_by_a_confirmed_action_cannot_be_deleted(client):
    """删除保护不区分草稿与已确认：被已确认的动作引用着的对象类型一样删不掉。"""
    graph_id = _graph_id(client)
    order_id = _create_type(client, graph_id, "订单")
    action_id = _create_action(client, graph_id, "下订单")
    _edits(
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
    changeset_id = client.get(f"{API}/graphs/{graph_id}/changesets").json()[0]["id"]
    assert client.post(f"{API}/changesets/{changeset_id}/confirm").status_code == 200

    response = client.post(
        f"{API}/graphs/{graph_id}/edits",
        json={"edits": [{"op": "delete", "element_kind": "object_type", "element_id": order_id}]},
    )
    assert response.status_code == 409
    assert "still used by action definitions" in response.json()["message"]


def test_add_action_rule_assignment_appends_to_an_existing_rule(client):
    """给已有规则补一条赋值：界面上「新增赋值」发的就是这一条 op。"""
    graph_id = _graph_id(client)
    order_id = _create_type(client, graph_id, "订单")
    property_id = _edits(
        client,
        graph_id,
        {"op": "create_property", "owner_type_id": order_id, "name": "备注", "datatype": "text"},
    )["ops"][-1]["target_id"]
    action_id = _create_action(client, graph_id, "关闭工单")
    parameter_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "订单",
            "kind": "object_ref",
            "target_object_type_id": order_id,
        },
    )["ops"][-1]["target_id"]
    rule_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_rule",
            "action_type_id": action_id,
            "rule_kind": "modify_object",
            "target_object_type_id": order_id,
            "source_parameter_id": parameter_id,
        },
    )["ops"][-1]["target_id"]

    third = _edits(
        client,
        graph_id,
        {
            "op": "add_action_rule_assignment",
            "rule_id": rule_id,
            "target_property_id": property_id,
            "value_source": "static",
            "static_value": "已关闭",
        },
    )
    assert _ops(third)[-1] == ("create", "action_rule_assignment")

    rule = client.get(f"{API}/actions/{action_id}").json()["rules"][0]
    assert [
        (item["target_property_id"], item["value_source"], item["static_value"])
        for item in rule["assignments"]
    ] == [(property_id, "static", "已关闭")]


def test_delete_then_add_an_assignment_for_the_same_property_in_one_changeset(client):
    """同一个改动集里删掉一条赋值、再补一条同属性的：草稿态的删除不该还占着这个属性。"""
    graph_id = _graph_id(client)
    order_id = _create_type(client, graph_id, "订单")
    property_id = _edits(
        client,
        graph_id,
        {"op": "create_property", "owner_type_id": order_id, "name": "备注", "datatype": "text"},
    )["ops"][-1]["target_id"]
    action_id = _create_action(client, graph_id, "关闭工单")
    parameter_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "订单",
            "kind": "object_ref",
            "target_object_type_id": order_id,
        },
    )["ops"][-1]["target_id"]
    rule_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_rule",
            "action_type_id": action_id,
            "rule_kind": "modify_object",
            "target_object_type_id": order_id,
            "source_parameter_id": parameter_id,
        },
    )["ops"][-1]["target_id"]
    assignment_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_rule_assignment",
            "rule_id": rule_id,
            "target_property_id": property_id,
            "value_source": "static",
            "static_value": "旧值",
        },
    )["ops"][-1]["target_id"]

    _edits(
        client,
        graph_id,
        {"op": "delete", "element_kind": "action_rule_assignment", "element_id": assignment_id},
        {
            "op": "add_action_rule_assignment",
            "rule_id": rule_id,
            "target_property_id": property_id,
            "value_source": "static",
            "static_value": "新值",
        },
    )

    # 确认之后新记录是这条规则唯一的赋值；确认前库里那两行都还在（删除是延后的），
    # 界面按改动集把标删的那行收起来。
    _confirm(client, graph_id)
    rule = client.get(f"{API}/actions/{action_id}").json()["rules"][0]
    assert [(item["target_property_id"], item["static_value"]) for item in rule["assignments"]] == [
        (property_id, "新值")
    ]


def test_delete_then_recreate_a_parameter_with_the_same_name(client):
    """参数也是同一回事：删掉「备注」再建一个同名参数，是改名之外的常规操作。"""
    graph_id = _graph_id(client)
    action_id = _create_action(client, graph_id, "关闭工单")
    parameter_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "备注",
            "datatype": "text",
        },
    )["ops"][-1]["target_id"]

    _edits(
        client,
        graph_id,
        {"op": "delete", "element_kind": "action_parameter", "element_id": parameter_id},
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "备注",
            "datatype": "integer",
        },
    )

    _confirm(client, graph_id)
    parameters = client.get(f"{API}/actions/{action_id}").json()["parameters"]
    assert [(item["name"], item["datatype"]) for item in parameters] == [("备注", "integer")]
