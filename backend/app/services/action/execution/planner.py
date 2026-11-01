"""把行动规则装配成数据服务可理解的 edit set 草稿。"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from typing import Any

from sqlalchemy.orm import Session

from app.models import ActionRule, Relation
from app.services import relation as relation_service
from app.services.action.detail import ActionDetail, RuleDetail
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.object_values import EffectiveObjectReader
from app.services.action.execution.parameters import ObjectReference, ResolvedParameters
from app.services.action.execution.planner_types import (
    EditSetDraft,
    ObjectCreateEdit,
    ObjectDeleteEdit,
    ObjectUpdateEdit,
    RelationEdit,
)
from app.services.action.execution.planner_values import RuleValueReader
from app.services.action.execution.primary_key import primary_key_for_create
from app.vocabulary import ActionRuleKind, RelationCardinality


def plan_edit_set(
    session: Session,
    detail: ActionDetail,
    parameters: ResolvedParameters,
    *,
    owner_id: uuid.UUID,
    client: Any,
    execution_key: str,
    generated_values: Mapping[uuid.UUID, Any] | None = None,
) -> EditSetDraft:
    return _Planner(
        session,
        detail,
        parameters,
        owner_id=owner_id,
        client=client,
        execution_key=execution_key,
        generated_values=generated_values,
    ).plan()


class _Planner:
    def __init__(
        self,
        session: Session,
        detail: ActionDetail,
        parameters: ResolvedParameters,
        *,
        owner_id: uuid.UUID,
        client: Any,
        execution_key: str,
        generated_values: Mapping[uuid.UUID, Any] | None = None,
    ) -> None:
        self.session = session
        self.detail = detail
        self.objects = EffectiveObjectReader(owner_id=owner_id, client=client)
        self.values = RuleValueReader(
            session,
            detail,
            parameters,
            self.objects,
            execution_key=execution_key,
            generated_values=generated_values,
        )
        self.execution_key = execution_key
        self.object_effects: dict[tuple[uuid.UUID, str], uuid.UUID] = {}
        self.relation_effects: dict[
            tuple[uuid.UUID, uuid.UUID, str, uuid.UUID, str], uuid.UUID
        ] = {}
        self.object_creates: list[ObjectCreateEdit] = []
        self.object_updates: list[ObjectUpdateEdit] = []
        self.object_deletes: list[ObjectDeleteEdit] = []
        self.relation_creates: list[RelationEdit] = []
        self.relation_deletes: list[RelationEdit] = []

    def plan(self) -> EditSetDraft:
        for item in self.detail.rules:
            rule = item.rule
            if rule.rule_kind == ActionRuleKind.CREATE_OBJECT.value:
                self._create_object(item)
            elif rule.rule_kind == ActionRuleKind.MODIFY_OBJECT.value:
                self._modify_object(item)
            elif rule.rule_kind == ActionRuleKind.DELETE_OBJECT.value:
                self._delete_object(item)
            elif rule.rule_kind == ActionRuleKind.CREATE_LINK.value:
                self._link(item, create=True)
            elif rule.rule_kind == ActionRuleKind.DELETE_LINK.value:
                self._link(item, create=False)
            else:
                raise _invalid_rule(rule, f"unsupported rule kind {rule.rule_kind!r}")
        return EditSetDraft(
            object_creates=tuple(self.object_creates),
            object_updates=tuple(self.object_updates),
            object_deletes=tuple(self.object_deletes),
            relation_creates=tuple(self.relation_creates),
            relation_deletes=tuple(self.relation_deletes),
            generated_values=dict(self.values.generated_values),
        )

    def _create_object(self, item: RuleDetail) -> None:
        rule = item.rule
        object_type_id = rule.target_object_type_id
        if object_type_id is None:
            raise _invalid_rule(rule, "create_object needs a target object type")
        values: dict[uuid.UUID, Any] = {}
        primary_key_value: Any = None
        for assignment in item.assignments:
            target = self.values.assignable_property(assignment, object_type_id)
            value = self.values.value(assignment, target)
            if target.is_primary_key:
                primary_key_value = value
            if value is not None:
                values[target.id] = value
        primary_key = primary_key_for_create(
            self.session,
            rule,
            object_type_id,
            primary_key_value,
            execution_key=self.execution_key,
        )
        self._claim_object(object_type_id, primary_key, rule)
        self.object_creates.append(
            ObjectCreateEdit(
                object_type_id=object_type_id,
                primary_key=primary_key,
                values=values,
            )
        )

    def _modify_object(self, item: RuleDetail) -> None:
        rule = item.rule
        object_type_id = rule.target_object_type_id
        if object_type_id is None:
            raise _invalid_rule(rule, "modify_object needs a target object type")
        reference = self.values.object_reference(
            rule,
            rule.source_parameter_id,
            expected_type_id=object_type_id,
            role="source",
        )
        set_values: dict[uuid.UUID, Any] = {}
        clear: list[uuid.UUID] = []
        for assignment in item.assignments:
            target = self.values.assignable_property(assignment, object_type_id)
            if target.is_primary_key:
                raise ActionExecutionError(
                    "PRIMARY_KEY_IMMUTABLE",
                    f"primary key {target.name!r} cannot be changed",
                    details={
                        "property_id": str(target.id),
                        "property_name": target.name,
                        "rule_id": str(rule.id),
                    },
                )
            value = self.values.value(assignment, target)
            if value is None:
                clear.append(target.id)
            else:
                set_values[target.id] = value
        self._claim_object(reference.object_type_id, reference.primary_key, rule)
        self.object_updates.append(
            ObjectUpdateEdit(
                object_type_id=reference.object_type_id,
                primary_key=reference.primary_key,
                set_values=set_values,
                clear=tuple(clear),
            )
        )

    def _delete_object(self, item: RuleDetail) -> None:
        rule = item.rule
        object_type_id = rule.target_object_type_id
        if object_type_id is None:
            raise _invalid_rule(rule, "delete_object needs a target object type")
        reference = self.values.object_reference(
            rule,
            rule.source_parameter_id,
            expected_type_id=object_type_id,
            role="source",
        )
        self._claim_object(reference.object_type_id, reference.primary_key, rule)
        self.object_deletes.append(
            ObjectDeleteEdit(
                object_type_id=reference.object_type_id,
                primary_key=reference.primary_key,
            )
        )

    def _link(self, item: RuleDetail, *, create: bool) -> None:
        rule = item.rule
        if rule.target_relation_id is None:
            raise _invalid_rule(rule, "link rule needs a target relation")
        relation = relation_service.get_relation(self.session, rule.target_relation_id)
        if relation.cardinality != RelationCardinality.MANY_TO_MANY.value:
            raise ActionExecutionError(
                "INVALID_RELATION_RULE",
                "link rules only support many-to-many relations",
                details={
                    "rule_id": str(rule.id),
                    "relation_id": str(relation.id),
                    "cardinality": relation.cardinality,
                },
            )
        source = self.values.object_reference(
            rule,
            rule.source_parameter_id,
            expected_type_id=relation.source_type_id,
            role="source",
        )
        target = self.values.object_reference(
            rule,
            rule.target_parameter_id,
            expected_type_id=relation.target_type_id,
            role="target",
        )
        edit = RelationEdit(
            relation_id=relation.id,
            from_object_type_id=source.object_type_id,
            from_primary_key=source.primary_key,
            to_object_type_id=target.object_type_id,
            to_primary_key=target.primary_key,
        )
        self._claim_relation(relation, source, target, rule)
        (self.relation_creates if create else self.relation_deletes).append(edit)

    def _claim_object(self, object_type_id: uuid.UUID, primary_key: str, rule: ActionRule) -> None:
        key = (object_type_id, primary_key)
        previous = self.object_effects.get(key)
        if previous is not None:
            raise ActionExecutionError(
                "DUPLICATE_EFFECT",
                "the same object is changed more than once by this action",
                details={
                    "object_type_id": str(object_type_id),
                    "primary_key": primary_key,
                    "rule_ids": [str(previous), str(rule.id)],
                },
            )
        self.object_effects[key] = rule.id

    def _claim_relation(
        self,
        relation: Relation,
        source: ObjectReference,
        target: ObjectReference,
        rule: ActionRule,
    ) -> None:
        key = (
            relation.id,
            source.object_type_id,
            source.primary_key,
            target.object_type_id,
            target.primary_key,
        )
        previous = self.relation_effects.get(key)
        if previous is not None:
            raise ActionExecutionError(
                "DUPLICATE_EFFECT",
                "the same relation endpoints are changed more than once by this action",
                details={
                    "relation_id": str(relation.id),
                    "from_primary_key": source.primary_key,
                    "to_primary_key": target.primary_key,
                    "rule_ids": [str(previous), str(rule.id)],
                },
            )
        self.relation_effects[key] = rule.id


def _invalid_rule(rule: ActionRule, message: str) -> ActionExecutionError:
    return ActionExecutionError(
        "INVALID_ACTION_DEFINITION",
        message,
        details={"rule_id": str(rule.id), "rule_kind": rule.rule_kind},
    )


__all__ = [
    "EditSetDraft",
    "ObjectCreateEdit",
    "ObjectDeleteEdit",
    "ObjectUpdateEdit",
    "RelationEdit",
    "plan_edit_set",
]
