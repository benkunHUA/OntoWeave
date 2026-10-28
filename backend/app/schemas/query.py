"""查询子 Agent 的输入、工具结果与最终结构化输出。"""

from __future__ import annotations

import uuid
from enum import StrEnum
from typing import Any, Literal

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, field_validator, model_validator


class QueryStatus(StrEnum):
    OK = "OK"
    EMPTY = "EMPTY"
    TRUNCATED = "TRUNCATED"
    NOT_INDEXED = "NOT_INDEXED"
    FAILED = "FAILED"


class QueryAgentStatus(StrEnum):
    OK = "OK"
    EMPTY = "EMPTY"
    NOT_INDEXED = "NOT_INDEXED"
    NEEDS_USER = "NEEDS_USER"
    BLOCKED = "BLOCKED"


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

_EXPECTED_CARDINALITY_ALIASES = {
    "one": "one",
    "many": "many",
    "one_to_one": "one",
    "one_to_many": "many",
    "many_to_one": "one",
    "many_to_many": "many",
}


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
    primary_key: str | None = None
    q: str | None = None
    filters: list[QueryFilter] = Field(default_factory=list, max_length=20)
    sort: list[QuerySort] = Field(default_factory=list, max_length=3)
    limit: int = Field(default=20, ge=1, le=50)
    project: list[str] = Field(default_factory=list, max_length=50)

    @model_validator(mode="after")
    def primary_key_is_exclusive(self) -> QueryObjectsInput:
        if self.primary_key is not None and (self.q or self.filters or self.sort):
            raise ValueError("primary_key cannot be combined with q, filters or sort")
        return self


class QueryStepInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    input_step_id: str = Field(min_length=1)
    relation_id: str
    direction: QueryDirection
    filters: list[QueryFilter] = Field(default_factory=list, max_length=20)
    sort: list[QuerySort] = Field(default_factory=list, max_length=3)
    limit: int = Field(default=20, ge=1, le=50)
    project: list[str] = Field(default_factory=list, max_length=50)
    expected_cardinality: Literal["one", "many"] | None = None

    @field_validator("expected_cardinality", mode="before")
    @classmethod
    def normalize_expected_cardinality(cls, value: object) -> object:
        if value is None:
            return None
        return _EXPECTED_CARDINALITY_ALIASES.get(str(value).strip().lower(), value)


class QueryItem(BaseModel):
    object_type_id: uuid.UUID | None = None
    primary_key: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)


class QueryObjectsResult(BaseModel):
    step_id: str
    object_type_id: uuid.UUID
    index_version_id: uuid.UUID | None = None
    items: list[QueryItem] = Field(default_factory=list)
    total: int = 0
    status: QueryStatus
    truncated: bool = False


class QueryStepResult(BaseModel):
    step_id: str
    target_type_id: uuid.UUID
    index_version_id: uuid.UUID | None = None
    items: list[QueryItem] = Field(default_factory=list)
    total: int = 0
    status: QueryStatus
    truncated: bool = False


class QueryEvidence(BaseModel):
    # 模型只负责给答案；最终证据由 runner 从工具状态注入。这里放宽必填字段，
    # 避免模型手抄证据时漏字段导致整份结构化结果作废。
    step_id: str = ""
    object_type_id: uuid.UUID | None = None
    object_type_name: str | None = None
    primary_key: str = ""
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)
    property_names: dict[str, str] = Field(default_factory=dict)
    index_version_id: uuid.UUID | None = None


class QueryAgentResult(BaseModel):
    status: QueryAgentStatus
    answer: str | None = None
    evidence: list[QueryEvidence] = Field(default_factory=list)
    question: str | None = None
    options: list[str] = Field(default_factory=list)
    limitations: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def required_fields_by_status(self) -> QueryAgentResult:
        if self.status is QueryAgentStatus.NEEDS_USER and not (self.question or "").strip():
            raise ValueError("NEEDS_USER requires question")
        if self.status is QueryAgentStatus.OK and not (self.answer or "").strip():
            raise ValueError("OK requires answer")
        if self.status is QueryAgentStatus.BLOCKED and not self.limitations:
            raise ValueError("BLOCKED requires limitations")
        return self
