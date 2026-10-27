"""字段映射、预览与索引运行模型。"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.schemas.ontology.types import DatasetKind, RunPhase, RunStatus, SubjectKind, TriggerType
from app.vocabulary import PropertyDatatype


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
    required_attribute_ids: list[uuid.UUID] = Field(default_factory=list)


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

    @model_validator(mode="before")
    @classmethod
    def derive_valid(cls, value: Any) -> Any:
        if isinstance(value, dict) and "valid" not in value:
            return {**value, "valid": not bool(value.get("blocking"))}
        return value


class PreviewSkip(BaseModel):
    code: str
    message: str
    reference: str | None = None


class ObjectPreviewRow(BaseModel):
    primary_key: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)


class ObjectPreview(BaseModel):
    rows: list[ObjectPreviewRow] = Field(default_factory=list)
    validation: MappingValidation
    skipped: list[PreviewSkip] = Field(default_factory=list)
    skipped_count: int = 0
    skipped_reasons: dict[str, int] = Field(default_factory=dict)


class RelationPreviewPair(BaseModel):
    from_primary_key: str
    to_primary_key: str


class RelationPreview(BaseModel):
    pairs: list[RelationPreviewPair] = Field(default_factory=list)
    skipped_count: int = 0
    skipped_reasons: dict[str, int] = Field(default_factory=dict)
    validation: MappingValidation
    skipped: list[PreviewSkip] = Field(default_factory=list)


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


MappingPayload = ObjectMapping | RelationMapping
