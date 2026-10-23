"""本体建模的统一入口（门面）。

对象类型 / 属性 / 关系的读写按元素拆到 `object_type` / `property` / `relation`，
状态机抽到 `status`。这里只做转发，让既有调用方（changeset、element_edit、
agent 工具、API、测试）不必改 import。
"""

from __future__ import annotations

import hashlib
import json
import logging
import uuid
from difflib import SequenceMatcher
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    InvariantViolationError,
    NotFoundError,
)
from app.models import Conversation, ObjectType, Property, Relation
from app.services import conversation
from app.services.object_type import (
    create_object_type,
    get_object_type,
    list_object_types,
    transition_object_type,
)
from app.services.property import (
    create_property,
    get_property,
    list_properties,
    transition_property,
    validate_property_patch,
)
from app.services.relation import (
    create_relation,
    get_relation,
    transition_relation,
    validate_relation_patch,
)
from app.services.status import ALLOWED_TRANSITIONS, APPROVED, CONFIRMED, DRAFT
from app.vocabulary import ConversationKind, RelationCardinality

logger = logging.getLogger(__name__)

OBJECT_TYPE = "object_type"
RELATION = "relation"
DIRECTION_LABELS = {"outgoing": "出向", "incoming": "入向", "both": "双向"}


def normalize_resource_kind(value: str) -> str:
    normalized = value.strip().lower().replace("-", "_")
    if normalized in {OBJECT_TYPE, "objecttype", "object_types"}:
        return OBJECT_TYPE
    if normalized in {RELATION, "relation_type", "relation_types", "relations"}:
        return RELATION
    raise InvalidRequestError(f"unsupported resource kind: {value}")


def resource_subject(
    session: Session, *, owner_id: uuid.UUID, resource_kind: str, resource_id: uuid.UUID
) -> ObjectType | Relation:
    kind = normalize_resource_kind(resource_kind)
    if kind == OBJECT_TYPE:
        obj = get_object_type(session, resource_id)
        if obj.owner_id != owner_id:
            raise NotFoundError(f"object type {resource_id} not found")
        return obj
    rel = get_relation(session, resource_id)
    if rel.owner_id != owner_id:
        raise NotFoundError(f"relation {resource_id} not found")
    return rel


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
            "status": obj.status,
            "kind": OBJECT_TYPE,
            "binding": _safe_binding(
                client, owner_id=owner_id, subject_kind=OBJECT_TYPE, subject_id=obj.id
            ),
        }
        for obj in objects
    ]
    fk: list[dict[str, Any]] = []
    many: list[dict[str, Any]] = []
    for rel in relations:
        item = _relation_resource(
            rel,
            source_name=names.get(rel.source_type_id),
            target_name=names.get(rel.target_type_id),
        )
        if rel.cardinality == RelationCardinality.MANY_TO_MANY:
            item["binding"] = _safe_binding(
                client, owner_id=owner_id, subject_kind=RELATION, subject_id=rel.id
            )
            many.append(item)
        else:
            fk.append(item)
    return {
        "object_types": object_resources,
        "foreign_key_relations": fk,
        "many_to_many_relations": many,
    }


def ensure_conversation(
    session: Session, *, owner_id: uuid.UUID, resource_kind: str, resource_id: uuid.UUID
) -> Conversation:
    subject = resource_subject(
        session, owner_id=owner_id, resource_kind=resource_kind, resource_id=resource_id
    )
    existing = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=resource_id,
        owner_id=owner_id,
    )
    if existing is not None:
        return existing
    return conversation.create_conversation(
        session,
        owner_id=owner_id,
        title=f"{subject.name} 映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=resource_id,
    )


def get_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> dict[str, Any]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    if kind == OBJECT_TYPE:
        return client.get_object_binding(subject_id, owner_id=owner_id)
    return client.get_relation_binding(subject_id, owner_id=owner_id)


def save_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any],
    operation_id: uuid.UUID | None = None,
) -> dict[str, Any]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    if kind == OBJECT_TYPE:
        return client.put_object_binding(
            subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
        )
    return client.put_relation_binding(
        subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
    )


