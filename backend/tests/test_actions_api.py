"""动作层的 REST 边界：列表、详情、画布 brief，以及走编辑接口的写入形状。"""

import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.main import _create_app
from app.schemas.changeset import ElementKind
from app.services import element_kinds

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(_create_app(session_factory, enable_agent_checkpoints=False))


def _graph_id(client: TestClient) -> str:
    return client.post(f"{API}/conversations", json={"title": "动作"}).json()["graph_id"]


def _edits(client: TestClient, graph_id: str, *edits: dict) -> dict:
    response = client.post(f"{API}/graphs/{graph_id}/edits", json={"edits": list(edits)})
    assert response.status_code == 200, response.text
    return response.json()


def _ops(changeset: dict) -> list[tuple[str, str]]:
    return [(op["op"], op["payload"]["kind"]) for op in changeset["ops"]]


def _create_type(client: TestClient, graph_id: str, name: str) -> str:
    """建一个草稿对象类型，返回它的 id。"""
    return _edits(client, graph_id, {"op": "create_object_type", "name": name})["ops"][-1][
        "target_id"
    ]


def _create_action(client: TestClient, graph_id: str, name: str) -> str:
    return _edits(client, graph_id, {"op": "create_action_type", "name": name})["ops"][-1][
        "target_id"
    ]


def _confirm(client: TestClient, graph_id: str) -> None:
    """确认图上那一个未定稿的改动集，把草稿翻成已确认。"""
    changeset_id = client.get(f"{API}/graphs/{graph_id}/changesets?limit=1").json()[0]["id"]
    response = client.post(f"{API}/changesets/{changeset_id}/confirm")
    assert response.status_code == 200, response.text


def _snapshot_actions(client: TestClient, graph_id: str) -> list[dict]:
    return client.get(f"{API}/graphs/{graph_id}/snapshot").json()["actions"]


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
