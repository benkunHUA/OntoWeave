"""连接生命周期、表结构与连接状态。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.clients.data_service import mapping
from app.clients.data_service.client import DataServiceClient
from app.models import Conversation
from app.schemas.data_connection import DataConnectionCreate, DataConnectionUpdate
from app.services import conversation
from app.services.conversation import event as conversation_event
from app.vocabulary import ConversationKind


def create_connection(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    payload: DataConnectionCreate,
) -> dict:
    # 连接草稿先落 Java；Python 会话等用户发第一句话时再建。
    conversation_id = uuid.uuid4()
    body: dict[str, Any] = {
        "conversationId": str(conversation_id),
        "name": payload.name,
        "kind": payload.kind,
    }
    # Java 的 JsonNode 会把显式 null 解析成 NullNode，只有整个字段缺失才是“未填”。
    if payload.config:
        body["config"] = payload.config
    if payload.secret is not None:
        body["secret"] = payload.secret
    created = client.create_connection(owner_id=owner_id, payload=body)
    return mapping.connection(created)


def find_conversation(
    session: Session, *, owner_id: uuid.UUID, connection_id: uuid.UUID
) -> Conversation | None:
    return conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=connection_id,
        owner_id=owner_id,
    )


def ensure_conversation(
    session: Session,
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    connection_id: uuid.UUID,
) -> Conversation:
    existing = find_conversation(session, owner_id=owner_id, connection_id=connection_id)
    if existing is not None:
        return existing
    connection = get_connection(client, owner_id=owner_id, connection_id=connection_id)
    return conversation.ensure_resource_conversation(
        session,
        owner_id=owner_id,
        title=connection["name"],
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=connection_id,
        conversation_id=uuid.UUID(connection["conversation_id"]),
    )


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
    conv = find_conversation(session, owner_id=owner_id, connection_id=connection_id)
    latest = (
        conversation_event.list_recent_events(session, conv.id, limit=1) if conv is not None else []
    )
    # Java 的 lastEventId 是 Outbox 事件 id，和 Python 的事件游标不是同一套；对外统一给后者。
    state["last_event_id"] = latest[-1].id if latest else None
    state["conversation_ready"] = conv is not None
    return state
