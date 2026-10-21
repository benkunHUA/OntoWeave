"""数据流程 Java DTO 到浏览器 snake_case 的映射。"""

from __future__ import annotations

from app.services.data_service.pipeline_execution_mapping import (
    build,
    build_output,
    input_version,
    output_sources,
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
    "input_version",
    "output_sources",
    "preview",
    "preview_issue",
    "schedule",
    "schedule_run",
    "source_build",
    "source_input",
    "source_output",
    "source_pipeline_version",
]


def pipeline(row: dict) -> dict:
    return {
        "id": row.get("id"),
        "conversation_id": row.get("conversationId"),
        "name": row.get("name"),
        "status": row.get("status"),
        "draft_revision": row.get("draftRevision"),
        "current_version_id": row.get("currentVersionId"),
        "current_version_number": row.get("currentVersionNumber"),
        "current_version_draft_revision": row.get("currentVersionDraftRevision"),
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
