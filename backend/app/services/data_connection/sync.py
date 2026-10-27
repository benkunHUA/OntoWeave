"""同步任务与同步运行的编排。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.clients.data_service import mapping
from app.clients.data_service.client import DataServiceClient
from app.errors import InvalidRequestError
from app.schemas.data_connection import (
    SyncRunTriggerRequest,
    SyncTasksSaveRequest,
    TableSelection,
)
from app.services.data_connection.connections import get_state


def save_sync_tasks(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
    payload: SyncTasksSaveRequest,
    operation_id: uuid.UUID | None = None,
) -> dict:
    tables = [_table_selection(item) for item in payload.tables]
    return mapping.sync_tasks(
        client.create_sync_tasks(
            connection_id, owner_id=owner_id, tables=tables, operation_id=operation_id
        )
    )


def list_sync_tasks(
    client: DataServiceClient, *, owner_id: uuid.UUID, connection_id: uuid.UUID
) -> dict:
    return mapping.sync_tasks(client.list_sync_tasks(connection_id, owner_id=owner_id))


def trigger_sync(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
    payload: SyncRunTriggerRequest,
) -> dict:
    tasks = mapping.sync_tasks(client.list_sync_tasks(connection_id, owner_id=owner_id))["tasks"]
    by_id = {item["id"]: item for item in tasks}
    missing = [str(task_id) for task_id in payload.sync_task_ids if str(task_id) not in by_id]
    if missing:
        raise InvalidRequestError(f"同步任务不存在：{', '.join(missing)}")
    tables = [
        {"schema": by_id[str(task_id)]["schema"], "table": by_id[str(task_id)]["table"]}
        for task_id in payload.sync_task_ids
    ]
    operation_id = payload.operation_id or uuid.uuid4()
    return mapping.sync_runs(
        client.trigger_sync(
            connection_id,
            owner_id=owner_id,
            operation_id=operation_id,
            tables=tables,
        )
    )


def get_sync_run(client: DataServiceClient, *, owner_id: uuid.UUID, run_id: uuid.UUID) -> dict:
    return mapping.sync_run(client.get_sync_run(run_id, owner_id=owner_id))


def stop_sync_run(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    run_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict:
    return mapping.sync_run(
        client.stop_sync_run(run_id, owner_id=owner_id, operation_id=operation_id)
    )


def recent_runs(
    session: Session,
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
    limit: int,
) -> dict:
    state = get_state(session, client, owner_id=owner_id, connection_id=connection_id)
    return {"runs": state["recent_runs"][:limit]}


def _table_selection(item: TableSelection) -> dict:
    body: dict[str, Any] = {
        "schema": item.schema_name,
        "table": item.table,
        "syncMode": item.sync_mode,
    }
    if item.version_interval_seconds is not None:
        body["versionIntervalSeconds"] = item.version_interval_seconds
    return body
