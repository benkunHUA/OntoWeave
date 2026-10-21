"""数据流程的 Python 编排：稳定 ID、会话先落库、Java 4xx 补偿。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.errors import (
    InvalidRequestError,
    InvariantViolationError,
    NotFoundError,
)
from app.models import Conversation
from app.schemas.data_pipeline import PipelineCopy, PipelineCreate
from app.services import conversation
from app.services.data_service import mapping
from app.services.data_service.client import DataServiceClient
from app.vocabulary import ConversationKind

PIPELINE_NAMESPACE = uuid.NAMESPACE_URL
_JAVA_4XX = (InvalidRequestError, InvariantViolationError, NotFoundError)


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
        session,
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
    )
    if existing is not None:
        if existing.owner_id != owner_id:
            raise InvariantViolationError("pipeline conversation belongs to another owner")
        return existing, False

    try:
        with session.begin_nested():
            created = conversation.create_conversation(
                session,
                owner_id=owner_id,
                title=title,
                kind=ConversationKind.DATA_PIPELINE.value,
                resource_id=pipeline_id,
                conversation_id=conversation_id,
            )
        return created, True
    except IntegrityError:
        # 另一个请求先插入成功；savepoint 已回滚，事务仍可用，重查后收敛。
        session.expire_all()
        raced = conversation.find_conversation_by_resource(
            session,
            kind=ConversationKind.DATA_PIPELINE.value,
            resource_id=pipeline_id,
        )
        if raced is None:
            raise
        if raced.owner_id != owner_id:
            raise InvariantViolationError(
                "pipeline conversation belongs to another owner"
            ) from None
        return raced, False


def _create_with_java(
    session: Session,
    *,
    owner_id: uuid.UUID,
    operation_id: uuid.UUID,
    payload: PipelineCreate,
    invoke,
) -> dict:
    pipeline_id, conversation_id = pipeline_ids(owner_id, operation_id)
    conv, created = _ensure_conversation(
        session,
        owner_id=owner_id,
        pipeline_id=pipeline_id,
        conversation_id=conversation_id,
        title=payload.name,
    )
    # Java 必须能在内部接口里查到会话；先提交，超出请求级事务也保留可重试记录。
    session.commit()
    try:
        row = invoke(pipeline_id, conv.id)
    except _JAVA_4XX:
        if created:
            conversation.delete_conversation(session, conv.id, owner_id=owner_id)
            session.commit()
        raise
    # 超时 / 5xx 直接上抛：会话已提交，同一 operation_id 重试会复用。
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
        payload=payload,
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
        payload=PipelineCreate(name=name),
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
