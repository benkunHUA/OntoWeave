from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import InstrumentedAttribute, Session

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.models import ObjectType, Property, Relation
from app.vocabulary import (
    DEFAULT_CARDINALITY,
    DEFAULT_DATATYPE,
    PROPERTY_DATATYPES,
    RELATION_CARDINALITIES,
    PropertyDatatype,
    RelationCardinality,
)

DRAFT = "draft"
CONFIRMED = "confirmed"
APPROVED = "approved"

ALLOWED_TRANSITIONS: dict[str, set[str]] = {
    DRAFT: {CONFIRMED},
    CONFIRMED: {APPROVED},
    APPROVED: set(),
}


def create_object_type(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> ObjectType:
    obj = ObjectType(
        owner_id=owner_id,
        name=name,
        description=description,
        status=DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(obj)
    session.flush()
    return obj


def get_object_type(session: Session, object_type_id: uuid.UUID) -> ObjectType:
    obj = session.get(ObjectType, object_type_id)
    if obj is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    return obj


def list_object_types(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[ObjectType]:
    """按名称排序分页。加上 id 作次级排序键，保证翻页时顺序稳定。"""
    stmt = select(ObjectType)
    if owner_id is not None:
        stmt = stmt.where(ObjectType.owner_id == owner_id)
    return list(
        session.scalars(stmt.order_by(ObjectType.name, ObjectType.id).limit(limit).offset(offset))
    )


def transition_object_type(
    session: Session,
    object_type_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> ObjectType:
    obj = get_object_type(session, object_type_id)
    if owner_id is not None and obj.owner_id != owner_id:
        raise NotFoundError(f"object type {object_type_id} not found")
    if target not in ALLOWED_TRANSITIONS.get(obj.status, set()):
        raise InvalidTransitionError(
            f"object type {object_type_id} cannot move {obj.status} -> {target}"
        )
    obj.status = target
    session.flush()
    return obj


def create_property(
    session: Session,
    *,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = DEFAULT_DATATYPE,
    required: bool = False,
    is_array: bool = False,
    is_primary_key: bool = False,
    is_title_key: bool = False,
) -> Property:
    get_object_type(session, owner_type_id)
    _ensure_datatype(datatype)
    if is_primary_key:
        _ensure_key_free(session, owner_type_id=owner_type_id, column=Property.is_primary_key)
    if is_title_key:
        _ensure_key_free(session, owner_type_id=owner_type_id, column=Property.is_title_key)
    prop = Property(
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
        is_array=is_array,
        is_primary_key=is_primary_key,
        is_title_key=is_title_key,
        status=DRAFT,
    )
    session.add(prop)
    session.flush()
    return prop


def _ensure_datatype(datatype: str) -> None:
    try:
        PropertyDatatype(datatype)
    except ValueError as exc:
        raise InvariantViolationError(
            f"unsupported datatype {datatype!r}; expected one of {list(PROPERTY_DATATYPES)}"
        ) from exc


def _ensure_key_free(
    session: Session,
    *,
    owner_type_id: uuid.UUID,
    column: InstrumentedAttribute[bool],
    excluding_id: uuid.UUID | None = None,
) -> None:
    """一个对象类型至多一个主键 / 标题键；已有别的属性占着就拒绝。

    column 直接传列属性（Property.is_primary_key / Property.is_title_key），
    不按字段名反射，写错在类型检查阶段就暴露。
    """
    stmt = select(Property).where(
        Property.owner_type_id == owner_type_id,
        column.is_(True),
    )
    if excluding_id is not None:
        stmt = stmt.where(Property.id != excluding_id)
    holder = session.scalar(stmt)
    if holder is not None:
        raise InvariantViolationError(
            f"object type {owner_type_id} already has a {column.key} on property {holder.id}"
        )


def validate_property_patch(session: Session, *, property_id: uuid.UUID, patch: dict) -> None:
    """更新属性前校验：datatype 合法、主键 / 标题键唯一。"""
    prop = session.get(Property, property_id)
    if prop is None:
        raise NotFoundError(f"property {property_id} not found")
    if "datatype" in patch:
        _ensure_datatype(patch["datatype"])
    if patch.get("is_primary_key"):
        _ensure_key_free(
            session,
            owner_type_id=prop.owner_type_id,
            column=Property.is_primary_key,
            excluding_id=prop.id,
        )
    if patch.get("is_title_key"):
        _ensure_key_free(
            session,
            owner_type_id=prop.owner_type_id,
            column=Property.is_title_key,
            excluding_id=prop.id,
        )


def validate_relation_patch(patch: dict) -> None:
    if "cardinality" not in patch:
        return
    try:
        RelationCardinality(patch["cardinality"])
    except ValueError as exc:
        raise InvariantViolationError(
            f"unsupported cardinality {patch['cardinality']!r}; "
            f"expected one of {list(RELATION_CARDINALITIES)}"
        ) from exc


def list_properties(session: Session, owner_type_id: uuid.UUID) -> list[Property]:
    return list(
        session.scalars(
            select(Property).where(Property.owner_type_id == owner_type_id).order_by(Property.name)
        )
    )


def create_relation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    cardinality: str = DEFAULT_CARDINALITY,
    forward_label: str | None = None,
    reverse_label: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> Relation:
    """幂等：同一对类型之间同名同向的关系只建一条，重复调用返回已有的那条。

    cardinality / 两端标签属于关系本身的语义，重复调用不会覆盖已存在的那条。
    """
    get_object_type(session, source_type_id)
    get_object_type(session, target_type_id)
    validate_relation_patch({"cardinality": cardinality})
    existing = session.scalar(
        select(Relation).where(
            Relation.source_type_id == source_type_id,
            Relation.target_type_id == target_type_id,
            Relation.name == name,
            Relation.direction == direction,
        )
    )
    if existing is not None:
        return existing
    rel = Relation(
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        cardinality=cardinality,
        forward_label=forward_label,
        reverse_label=reverse_label,
        status=DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(rel)
    session.flush()
    return rel


def get_relation(session: Session, relation_id: uuid.UUID) -> Relation:
    rel = session.get(Relation, relation_id)
    if rel is None:
        raise NotFoundError(f"relation {relation_id} not found")
    return rel


def transition_relation(
    session: Session,
    relation_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> Relation:
    rel = get_relation(session, relation_id)
    if owner_id is not None and rel.owner_id != owner_id:
        raise NotFoundError(f"relation {relation_id} not found")
    if target not in ALLOWED_TRANSITIONS.get(rel.status, set()):
        raise InvalidTransitionError(f"relation {relation_id} cannot move {rel.status} -> {target}")
    rel.status = target
    session.flush()
    return rel


def transition_property(
    session: Session,
    property_id: uuid.UUID,
    target: str,
    *,
    owner_id: uuid.UUID | None = None,
) -> Property:
    prop = session.get(Property, property_id)
    if prop is None:
        raise NotFoundError(f"property {property_id} not found")
    if owner_id is not None:
        owner_type = get_object_type(session, prop.owner_type_id)
        if owner_type.owner_id != owner_id:
            raise NotFoundError(f"property {property_id} not found")
    if target not in ALLOWED_TRANSITIONS.get(prop.status, set()):
        raise InvalidTransitionError(
            f"property {property_id} cannot move {prop.status} -> {target}"
        )
    prop.status = target
    session.flush()
    return prop
