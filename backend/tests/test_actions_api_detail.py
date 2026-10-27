"""`test_actions_api.py` 中 detail 相关测试。"""

import pytest

from tests.support.actions_api_fixtures import (
    API,
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


def test_action_detail_gives_parameters_rules_and_criteria(client):
    graph_id = _graph_id(client)
    order_id = _create_type(client, graph_id, "订单")
    property_id = _edits(
        client,
        graph_id,
        {
            "op": "create_property",
            "owner_type_id": order_id,
            "name": "金额",
            "datatype": "decimal",
        },
    )["ops"][-1]["target_id"]
    action_id = _create_action(client, graph_id, "下订单")

    amount_parameter_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "金额",
            "datatype": "decimal",
            "description": "订单金额",
            "is_required": True,
        },
    )["ops"][-1]["target_id"]
    _edits(
        client,
        graph_id,
        {
            "op": "add_action_rule",
            "action_type_id": action_id,
            "rule_kind": "create_object",
            "target_object_type_id": order_id,
            "assignments": [
                {
                    "target_property_id": property_id,
                    "value_source": "parameter",
                    "value_parameter_id": amount_parameter_id,
                }
            ],
        },
    )
    _edits(
        client,
        graph_id,
        {
            "op": "set_action_criteria",
            "action_type_id": action_id,
            "conditions": [
                {
                    "operator": ">=",
                    "failure_message": "金额必须写正数",
                    "left": {"kind": "parameter", "value_parameter_id": amount_parameter_id},
                    "right": {"kind": "static", "static_value": 1},
                }
            ],
        },
    )

    body = client.get(f"{API}/actions/{action_id}").json()
    assert (body["name"], body["status"]) == ("下订单", "draft")
    assert body["parameters"] == [
        {
            "id": amount_parameter_id,
            "name": "金额",
            "description": "订单金额",
            "kind": "value",
            "datatype": "decimal",
            "target_object_type_id": None,
            "is_array": False,
            "is_required": True,
            "default_value": None,
            "enum_values": None,
            "order_index": 0,
            "status": "draft",
        }
    ]
    rule = body["rules"][0]
    assert (rule["rule_kind"], rule["target_object_type_id"]) == ("create_object", order_id)
    assert rule["assignments"][0]["value_source"] == "parameter"
    assert rule["assignments"][0]["value_parameter_id"] == amount_parameter_id

    root = body["criteria"][0]
    assert (root["node_kind"], root["logic"]) == ("group", "and")
    condition = root["children"][0]
    assert condition["operator"] >= ">="
    assert condition["failure_message"] == "金额必须写正数"
    assert condition["left"]["value_parameter_id"] == amount_parameter_id
    assert condition["right"]["static_value"] == 1


def test_set_action_criteria_keeps_unchanged_conditions(client):
    """值没变的条件原地留着：第二次保存不该再建一遍，也不该标删。"""
    graph_id = _graph_id(client)
    action_id = _create_action(client, graph_id, "关闭工单")
    parameter_id = _edits(
        client,
        graph_id,
        {
            "op": "add_action_parameter",
            "action_type_id": action_id,
            "name": "原因",
            "datatype": "text",
        },
    )["ops"][-1]["target_id"]
    condition = {
        "operator": "is not null",
        "left": {"kind": "parameter", "value_parameter_id": parameter_id},
    }

    first = _edits(
        client,
        graph_id,
        {"op": "set_action_criteria", "action_type_id": action_id, "conditions": [condition]},
    )
    assert _ops(first) == [
        ("create", "action_type"),
        ("create", "action_parameter"),
        ("create", "action_criteria_node"),
        ("create", "action_criteria_node"),
        ("create", "action_criteria_operand"),
    ]

    again = _edits(
        client,
        graph_id,
        {"op": "set_action_criteria", "action_type_id": action_id, "conditions": [condition]},
    )
    assert _ops(again) == _ops(first)

    cleared = _edits(
        client,
        graph_id,
        {"op": "set_action_criteria", "action_type_id": action_id, "conditions": []},
    )
    assert _ops(cleared)[-1] == ("delete", "action_criteria_node")

    # 标删的节点还在库里：凭空再交一遍同样的条件，只会新建一个，不会把旧的留下。
    re_added = _edits(
        client,
        graph_id,
        {"op": "set_action_criteria", "action_type_id": action_id, "conditions": [condition]},
    )
    assert _ops(re_added)[-2:] == [
        ("create", "action_criteria_node"),
        ("create", "action_criteria_operand"),
    ]
