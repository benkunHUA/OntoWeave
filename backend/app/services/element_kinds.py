"""本体元素的种类注册表。

可进改动集的元素种类集中登记在这里：模型、可还原字段、归属解析、确认时的
状态流转、删除顺序、更新校验。changeset 与 element_edit 都从这里取，新增一种
元素时只加一行登记，不再各自维护一套按 kind 的分支。
"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from sqlalchemy.orm import Session

from app.models import ObjectType, Property, Relation
from app.services import object_type, property, relation, status

# 撤销时的字段白名单用 frozenset；归属解析返回元素所属用户；promote 在确认时
# 把草稿翻成已确认；delete_phase 决定删除顺序（小的先删，先子后父）。


def _owner_id_attribute(session: Session, element: Any) -> uuid.UUID | None:
    """元素自带 owner_id 时直接取（对象类型、关系、动作类型都是共享池元素）。"""
    return getattr(element, "owner_id", None)


def _property_owner_id(session: Session, element: Any) -> uuid.UUID | None:
    """属性的归属看它所属的对象类型。"""
    owner_type = session.get(ObjectType, element.owner_type_id)
    return owner_type.owner_id if owner_type is not None else None


def _promote_object_type(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    object_type.transition_object_type(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _promote_property(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    property.transition_property(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _promote_relation(session: Session, element_id: uuid.UUID, *, owner_id=None) -> None:
    relation.transition_relation(session, element_id, status.CONFIRMED, owner_id=owner_id)


def _validate_property_patch(session: Session, element_id: uuid.UUID, patch: dict) -> None:
    property.validate_property_patch(session, property_id=element_id, patch=patch)


def _validate_relation_patch(session: Session, element_id: uuid.UUID, patch: dict) -> None:
    relation.validate_relation_patch(session, relation_id=element_id, patch=patch)


@dataclass(frozen=True)
class ElementKind:
    model: type
    restorable_fields: frozenset[str]
    delete_phase: int
    load_owner_id: Callable[[Session, Any], uuid.UUID | None]
    promote: Callable[..., None]
    validate_patch: Callable[[Session, uuid.UUID, dict], None] | None = None


REGISTRY: dict[str, ElementKind] = {
    "object_type": ElementKind(
        model=ObjectType,
        restorable_fields=frozenset({"name", "description"}),
        delete_phase=2,
        load_owner_id=_owner_id_attribute,
        promote=_promote_object_type,
    ),
    "property": ElementKind(
        model=Property,
        restorable_fields=frozenset(
            {"name", "datatype", "required", "is_array", "is_primary_key", "is_title_key"}
        ),
        delete_phase=1,
        load_owner_id=_property_owner_id,
        promote=_promote_property,
        validate_patch=_validate_property_patch,
    ),
    "relation": ElementKind(
        model=Relation,
        restorable_fields=frozenset(
            {
                "name",
                "direction",
                "cardinality",
                "forward_label",
                "reverse_label",
                "foreign_key_property_id",
            }
        ),
        delete_phase=0,
        load_owner_id=_owner_id_attribute,
        promote=_promote_relation,
        validate_patch=_validate_relation_patch,
    ),
}


def get(kind: str) -> ElementKind:
    """按种类取登记项；未登记就抛，报错统一在调用点转成领域错误。"""
    entry = REGISTRY.get(kind)
    if entry is None:
        raise KeyError(kind)
    return entry
