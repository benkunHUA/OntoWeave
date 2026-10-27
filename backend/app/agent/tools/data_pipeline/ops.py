"""数据整理工具的领域读写逻辑，与 LangChain 包装解耦。"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.clients.data_service import pipeline_mapping as mapping
from app.clients.data_service.client import DataServiceClient
from app.services import conversation
from app.services.conversation import event as conversation_event


def get_pipeline_state(
    client: DataServiceClient, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID
) -> dict:
    return mapping.pipeline(client.get_pipeline(pipeline_id, owner_id=owner_id))


def get_draft(client: DataServiceClient, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID) -> dict:
    return mapping.draft(client.get_draft(pipeline_id, owner_id=owner_id))


def check(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    revision: int,
) -> dict:
    return mapping.check_result(
        client.check_pipeline(pipeline_id, owner_id=owner_id, revision=revision)
    )


def get_events(
    session: Session,
    *,
    owner_id: uuid.UUID,
    conversation_id: uuid.UUID,
    after_event_id: uuid.UUID | None,
    limit: int,
) -> list[dict]:
    conversation.get_conversation(session, conversation_id, owner_id=owner_id)
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
            "turn_id": str(row.turn_id) if row.turn_id else None,
            "event_type": row.event_type,
            "summary": row.summary,
            "payload": row.payload,
            "created_at": row.created_at.isoformat(),
        }
        for row in rows
    ]


def get_node_catalog(client: DataServiceClient) -> dict:
    return mapping.node_catalog(client.get_node_catalog())


def list_input_datasets(
    client: DataServiceClient, *, owner_id: uuid.UUID, limit: int, offset: int
) -> dict:
    return mapping.input_dataset_page(
        client.list_input_datasets(owner_id=owner_id, limit=limit, offset=offset)
    )


def get_dataset_schema(
    client: DataServiceClient, *, owner_id: uuid.UUID, dataset_id: uuid.UUID
) -> dict:
    return mapping.dataset_schema(client.get_dataset_schema(dataset_id, owner_id=owner_id))


def get_output_sources(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    dataset_id: uuid.UUID,
    version_id: uuid.UUID | None,
) -> dict:
    return mapping.output_sources(
        client.get_output_sources(
            pipeline_id,
            dataset_id,
            owner_id=owner_id,
            version_id=version_id,
        )
    )


def apply_command(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    payload: dict,
) -> dict:
    return client.apply_draft_command(pipeline_id, owner_id=owner_id, payload=payload)


def preview(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    payload: dict,
) -> dict:
    return mapping.preview(client.preview_node(pipeline_id, owner_id=owner_id, payload=payload))


def build(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    payload: dict,
) -> dict:
    return mapping.build(client.create_build(pipeline_id, owner_id=owner_id, payload=payload))
