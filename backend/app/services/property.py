"""对象类型属性的读写、类型校验与主键 / 标题键唯一约束。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import InstrumentedAttribute, Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Property
from app.services import object_type, status
from app.vocabulary import DEFAULT_DATATYPE, PROPERTY_DATATYPES, PropertyDatatype


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
    object_type.get_object_type(session, owner_type_id)
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
        status=status.DRAFT,
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


def list_properties(session: Session, owner_type_id: uuid.UUID) -> list[Property]:
    return list(
        session.scalars(
            select(Property).where(Property.owner_type_id == owner_type_id).order_by(Property.name)
        )
    )


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
        owner_type = object_type.get_object_type(session, prop.owner_type_id)
        if owner_type.owner_id != owner_id:
            raise NotFoundError(f"property {property_id} not found")
    status.ensure_transition(prop.status, target, f"property {property_id}")
    prop.status = target
    session.flush()
    return prop
