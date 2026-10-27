"""左侧栏资源汇总与工作台会话。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Conversation, ObjectType, Relation
from app.services import conversation
from app.services.ontology.bindings import safe_binding
from app.services.ontology.common import (
    OBJECT_TYPE,
    RELATION,
    relation_resource,
    resource_subject,
)
from app.services.ontology.relation_sync import sync_relations_best_effort
from app.vocabulary import ConversationKind, RelationCardinality


def list_resources(
    session: Session, client: Any, *, owner_id: uuid.UUID
) -> dict[str, list[dict[str, Any]]]:
    """一次返回左侧栏需要的对象类型、两类关系与绑定摘要。"""
    objects = list(
        session.scalars(
            select(ObjectType)
            .where(ObjectType.owner_id == owner_id)
            .order_by(ObjectType.name, ObjectType.id)
        )
    )
    relations = list(
        session.scalars(
            select(Relation)
            .where(Relation.owner_id == owner_id)
            .order_by(Relation.name, Relation.id)
        )
    )
    sync_relations_best_effort(session, client, owner_id=owner_id, relations=relations)
    names = {obj.id: obj.name for obj in objects}
    object_resources = [
        {
            "id": obj.id,
            "name": obj.name,
            "name_en": obj.name_en,
            "status": obj.status,
            "kind": OBJECT_TYPE,
            "binding": safe_binding(
                client, owner_id=owner_id, subject_kind=OBJECT_TYPE, subject_id=obj.id
            ),
        }
        for obj in objects
    ]
    foreign_keys: list[dict[str, Any]] = []
    many_to_many: list[dict[str, Any]] = []
    for relation in relations:
        item = relation_resource(
            relation,
            source_name=names.get(relation.source_type_id),
            target_name=names.get(relation.target_type_id),
        )
        if relation.cardinality == RelationCardinality.MANY_TO_MANY:
            item["binding"] = safe_binding(
                client, owner_id=owner_id, subject_kind=RELATION, subject_id=relation.id
            )
            many_to_many.append(item)
        else:
            foreign_keys.append(item)
    return {
        "object_types": object_resources,
        "foreign_key_relations": foreign_keys,
        "many_to_many_relations": many_to_many,
    }


def ensure_conversation(
    session: Session, *, owner_id: uuid.UUID, resource_kind: str, resource_id: uuid.UUID
) -> Conversation:
    subject = resource_subject(
        session, owner_id=owner_id, resource_kind=resource_kind, resource_id=resource_id
    )
    return conversation.ensure_resource_conversation(
        session,
        owner_id=owner_id,
        title=f"{subject.name} 映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=resource_id,
    )


def find_conversation(
    session: Session, *, owner_id: uuid.UUID, resource_id: uuid.UUID
) -> Conversation | None:
    return conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=resource_id,
        owner_id=owner_id,
    )


__all__ = ["ensure_conversation", "find_conversation", "list_resources"]
