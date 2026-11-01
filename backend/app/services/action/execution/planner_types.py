"""行动规则装配出的 edit set 数据形状。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class ObjectCreateEdit:
    object_type_id: uuid.UUID
    primary_key: str
    values: dict[uuid.UUID, Any]


@dataclass(frozen=True)
class ObjectUpdateEdit:
    object_type_id: uuid.UUID
    primary_key: str
    set_values: dict[uuid.UUID, Any]
    clear: tuple[uuid.UUID, ...]


@dataclass(frozen=True)
class ObjectDeleteEdit:
    object_type_id: uuid.UUID
    primary_key: str


@dataclass(frozen=True)
class RelationEdit:
    relation_id: uuid.UUID
    from_object_type_id: uuid.UUID
    from_primary_key: str
    to_object_type_id: uuid.UUID
    to_primary_key: str


@dataclass(frozen=True)
class EditSetDraft:
    object_creates: tuple[ObjectCreateEdit, ...] = ()
    object_updates: tuple[ObjectUpdateEdit, ...] = ()
    object_deletes: tuple[ObjectDeleteEdit, ...] = ()
    relation_creates: tuple[RelationEdit, ...] = ()
    relation_deletes: tuple[RelationEdit, ...] = ()
    #: 首次预览时生成、确认后必须沿用的值（例如 current_time）。
    generated_values: dict[uuid.UUID, Any] = field(default_factory=dict)


__all__ = [
    "EditSetDraft",
    "ObjectCreateEdit",
    "ObjectDeleteEdit",
    "ObjectUpdateEdit",
    "RelationEdit",
]
