"""数据流程 BFF 的浏览器契约：snake_case，不与 Java DTO 共享模型。"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class _Request(BaseModel):
    model_config = ConfigDict(extra="forbid")


class PipelineCreate(_Request):
    name: str = Field(min_length=1, max_length=200)


class PipelineUpdate(_Request):
    name: str = Field(min_length=1, max_length=200)


class PipelineCopy(_Request):
    name: str | None = Field(default=None, min_length=1, max_length=200)


class DraftCommandRequest(_Request):
    type: str = Field(min_length=1, max_length=40)
    base_revision: int = Field(ge=0)
    operation_id: uuid.UUID
    turn_id: uuid.UUID | None = None
    node: dict[str, Any] | None = None
    edge: dict[str, Any] | None = None
    output: dict[str, Any] | None = None


class NodePosition(_Request):
    node_id: uuid.UUID
    x: float
    y: float


class DraftPositionsRequest(_Request):
    positions: list[NodePosition] = Field(min_length=1)


class UndoTurnRequest(_Request):
    revision: int = Field(ge=0)


class PreviewRequest(_Request):
    draft_revision: int = Field(ge=0)
    node_id: uuid.UUID
    request_id: uuid.UUID = Field(default_factory=uuid.uuid4)


class BuildCreateRequest(_Request):
    draft_revision: int = Field(ge=0)
    operation_id: uuid.UUID = Field(default_factory=uuid.uuid4)


class SchedulePutRequest(_Request):
    name: str = Field(min_length=1, max_length=200)
    cron_expression: str = Field(min_length=1, max_length=100)
    timezone: str = Field(min_length=1, max_length=100)


class DataPipelineRead(BaseModel):
    id: uuid.UUID
    conversation_id: uuid.UUID
    name: str
    status: str
    draft_revision: int | None = None
    current_version_id: uuid.UUID | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None


class PipelinePageRead(BaseModel):
    items: list[DataPipelineRead]
    total: int
    limit: int
    offset: int


class DraftRead(BaseModel):
    pipeline_id: uuid.UUID
    revision: int
    definition: dict[str, Any]


class PipelineIssueRead(BaseModel):
    code: str
    phase: str | None = None
    blocking: bool = False
    node_id: str | None = None
    port_id: str | None = None
    field: str | None = None
    message: str
    hint: str | None = None


class NextActionRead(BaseModel):
    action: str
    node_id: str | None = None


class PipelineCheckRead(BaseModel):
    pipeline_id: uuid.UUID
    revision: int
    stage: str
    blocking_issues: list[PipelineIssueRead]
    warnings: list[PipelineIssueRead]
    next_actions: list[NextActionRead]


class PreviewColumnRead(BaseModel):
    name: str
    type: str
    nullable: bool


class PreviewIssueRead(BaseModel):
    code: str | None = None
    phase: str | None = None
    level: str | None = None
    node_id: str | None = None
    port_id: str | None = None
    field: str | None = None
    row_number: int | None = None
    message: str | None = None
    hint: str | None = None
    original_value: str | None = None


class InputVersionRead(BaseModel):
    dataset_kind: str
    dataset_id: uuid.UUID
    dataset_version_id: uuid.UUID
    dataset_version_number: int
    schema_hash: str
    iceberg_catalog: str | None = None
    iceberg_namespace: str | None = None
    iceberg_table: str | None = None
    iceberg_snapshot_id: int | None = None
    display_snapshot: dict[str, Any] | None = None


class PreviewRead(BaseModel):
    preview_id: uuid.UUID
    pipeline_id: uuid.UUID
    draft_revision: int
    node_id: str
    columns: list[PreviewColumnRead]
    rows: list[dict[str, Any]]
    returned_rows: int
    scanned_rows: int | None = None
    truncated: bool
    truncation_reason: str | None = None
    elapsed_ms: int
    input_versions: list[InputVersionRead]
    errors: list[PreviewIssueRead]
    warnings: list[PreviewIssueRead]


class NodeCatalogRead(BaseModel):
    catalog_version: str
    nodes: list[dict[str, Any]]


class InputDatasetVersionRead(BaseModel):
    id: uuid.UUID
    version_number: int
    row_count: int
    column_count: int
    schema_value: Any = Field(default=None, alias="schema")
    published_at: datetime | None = None


class InputDatasetRead(BaseModel):
    id: uuid.UUID
    name: str
    connection_id: uuid.UUID
    sync_task_id: uuid.UUID
    current_version_id: uuid.UUID | None = None
    current_version: InputDatasetVersionRead | None = None
    source_schema: str | None = None
    source_table: str | None = None
    updated_at: datetime | None = None


class InputDatasetPageRead(BaseModel):
    items: list[InputDatasetRead]
    total: int
    limit: int
    offset: int


class DatasetSchemaRead(BaseModel):
    dataset_id: uuid.UUID
    version_id: uuid.UUID | None = None
    version_number: int | None = None
    schema_value: Any = Field(default=None, alias="schema")
    column_count: int = 0
