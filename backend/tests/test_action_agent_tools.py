"""动作工具：绑定、形状与「写进去就落进改动集」。"""

import uuid

from app.agent.tools import OntologyToolContext, build_action_subagent, build_action_tools
from app.services import (
    action,
    changeset,
    conversation,
    embedding,
    graph,
    ontology,
)


def _context(session, owner) -> OntologyToolContext:
    conv = conversation.create_conversation(session, owner_id=owner, title="动作工具")
    cs = changeset.create_changeset(session, graph_id=conv.resource_id, owner_id=owner)
    return OntologyToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.resource_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def _graph_with_types(session, ctx) -> tuple[uuid.UUID, uuid.UUID, uuid.UUID, uuid.UUID]:
    """客户 / 订单 + 一条多对多关系 + 订单上的金额属性。"""
    customer = ontology.create_object_type(session, owner_id=ctx.owner_id, name="客户")
    order = ontology.create_object_type(session, owner_id=ctx.owner_id, name="订单")
    amount = ontology.create_property(
        session, owner_type_id=order.id, name="金额", datatype="decimal"
    )
    relation = ontology.create_relation(
        session,
        owner_id=ctx.owner_id,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    for element_id in (customer.id, order.id):
        graph.add_object_to_graph(session, ctx.graph_id, element_id, owner_id=ctx.owner_id)
    graph.add_relation_to_graph(session, ctx.graph_id, relation.id, owner_id=ctx.owner_id)
    return customer.id, order.id, amount.id, relation.id


def _tools(ctx) -> dict:
    return {item.name: item for item in build_action_tools(ctx)}


def test_binds_expected_tool_names(session):
    ctx = _context(session, uuid.uuid4())
    assert [item.name for item in build_action_tools(ctx)] == [
        "search_actions",
        "get_action",
        "get_object_type",
        "propose_action",
        "reuse_action",
        "propose_action_parameter",
        "propose_action_rule",
        "set_action_criteria",
    ]


def test_every_tool_exposes_a_description(session):
    ctx = _context(session, uuid.uuid4())
    for item in build_action_tools(ctx):
        assert item.description and item.description.strip()


def test_subagent_loads_the_read_tool_it_needs(session):
    """动作要靠 search 把对象类型找出来，所以子智能体的工具里必须有它。"""
    ctx = _context(session, uuid.uuid4())
    subagent = build_action_subagent(ctx)

    assert subagent["name"] == "action_modeler"
    names = [item.name for item in subagent["tools"]]
    assert names[0] == "search"
    assert set(names[1:]) == {item.name for item in build_action_tools(ctx)}


def test_propose_action_and_parameter_reach_the_changeset(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    tools = _tools(ctx)

    created = tools["propose_action"].invoke({"name": "指派工单"})
    assert created["status"] == "draft"

    parameter = tools["propose_action_parameter"].invoke(
        {"action_type_id": created["id"], "name": "原因", "datatype": "text", "is_required": True}
    )
    assert parameter["name"] == "原因"
    assert parameter["is_required"] is True

    assert [op.payload["kind"] for op in changeset.list_ops(session, ctx.changeset_id)] == [
        "action_type",
        "action_parameter",
    ]


def test_propose_action_rule_with_inline_assignments(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    _, order_id, amount_id, _ = _graph_with_types(session, ctx)
    tools = _tools(ctx)
    action_id = tools["propose_action"].invoke({"name": "下订单"})["id"]
    amount_parameter_id = tools["propose_action_parameter"].invoke(
        {"action_type_id": action_id, "name": "金额", "datatype": "decimal"}
    )["id"]

    rule = tools["propose_action_rule"].invoke(
        {
            "action_type_id": action_id,
            "rule_kind": "create_object",
            "target_object_type_id": str(order_id),
            "assignments": [
                {
                    "target_property_id": str(amount_id),
                    "value_source": "parameter",
                    "value_parameter_id": amount_parameter_id,
                }
            ],
        }
    )

    assert rule["rule_kind"] == "create_object"
    assert rule["assignments"] == [
        {
            "target_property_id": str(amount_id),
            "value_source": "parameter",
            "value_parameter_id": amount_parameter_id,
            "object_parameter_id": None,
            "object_property_id": None,
            "static_value": None,
        }
    ]


def test_set_action_criteria_writes_then_reads_back_the_same_shape(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    tools = _tools(ctx)
    action_id = tools["propose_action"].invoke({"name": "关闭工单"})["id"]
    parameter_id = tools["propose_action_parameter"].invoke(
        {"action_type_id": action_id, "name": "金额", "datatype": "decimal"}
    )["id"]
    condition = {
        "operator": ">=",
        "failure_message": "金额必须写正数",
        "left": {"kind": "parameter", "value_parameter_id": parameter_id},
        "right": {"kind": "static", "static_value": 1},
    }

    written = tools["set_action_criteria"].invoke(
        {"action_type_id": action_id, "conditions": [condition]}
    )

    # 读出来的形状就是写进去的形状：可以原样交回 set_action_criteria。
    criteria = written["criteria"]
    assert len(criteria) == 1
    assert criteria[0]["operator"] == ">="
    assert criteria[0]["failure_message"] == "金额必须写正数"
    assert criteria[0]["left"]["value_parameter_id"] == parameter_id
    assert criteria[0]["right"]["static_value"] == 1
    assert tools["get_action"].invoke({"action_type_id": action_id})["criteria"] == criteria


def test_get_object_type_lists_properties_and_relations(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    customer_id, order_id, amount_id, relation_id = _graph_with_types(session, ctx)

    body = _tools(ctx)["get_object_type"].invoke({"object_type_id": str(order_id)})

    assert body["name"] == "订单"
    assert [prop["id"] for prop in body["properties"]] == [str(amount_id)]
    assert (body["relations"][0]["id"], body["relations"][0]["name"]) == (
        str(relation_id),
        "下订单",
    )
    assert body["relations"][0]["source_type_id"] == str(customer_id)


def test_get_action_reads_back_parameters_and_rules(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    _, order_id, _, _ = _graph_with_types(session, ctx)
    tools = _tools(ctx)
    action_id = tools["propose_action"].invoke({"name": "下订单"})["id"]
    parameter_id = tools["propose_action_parameter"].invoke(
        {
            "action_type_id": action_id,
            "name": "订单",
            "kind": "object_ref",
            "target_object_type_id": str(order_id),
        }
    )["id"]
    tools["propose_action_rule"].invoke(
        {
            "action_type_id": action_id,
            "rule_kind": "modify_object",
            "target_object_type_id": str(order_id),
            "source_parameter_id": parameter_id,
        }
    )

    body = tools["get_action"].invoke({"action_type_id": action_id})

    assert (body["name"], body["status"]) == ("下订单", "draft")
    assert body["parameters"][0]["kind"] == "object_ref"
    assert body["parameters"][0]["target_object_type_id"] == str(order_id)
    assert body["rules"][0]["rule_kind"] == "modify_object"
    assert body["rules"][0]["source_parameter_id"] == parameter_id


def test_reuse_action_mounts_a_confirmed_action(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    other = action.create_action_type(session, owner_id=owner, name="关闭工单")
    action.transition_action_type(session, other.id, "confirmed", owner_id=owner)

    reused = _tools(ctx)["reuse_action"].invoke({"action_type_id": str(other.id)})

    assert reused["reused"] is True
    assert action.list_graph_actions(session, ctx.graph_id) == [other.id]
    assert changeset.list_ops(session, ctx.changeset_id) == []


def test_tool_errors_come_back_as_results(session):
    """模型编个坏 id 不该掀翻整轮：写路径上的错误都兜成结构化结果。"""
    ctx = _context(session, uuid.uuid4())
    result = _tools(ctx)["get_action"].invoke({"action_type_id": "不是 uuid"})

    assert result["error"] == "ValueError"
