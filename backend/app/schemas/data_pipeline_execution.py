"""构建、输出来源与调度的独立浏览器响应模型。"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pydantic import BaseModel


class BuildOutputRead(BaseModel):
    id: uuid.UUID
    output_id: uuid.UUID
    output_type: str
    status: str
    stage: str | None = None
    engine_job_id: str | None = None
    error_code: str | None = None
    error_message: str | None = None
    result: dict[str, Any] | None = None
    stats: dict[str, Any] | None = None
    started_at: datetime | None = None
    finished_at: datetime | None = None


class BuildRead(BaseModel):
    id: uuid.UUID
    pipeline_id: uuid.UUID
    pipeline_version_id: uuid.UUID | None = None
    status: str
    stage: str | None = None
    trigger: str
    error_code: str | None = None
    error_message: str | None = None
    started_at: datetime | None = None
    finished_at: datetime | None = None
    outputs: list[BuildOutputRead]


class SourceOutputRead(BaseModel):
    output_id: uuid.UUID
    name: str
    version_number: int
    row_count: int
    published_at: datetime | None = None


class SourceBuildRead(BaseModel):
    build_id: uuid.UUID
    status: str
    trigger: str
    started_at: datetime | None = None
    finished_at: datetime | None = None


class SourcePipelineVersionRead(BaseModel):
    version_id: uuid.UUID
    version_number: int
    created_at: datetime | None = None


class SourceInputRead(BaseModel):
    dataset_kind: str
    dataset_id: uuid.UUID
    dataset_version_id: uuid.UUID | None = None
    dataset_version_number: int | None = None
    schema_hash: str | None = None
    dataset_name: str | None = None
    connection_name: str | None = None
    source_schema_name: str | None = None
    source_table_name: str | None = None
    metadata_status: str | None = None


class SourceRead(BaseModel):
    pipeline_id: uuid.UUID
    dataset_id: uuid.UUID
    version_id: uuid.UUID | None = None
    output: SourceOutputRead
    build: SourceBuildRead | None = None
    pipeline_version: SourcePipelineVersionRead | None = None
    inputs: list[SourceInputRead]


class ScheduleRead(BaseModel):
    id: uuid.UUID
    pipeline_id: uuid.UUID
    name: str
    status: str
    trigger_type: str
    cron_expression: str
    timezone: str
    next_run_at: datetime | None = None
    last_run_at: datetime | None = None
    paused_at: datetime | None = None


class ScheduleRunRead(BaseModel):
    id: uuid.UUID
    schedule_id: uuid.UUID
    pipeline_id: uuid.UUID
    status: str
    trigger_type: str | None = None
    scheduled_for: datetime
    build_id: uuid.UUID | None = None
    logic_hash: str | None = None
    reason_code: str | None = None
    message: str | None = None
