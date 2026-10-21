"""数据流程 Java DTO 到浏览器 snake_case 的映射。"""

from __future__ import annotations


def pipeline(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "conversation_id": row.get("conversationId"),
        "name": row.get("name"),
        "status": row.get("status"),
        "draft_revision": row.get("draftRevision"),
        "current_version_id": row.get("currentVersionId"),
        "created_at": row.get("createdAt"),
        "updated_at": row.get("updatedAt"),
    }


def pipeline_page(row: dict) -> dict:
    return {
        "items": [pipeline(item) for item in row.get("items") or []],
        "total": row.get("total", 0),
        "limit": row.get("limit", 0),
        "offset": row.get("offset", 0),
    }


def draft(row: dict) -> dict:
    return {
        "pipeline_id": row.get("pipelineId"),
        "revision": row.get("revision", 0),
        "definition": row.get("definition") or {},
    }


def pipeline_issue(row: dict) -> dict:
    return {
        "code": row.get("code"),
        "phase": row.get("phase"),
        "blocking": row.get("blocking", False),
        "node_id": row.get("nodeId"),
        "port_id": row.get("portId"),
        "field": row.get("field"),
        "message": row.get("message"),
        "hint": row.get("hint"),
    }


def next_action(row: dict) -> dict:
    return {"action": row.get("action"), "node_id": row.get("nodeId")}


def check_result(row: dict) -> dict:
    return {
        "pipeline_id": row.get("pipelineId"),
        "revision": row.get("revision", 0),
        "stage": row.get("stage"),
        "blocking_issues": [pipeline_issue(item) for item in row.get("blockingIssues") or []],
        "warnings": [pipeline_issue(item) for item in row.get("warnings") or []],
        "next_actions": [next_action(item) for item in row.get("nextActions") or []],
    }


def preview_issue(row: dict) -> dict:
    return {
        "code": row.get("code"),
        "phase": row.get("phase"),
        "level": row.get("level"),
        "node_id": row.get("nodeId"),
        "port_id": row.get("portId"),
        "field": row.get("field"),
        "row_number": row.get("rowNumber"),
        "message": row.get("message"),
        "hint": row.get("hint"),
        "original_value": row.get("originalValue"),
    }


def input_version(row: dict) -> dict:
    return {
        "dataset_kind": row.get("datasetKind"),
        "dataset_id": row.get("datasetId"),
        "dataset_version_id": row.get("datasetVersionId"),
        "dataset_version_number": row.get("datasetVersionNumber"),
        "schema_hash": row.get("schemaHash"),
        "iceberg_catalog": row.get("icebergCatalog"),
        "iceberg_namespace": row.get("icebergNamespace"),
        "iceberg_table": row.get("icebergTable"),
        "iceberg_snapshot_id": row.get("icebergSnapshotId"),
        "display_snapshot": row.get("displaySnapshot"),
    }


def preview(row: dict) -> dict:
    return {
        "preview_id": row.get("previewId"),
        "pipeline_id": row.get("pipelineId"),
        "draft_revision": row.get("draftRevision", 0),
        "node_id": row.get("nodeId"),
        "columns": row.get("columns") or [],
        "rows": row.get("rows") or [],
        "returned_rows": row.get("returnedRows", 0),
        "scanned_rows": row.get("scannedRows"),
        "truncated": row.get("truncated", False),
        "truncation_reason": row.get("truncationReason"),
        "elapsed_ms": row.get("elapsedMs", 0),
        "input_versions": [input_version(item) for item in row.get("inputVersions") or []],
        "errors": [preview_issue(item) for item in row.get("errors") or []],
        "warnings": [preview_issue(item) for item in row.get("warnings") or []],
    }


def node_catalog(row: dict) -> dict:
    return {
        "catalog_version": row.get("catalogVersion"),
        "nodes": [
            {
                "type": item.get("type"),
                "version": item.get("version"),
                "label": item.get("label"),
                "category": item.get("category"),
                "ports": [
                    {
                        "id": port.get("id"),
                        "role": port.get("role"),
                        "data_type": port.get("dataType"),
                        "required": port.get("required", False),
                        "multiple": port.get("multiple", False),
                    }
                    for port in item.get("ports") or []
                ],
                "config_schema": item.get("configSchema") or {},
                "ui_schema": item.get("uiSchema") or {},
            }
            for item in row.get("nodes") or []
        ],
    }


def input_dataset_version(row: dict | None) -> dict | None:
    if not isinstance(row, dict):
        return None
    return {
        "id": row.get("id"),
        "version_number": row.get("versionNumber"),
        "row_count": row.get("rowCount", 0),
        "column_count": row.get("columnCount", 0),
        "schema": row.get("schema"),
        "published_at": row.get("publishedAt"),
    }


