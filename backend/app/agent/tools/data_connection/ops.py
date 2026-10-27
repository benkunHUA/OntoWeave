"""数据连接工具的纯读取逻辑，便于单测。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.clients.data_service import mapping
from app.clients.data_service.client import DataServiceClient
from app.services.conversation import event as conversation_event


def get_state(client: DataServiceClient, *, owner_id: uuid.UUID, connection_id: uuid.UUID) -> dict:
    return mapping.state(client.get_state(connection_id, owner_id=owner_id))


def get_events(
    session: Session,
    *,
    owner_id: uuid.UUID,
    conversation_id: uuid.UUID,
    after_event_id: uuid.UUID | None,
    limit: int,
) -> list[dict]:
    if after_event_id is None:
        rows = conversation_event.list_recent_events(session, conversation_id, limit=limit)
    else:
        rows = conversation_event.list_events(
            session,
            conversation_id,
            owner_id=owner_id,
            after_event_id=after_event_id,
            limit=limit,
        )
    return [
        {
            "id": str(row.id),
            "event_id": str(row.event_id),
            "run_id": str(row.run_id) if row.run_id else None,
            "event_type": row.event_type,
            "summary": row.summary,
            "created_at": row.created_at.isoformat(),
        }
        for row in rows
    ]


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
