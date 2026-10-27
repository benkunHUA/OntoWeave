"""绑定草稿、校验与建议模型。"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.schemas.ontology.mappings import MappingIssue, MappingPayload, MappingValidation
from app.schemas.ontology.types import DatasetKind, RelationKind, ValidationState


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

    binding_id: uuid.UUID | None = None
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
    validation: MappingValidation | None = None
    recent_run: dict[str, Any] | None = None
    latest_run: dict[str, Any] | None = None

    @model_validator(mode="before")
    @classmethod
    def keep_compatible_fields(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value
        data = dict(value)
        binding_id = data.get("binding_id", data.get("bindingId", data.get("id")))
        if binding_id is not None:
            data.setdefault("binding_id", binding_id)
            data.setdefault("id", binding_id)
        recent_run = data.get(
            "recent_run",
            data.get("recentRun", data.get("latest_run", data.get("latestRun"))),
        )
        if recent_run is not None:
            data.setdefault("recent_run", recent_run)
            data.setdefault("latest_run", recent_run)
        return data


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
    relation_name: str | None = None
    source_type_id: uuid.UUID | None = None
    target_type_id: uuid.UUID | None = None
    foreign_key_property_id: uuid.UUID | None = None
    warnings: list[MappingIssue] = Field(default_factory=list)
    latest_version: dict[str, Any] | None = None
    latest_run: dict[str, Any] | None = None
    source: dict[str, Any] | None = None
    target: dict[str, Any] | None = None