def input_dataset(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "name": row.get("name"),
        "connection_id": row.get("connectionId"),
        "sync_task_id": row.get("syncTaskId"),
        "current_version_id": row.get("currentVersionId"),
        "current_version": input_dataset_version(row.get("currentVersion")),
        "source_schema": row.get("sourceSchema"),
        "source_table": row.get("sourceTable"),
        "updated_at": row.get("updatedAt"),
    }


def input_dataset_page(row: dict) -> dict:
    return {
        "items": [input_dataset(item) for item in row.get("items") or []],
        "total": row.get("total", 0),
        "limit": row.get("limit", 0),
        "offset": row.get("offset", 0),
    }


def dataset_schema(row: dict) -> dict:
    current = row.get("currentVersion")
    version = current if isinstance(current, dict) else {}
    return {
        "dataset_id": row.get("id"),
        "version_id": version.get("id") or row.get("currentVersionId"),
        "version_number": version.get("versionNumber"),
        "schema": version.get("schema"),
        "column_count": version.get("columnCount", 0),
    }


def build_output(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "output_id": row.get("outputId"),
        "output_type": row.get("outputType"),
        "status": row.get("status"),
        "stage": row.get("stage"),
        "engine_job_id": row.get("engineJobId"),
        "error_code": row.get("errorCode"),
        "error_message": row.get("errorMessage"),
        "result": row.get("result"),
        "stats": row.get("stats"),
        "started_at": row.get("startedAt"),
        "finished_at": row.get("finishedAt"),
    }


def build(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "pipeline_id": row.get("pipelineId"),
        "pipeline_version_id": row.get("pipelineVersionId"),
        "status": row.get("status"),
        "stage": row.get("stage"),
        "trigger": row.get("trigger"),
        "error_code": row.get("errorCode"),
        "error_message": row.get("errorMessage"),
        "started_at": row.get("startedAt"),
        "finished_at": row.get("finishedAt"),
        "outputs": [build_output(item) for item in row.get("outputs") or []],
    }


def source_output(row: dict) -> dict:
    return {
        "output_id": row.get("outputId"),
        "name": row.get("name"),
        "version_number": row.get("versionNumber"),
        "row_count": row.get("rowCount", 0),
        "published_at": row.get("publishedAt"),
    }


def source_build(row: dict | None) -> dict | None:
    if not isinstance(row, dict):
        return None
    return {
        "build_id": row.get("buildId"),
        "status": row.get("status"),
        "trigger": row.get("trigger"),
        "started_at": row.get("startedAt"),
        "finished_at": row.get("finishedAt"),
    }


def source_pipeline_version(row: dict | None) -> dict | None:
    if not isinstance(row, dict):
        return None
    return {
        "version_id": row.get("versionId"),
        "version_number": row.get("versionNumber"),
        "created_at": row.get("createdAt"),
    }


def source_input(row: dict) -> dict:
    return {
        "dataset_kind": row.get("datasetKind"),
        "dataset_id": row.get("datasetId"),
        "dataset_version_id": row.get("datasetVersionId"),
        "dataset_version_number": row.get("datasetVersionNumber"),
        "schema_hash": row.get("schemaHash"),
        "dataset_name": row.get("datasetName"),
        "connection_name": row.get("connectionName"),
        "source_schema_name": row.get("sourceSchemaName"),
        "source_table_name": row.get("sourceTableName"),
        "metadata_status": row.get("metadataStatus"),
    }


def output_sources(row: dict) -> dict:
    return {
        "pipeline_id": row.get("pipelineId"),
        "dataset_id": row.get("datasetId"),
        "version_id": row.get("versionId"),
        "output": source_output(row.get("output") or {}),
        "build": source_build(row.get("build")),
        "pipeline_version": source_pipeline_version(row.get("pipelineVersion")),
        "inputs": [source_input(item) for item in row.get("inputs") or []],
    }


def schedule(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "pipeline_id": row.get("pipelineId"),
        "name": row.get("name"),
        "status": row.get("status"),
        "trigger_type": row.get("triggerType"),
        "cron_expression": row.get("cronExpression"),
        "timezone": row.get("timezone"),
        "next_run_at": row.get("nextRunAt"),
        "last_run_at": row.get("lastRunAt"),
        "paused_at": row.get("pausedAt"),
    }


def schedule_run(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "schedule_id": row.get("scheduleId"),
        "pipeline_id": row.get("pipelineId"),
        "status": row.get("status"),
        "trigger_type": row.get("triggerType"),
        "scheduled_for": row.get("scheduledFor"),
        "build_id": row.get("buildId"),
        "logic_hash": row.get("logicHash"),
        "reason_code": row.get("reasonCode"),
        "message": row.get("message"),
    }
