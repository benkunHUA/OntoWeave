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


MappingPayload = ObjectMapping | RelationMapping


class BindingDraftRequest(BaseModel):
    """保存对象 / 多对多关系草稿；base_revision=0 表示首次创建。"""

    model_config = ConfigDict(extra="forbid")

    base_revision: int = Field(ge=0)
    dataset_kind: DatasetKind
    dataset_id: uuid.UUID
    mapping: MappingPayload


class SuggestBindingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    dataset_kind: DatasetKind | None = None
    dataset_id: uuid.UUID | None = None
    mapping: MappingPayload | None = None


class ValidateBindingRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    dataset_kind: DatasetKind | None = None
    dataset_id: uuid.UUID | None = None
    mapping: MappingPayload | None = None


class PreviewBindingRequest(ValidateBindingRequest):
    limit: int = Field(20, ge=1, le=100)


class BindingRead(BaseModel):
    """Java 绑定 DTO 的浏览器视图；不同资源允许携带不同字段。"""

    model_config = ConfigDict(extra="allow")

    id: uuid.UUID | None = None
    object_type_id: uuid.UUID | None = None
    relation_id: uuid.UUID | None = None
    status: str | None = None
    revision: int | None = None
    draft_dataset_kind: DatasetKind | None = None
    draft_dataset_id: uuid.UUID | None = None
    draft_mapping: dict[str, Any] | None = None
    active_dataset_kind: DatasetKind | None = None
    active_dataset_id: uuid.UUID | None = None
    active_dataset_version_id: uuid.UUID | None = None
    active_mapping_revision: int | None = None
    current_index_version_id: uuid.UUID | None = None
    validation_state: ValidationState | None = None
    validation_error: dict[str, Any] | None = None
    latest_run: dict[str, Any] | None = None


class MappingSuggestionReason(BaseModel):
    model_config = ConfigDict(extra="allow")

    attribute_id: uuid.UUID | None = None
    column: str
    reason: str
    score: float = 0


class MappingSuggestion(BaseModel):
    dataset_kind: DatasetKind
    dataset_id: uuid.UUID
    mapping: MappingPayload
    reasons: list[MappingSuggestionReason] = Field(default_factory=list)


class RelationOverviewRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    relation_id: uuid.UUID | None = None
    relation_kind: RelationKind | None = None
    source_type_id: uuid.UUID | None = None
    target_type_id: uuid.UUID | None = None
    foreign_key_property_id: uuid.UUID | None = None
    latest_run: dict[str, Any] | None = None


class ObjectRelationGroupRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    relation_id: uuid.UUID
    direction: str
    label: str | None = None
    direction_label: str | None = None
    source_type_id: uuid.UUID | None = None
    source_type_name: str | None = None
    target_type_id: uuid.UUID | None = None
    target_type_name: str | None = None
    total: int = 0
    items: list[OntologyObject] = Field(default_factory=list)


class ObjectRelationPage(BaseModel):
    items: list[OntologyObject] = Field(default_factory=list)
    total: int = 0
    page: int = 1
    page_size: int = 0


class OntologyResourceRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: uuid.UUID
    name: str
    status: str
    kind: str
    binding: dict[str, Any] | None = None
    cardinality: str | None = None
    relation_kind: RelationKind | None = None
    source_type_id: uuid.UUID | None = None
    source_type_name: str | None = None
    target_type_id: uuid.UUID | None = None
    target_type_name: str | None = None
    forward_label: str | None = None
    reverse_label: str | None = None
    direction_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None


class OntologyResourcesRead(BaseModel):
    object_types: list[OntologyResourceRead] = Field(default_factory=list)
    foreign_key_relations: list[OntologyResourceRead] = Field(default_factory=list)
    many_to_many_relations: list[OntologyResourceRead] = Field(default_factory=list)
