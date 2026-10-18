"""数据连接的编排层：复用 Python 会话，业务动作转发 Java 数据服务。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvalidRequestError
from app.schemas.data_connection import (
    DataConnectionCreate,
    DataConnectionUpdate,
    SyncRunTriggerRequest,
    SyncTasksSaveRequest,
    TableSelection,
)
from app.services import conversation, conversation_event
from app.services.data_service import mapping
from app.services.data_service.client import DataServiceClient
from app.vocabulary import ConversationKind


def create_connection(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    payload: DataConnectionCreate,
) -> dict:
    # Java 自己生成连接 id，所以先建会话拿到 conversation_id，再用返回值回填 resource_id。
    conv = conversation.create_conversation(
        session,
        owner_id=owner_id,
        title=payload.name,
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=uuid.uuid4(),
    )
    body: dict[str, Any] = {
        "conversationId": str(conv.id),
        "name": payload.name,
        "kind": payload.kind,
    }
    # Java 的 JsonNode 会把显式 null 解析成 NullNode，只有整个字段缺失才是“未填”。
    if payload.config:
        body["config"] = payload.config
    if payload.secret is not None:
        body["secret"] = payload.secret
    created = client.create_connection(owner_id=owner_id, payload=body)
    conv.resource_id = uuid.UUID(str(created["id"]))
    session.flush()
    return mapping.connection(created)


def get_connection(
    client: DataServiceClient, *, owner_id: uuid.UUID, connection_id: uuid.UUID
) -> dict:
    return mapping.connection(client.get_connection(connection_id, owner_id=owner_id))


def list_connections(
    client: DataServiceClient, *, owner_id: uuid.UUID, limit: int, offset: int
) -> dict:
    return mapping.connection_page(
        client.list_connections(owner_id=owner_id, limit=limit, offset=offset)
    )


def update_connection(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    connection_id: uuid.UUID,
    payload: DataConnectionUpdate,
    operation_id: uuid.UUID | None = None,
) -> dict:
    body: dict[str, Any] = {}
    if payload.name is not None:
        body["name"] = payload.name
    if payload.config is not None:
        body["config"] = payload.config
    if payload.secret is not None:
        body["secret"] = payload.secret
    updated = mapping.connection(
        client.update_connection(
            connection_id,
            owner_id=owner_id,
            payload=body,
            operation_id=operation_id,
        )
    )
    if payload.name is not None:
        conv = conversation.find_conversation_by_resource(
            session,
            kind=ConversationKind.DATA_CONNECTION.value,
            resource_id=connection_id,
            owner_id=owner_id,
        )
        if conv is not None:
            conversation.rename_conversation(
                session, conv.id, title=payload.name, owner_id=owner_id
            )
    return updated


def test_connection(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> dict:
    return mapping.connection_test(
        client.test_connection(connection_id, owner_id=owner_id, operation_id=operation_id)
    )


def list_tables(
    client: DataServiceClient, *, owner_id: uuid.UUID, connection_id: uuid.UUID
) -> dict:
    return mapping.table_list(client.list_tables(connection_id, owner_id=owner_id))


def describe_table(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
    schema: str,
    table: str,
) -> dict:
    return mapping.table_detail(
        client.describe_table(connection_id, owner_id=owner_id, schema=schema, table=table)
    )


def get_state(
    session: Session,
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
) -> dict:
    state = mapping.state(client.get_state(connection_id, owner_id=owner_id))
    conv = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=connection_id,
        owner_id=owner_id,
    )
    latest = (
        conversation_event.list_recent_events(session, conv.id, limit=1) if conv is not None else []
    )
    # Java 的 lastEventId 是 Outbox 事件 id，和 Python 的事件游标不是同一套；对外统一给后者。
    state["last_event_id"] = latest[-1].id if latest else None
    return state


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


def stop_sync_run(client: DataServiceClient, *, owner_id: uuid.UUID, run_id: uuid.UUID) -> dict:
    return mapping.sync_run(client.stop_sync_run(run_id, owner_id=owner_id))


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


def list_datasets(
    session: Session,
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
) -> list[dict]:
    return get_state(session, client, owner_id=owner_id, connection_id=connection_id)["datasets"]


def get_dataset(client: DataServiceClient, *, owner_id: uuid.UUID, dataset_id: uuid.UUID) -> dict:
    return mapping.dataset(client.get_dataset(dataset_id, owner_id=owner_id))


def get_dataset_preview(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None,
    limit: int,
) -> dict:
    return mapping.dataset_preview(
        client.get_dataset_preview(
            dataset_id,
            owner_id=owner_id,
            version_id=version_id,
            limit=limit,
        )
    )


def _table_selection(item: TableSelection) -> dict:
    body: dict[str, Any] = {
        "schema": item.schema_name,
        "table": item.table,
        "syncMode": item.sync_mode,
    }
    if item.version_interval_seconds is not None:
        body["versionIntervalSeconds"] = item.version_interval_seconds
    return body
