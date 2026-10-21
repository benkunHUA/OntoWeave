"""Java camelCase DTO → Python snake_case 的显式映射。

只映射协议里已知的字段，`config`、`payload`、数据集 schema 这些用户数据原样保留。
"""

from __future__ import annotations

from typing import Any

from app.services.data_service.pipeline_mapping import (
    build,
    build_output,
    check_result,
    dataset_schema,
    draft,
    input_dataset,
    input_dataset_page,
    input_dataset_version,
    input_version,
    next_action,
    node_catalog,
    output_sources,
    pipeline,
    pipeline_issue,
    pipeline_page,
    preview,
    preview_issue,
    schedule,
    schedule_run,
    source_build,
    source_input,
    source_output,
    source_pipeline_version,
)

__all__ = [
    "build",
    "build_output",
    "check_result",
    "connection",
    "connection_page",
    "connection_test",
    "dataset",
    "dataset_preview",
    "dataset_schema",
    "dataset_version",
    "draft",
    "input_dataset",
    "input_dataset_page",
    "input_dataset_version",
    "input_version",
    "next_action",
    "node_catalog",
    "output_sources",
    "pipeline",
    "pipeline_issue",
    "pipeline_page",
    "preview",
    "preview_issue",
    "schedule",
    "schedule_run",
    "source_build",
    "source_input",
    "source_output",
    "source_pipeline_version",
    "state",
    "state_summary",
    "sync_run",
    "sync_runs",
    "sync_task",
    "sync_tasks",
    "table_detail",
    "table_list",
    "table_summary",
]


def connection(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "conversation_id": row.get("conversationId"),
        "name": row.get("name"),
        "kind": row.get("kind"),
        "config": row.get("config") or {},
        "config_version": row.get("configVersion", 1),
        "status": row.get("status"),
        "last_test_status": row.get("lastTestStatus"),
        "last_test_at": row.get("lastTestAt"),
        "last_test_error": row.get("lastTestError"),
        "last_test_details": row.get("lastTestDetails"),
        "secret_configured": row.get("secretConfigured", False),
        "last_sync_status": row.get("lastSyncStatus"),
        "last_sync_at": row.get("lastSyncAt"),
        "created_at": row.get("createdAt"),
        "updated_at": row.get("updatedAt"),
    }


def connection_page(row: dict) -> dict:
    return {
        "items": [connection(item) for item in row.get("items") or []],
        "total": row.get("total", 0),
        "limit": row.get("limit", 0),
        "offset": row.get("offset", 0),
    }


def connection_test(row: dict) -> dict:
    return {
        "status": row.get("status"),
        "latency_ms": row.get("latencyMs", 0),
        "server_version": row.get("serverVersion"),
        "error_code": row.get("errorCode"),
        "message": row.get("message"),
        "tested_at": row.get("testedAt"),
        "details": row.get("details"),
    }


def table_summary(row: dict) -> dict:
    return {
        "schema": row.get("schema"),
        "name": row.get("name"),
        "type": row.get("type"),
        "comment": row.get("comment"),
        "estimated_rows": row.get("estimatedRows"),
        "has_primary_key": row.get("hasPrimaryKey", False),
    }


def table_list(row: dict) -> dict:
    return {
        "tables": [table_summary(item) for item in row.get("tables") or []],
        "warnings": row.get("warnings") or [],
    }


def table_detail(row: dict) -> dict:
    return {
        "schema": row.get("schema"),
        "table": row.get("table"),
        "type": row.get("type"),
        "comment": row.get("comment"),
        "columns": [
            {
                "name": item.get("name"),
                "ordinal_position": item.get("ordinalPosition"),
                "data_type": item.get("dataType"),
                "column_type": item.get("columnType"),
                "nullable": item.get("nullable", True),
                "default": item.get("defaultValue"),
                "is_primary_key": item.get("primaryKey", False),
                "comment": item.get("comment"),
            }
            for item in row.get("columns") or []
        ],
        "primary_key_columns": row.get("primaryKeyColumns") or [],
    }


def sync_task(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "connection_id": row.get("connectionId"),
        "dataset_id": row.get("datasetId"),
        "schema": row.get("schema"),
        "table": row.get("table"),
        "sync_mode": row.get("syncMode"),
        "version_interval_seconds": row.get("versionIntervalSeconds"),
        "primary_key_columns": row.get("primaryKeyColumns") or [],
        "status": row.get("status"),
        "created_at": row.get("createdAt"),
        "updated_at": row.get("updatedAt"),
    }


def sync_tasks(row: dict) -> dict:
    return {"tasks": [sync_task(item) for item in row.get("tasks") or []]}


