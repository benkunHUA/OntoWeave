from __future__ import annotations

import uuid
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.vocabulary import PropertyDatatype


class ObjectTypeCreate(BaseModel):
    name: str
    description: str | None = None


class ObjectTypeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    description: str | None
    status: str


class DatasetKind(StrEnum):
    SYNC = "SYNC"
    PIPELINE = "PIPELINE"


class SubjectKind(StrEnum):
    OBJECT_TYPE = "OBJECT_TYPE"
    RELATION = "RELATION"


class RelationKind(StrEnum):
    FK = "FK"
    MANY_TO_MANY = "MANY_TO_MANY"


class RunStatus(StrEnum):
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"
    SUPERSEDED = "SUPERSEDED"


class RunPhase(StrEnum):
    READING = "READING"
    VALIDATING = "VALIDATING"
    WRITING = "WRITING"
    SWITCHING = "SWITCHING"


class TriggerType(StrEnum):
    MANUAL = "MANUAL"
    DATASET_PUBLISHED = "DATASET_PUBLISHED"
    RETRY = "RETRY"


class ValidationState(StrEnum):
    OK = "OK"
    NEEDS_REPAIR = "NEEDS_REPAIR"


class FilterOp(StrEnum):
    EQ = "eq"
    NE = "ne"
    CONTAINS = "contains"
    GT = "gt"
    GTE = "gte"
    LT = "lt"
    LTE = "lte"
    IS_NULL = "is_null"
    IS_NOT_NULL = "is_not_null"


UNARY_FILTER_OPS = frozenset({FilterOp.IS_NULL, FilterOp.IS_NOT_NULL})
ORDERED_FILTER_OPS = frozenset({FilterOp.GT, FilterOp.GTE, FilterOp.LT, FilterOp.LTE})


class DatasetColumn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str
    type: str
    optional: bool
    example: Any | None = None


class OntologyDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: DatasetKind
    id: uuid.UUID
    name: str
    version_id: uuid.UUID
    version_number: int
    row_count: int
    columns: list[DatasetColumn] = Field(default_factory=list)


class OntologyDatasetPage(BaseModel):
    items: list[OntologyDataset]
    total: int
    page: int
    size: int


class AttributeMapping(BaseModel):
    model_config = ConfigDict(extra="forbid")

    attribute_id: uuid.UUID
    column: str
    datatype: PropertyDatatype
    required: bool
    is_array: bool = False
    enum_values: list[str] | None = None


class ObjectMapping(BaseModel):
    model_config = ConfigDict(extra="forbid")

    primary_key_attribute_id: uuid.UUID
    title_key_attribute_id: uuid.UUID | None = None
    attributes: list[AttributeMapping] = Field(default_factory=list)


class RelationMapping(BaseModel):
    model_config = ConfigDict(extra="forbid")

    from_key_column: str
    from_key_datatype: PropertyDatatype
    to_key_column: str
    to_key_datatype: PropertyDatatype


class MappingIssue(BaseModel):
    code: str
    message: str
    attribute_id: uuid.UUID | None = None
    column: str | None = None


class MappingValidation(BaseModel):
    blocking: list[MappingIssue] = Field(default_factory=list)
    warnings: list[MappingIssue] = Field(default_factory=list)
    valid: bool


class ObjectPreviewRow(BaseModel):
    primary_key: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)


class ObjectPreview(BaseModel):
    rows: list[ObjectPreviewRow] = Field(default_factory=list)
    validation: MappingValidation


class RelationPreviewPair(BaseModel):
    from_primary_key: str
    to_primary_key: str


class RelationPreview(BaseModel):
    pairs: list[RelationPreviewPair] = Field(default_factory=list)
    skipped_count: int = 0
    skipped_reasons: dict[str, int] = Field(default_factory=dict)
    validation: MappingValidation


class MappingUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    base_revision: int = Field(ge=1)
    mapping: ObjectMapping | RelationMapping


class GenerateMappingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    mapping_revision: int = Field(ge=1)


class GenerateMappingResponse(BaseModel):
    run_id: uuid.UUID


class IndexRun(BaseModel):
    id: uuid.UUID
    owner_id: uuid.UUID
    subject_kind: SubjectKind
    subject_id: uuid.UUID
    binding_id: uuid.UUID | None = None
    trigger_type: TriggerType
    status: RunStatus
    phase: RunPhase | None = None
    target_dataset_kind: DatasetKind
    target_dataset_version_id: uuid.UUID
    mapping_revision: int
    requested_at: str
    started_at: str | None = None
    heartbeat_at: str | None = None
    finished_at: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    stats: dict[str, Any] = Field(default_factory=dict)


class OntologyObject(BaseModel):
    primary_key: str
    title: str | None = None
    index_version_id: uuid.UUID


class OntologyObjectPage(BaseModel):
    items: list[OntologyObject] = Field(default_factory=list)
    total: int
    page: int
    page_size: int
    index_version_id: uuid.UUID | None = None


class OntologyObjectDetail(BaseModel):
    primary_key: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)
    index_version_id: uuid.UUID


class RelationInstance(BaseModel):
    from_object_type_id: uuid.UUID
    from_primary_key: str
    to_object_type_id: uuid.UUID
    to_primary_key: str
    index_version_id: uuid.UUID | None = None


class RelationInstancePage(BaseModel):
    items: list[RelationInstance] = Field(default_factory=list)
    total: int
    page: int
    page_size: int


class FilterCondition(BaseModel):
    model_config = ConfigDict(extra="forbid")

    attribute_id: uuid.UUID
    op: FilterOp
    value: Any | None = None

    @model_validator(mode="after")
    def validate_operator_shape(self) -> FilterCondition:
        has_value = "value" in self.model_fields_set
        if self.op in UNARY_FILTER_OPS:
            if has_value:
                raise ValueError(f"{self.op.value} does not accept a value")
            return self
        if not has_value or self.value is None:
            raise ValueError(f"{self.op.value} requires a value")
        if self.op is FilterOp.CONTAINS and not isinstance(self.value, str):
            raise ValueError("contains requires a string value")
        if self.op in ORDERED_FILTER_OPS and isinstance(self.value, bool):
            raise ValueError(f"{self.op.value} does not accept a boolean value")
        return self
