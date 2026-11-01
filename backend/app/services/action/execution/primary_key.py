"""创建对象时的主键规则。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.models import ActionRule
from app.services.action.execution.errors import ActionExecutionError
from app.services.action.execution.generated_values import auto_primary_key_value
from app.services.ontology.common import primary_key_property
from app.vocabulary import AUTO_GENERATED_PRIMARY_KEY_DATATYPES


def primary_key_for_create(
    session: Session,
    rule: ActionRule,
    object_type_id: uuid.UUID,
    explicit: Any,
    *,
    execution_key: str,
) -> str:
    primary = primary_key_property(session, object_type_id)
    if primary is None:
        raise _primary_key_required(object_type_id, rule, "object type has no primary key")
    if explicit is None or (isinstance(explicit, str) and not explicit.strip()):
        if primary.datatype in AUTO_GENERATED_PRIMARY_KEY_DATATYPES:
            generated = auto_primary_key_value(
                primary,
                execution_key=execution_key,
                rule_id=rule.id,
            )
            return str(generated)
        raise _primary_key_required(
            object_type_id,
            rule,
            "primary key datatype cannot be generated automatically",
        )
    return str(explicit)


def _primary_key_required(
    object_type_id: uuid.UUID, rule: ActionRule, message: str
) -> ActionExecutionError:
    return ActionExecutionError(
        "PRIMARY_KEY_REQUIRED",
        message,
        details={"object_type_id": str(object_type_id), "rule_id": str(rule.id)},
    )


__all__ = ["primary_key_for_create"]
