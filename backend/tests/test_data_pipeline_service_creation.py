"""`test_data_pipeline_service.py` 中 creation 相关测试。"""

import uuid

import pytest
from sqlalchemy import select

from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    UnprocessableEntityError,
)
from app.models import Conversation
from app.schemas.data_pipeline import PipelineCreate
from app.services import conversation
from app.services.data_pipeline import create_pipeline, pipeline_ids
from app.vocabulary import ConversationKind
from tests.support.data_pipeline_service_fixtures import _CreateClient


def test_pipeline_ids_are_stable_for_same_operation() -> None:
    owner = uuid.UUID("11111111-1111-4111-8111-111111111111")
    operation = uuid.UUID("22222222-2222-4222-8222-222222222222")

    first = pipeline_ids(owner, operation)
    second = pipeline_ids(owner, operation)

    assert first == second
    assert first[0] != first[1]


def test_create_pipeline_defers_conversation_until_first_message(session_factory) -> None:
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    expected_pipeline, expected_conversation = pipeline_ids(owner, operation)
    client = _CreateClient(session_factory)

    with session_factory() as session:
        result = create_pipeline(
            session,
            owner_id=owner,
            client=client,
            payload=PipelineCreate(name="订单清洗"),
            operation_id=operation,
        )

    assert client.calls[0]["pipelineId"] == str(expected_pipeline)
    assert client.calls[0]["conversationId"] == str(expected_conversation)
    assert client.conversation_visible is False
    assert result["conversation_id"] == str(expected_conversation)
    with session_factory() as session:
        conv = session.get(Conversation, expected_conversation)
        assert conv is None


def test_create_pipeline_4xx_deletes_only_the_conversation_it_created(session_factory) -> None:
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    client = _CreateClient(session_factory, failure=InvalidRequestError("名称不合法"))

    with session_factory() as session, pytest.raises(InvalidRequestError):
        create_pipeline(
            session,
            owner_id=owner,
            client=client,
            payload=PipelineCreate(name="坏流程"),
            operation_id=operation,
        )

    with session_factory() as session:
        assert (
            conversation.find_conversation_by_resource(
                session,
                kind=ConversationKind.DATA_PIPELINE.value,
                resource_id=pipeline_id,
                owner_id=owner,
            )
            is None
        )
        assert session.get(Conversation, conversation_id) is None


def test_create_pipeline_422_deletes_only_the_conversation_it_created(
    session_factory,
) -> None:
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    client = _CreateClient(
        session_factory,
        failure=UnprocessableEntityError(
            "blocking issues", code="PIPELINE_NOT_READY", details={"issues": []}
        ),
    )

    with session_factory() as session, pytest.raises(UnprocessableEntityError):
        create_pipeline(
            session,
            owner_id=owner,
            client=client,
            payload=PipelineCreate(name="不可构建"),
            operation_id=operation,
        )

    with session_factory() as session:
        assert (
            conversation.find_conversation_by_resource(
                session,
                kind=ConversationKind.DATA_PIPELINE.value,
                resource_id=pipeline_id,
                owner_id=owner,
            )
            is None
        )
        assert session.get(Conversation, conversation_id) is None


def test_retry_422_reuses_the_conversation_created_by_an_earlier_attempt(
    session_factory,
) -> None:
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)

    with session_factory() as session, pytest.raises(DataServiceUnavailableError):
        create_pipeline(
            session,
            owner_id=owner,
            client=_CreateClient(session_factory, failure=DataServiceUnavailableError("超时")),
            payload=PipelineCreate(name="先超时"),
            operation_id=operation,
        )

    with session_factory() as session, pytest.raises(UnprocessableEntityError):
        create_pipeline(
            session,
            owner_id=owner,
            client=_CreateClient(
                session_factory,
                failure=UnprocessableEntityError("blocking issues", code="PIPELINE_NOT_READY"),
            ),
            payload=PipelineCreate(name="再校验失败"),
            operation_id=operation,
        )

    with session_factory() as session:
        rows = list(
            session.scalars(
                select(Conversation).where(
                    Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                    Conversation.resource_id == pipeline_id,
                )
            )
        )
    assert rows == []


def test_create_pipeline_timeout_keeps_conversation_and_retry_reuses_it(session_factory) -> None:
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    failing = _CreateClient(session_factory, failure=DataServiceUnavailableError("超时"))

    with session_factory() as session, pytest.raises(DataServiceUnavailableError):
        create_pipeline(
            session,
            owner_id=owner,
            client=failing,
            payload=PipelineCreate(name="可重试"),
            operation_id=operation,
        )

    with session_factory() as session:
        assert session.get(Conversation, conversation_id) is None

    retrying = _CreateClient(session_factory)
    with session_factory() as session:
        result = create_pipeline(
            session,
            owner_id=owner,
            client=retrying,
            payload=PipelineCreate(name="可重试"),
            operation_id=operation,
        )

    assert result["conversation_id"] == str(conversation_id)
    with session_factory() as session:
        rows = list(
            session.scalars(
                select(Conversation).where(
                    Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                    Conversation.resource_id == pipeline_id,
                )
            )
        )
    assert rows == []
