"""按元素种类分派的更新校验。"""

from __future__ import annotations

import uuid
from collections.abc import Collection

from sqlalchemy.orm import Session

from app.services import property, relation
from app.services.action.parameters import validate_parameter_patch as _validate_parameter_patch
from app.services.action.rules.assignments import (
    validate_assignment_patch as _validate_assignment_patch,
)
from app.services.action.rules.rules import validate_rule_patch as _validate_rule_patch
from app.services.criteria.tree import validate_node_patch, validate_operand_patch


def validate_property_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    del exclude_ids
    property.validate_property_patch(session, property_id=element_id, patch=patch)


def validate_relation_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    del exclude_ids
    relation.validate_relation_patch(session, relation_id=element_id, patch=patch)


def validate_action_parameter_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    _validate_parameter_patch(
        session, parameter_id=element_id, patch=patch, exclude_ids=exclude_ids
    )


def validate_action_rule_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    _validate_rule_patch(session, rule_id=element_id, patch=patch, exclude_ids=exclude_ids)


def validate_assignment_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    _validate_assignment_patch(
        session, assignment_id=element_id, patch=patch, exclude_ids=exclude_ids
    )


def validate_criteria_node_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    del exclude_ids
    validate_node_patch(session, node_id=element_id, patch=patch)


def validate_criteria_operand_patch(
    session: Session, element_id: uuid.UUID, patch: dict, exclude_ids: Collection[uuid.UUID]
) -> None:
    del exclude_ids
    validate_operand_patch(session, operand_id=element_id, patch=patch)
