"""把 Python 中的关系定义同步到 Java 快照。"""

from __future__ import annotations

import hashlib
import json
import logging
import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError
from app.models import Property, Relation
from app.services.ontology.common import (
    OBJECT_TYPE,
    normalize_resource_kind,
    primary_key_property,
)
from app.services.relation import get_relation

logger = logging.getLogger(__name__)


def sync_subject_relations(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> None:
    kind = normalize_resource_kind(subject_kind)
    if kind == OBJECT_TYPE:
        relations = list(
            session.scalars(
                select(Relation).where(
                    Relation.owner_id == owner_id,
                    (Relation.source_type_id == subject_id)
                    | (Relation.target_type_id == subject_id),
                )
            )
        )
    else:
        relations = [get_relation(session, subject_id)]
    sync_relations(session, client, owner_id=owner_id, relations=relations)


def sync_subject_relations_best_effort(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> None:
    try:
        sync_subject_relations(
            session, client, owner_id=owner_id, subject_kind=subject_kind, subject_id=subject_id
        )
    except Exception as exc:  # 打开/保存/预览可继续，生成前会严格重试。
        logger.warning("ontology relation definition sync failed: %s", exc)


def sync_relations(
    session: Session, client: Any, *, owner_id: uuid.UUID, relations: list[Relation]
) -> None:
    for relation in relations:
        payload = relation_definition_payload(session, relationship=relation)
        client.put_relation_definition(
            relation.id, owner_id=owner_id, payload=payload, operation_id=uuid.uuid4()
        )


def sync_relations_best_effort(
    session: Session, client: Any, *, owner_id: uuid.UUID, relations: list[Relation]
) -> None:
    try:
        sync_relations(session, client, owner_id=owner_id, relations=relations)
    except Exception as exc:
        logger.warning("ontology relation definition sync failed: %s", exc)


def relation_definition_payload(session: Session, *, relationship: Relation) -> dict[str, Any]:
    source_pk = primary_key_property(session, relationship.source_type_id)
    target_pk = primary_key_property(session, relationship.target_type_id)
    if source_pk is None or target_pk is None:
        raise InvariantViolationError(f"relation {relationship.id} requires both primary keys")
    foreign_key = None
    if relationship.foreign_key_property_id is not None:
        foreign_key = session.get(Property, relationship.foreign_key_property_id)
    payload = {
        "source_type_id": relationship.source_type_id,
        "target_type_id": relationship.target_type_id,
        "name": relationship.name,
        "cardinality": relationship.cardinality,
        "foreign_key_property_id": relationship.foreign_key_property_id,
        "foreign_key_datatype": foreign_key.datatype if foreign_key else None,
        "source_primary_key_attribute_id": source_pk.id,
        "source_primary_key_datatype": source_pk.datatype,
        "target_primary_key_attribute_id": target_pk.id,
        "target_primary_key_datatype": target_pk.datatype,
    }
    payload["definition_revision"] = _definition_revision(payload)
    return payload


def _definition_revision(payload: dict[str, Any]) -> int:
    encoded = json.dumps(payload, sort_keys=True, default=str, ensure_ascii=False).encode()
    return max(1, int.from_bytes(hashlib.sha256(encoded).digest()[:8], "big") & ((1 << 63) - 1))


__all__ = [
    "relation_definition_payload",
    "sync_relations",
    "sync_relations_best_effort",
    "sync_subject_relations",
    "sync_subject_relations_best_effort",
]
