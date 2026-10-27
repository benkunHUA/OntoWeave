"""动作规则的列组合与生命周期校验。"""

from __future__ import annotations

import uuid
from collections.abc import Collection, Mapping, Sequence
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import ActionRule
from app.services import object_type, status
from app.services import relation as relation_service
from app.services.action.rules.assignments import create_assignment
from app.services.action.rules.common import ensure_object_parameter, get_rule
from app.services.action.types import get_action_type
from app.vocabulary import ACTION_RULE_KINDS, ActionRuleKind, RelationCardinality

_OBJECT_RULE_KINDS: tuple[str, ...] = (
    ActionRuleKind.CREATE_OBJECT.value,
    ActionRuleKind.MODIFY_OBJECT.value,
    ActionRuleKind.DELETE_OBJECT.value,
)
_LINK_RULE_KINDS: tuple[str, ...] = (
    ActionRuleKind.CREATE_LINK.value,
    ActionRuleKind.DELETE_LINK.value,
)


def list_rules(session: Session, action_type_id: uuid.UUID) -> list[ActionRule]:
    return list(
        session.scalars(
            select(ActionRule)
            .where(ActionRule.action_type_id == action_type_id)
            .order_by(ActionRule.order_index, ActionRule.rule_kind)
        )
    )


def next_order_index(session: Session, action_type_id: uuid.UUID) -> int:
    indexes = session.scalars(
        select(ActionRule.order_index).where(ActionRule.action_type_id == action_type_id)
    )
    return max(indexes, default=-1) + 1


def create_rule(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    rule_kind: str,
    target_object_type_id: uuid.UUID | None = None,
    target_relation_id: uuid.UUID | None = None,
    source_parameter_id: uuid.UUID | None = None,
    target_parameter_id: uuid.UUID | None = None,
    order_index: int | None = None,
    assignments: Sequence[Mapping[str, Any]] = (),
) -> ActionRule:
    """加一条规则；`assignments` 内联落库，省得调用方再补一轮校验。"""
    get_action_type(session, action_type_id)
    where = f"action rule {rule_kind!r}"
    ensure_rule_shape(
        session,
        action_type_id=action_type_id,
        rule_kind=rule_kind,
        target_object_type_id=target_object_type_id,
        target_relation_id=target_relation_id,
        source_parameter_id=source_parameter_id,
        target_parameter_id=target_parameter_id,
        where=where,
    )
    rule = ActionRule(
        action_type_id=action_type_id,
        rule_kind=rule_kind,
        target_object_type_id=target_object_type_id,
        target_relation_id=target_relation_id,
        source_parameter_id=source_parameter_id,
        target_parameter_id=target_parameter_id,
        order_index=(
            next_order_index(session, action_type_id) if order_index is None else order_index
        ),
        status=status.DRAFT,
    )
    session.add(rule)
    session.flush()
    for spec in assignments:
        create_assignment(session, rule_id=rule.id, **dict(spec))
    return rule


def validate_rule_patch(
    session: Session,
    *,
    rule_id: uuid.UUID,
    patch: dict,
    exclude_ids: Collection[uuid.UUID] = (),
) -> None:
    """改规则前校验。种类不可改：换种类等于换语义，请删掉重建。

    规则本身没有跨行唯一约束，`exclude_ids` 只是让更新校验的入参保持统一。
    """
    del exclude_ids
    rule = get_rule(session, rule_id)
    if "rule_kind" in patch and patch["rule_kind"] != rule.rule_kind:
        raise InvariantViolationError(
            f"action rule {rule_id} cannot change its kind; delete it and add a new rule"
        )
    ensure_rule_shape(
        session,
        action_type_id=rule.action_type_id,
        rule_kind=rule.rule_kind,
        target_object_type_id=patch.get("target_object_type_id", rule.target_object_type_id),
        target_relation_id=patch.get("target_relation_id", rule.target_relation_id),
        source_parameter_id=patch.get("source_parameter_id", rule.source_parameter_id),
        target_parameter_id=patch.get("target_parameter_id", rule.target_parameter_id),
        where=f"action rule {rule.rule_kind!r}",
    )


def ensure_rule_shape(
    session: Session,
    *,
    action_type_id: uuid.UUID,
    rule_kind: str,
    target_object_type_id: uuid.UUID | None,
    target_relation_id: uuid.UUID | None,
    source_parameter_id: uuid.UUID | None,
    target_parameter_id: uuid.UUID | None,
    where: str,
) -> None:
    """每种规则要哪些列、不要哪些列，只有这一份判断。"""
    if rule_kind not in ACTION_RULE_KINDS:
        raise InvariantViolationError(
            f"{where}: unsupported rule kind {rule_kind!r}; "
            f"expected one of {list(ACTION_RULE_KINDS)}"
        )

    if rule_kind in _OBJECT_RULE_KINDS:
        if target_relation_id is not None or target_parameter_id is not None:
            raise InvariantViolationError(
                f"{where}: object rule must not carry a relation or a target parameter"
            )
        if target_object_type_id is None:
            raise InvariantViolationError(f"{where}: object rule needs the object type")
        object_type.get_object_type(session, target_object_type_id)
        if rule_kind == ActionRuleKind.CREATE_OBJECT.value:
            if source_parameter_id is not None:
                raise InvariantViolationError(
                    f"{where}: create_object builds a new object, it takes no source parameter"
                )
            return
        if source_parameter_id is None:
            raise InvariantViolationError(
                f"{where}: {rule_kind} needs the object parameter it works on"
            )
        ensure_object_parameter(
            session,
            parameter_id=source_parameter_id,
            action_type_id=action_type_id,
            object_type_id=target_object_type_id,
            where=where,
            role="source",
        )
        return

    if target_object_type_id is not None:
        raise InvariantViolationError(f"{where}: link rule must not carry an object type")
    if target_relation_id is None:
        raise InvariantViolationError(f"{where}: link rule needs the relation it works on")
    relation = relation_service.get_relation(session, target_relation_id)
    if relation.cardinality != RelationCardinality.MANY_TO_MANY.value:
        raise InvariantViolationError(
            f"{where}: only many-to-many relations use link rules; "
            "one-to-one / one-to-many change the foreign key property with modify_object"
        )
    if source_parameter_id is None or target_parameter_id is None:
        raise InvariantViolationError(f"{where}: link rule needs both endpoint parameters")
    if source_parameter_id == target_parameter_id:
        raise InvariantViolationError(f"{where}: the two endpoints cannot be the same parameter")
    ensure_object_parameter(
        session,
        parameter_id=source_parameter_id,
        action_type_id=action_type_id,
        object_type_id=relation.source_type_id,
        where=where,
        role="source",
    )
    ensure_object_parameter(
        session,
        parameter_id=target_parameter_id,
        action_type_id=action_type_id,
        object_type_id=relation.target_type_id,
        where=where,
        role="target",
    )