def delete_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> None:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    if kind == OBJECT_TYPE:
        client.delete_object_binding(subject_id, owner_id=owner_id, operation_id=operation_id)
    else:
        client.delete_relation_binding(subject_id, owner_id=owner_id, operation_id=operation_id)


def validate_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    if kind != OBJECT_TYPE:
        raise InvalidRequestError("relation bindings do not support validate")
    return client.validate_object_binding(subject_id, owner_id=owner_id, payload=payload)


def preview_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    if kind == OBJECT_TYPE:
        return client.preview_object_binding(subject_id, owner_id=owner_id, payload=payload)
    return client.preview_relation_binding(subject_id, owner_id=owner_id, payload=payload)


def generate_binding(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    payload: dict[str, Any],
    operation_id: uuid.UUID | None = None,
) -> dict[str, Any]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    try:
        sync_subject_relations(
            session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
        )
    except Exception as exc:
        raise DataServiceUnavailableError(f"关系定义同步失败，生成已取消：{exc}") from exc
    if kind == OBJECT_TYPE:
        return client.generate_object_binding(
            subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
        )
    return client.generate_relation_binding(
        subject_id, owner_id=owner_id, payload=payload, operation_id=operation_id
    )


def list_runs(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    limit: int = 20,
) -> list[dict[str, Any]]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    if kind == OBJECT_TYPE:
        return client.list_object_runs(subject_id, owner_id=owner_id, limit=limit)
    return client.list_relation_runs(subject_id, owner_id=owner_id, limit=limit)


def get_run(client: Any, *, owner_id: uuid.UUID, run_id: uuid.UUID) -> dict[str, Any]:
    return client.get_run(run_id, owner_id=owner_id)


def cancel_run(
    client: Any,
    *,
    owner_id: uuid.UUID,
    run_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict[str, Any]:
    return client.cancel_run(run_id, owner_id=owner_id, operation_id=operation_id)


def list_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    page: int,
    page_size: int,
    q: str | None,
    filters: list[dict[str, Any]] | None,
) -> dict[str, Any]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    return client.list_objects(
        object_type_id,
        owner_id=owner_id,
        page=page,
        page_size=page_size,
        q=q,
        filters=filters,
    )


def get_object(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    primary_key: str,
) -> dict[str, Any]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    detail = client.get_object(object_type_id, owner_id=owner_id, primary_key=primary_key)
    return merge_object_detail(session, object_type_id=object_type_id, detail=detail)


def merge_object_detail(
    session: Session, *, object_type_id: uuid.UUID, detail: dict[str, Any]
) -> dict[str, Any]:
    """按本体定义顺序输出全部属性；未映射或缺失值补 null。"""
    raw = detail.get("properties")
    values: dict[str, Any] = {}
    if isinstance(raw, dict):
        values = {str(key): value for key, value in raw.items()}
        values = {
            str(key): value.get("value") if isinstance(value, dict) and "value" in value else value
            for key, value in values.items()
        }
    elif isinstance(raw, list):
        for item in raw:
            if not isinstance(item, dict):
                continue
            attribute_id = item.get("attribute_id") or item.get("attributeId")
            if attribute_id is not None:
                values[str(attribute_id)] = item.get("value")
    merged: dict[str, Any] = {}
    properties = list(
        session.scalars(
            select(Property)
            .where(Property.owner_type_id == object_type_id)
            .order_by(Property.name, Property.id)
        )
    )
    for prop in properties:
        merged[str(prop.id)] = values.get(str(prop.id))
    return {**detail, "properties": merged}


def relation_groups(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    primary_key: str,
    direction: str = "both",
) -> list[dict[str, Any]]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    groups = client.list_object_relation_groups(
        object_type_id, owner_id=owner_id, primary_key=primary_key, direction=direction
    )
    return [_decorate_group(session, group) for group in groups]


def related_objects(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    object_type_id: uuid.UUID,
    relation_id: uuid.UUID,
    primary_key: str,
    direction: str,
    page: int,
    page_size: int,
) -> dict[str, Any]:
    resource_subject(
        session, owner_id=owner_id, resource_kind=OBJECT_TYPE, resource_id=object_type_id
    )
    return client.list_object_relations(
        object_type_id,
        relation_id,
        owner_id=owner_id,
        primary_key=primary_key,
        direction=direction,
        page=page,
        page_size=page_size,
    )


