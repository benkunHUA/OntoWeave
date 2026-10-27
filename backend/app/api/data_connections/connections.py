"""连接生命周期、表结构与状态接口。"""

from __future__ import annotations

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Query
from sqlalchemy.orm import Session

from app.clients.data_service.client import DataServiceClient
from app.deps import get_current_user_id, get_data_service_client, get_session
from app.schemas.conversation import ConversationRead
from app.schemas.data_connection import (
    ConnectionTestRead,
    DataConnectionCreate,
    DataConnectionPageRead,
    DataConnectionRead,
    DataConnectionStateRead,
    DataConnectionUpdate,
    TableDetailRead,
    TableListRead,
)
from app.services import data_connection

router = APIRouter()


@router.post("/data-connections", response_model=DataConnectionRead, status_code=201)
def create_connection(
    payload: DataConnectionCreate,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionRead:
    return DataConnectionRead.model_validate(
        data_connection.create_connection(
            session,
            owner_id=get_current_user_id(),
            client=client,
            payload=payload,
        )
    )


@router.get("/data-connections", response_model=DataConnectionPageRead)
def list_connections(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionPageRead:
    return DataConnectionPageRead.model_validate(
        data_connection.list_connections(
            client, owner_id=get_current_user_id(), limit=limit, offset=offset
        )
    )


@router.get("/data-connections/{connection_id}", response_model=DataConnectionRead)
def get_connection(
    connection_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionRead:
    return DataConnectionRead.model_validate(
        data_connection.get_connection(
            client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.patch("/data-connections/{connection_id}", response_model=DataConnectionRead)
def update_connection(
    connection_id: uuid.UUID,
    payload: DataConnectionUpdate,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionRead:
    return DataConnectionRead.model_validate(
        data_connection.update_connection(
            session,
            owner_id=get_current_user_id(),
            client=client,
            connection_id=connection_id,
            payload=payload,
            operation_id=operation_id,
        )
    )


@router.post("/data-connections/{connection_id}/test", response_model=ConnectionTestRead)
def test_connection(
    connection_id: uuid.UUID,
    operation_id: Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")] = None,
    client: DataServiceClient = Depends(get_data_service_client),
) -> ConnectionTestRead:
    return ConnectionTestRead.model_validate(
        data_connection.test_connection(
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            operation_id=operation_id,
        )
    )


@router.get("/data-connections/{connection_id}/tables", response_model=TableListRead)
def list_tables(
    connection_id: uuid.UUID,
    client: DataServiceClient = Depends(get_data_service_client),
) -> TableListRead:
    return TableListRead.model_validate(
        data_connection.list_tables(
            client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.get(
    "/data-connections/{connection_id}/tables/{schema}/{table}",
    response_model=TableDetailRead,
)
def describe_table(
    connection_id: uuid.UUID,
    schema: str,
    table: str,
    client: DataServiceClient = Depends(get_data_service_client),
) -> TableDetailRead:
    return TableDetailRead.model_validate(
        data_connection.describe_table(
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
            schema=schema,
            table=table,
        )
    )


@router.get("/data-connections/{connection_id}/state", response_model=DataConnectionStateRead)
def get_state(
    connection_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> DataConnectionStateRead:
    return DataConnectionStateRead.model_validate(
        data_connection.get_state(
            session, client, owner_id=get_current_user_id(), connection_id=connection_id
        )
    )


@router.post(
    "/data-connections/{connection_id}/conversation",
    response_model=ConversationRead,
    status_code=201,
)
def ensure_conversation(
    connection_id: uuid.UUID,
    session: Session = Depends(get_session),
    client: DataServiceClient = Depends(get_data_service_client),
) -> ConversationRead:
    return ConversationRead.model_validate(
        data_connection.ensure_conversation(
            session,
            client,
            owner_id=get_current_user_id(),
            connection_id=connection_id,
        )
    )
