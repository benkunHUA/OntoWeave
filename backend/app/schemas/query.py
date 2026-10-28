"""业务问答的查询工具入参与结果形状。"""

from __future__ import annotations

import uuid
from enum import StrEnum
from typing import Any

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator, model_validator


class QueryStatus(StrEnum):
    OK = "OK"
    EMPTY = "EMPTY"
    TRUNCATED = "TRUNCATED"
    NOT_INDEXED = "NOT_INDEXED"
    FAILED = "FAILED"


class QueryDirection(StrEnum):
    OUTGOING = "outgoing"
    INCOMING = "incoming"

    @classmethod
    def _missing_(cls, value: object) -> QueryDirection | None:
        aliases = {
            "forward": cls.OUTGOING,
            "out": cls.OUTGOING,
            "source_to_target": cls.OUTGOING,
            "backward": cls.INCOMING,
            "reverse": cls.INCOMING,
            "in": cls.INCOMING,
            "target_to_source": cls.INCOMING,
        }
        return aliases.get(str(value).strip().lower())


class QuerySortDirection(StrEnum):
    ASC = "asc"
    DESC = "desc"


class QueryFilterOp(StrEnum):
    EQ = "eq"
    NE = "ne"
    CONTAINS = "contains"
    GT = "gt"
    GTE = "gte"
    LT = "lt"
    LTE = "lte"
    IS_NULL = "is_null"
    IS_NOT_NULL = "is_not_null"


UNARY_QUERY_OPS = frozenset({QueryFilterOp.IS_NULL, QueryFilterOp.IS_NOT_NULL})


class _PropertyRef(BaseModel):
    model_config = ConfigDict(extra="forbid")

    property_id: str = Field(
        validation_alias=AliasChoices(
            "property_id",
            "property",
            "attribute_id",
            "attributeId",
            "field",
        )
    )


class QueryFilter(_PropertyRef):
    op: QueryFilterOp = Field(validation_alias=AliasChoices("op", "operator"))
    value: Any | None = None

    @model_validator(mode="after")
    def validate_shape(self) -> QueryFilter:
        if self.op in UNARY_QUERY_OPS:
            if "value" in self.model_fields_set:
                raise ValueError(f"{self.op.value} does not accept a value")
        elif "value" not in self.model_fields_set:
            raise ValueError(f"{self.op.value} requires a value")
        return self


class QuerySort(_PropertyRef):
    direction: QuerySortDirection = QuerySortDirection.ASC


class QueryObjectsInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    object_type_id: str
    primary_keys: list[str] = Field(default_factory=list, max_length=50)
    q: str | None = None
    filters: list[QueryFilter] = Field(default_factory=list, max_length=20)
    sort: list[QuerySort] = Field(default_factory=list, max_length=3)
    limit: int = Field(default=20, ge=1, le=50)
    project: list[str] = Field(default_factory=list, max_length=50)

    @field_validator("primary_keys")
    @classmethod
    def normalize_primary_keys(cls, values: list[str]) -> list[str]:
        resolved: list[str] = []
        seen: set[str] = set()
        for value in values:
            text = value.strip()
            if not text:
                raise ValueError("primary_keys cannot contain empty values")
            if text not in seen:
                seen.add(text)
                resolved.append(text)
        return resolved


class ObjectReferenceInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    object_type_id: str
    primary_key: str = Field(min_length=1)


class RelatedObjectsInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    relation_id: str
    direction: QueryDirection
    inputs: list[ObjectReferenceInput] = Field(min_length=1, max_length=50)
    limit: int = Field(default=50, ge=1, le=50)


class QueryItem(BaseModel):
    object_type_id: uuid.UUID | None = None
    primary_key: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)


class QueryObjectsResult(BaseModel):
    object_type_id: uuid.UUID
    index_version_id: uuid.UUID | None = None
    items: list[QueryItem] = Field(default_factory=list)
    total: int = 0
    status: QueryStatus
    truncated: bool = False


class RelatedObjectItem(BaseModel):
    object_type_id: uuid.UUID
    primary_key: str
    title: str | None = None


class RelatedObjectsResult(BaseModel):
    relation_id: uuid.UUID
    target_type_id: uuid.UUID
    index_version_id: uuid.UUID | None = None
    items: list[RelatedObjectItem] = Field(default_factory=list)
    total: int = 0
    status: QueryStatus
    truncated: bool = False
