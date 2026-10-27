"""数据连接对前端的出入参；字段统一 snake_case。"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

ConnectionKind = Literal["mysql"]
SyncMode = Literal["cdc", "snapshot"]


class _Model(BaseModel):
    model_config = ConfigDict(extra="forbid")


class DataConnectionCreate(_Model):
    name: str = Field(min_length=1, max_length=200)
    kind: ConnectionKind = "mysql"
    # 不传表示先建草稿；空对象也按“还没填”处理，避免 Java 配置校验把草稿挡掉。
    config: dict[str, Any] | None = None
    # 密码只写不读；创建草稿时允许先不填。
    secret: dict[str, Any] | None = None


class DataConnectionUpdate(_Model):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    config: dict[str, Any] | None = None
    secret: dict[str, Any] | None = None


class DataConnectionRead(BaseModel):
    id: uuid.UUID
    conversation_id: uuid.UUID
    name: str
    kind: str
    config: dict[str, Any]
    config_version: int
    status: str
    last_test_status: str
    last_test_at: datetime | None
    last_test_error: str | None
    last_test_details: dict[str, Any] | None = None
    secret_configured: bool
    last_sync_status: str | None = None
    last_sync_at: datetime | None = None
    created_at: datetime
    updated_at: datetime


class DataConnectionPageRead(BaseModel):
    items: list[DataConnectionRead]
    total: int
    limit: int
    offset: int


class ConnectionTestRead(BaseModel):
    status: str
    latency_ms: int
    server_version: str | None = None
    error_code: str | None = None
    message: str | None = None
    tested_at: datetime | None = None
    details: dict[str, Any] | None = None


class TableSummaryRead(BaseModel):
    schema_name: str = Field(alias="schema")
    name: str
    type: str
    comment: str | None = None
    estimated_rows: int | None = None
    has_primary_key: bool


class TableListRead(BaseModel):
    tables: list[TableSummaryRead]
    warnings: list[str] = Field(default_factory=list)


class ColumnRead(BaseModel):
    name: str
    ordinal_position: int
    data_type: str
    column_type: str | None = None
    nullable: bool
    default: str | None = None
    is_primary_key: bool
    comment: str | None = None


class TableDetailRead(BaseModel):
    schema_name: str = Field(alias="schema")
    table: str
    type: str
    comment: str | None = None
    columns: list[ColumnRead]
    primary_key_columns: list[str]


class TableSelection(_Model):
    schema_name: str = Field(alias="schema", min_length=1)
    table: str = Field(min_length=1)
    sync_mode: SyncMode = "cdc"
    version_interval_seconds: int | None = Field(default=None, ge=60)


class SyncTasksSaveRequest(_Model):
    tables: list[TableSelection] = Field(min_length=1)


class SyncTaskRead(BaseModel):
    id: uuid.UUID
    connection_id: uuid.UUID
    dataset_id: uuid.UUID
    schema_name: str = Field(alias="schema")
    table: str
    sync_mode: str
    version_interval_seconds: int
    primary_key_columns: list[str]
    status: str
    created_at: datetime
    updated_at: datetime


class SyncTasksRead(BaseModel):
    tasks: list[SyncTaskRead]


class SyncRunTriggerRequest(_Model):
    sync_task_ids: list[uuid.UUID] = Field(min_length=1)
    operation_id: uuid.UUID | None = None


class SyncRunRead(BaseModel):
    id: uuid.UUID
    sync_task_id: uuid.UUID
    dataset_id: uuid.UUID
    operation_id: uuid.UUID | None = None
    run_mode: str
    status: str
    flink_job_id: str | None = None
    flink_job_state: str | None = None
    snapshot_completed_at: datetime | None = None
    last_checkpoint_at: datetime | None = None
    next_version_due_at: datetime | None = None
    savepoint_path: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    started_at: datetime | None = None
    finished_at: datetime | None = None
    created_at: datetime


class SyncRunsRead(BaseModel):
    operation_id: uuid.UUID | None = None
    runs: list[SyncRunRead]


class DatasetVersionRead(BaseModel):
    id: uuid.UUID
    version_number: int
    row_count: int
    row_count_estimated: bool
    column_count: int
    byte_size: int
    schema_value: Any = Field(default=None, alias="schema")
    committed_at: datetime | None = None
    published_at: datetime | None = None
    version_source: str
    source_run_id: uuid.UUID | None = None
    storage_path: str | None = None


class DatasetVersionsRead(BaseModel):
    dataset_id: uuid.UUID
    total: int
    limit: int
    offset: int
    versions: list[DatasetVersionRead]


class DatasetRead(BaseModel):
    id: uuid.UUID
    connection_id: uuid.UUID
    sync_task_id: uuid.UUID
    name: str
    current_version_id: uuid.UUID | None = None
    current_version: DatasetVersionRead | None = None
    versions: list[DatasetVersionRead] = Field(default_factory=list)
    version_count: int = 0
    source_schema: str | None = None
    source_table: str | None = None
    created_at: datetime
    updated_at: datetime


class DataConnectionStateRead(BaseModel):
    connection: DataConnectionRead
    tasks: list[SyncTaskRead]
    recent_runs: list[SyncRunRead]
    datasets: list[DatasetRead]
    missing_steps: list[str]
    last_event_id: uuid.UUID | None = None
    conversation_ready: bool = False