def list_relation_instances(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    relation_id: uuid.UUID,
    page: int,
    page_size: int,
    from_primary_key: str | None,
    to_primary_key: str | None,
) -> dict[str, Any]:
    resource_subject(session, owner_id=owner_id, resource_kind=RELATION, resource_id=relation_id)
    return client.list_relation_instances(
        relation_id,
        owner_id=owner_id,
        page=page,
        page_size=page_size,
        from_primary_key=from_primary_key,
        to_primary_key=to_primary_key,
    )


def get_relation_overview(
    session: Session, client: Any, *, owner_id: uuid.UUID, relation_id: uuid.UUID
) -> dict[str, Any]:
    _normalize_subject(session, owner_id, RELATION, relation_id)
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=RELATION, subject_id=relation_id
    )
    return client.get_relation_overview(relation_id, owner_id=owner_id)


def suggest_mapping(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    dataset_kind: str | None = None,
    dataset_id: uuid.UUID | None = None,
    current_mapping: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """按名称相似度与类型兼容度给出建议；只填充空位，不覆盖已有兼容映射。"""
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    dataset_kind, dataset_id = _suggest_dataset(
        client,
        owner_id=owner_id,
        subject_kind=kind,
        subject_id=subject_id,
        dataset_kind=dataset_kind,
        dataset_id=dataset_id,
    )
    columns = client.list_dataset_columns(
        owner_id=owner_id, dataset_kind=dataset_kind, dataset_id=dataset_id
    )
    if kind == OBJECT_TYPE:
        mapping, reasons = _suggest_object_mapping(
            session,
            object_type_id=subject_id,
            columns=columns,
            current=current_mapping,
        )
    else:
        mapping, reasons = _suggest_relation_mapping(
            session, relation_id=subject_id, columns=columns, current=current_mapping
        )
    return {
        "dataset_kind": dataset_kind,
        "dataset_id": dataset_id,
        "mapping": mapping,
        "reasons": reasons,
    }


def get_subject_definition(
    session: Session, *, owner_id: uuid.UUID, subject_kind: str, subject_id: uuid.UUID
) -> dict[str, Any]:
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    if kind == OBJECT_TYPE:
        obj = get_object_type(session, subject_id)
        return {**_object_brief(obj), "properties": _properties(session, obj.id)}
    rel = get_relation(session, subject_id)
    source = get_object_type(session, rel.source_type_id)
    target = get_object_type(session, rel.target_type_id)
    foreign_key = (
        session.get(Property, rel.foreign_key_property_id) if rel.foreign_key_property_id else None
    )
    return {
        **_relation_brief(rel, source_name=source.name, target_name=target.name),
        "source": {**_object_brief(source), "properties": _properties(session, source.id)},
        "target": {**_object_brief(target), "properties": _properties(session, target.id)},
        "foreign_key_property": _property_brief(foreign_key) if foreign_key else None,
    }


def load_agent_state(
    session: Session,
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> dict[str, Any]:
    """每轮重新读取资源、绑定、草稿与最近 run；失败时保留可解释的 load_error。"""
    kind = _normalize_subject(session, owner_id, subject_kind, subject_id)
    definition = get_subject_definition(
        session, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    sync_subject_relations_best_effort(
        session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id
    )
    try:
        binding = _safe_binding(client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id)
        runs = list_runs(
            session, client, owner_id=owner_id, subject_kind=kind, subject_id=subject_id, limit=1
        )
        return {
            "subject_kind": kind,
            "subject_id": subject_id,
            "definition": definition,
            "binding": binding,
            "mapping": binding.get("draft_mapping") if binding else None,
            "latest_run": runs[0] if runs else None,
            "load_error": None,
        }
    except Exception as exc:  # 数据服务失败时仍允许 Agent 解释现状，不阻塞对话。
        return {
            "subject_kind": kind,
            "subject_id": subject_id,
            "definition": definition,
            "binding": None,
            "mapping": None,
            "latest_run": None,
            "load_error": str(exc),
        }


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
    source_pk = _primary_key_property(session, relationship.source_type_id)
    target_pk = _primary_key_property(session, relationship.target_type_id)
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


def _normalize_subject(
    session: Session, owner_id: uuid.UUID, subject_kind: str, subject_id: uuid.UUID
) -> str:
    kind = normalize_resource_kind(subject_kind)
    resource_subject(session, owner_id=owner_id, resource_kind=kind, resource_id=subject_id)
    return kind


def _safe_binding(
    client: Any, *, owner_id: uuid.UUID, subject_kind: str, subject_id: uuid.UUID
) -> dict[str, Any] | None:
    try:
        if subject_kind == OBJECT_TYPE:
            return client.get_object_binding(subject_id, owner_id=owner_id)
        return client.get_relation_binding(subject_id, owner_id=owner_id)
    except (NotFoundError, DataServiceUnavailableError):
        return None


def _relation_resource(
    relation: Relation, *, source_name: str | None, target_name: str | None
) -> dict[str, Any]:
    forward = relation.forward_label or relation.name
    reverse = relation.reverse_label or relation.name
    source = source_name or str(relation.source_type_id)
    target = target_name or str(relation.target_type_id)
    return {
        "id": relation.id,
        "name": relation.name,
        "status": relation.status,
        "kind": RELATION,
        "cardinality": relation.cardinality,
        "relation_kind": (
            "MANY_TO_MANY" if relation.cardinality == RelationCardinality.MANY_TO_MANY else "FK"
        ),
        "source_type_id": relation.source_type_id,
        "source_type_name": source_name,
        "target_type_id": relation.target_type_id,
        "target_type_name": target_name,
        "forward_label": forward,
        "reverse_label": reverse,
        "direction_label": f"{source} --{forward}--> {target}",
        "foreign_key_property_id": relation.foreign_key_property_id,
        "binding": None,
    }


def _decorate_group(session: Session, group: dict[str, Any]) -> dict[str, Any]:
    relation_id = group.get("relation_id")
    relation = session.get(Relation, relation_id) if relation_id else None
    direction = _direction(group.get("direction"))
    source = get_object_type(session, relation.source_type_id) if relation else None
    target = get_object_type(session, relation.target_type_id) if relation else None
    label = group.get("label")
    if relation is not None:
        if direction == "incoming":
            label = relation.reverse_label or relation.name
        elif direction == "outgoing":
            label = relation.forward_label or relation.name
        else:
            label = relation.name
    items = group.get("items") or group.get("objects") or group.get("counterparts") or []
    return {
        **group,
        "relation_id": relation_id,
        "direction": direction,
        "direction_label": DIRECTION_LABELS[direction],
        "label": label,
        "source_type_id": relation.source_type_id if relation else group.get("source_type_id"),
        "source_type_name": source.name if source else group.get("source_type_name"),
        "target_type_id": relation.target_type_id if relation else group.get("target_type_id"),
        "target_type_name": target.name if target else group.get("target_type_name"),
        "total": group.get("total", len(items)),
        "items": items,
    }


def _direction(value: Any) -> str:
    normalized = str(value or "both").strip().lower()
    if normalized in {"outgoing", "out", "from", "forward"}:
        return "outgoing"
    if normalized in {"incoming", "in", "to", "reverse"}:
        return "incoming"
    return "both"


def _object_brief(obj: ObjectType) -> dict[str, Any]:
    return {
        "id": obj.id,
        "name": obj.name,
        "description": obj.description,
        "status": obj.status,
    }


def _properties(session: Session, object_type_id: uuid.UUID) -> list[dict[str, Any]]:
    rows = session.scalars(
        select(Property)
        .where(Property.owner_type_id == object_type_id)
        .order_by(Property.name, Property.id)
    )
    return [_property_brief(prop) for prop in rows]


def _property_brief(prop: Property) -> dict[str, Any]:
    return {
        "id": prop.id,
        "name": prop.name,
        "datatype": prop.datatype,
        "required": prop.required,
        "is_array": prop.is_array,
        "is_primary_key": prop.is_primary_key,
        "is_title_key": prop.is_title_key,
        "enum_values": prop.enum_values,
        "status": prop.status,
    }


def _relation_brief(
    relation: Relation, *, source_name: str | None, target_name: str | None
) -> dict[str, Any]:
    return {
        "id": relation.id,
        "name": relation.name,
        "status": relation.status,
        "cardinality": relation.cardinality,
        "source_type_id": relation.source_type_id,
        "source_type_name": source_name,
        "target_type_id": relation.target_type_id,
        "target_type_name": target_name,
        "forward_label": relation.forward_label,
        "reverse_label": relation.reverse_label,
        "foreign_key_property_id": relation.foreign_key_property_id,
    }


def _primary_key_property(session: Session, object_type_id: uuid.UUID) -> Property | None:
    return session.scalar(
        select(Property).where(
            Property.owner_type_id == object_type_id, Property.is_primary_key.is_(True)
        )
    )


def _definition_revision(payload: dict[str, Any]) -> int:
    encoded = json.dumps(payload, sort_keys=True, default=str, ensure_ascii=False).encode()
    return max(1, int.from_bytes(hashlib.sha256(encoded).digest()[:8], "big") & ((1 << 63) - 1))


def _mapping_value(row: dict[str, Any], snake: str, camel: str | None = None) -> Any:
    return row.get(snake, row.get(camel)) if camel else row.get(snake)


def _suggest_dataset(
    client: Any,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    dataset_kind: str | None,
    dataset_id: uuid.UUID | None,
) -> tuple[str, uuid.UUID]:
    if dataset_id is None:
        if dataset_kind is not None:
            raise InvalidRequestError("dataset_kind requires dataset_id")
        binding = _safe_binding(
            client, owner_id=owner_id, subject_kind=subject_kind, subject_id=subject_id
        )
        if binding is None:
            raise InvalidRequestError("请先选择数据集")
        kind = binding.get("draft_dataset_kind") or binding.get("active_dataset_kind")
        identity = binding.get("draft_dataset_id") or binding.get("active_dataset_id")
        if not kind or not identity:
            raise InvalidRequestError("当前绑定没有可用数据集")
        return str(kind).upper(), uuid.UUID(str(identity))
    if dataset_kind is not None:
        return dataset_kind.upper(), dataset_id
    binding = _safe_binding(
        client, owner_id=owner_id, subject_kind=subject_kind, subject_id=subject_id
    )
    kind = binding.get("draft_dataset_kind") if binding else None
    if not kind:
        raise InvalidRequestError("dataset_kind is required for an unbound subject")
    return str(kind).upper(), dataset_id


def _suggest_object_mapping(
    session: Session,
    *,
    object_type_id: uuid.UUID,
    columns: list[dict[str, Any]],
    current: dict[str, Any] | None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    properties = _properties(session, object_type_id)
    by_id = {str(item["id"]): item for item in properties}
    columns_by_name = {_column_name(item): item for item in columns}
    current = current or {}
    current_rows = current.get("attributes") or []
    mapped: dict[str, str] = {}
    reasons: list[dict[str, Any]] = []
    used: set[str] = set()
    for row in current_rows:
        attribute_id = str(_mapping_value(row, "attribute_id", "attributeId"))
        current_column = str(_mapping_value(row, "column", "column") or "")
        prop = by_id.get(attribute_id)
        if prop is None or current_column in used or prop["is_array"]:
            continue
        if not _type_compatible(prop["datatype"], columns_by_name.get(current_column)):
            continue
        mapped[attribute_id] = current_column
        used.add(current_column)
    for prop in properties:
        attribute_id = str(prop["id"])
        if attribute_id in mapped or prop["is_array"]:
            continue
        candidates = [
            item
            for item in columns
            if _column_name(item) not in used and _type_compatible(prop["datatype"], item)
        ]
        if not candidates:
            continue
        selected = max(
            candidates,
            key=lambda item: (
                _name_similarity(prop["name"], _column_name(item)),
                _column_name(item),
            ),
        )
        selected_column = _column_name(selected)
        score = _name_similarity(prop["name"], selected_column)
        mapped[attribute_id] = selected_column
        used.add(selected_column)
        reasons.append(
            {
                "attribute_id": prop["id"],
                "column": selected_column,
                "reason": _reason(prop["name"], selected_column, score),
                "score": round(score, 3),
            }
        )
    primary = _pick_key(
        properties, mapped, current, "primary_key_attribute_id", ("id", "key", "pk")
    )
    if primary is None:
        raise InvalidRequestError("没有可用于主键的类型兼容列")
    title = _pick_key(
        properties, mapped, current, "title_key_attribute_id", ("name", "title", "label", "email")
    )
    attributes = [
        _attribute_mapping(by_id[attribute_id], column)
        for attribute_id, column in mapped.items()
        if attribute_id in by_id
    ]
    mapping: dict[str, Any] = {
        "primary_key_attribute_id": uuid.UUID(primary),
        "title_key_attribute_id": uuid.UUID(title) if title else None,
        "attributes": attributes,
    }
    return mapping, reasons


def _suggest_relation_mapping(
    session: Session,
    *,
    relation_id: uuid.UUID,
    columns: list[dict[str, Any]],
    current: dict[str, Any] | None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    relation = get_relation(session, relation_id)
    source_pk = _primary_key_property(session, relation.source_type_id)
    target_pk = _primary_key_property(session, relation.target_type_id)
    if source_pk is None or target_pk is None:
        raise InvalidRequestError("关系两端都必须先设置主键")
    current = current or {}
    from_column = str(_mapping_value(current, "from_key_column", "fromKeyColumn") or "")
    to_column = str(_mapping_value(current, "to_key_column", "toKeyColumn") or "")
    names = {_column_name(item): item for item in columns}
    if not _valid_relation_column(from_column, source_pk.datatype, names, set()):
        from_column = ""
    if not _valid_relation_column(to_column, target_pk.datatype, names, {from_column}):
        to_column = ""
    if from_column:
        from_score = _name_similarity(source_pk.name, from_column)
    else:
        from_column, from_score = _pick_relation_column(
            columns, source_pk.name, source_pk.datatype, used={to_column} if to_column else set()
        )
    if to_column:
        to_score = _name_similarity(target_pk.name, to_column)
    else:
        to_column, to_score = _pick_relation_column(
            columns,
            target_pk.name,
            target_pk.datatype,
            used={from_column} if from_column else set(),
        )
    reasons = [
        {
            "attribute_id": source_pk.id,
            "column": from_column or "",
            "reason": _reason(source_pk.name, from_column or "", from_score),
            "score": round(from_score, 3),
        },
        {
            "attribute_id": target_pk.id,
            "column": to_column or "",
            "reason": _reason(target_pk.name, to_column or "", to_score),
            "score": round(to_score, 3),
        },
    ]
    mapping = {
        "from_key_column": from_column,
        "from_key_datatype": source_pk.datatype,
        "to_key_column": to_column,
        "to_key_datatype": target_pk.datatype,
    }
    return mapping, reasons


def _attribute_mapping(prop: dict[str, Any], column: str) -> dict[str, Any]:
    return {
        "attribute_id": prop["id"],
        "column": column,
        "datatype": prop["datatype"],
        "required": prop["required"],
        "is_array": prop["is_array"],
        "enum_values": prop["enum_values"],
    }


def _pick_key(
    properties: list[dict[str, Any]],
    mapped: dict[str, str],
    current: dict[str, Any],
    field: str,
    hints: tuple[str, ...],
) -> str | None:
    current_id = _mapping_value(current, field, _camel_key(field))
    if current_id is not None and str(current_id) in mapped:
        return str(current_id)
    flag = "is_primary_key" if field == "primary_key_attribute_id" else "is_title_key"
    flagged = [item for item in properties if item.get(flag) and str(item["id"]) in mapped]
    if flagged:
        return str(flagged[0]["id"])
    hinted = [
        item for item in properties if str(item["id"]) in mapped and _has_hint(item["name"], hints)
    ]
    if hinted:
        return str(hinted[0]["id"])
    return None


def _has_hint(name: str, hints: tuple[str, ...]) -> bool:
    tokens = set(_tokens(name))
    return bool(tokens & set(hints)) or any(hint in _normalize_name(name) for hint in hints)


def _pick_relation_column(
    columns: list[dict[str, Any]], name: str, datatype: str, *, used: set[str]
) -> tuple[str, float]:
    candidates = [
        item
        for item in columns
        if _column_name(item) not in used and _type_compatible(datatype, item)
    ]
    if not candidates:
        return "", 0.0
    selected = max(
        candidates,
        key=lambda item: (_name_similarity(name, _column_name(item)), _column_name(item)),
    )
    return _column_name(selected), _name_similarity(name, _column_name(selected))


def _valid_relation_column(
    column: str, datatype: str, columns: dict[str, dict[str, Any]], used: set[str]
) -> bool:
    return bool(column) and column not in used and _type_compatible(datatype, columns.get(column))


def _column_name(column: dict[str, Any]) -> str:
    return str(column.get("name") or "")


def _column_type(column: dict[str, Any] | None) -> str:
    if column is None:
        return ""
    return str(column.get("type") or "").split("(", 1)[0].strip().lower()


def _type_compatible(datatype: str, column: dict[str, Any] | None) -> bool:
    expected = str(datatype).lower()
    actual = _column_type(column)
    allowed = {
        "text": {"string", "uuid", "varchar", "char", "text"},
        "integer": {"int", "integer", "long", "bigint", "smallint"},
        "decimal": {"decimal", "numeric", "int", "integer", "long", "bigint"},
        "boolean": {"boolean", "bool"},
        "date": {"date"},
        "timestamp": {"timestamp", "timestamptz", "datetime"},
        "enum": {"string", "varchar", "char", "text"},
    }
    return actual in allowed.get(expected, set())


def _name_similarity(left: str, right: str) -> float:
    a = _normalize_name(left)
    b = _normalize_name(right)
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    if a in b or b in a:
        return 0.8
    tokens = set(_tokens(left)) & set(_tokens(right))
    if tokens:
        return min(0.9, 0.6 + 0.1 * len(tokens))
    return round(SequenceMatcher(None, a, b).ratio() * 0.65, 3)


def _reason(left: str, right: str, score: float) -> str:
    if not right:
        return "没有类型兼容的剩余列"
    if score >= 0.99:
        return "名称完全一致且类型兼容"
    if score >= 0.6:
        return "名称相近且类型兼容"
    return "类型兼容"


def _normalize_name(value: str) -> str:
    return "".join(char.lower() for char in value if char.isalnum())


def _tokens(value: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    for index, char in enumerate(value):
        if not char.isalnum():
            if current:
                parts.append("".join(current).lower())
                current = []
            continue
        if char.isupper() and current and value[index - 1].islower():
            parts.append("".join(current).lower())
            current = []
        current.append(char)
    if current:
        parts.append("".join(current).lower())
    return parts


def _camel_key(key: str) -> str:
    head, *tail = key.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


__all__ = [
    "ALLOWED_TRANSITIONS",
    "APPROVED",
    "CONFIRMED",
    "DRAFT",
    "create_object_type",
    "create_property",
    "create_relation",
    "cancel_run",
    "delete_binding",
    "ensure_conversation",
    "generate_binding",
    "get_binding",
    "get_object_type",
    "get_object",
    "get_property",
    "get_relation",
    "get_relation_overview",
    "get_run",
    "get_subject_definition",
    "list_objects",
    "list_relation_instances",
    "list_resources",
    "list_runs",
    "list_object_types",
    "list_properties",
    "load_agent_state",
    "merge_object_detail",
    "normalize_resource_kind",
    "preview_binding",
    "relation_definition_payload",
    "relation_groups",
    "related_objects",
    "resource_subject",
    "save_binding",
    "suggest_mapping",
    "sync_relations",
    "sync_relations_best_effort",
    "sync_subject_relations",
    "sync_subject_relations_best_effort",
    "transition_object_type",
    "transition_property",
    "transition_relation",
    "validate_binding",
    "validate_property_patch",
    "validate_relation_patch",
]
