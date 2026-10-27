"""`test_data_pipeline_service.py` 中 locking 相关测试。"""

import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from sqlalchemy import create_engine, delete, select, update

from app.db import build_session_factory
from app.errors import (
    InvalidRequestError,
)
from app.models import Conversation
from app.schemas.data_pipeline import PipelineCreate
from app.services import conversation
from app.services.data_pipeline import create_pipeline, pipeline_ids
from app.services.data_pipeline.core import _ensure_conversation
from app.vocabulary import ConversationKind
from tests.support.data_pipeline_service_fixtures import (
    _CreateClient,
    _NoLookupClient,
    _pipeline_row,
)


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
        assert rows == []
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
        assert rows == []
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
        assert rows == []
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
                assert check.get(Conversation, uuid.UUID(payload["conversationId"])) is None
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
        assert rows == []
    finally:
        with factory() as session:
            session.execute(
                delete(Conversation).where(
                    Conversation.kind == ConversationKind.DATA_PIPELINE.value,
                    Conversation.resource_id == pipeline_id,
                )
            )
            session.commit()
