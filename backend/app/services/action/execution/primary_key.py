"""创建对象时的主键规则。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.models import ActionRule
from app.services.action.execution.errors import ActionExecutionError
from app.services.ontology.common import primary_key_property
from app.vocabulary import PropertyDatatype


def primary_key_for_create(
    session: Session,
    rule: ActionRule,
    object_type_id: uuid.UUID,
    explicit: Any,
) -> str:
    primary = primary_key_property(session, object_type_id)
    if primary is None:
        raise _primary_key_required(object_type_id, rule, "object type has no primary key")
    if explicit is None or (isinstance(explicit, str) and not explicit.strip()):
        if primary.datatype == PropertyDatatype.TEXT.value:
            seed = f"ontoweave:action-create:{rule.id}:{object_type_id}"
            return str(uuid.uuid5(uuid.NAMESPACE_URL, seed))
        raise _primary_key_required(
            object_type_id,
            rule,
            "non-text primary key must be supplied by the action",
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
