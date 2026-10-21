import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from sqlalchemy import create_engine, delete, select, update

from app.db import build_session_factory
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    UnprocessableEntityError,
)
from app.models import Conversation
from app.schemas.data_pipeline import PipelineCreate
from app.services import conversation
from app.services.data_pipeline import _ensure_conversation, create_pipeline, pipeline_ids
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


class _NoLookupClient:
    def create_pipeline(self, *, owner_id, payload, operation_id=None):  # noqa: ANN001, ARG002
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
        assert session.get(Conversation, conversation_id) is not None
        rows = list(
            session.scalars(
                select(Conversation).where(
                    Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                    Conversation.resource_id == pipeline_id,
                )
            )
        )
    assert [row.id for row in rows] == [conversation_id]


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


def test_operation_lock_keeps_small_pool_for_same_operation(engine) -> None:
    limited = create_engine(engine.url, pool_size=2, max_overflow=0, pool_timeout=3, future=True)
    factory = build_session_factory(limited)
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    barrier = threading.Barrier(2)

    def run() -> dict:
        with factory() as session:
            barrier.wait(timeout=5)
            return create_pipeline(
                session,
                owner_id=owner,
                client=_NoLookupClient(),
                payload=PipelineCreate(name="小连接池同操作"),
                operation_id=operation,
            )

    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = [future.result(timeout=15) for future in [pool.submit(run), pool.submit(run)]]

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
            session.execute(delete(Conversation).where(Conversation.resource_id == pipeline_id))
            session.commit()
        limited.dispose()


def test_operation_lock_keeps_small_pool_for_different_operations(engine) -> None:
    limited = create_engine(engine.url, pool_size=2, max_overflow=0, pool_timeout=3, future=True)
    factory = build_session_factory(limited)
    owner = uuid.uuid4()
    operations = [uuid.uuid4(), uuid.uuid4()]
    expected = [pipeline_ids(owner, operation) for operation in operations]
    barrier = threading.Barrier(2)

    def run(operation: uuid.UUID) -> dict:
        with factory() as session:
            barrier.wait(timeout=5)
            return create_pipeline(
                session,
                owner_id=owner,
                client=_NoLookupClient(),
                payload=PipelineCreate(name="小连接池不同操作"),
                operation_id=operation,
            )

    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = [
                future.result(timeout=15)
                for future in [pool.submit(run, operation) for operation in operations]
            ]

        assert {item["id"] for item in results} == {str(pipeline_id) for pipeline_id, _ in expected}
        with factory() as session:
            rows = list(
                session.scalars(
                    select(Conversation).where(
                        Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                        Conversation.resource_id.in_([pipeline_id for pipeline_id, _ in expected]),
                    )
                )
            )
        assert {row.resource_id for row in rows} == {pipeline_id for pipeline_id, _ in expected}
    finally:
        with factory() as session:
            session.execute(
                delete(Conversation).where(
                    Conversation.resource_id.in_([pipeline_id for pipeline_id, _ in expected])
                )
            )
            session.commit()
        limited.dispose()


def test_concurrent_ensure_conversation_uses_savepoint_and_keeps_transaction_usable(
    engine, monkeypatch
) -> None:
    factory = build_session_factory(engine)
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    barrier = threading.Barrier(2)
    original_find = conversation.find_conversation_by_resource
    forced_missing = threading.local()

    def force_both_inserts_to_race(session, **kwargs):  # noqa: ANN001, ANN003
        if not getattr(forced_missing, "done", False):
            forced_missing.done = True
            return None
        return original_find(session, **kwargs)

    monkeypatch.setattr(conversation, "find_conversation_by_resource", force_both_inserts_to_race)

    def run() -> tuple[uuid.UUID, bool]:
        with factory() as session:
            barrier.wait(timeout=5)
            conv, created = _ensure_conversation(
                session,
                owner_id=owner,
                pipeline_id=pipeline_id,
                conversation_id=conversation_id,
                title="并发收敛",
            )
            if not created:
                session.execute(
                    update(Conversation)
                    .where(Conversation.id == conv.id)
                    .values(title="冲突后仍可写")
                )
            session.commit()
            return conv.id, created

    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = [future.result(timeout=10) for future in [pool.submit(run), pool.submit(run)]]

        assert [conversation_id, conversation_id] == [item[0] for item in results]
        assert sorted(item[1] for item in results) == [False, True]
        with factory() as session:
            rows = list(
                session.scalars(
                    select(Conversation).where(
                        Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                        Conversation.resource_id == pipeline_id,
                    )
                )
            )
            title = session.scalar(
                select(Conversation.title).where(Conversation.id == conversation_id)
            )
        assert [row.id for row in rows] == [conversation_id]
        assert title == "冲突后仍可写"
    finally:
        with factory() as session:
            session.execute(
                delete(Conversation).where(
                    Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                    Conversation.resource_id == pipeline_id,
                )
            )
            session.commit()


def test_failed_create_does_not_delete_a_conversation_reused_by_same_operation(engine) -> None:
    factory = build_session_factory(engine)
    owner = uuid.uuid4()
    operation = uuid.uuid4()
    pipeline_id, conversation_id = pipeline_ids(owner, operation)
    first_entered_java = threading.Event()
    retry_succeeded = threading.Event()

    class _RaceClient:
        def __init__(self, *, fail: bool) -> None:
            self.fail = fail

        def create_pipeline(self, *, owner_id, payload, operation_id=None):  # noqa: ANN001, ARG002
            if self.fail:
                first_entered_java.set()
                # 无锁实现会立即复用并成功；有锁实现会等 A 失败释放后才新建。
                retry_succeeded.wait(timeout=2)
                raise InvalidRequestError("名称不合法")
            with factory() as check:
                assert check.get(Conversation, uuid.UUID(payload["conversationId"])) is not None
            retry_succeeded.set()
            return _pipeline_row(
                uuid.UUID(payload["pipelineId"]), uuid.UUID(payload["conversationId"])
            )

    def run(*, fail: bool):
        with factory() as session:
            return create_pipeline(
                session,
                owner_id=owner,
                client=_RaceClient(fail=fail),
                payload=PipelineCreate(name="并发补偿"),
                operation_id=operation,
            )

    try:
        with ThreadPoolExecutor(max_workers=2) as pool:
            failed = pool.submit(run, fail=True)
            assert first_entered_java.wait(timeout=5) is True
            succeeded = pool.submit(run, fail=False)
            with pytest.raises(InvalidRequestError):
                failed.result(timeout=10)
            assert succeeded.result(timeout=10)["id"] == str(pipeline_id)

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
