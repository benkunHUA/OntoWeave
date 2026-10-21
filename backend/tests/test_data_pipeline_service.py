import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from sqlalchemy import delete, select

from app.db import build_session_factory
from app.errors import DataServiceUnavailableError, InvalidRequestError
from app.models import Conversation
from app.schemas.data_pipeline import PipelineCreate
from app.services import conversation
from app.services.data_pipeline import create_pipeline, pipeline_ids
from app.vocabulary import ConversationKind


def _pipeline_row(pipeline_id: uuid.UUID, conversation_id: uuid.UUID, name: str = "流程") -> dict:
    return {
        "id": str(pipeline_id),
        "conversationId": str(conversation_id),
        "name": name,
        "status": "draft",
        "draftRevision": 0,
        "currentVersionId": None,
        "createdAt": "2026-10-18T00:00:00Z",
        "updatedAt": "2026-10-18T00:00:00Z",
    }


class _CreateClient:
    def __init__(self, session_factory, *, failure: Exception | None = None) -> None:
        self.session_factory = session_factory
        self.failure = failure
        self.calls: list[dict] = []
        self.conversation_visible = False

    def create_pipeline(self, *, owner_id, payload, operation_id=None):  # noqa: ANN001
        self.calls.append(dict(payload))
        if self.failure is not None:
            raise self.failure
        with self.session_factory() as check:
            row = check.get(Conversation, uuid.UUID(payload["conversationId"]))
            self.conversation_visible = row is not None
        return _pipeline_row(
            uuid.UUID(payload["pipelineId"]),
            uuid.UUID(payload["conversationId"]),
            payload["name"],
        )


def test_pipeline_ids_are_stable_for_same_operation() -> None:
    owner = uuid.UUID("11111111-1111-4111-8111-111111111111")
    operation = uuid.UUID("22222222-2222-4222-8222-222222222222")

    first = pipeline_ids(owner, operation)
    second = pipeline_ids(owner, operation)

    assert first == second
    assert first[0] != first[1]


def test_create_pipeline_persists_conversation_before_java(session_factory) -> None:
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
    assert client.conversation_visible is True
    assert result["conversation_id"] == str(expected_conversation)
    with session_factory() as session:
        conv = session.get(Conversation, expected_conversation)
        assert conv is not None
        assert conv.kind == ConversationKind.DATA_PIPELINE.value
        assert conv.resource_id == expected_pipeline


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
        assert session.get(Conversation, conversation_id) is not None

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
    assert [row.id for row in rows] == [conversation_id]


def test_concurrent_create_converges_on_the_unique_conversation(engine) -> None:
    factory = build_session_factory(engine)
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    barrier = threading.Barrier(2)

    def run() -> dict:
        client = _CreateClient(factory)
        with factory() as session:
            barrier.wait(timeout=5)
            return create_pipeline(
                session,
                owner_id=owner,
                client=client,
                payload=PipelineCreate(name="并发"),
                operation_id=operation,
            )

    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = [future.result(timeout=10) for future in [pool.submit(run), pool.submit(run)]]

        assert {item["id"] for item in results} == {str(pipeline_id)}
        with factory() as session:
            rows = list(
                session.scalars(
                    select(Conversation).where(
                        Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                        Conversation.resource_id == pipeline_id,
                    )
                )
            )
        assert [row.id for row in rows] == [conversation_id]
    finally:
        with factory() as session:
            session.execute(
                delete(Conversation).where(
                    Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                    Conversation.resource_id == pipeline_id,
                )
            )
            session.commit()