def sync_run(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "sync_task_id": row.get("syncTaskId"),
        "dataset_id": row.get("datasetId"),
        "operation_id": row.get("operationId"),
        "run_mode": row.get("runMode"),
        "status": row.get("status"),
        "flink_job_id": row.get("flinkJobId"),
        "flink_job_state": row.get("flinkJobState"),
        "snapshot_completed_at": row.get("snapshotCompletedAt"),
        "last_checkpoint_at": row.get("lastCheckpointAt"),
        "next_version_due_at": row.get("nextVersionDueAt"),
        "savepoint_path": row.get("savepointPath"),
        "error_code": row.get("errorCode"),
        "error_message": row.get("errorMessage"),
        "started_at": row.get("startedAt"),
        "finished_at": row.get("finishedAt"),
        "created_at": row.get("createdAt"),
    }


def sync_runs(row: dict) -> dict:
    return {
        "operation_id": row.get("operationId"),
        "runs": [sync_run(item) for item in row.get("runs") or []],
    }


def dataset_version(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "version_number": row.get("versionNumber"),
        "row_count": row.get("rowCount", 0),
        "row_count_estimated": row.get("rowCountEstimated", False),
        "column_count": row.get("columnCount", 0),
        "byte_size": row.get("byteSize", 0),
        "schema": row.get("schema"),
        "committed_at": row.get("committedAt"),
        "published_at": row.get("publishedAt"),
        "version_source": row.get("versionSource"),
        "source_run_id": row.get("sourceRunId"),
        "storage_path": row.get("storagePath"),
    }


def dataset(row: dict) -> dict:
    current = row.get("currentVersion")
    return {
        "id": row.get("id"),
        "connection_id": row.get("connectionId"),
        "sync_task_id": row.get("syncTaskId"),
        "name": row.get("name"),
        "current_version_id": row.get("currentVersionId"),
        "current_version": dataset_version(current) if isinstance(current, dict) else None,
        "versions": [dataset_version(item) for item in row.get("versions") or []],
        "version_count": row.get("versionCount", len(row.get("versions") or [])),
        "source_schema": row.get("sourceSchema"),
        "source_table": row.get("sourceTable"),
        "created_at": row.get("createdAt"),
        "updated_at": row.get("updatedAt"),
    }


def dataset_preview(row: dict) -> dict:
    return {
        "dataset_id": row.get("datasetId"),
        "version_id": row.get("versionId"),
        "version_number": row.get("versionNumber"),
        "snapshot_id": row.get("snapshotId"),
        "live": row.get("live", False),
        "columns": row.get("columns") or [],
        "row_count": row.get("rowCount", 0),
        "rows": row.get("rows") or [],
        "truncated": row.get("truncated", False),
    }


def state(row: dict) -> dict:
    return {
        "connection": connection(row.get("connection") or {}),
        "tasks": [sync_task(item) for item in row.get("tasks") or []],
        "recent_runs": [sync_run(item) for item in row.get("recentRuns") or []],
        "datasets": [dataset(item) for item in row.get("datasets") or []],
        "missing_steps": row.get("missingSteps") or [],
        "last_event_id": row.get("lastEventId"),
    }


def state_summary(row: dict | None) -> dict:
    """给提示词用的精简摘要：不带 Iceberg 物理信息和完整版本列表。"""
    if row is None:
        return {"unavailable": True}
    mapped = state(row)
    connection_row: dict[str, Any] = mapped["connection"]
    return {
        "connection": {
            "id": connection_row.get("id"),
            "name": connection_row.get("name"),
            "kind": connection_row.get("kind"),
            "status": connection_row.get("status"),
            "config": connection_row.get("config"),
            "last_test_status": connection_row.get("last_test_status"),
            "last_test_error": connection_row.get("last_test_error"),
            "secret_configured": connection_row.get("secret_configured"),
        },
        "missing_steps": mapped["missing_steps"],
        "tasks": [
            {
                "id": item["id"],
                "schema": item["schema"],
                "table": item["table"],
                "sync_mode": item["sync_mode"],
                "status": item["status"],
            }
            for item in mapped["tasks"]
        ],
        "recent_runs": [
            {
                "id": item["id"],
                "sync_task_id": item["sync_task_id"],
                "status": item["status"],
                "flink_job_id": item["flink_job_id"],
                "flink_job_state": item["flink_job_state"],
                "error_code": item["error_code"],
                "error_message": item["error_message"],
                "finished_at": item["finished_at"],
                "created_at": item["created_at"],
            }
            for item in mapped["recent_runs"][:5]
        ],
        "datasets": [
            {
                "id": item["id"],
                "name": item["name"],
                "sync_task_id": item["sync_task_id"],
                "current_version": item.get("current_version"),
            }
            for item in mapped["datasets"]
        ],
    }
