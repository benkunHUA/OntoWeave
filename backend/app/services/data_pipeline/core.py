"""数据流程的 Python 编排：稳定 ID 与首条消息时才落库的会话。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.clients.data_service import mapping
from app.clients.data_service.client import DataServiceClient
from app.errors import InvariantViolationError, NotFoundError
from app.models import Conversation
from app.schemas.data_pipeline import PipelineCopy, PipelineCreate
from app.services import conversation
from app.services.operation_lock import operation_lock
from app.vocabulary import ConversationKind

PIPELINE_NAMESPACE = uuid.NAMESPACE_URL


def pipeline_ids(owner_id: uuid.UUID, operation_id: uuid.UUID) -> tuple[uuid.UUID, uuid.UUID]:
    pipeline_id = uuid.uuid5(
        PIPELINE_NAMESPACE, f"ontoweave:data-pipeline:{owner_id}:{operation_id}"
    )
    conversation_id = uuid.uuid5(
        PIPELINE_NAMESPACE,
        f"ontoweave:data-pipeline:{owner_id}:{operation_id}:conversation",
    )
    return pipeline_id, conversation_id


def _ensure_conversation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    conversation_id: uuid.UUID,
    title: str,
) -> tuple[Conversation, bool]:
    existing = conversation.find_conversation_by_resource(
        session, kind=ConversationKind.DATA_PIPELINE.value, resource_id=pipeline_id
    )
    if existing is not None:
        if existing.owner_id != owner_id:
            raise InvariantViolationError("pipeline conversation belongs to another owner")
        return existing, False

    return conversation.ensure_resource_conversation_with_status(
        session,
        owner_id=owner_id,
        title=title,
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
        conversation_id=conversation_id,
    )


def _create_with_java(
    session: Session,
    *,
    owner_id: uuid.UUID,
    operation_id: uuid.UUID,
    invoke,
) -> dict:
    with operation_lock(session, "data-pipeline", owner_id, operation_id):
        pipeline_id, conversation_id = pipeline_ids(owner_id, operation_id)
        row = invoke(pipeline_id, conversation_id)
        return mapping.pipeline(row)


def create_pipeline(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    payload: PipelineCreate,
    operation_id: uuid.UUID,
) -> dict:
    def invoke(pipeline_id: uuid.UUID, conversation_id: uuid.UUID) -> dict:
        return client.create_pipeline(
            owner_id=owner_id,
            payload={
                "pipelineId": str(pipeline_id),
                "conversationId": str(conversation_id),
                "name": payload.name,
            },
            operation_id=operation_id,
        )

    return _create_with_java(
        session,
        owner_id=owner_id,
        operation_id=operation_id,
        invoke=invoke,
    )


def copy_pipeline(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    source_pipeline_id: uuid.UUID,
    payload: PipelineCopy,
    operation_id: uuid.UUID,
) -> dict:
    name = payload.name or "流程副本"

    def invoke(pipeline_id: uuid.UUID, conversation_id: uuid.UUID) -> dict:
        body: dict[str, Any] = {
            "pipelineId": str(pipeline_id),
            "conversationId": str(conversation_id),
            "name": name,
        }
        return client.copy_pipeline(
            source_pipeline_id,
            owner_id=owner_id,
            payload=body,
            operation_id=operation_id,
        )

    return _create_with_java(
        session,
        owner_id=owner_id,
        operation_id=operation_id,
        invoke=invoke,
    )


def list_pipelines(
    client: DataServiceClient, *, owner_id: uuid.UUID, limit: int, offset: int
) -> dict:
    return mapping.pipeline_page(
        client.list_pipelines(owner_id=owner_id, limit=limit, offset=offset)
    )


def get_pipeline(client: DataServiceClient, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID) -> dict:
    return mapping.pipeline(client.get_pipeline(pipeline_id, owner_id=owner_id))


def find_pipeline_conversation(
    session: Session, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID
) -> Conversation | None:
    return conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
        owner_id=owner_id,
    )


def ensure_pipeline_conversation(
    session: Session,
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
) -> Conversation:
    existing = find_pipeline_conversation(session, owner_id=owner_id, pipeline_id=pipeline_id)
    if existing is not None:
        return existing
    pipeline = get_pipeline(client, owner_id=owner_id, pipeline_id=pipeline_id)
    conv, _ = _ensure_conversation(
        session,
        owner_id=owner_id,
        pipeline_id=pipeline_id,
        conversation_id=uuid.UUID(pipeline["conversation_id"]),
        title=pipeline["name"],
    )
    return conv


def rename_pipeline(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    pipeline_id: uuid.UUID,
    name: str,
    operation_id: uuid.UUID | None = None,
) -> dict:
    result = mapping.pipeline(
        client.rename_pipeline(pipeline_id, owner_id=owner_id, name=name, operation_id=operation_id)
    )
    conv = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
        owner_id=owner_id,
    )
    if conv is not None:
        conversation.rename_conversation(session, conv.id, title=name, owner_id=owner_id)
    return result


def archive_pipeline(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    pipeline_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> None:
    try:
        client.archive_pipeline(pipeline_id, owner_id=owner_id, operation_id=operation_id)
    except NotFoundError:
        pass
    conv = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
        owner_id=owner_id,
    )
    if conv is not None:
        conversation.archive_conversation(session, conv.id, owner_id=owner_id)
