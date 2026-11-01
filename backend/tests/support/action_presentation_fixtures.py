"""动作展示与图发布测试共享的已确认动作构造器。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ActionCriteriaOperand, ActionRule, ActionRuleAssignment, ActionType
from app.services import action, criteria, graph, ontology, status


def graph_with_close_ticket_action(
    session: Session, *, include_current_time: bool = False
) -> dict[str, uuid.UUID]:
    owner = uuid.uuid4()
    row = graph.create_graph(session, owner_id=owner, name="售后运营")
    ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
    part = ontology.create_object_type(session, owner_id=owner, name="配件")
    ticket_key = ontology.create_property(
        session,
        owner_type_id=ticket.id,
        name="工单号",
        datatype="text",
        is_primary_key=True,
    )
    ticket_status = ontology.create_property(
        session,
        owner_type_id=ticket.id,
        name="状态",
        datatype="enum",
        enum_values=["处理中", "已关闭"],
    )
    resolution = ontology.create_property(
        session, owner_type_id=ticket.id, name="解决说明", datatype="text"
    )
    closed_at = (
        ontology.create_property(
            session, owner_type_id=ticket.id, name="关闭时间", datatype="timestamp"
        )
        if include_current_time
        else None
    )
    part_key = ontology.create_property(
        session,
        owner_type_id=part.id,
        name="配件编号",
        datatype="text",
        is_primary_key=True,
    )
    used = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=ticket.id,
        target_type_id=part.id,
        name="使用配件",
        forward_label="使用配件",
        cardinality="many_to_many",
    )
    for item in (ticket, part):
        graph.add_object_to_graph(session, row.id, item.id, owner_id=owner)
        ontology.transition_object_type(session, item.id, status.CONFIRMED, owner_id=owner)
    graph.add_relation_to_graph(session, row.id, used.id, owner_id=owner)
    ontology.transition_relation(session, used.id, status.CONFIRMED, owner_id=owner)
    properties = [ticket_key, ticket_status, resolution, part_key]
    if closed_at is not None:
        properties.append(closed_at)
    for prop in properties:
        ontology.transition_property(session, prop.id, status.CONFIRMED, owner_id=owner)

    action_type = action.create_action_type(
        session, owner_id=owner, name="关闭工单", description="关闭处理中的工单并记录解决说明"
    )
    action.add_action_to_graph(session, row.id, action_type.id, owner_id=owner)
    ticket_param = action.create_parameter(
        session,
        action_type_id=action_type.id,
        name="工单",
        description="要关闭的工单",
        kind="object_ref",
        target_object_type_id=ticket.id,
        is_required=True,
    )
    part_param = action.create_parameter(
        session,
        action_type_id=action_type.id,
        name="配件",
        description="要登记的配件",
        kind="object_ref",
        target_object_type_id=part.id,
        is_required=True,
    )
    reason_param = action.create_parameter(
        session,
        action_type_id=action_type.id,
        name="解决说明",
        description="关闭原因",
        datatype="text",
    )
    assignments = [
        {
            "target_property_id": resolution.id,
            "value_source": "parameter",
            "value_parameter_id": reason_param.id,
        }
    ]
    if closed_at is not None:
        assignments.append(
            {
                "target_property_id": closed_at.id,
                "value_source": "current_time",
            }
        )
    modify_rule = action.rules.create_rule(
        session,
        action_type_id=action_type.id,
        rule_kind="modify_object",
        target_object_type_id=ticket.id,
        source_parameter_id=ticket_param.id,
        assignments=assignments,
    )
    link_rule = action.rules.create_rule(
        session,
        action_type_id=action_type.id,
        rule_kind="create_link",
        target_relation_id=used.id,
        source_parameter_id=ticket_param.id,
        target_parameter_id=part_param.id,
    )
    criteria.replace_criteria(
        session,
        action_type_id=action_type.id,
        conditions=[
            {
                "operator": "is",
                "failure_message": "只有处理中的工单才能关闭",
                "left": {
                    "kind": "object_property",
                    "object_parameter_id": ticket_param.id,
                    "object_property_id": ticket_status.id,
                },
                "right": {"kind": "static", "static_value": "处理中"},
            }
        ],
    )
    _confirm(session, action_type, [modify_rule, link_rule])
    ids = {
        "owner": owner,
        "graph_id": row.id,
        "ticket_id": ticket.id,
        "part_id": part.id,
        "relation_id": used.id,
        "ticket_status_id": ticket_status.id,
        "resolution_id": resolution.id,
        "action_id": action_type.id,
        "ticket_parameter_id": ticket_param.id,
        "reason_parameter_id": reason_param.id,
    }
    if closed_at is not None:
        ids["closed_at_id"] = closed_at.id
    return ids


def _confirm(session: Session, action_type: ActionType, rules: list[ActionRule]) -> None:
    action.transition_action_type(session, action_type.id, status.CONFIRMED)
    for parameter in action.list_parameters(session, action_type.id):
        parameter.status = status.CONFIRMED
    for rule in rules:
        rule.status = status.CONFIRMED
        for assignment in session.scalars(
            select(ActionRuleAssignment).where(ActionRuleAssignment.rule_id == rule.id)
        ):
            assignment.status = status.CONFIRMED
    for node in criteria.list_nodes(session, action_type.id):
        node.status = status.CONFIRMED
    for operand in session.scalars(select(ActionCriteriaOperand)):
        operand.status = status.CONFIRMED
    session.flush()
