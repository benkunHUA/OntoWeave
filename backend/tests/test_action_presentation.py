from __future__ import annotations

from app.services.action import presentation
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


def test_action_summary_has_only_decision_fields_and_resolved_names(session):
    ids = graph_with_close_ticket_action(session)

    summary = presentation.load_action_summary(
        session,
        ids["action_id"],
        owner_id=ids["owner"],
        graph_id=ids["graph_id"],
    )
    data = presentation.summary_to_dict(summary)

    assert set(data) == {"id", "name", "description", "status", "parameters", "effects", "criteria"}
    assert data["id"] == str(ids["action_id"])
    assert [item["name"] for item in data["parameters"]] == ["工单", "配件", "解决说明"]
    assert data["parameters"][0]["target_object_type_name"] == "工单"
    assert data["parameters"][2]["datatype"] == "text"
    assert data["effects"]["objects"] == [
        {
            "object_type_id": str(ids["ticket_id"]),
            "object_type_name": "工单",
            "rule_kinds": ["modify_object"],
        }
    ]
    assert data["effects"]["relations"] == [
        {
            "relation_id": str(ids["relation_id"]),
            "relation_name": "使用配件",
            "rule_kinds": ["create_link"],
        }
    ]
    assert data["criteria"] == [
        {
            "condition_id": data["criteria"][0]["condition_id"],
            "text": "工单.状态 是 处理中",
            "failure_message": "只有处理中的工单才能关闭",
        }
    ]
    assert "assignments" not in data
    assert "rules" not in data


def test_action_detail_contains_assignments_operands_and_names(session):
    ids = graph_with_close_ticket_action(session)

    detail = presentation.load_action_detail_in_graph(
        session,
        ids["action_id"],
        owner_id=ids["owner"],
        graph_id=ids["graph_id"],
    )
    data = presentation.action_detail_to_dict(detail, session)

    modify = next(rule for rule in data["rules"] if rule["rule_kind"] == "modify_object")
    assert modify["target_object_type_name"] == "工单"
    assert modify["source_parameter_name"] == "工单"
    assert modify["assignments"] == [
        {
            "id": modify["assignments"][0]["id"],
            "target_property_id": str(ids["resolution_id"]),
            "target_property_name": "解决说明",
            "value_source": "parameter",
            "value_parameter_id": str(ids["reason_parameter_id"]),
            "value_parameter_name": "解决说明",
            "object_parameter_id": None,
            "object_parameter_name": None,
            "object_property_id": None,
            "object_property_name": None,
            "static_value": None,
            "status": "confirmed",
        }
    ]
    condition = data["criteria"][0]["children"][0]
    assert condition["text"] == "工单.状态 是 处理中"
    assert condition["left"]["object_parameter_name"] == "工单"
    assert condition["left"]["object_property_name"] == "状态"
    assert condition["right"]["static_value"] == "处理中"


def test_action_projection_is_stable_and_card_section_is_renderable(session):
    ids = graph_with_close_ticket_action(session)
    first = presentation.load_action_summaries(session, ids["graph_id"], owner_id=ids["owner"])
    second = presentation.load_action_summaries(session, ids["graph_id"], owner_id=ids["owner"])

    assert [presentation.summary_to_dict(item) for item in first] == [
        presentation.summary_to_dict(item) for item in second
    ]
    lines = presentation.render_action_card_section(first)
    assert lines[0] == "可执行动作："
    assert lines[1].startswith("- 关闭工单（id=")
    assert any(
        line.strip()
        == "参数：工单（对象：工单，必填）、配件（对象：配件，必填）、解决说明（文本，可选）"
        for line in lines
    )
    assert any(line.strip() == "影响：修改 工单；新增 使用配件" for line in lines)
    assert any(line.strip() == "校验：工单.状态 是 处理中" for line in lines)
