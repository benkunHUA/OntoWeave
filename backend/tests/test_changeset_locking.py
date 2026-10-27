"""`test_changeset.py` 中 locking 相关测试。"""

import uuid
from concurrent.futures import ThreadPoolExecutor
from threading import Event
from time import sleep

from app.db import build_session_factory
from app.errors import InvalidTransitionError
from app.services import changeset, conversation
from tests.support.changeset_fixtures import _default_user, _delete_conversation_and_graph


def test_concurrent_confirm_only_one_wins(engine):
    factory = build_session_factory(engine)
    owner = _default_user()
    with factory() as setup:
        conv = conversation.create_conversation(session=setup, owner_id=owner, title="并发确认")
        cs = changeset.create_changeset(session=setup, graph_id=conv.resource_id, owner_id=owner)
        setup.commit()
        conversation_id = conv.id
        graph_id = conv.resource_id
        changeset_id = cs.id

    started = Event()

    def confirm_in_second_session() -> str:
        with factory() as session:
            started.set()
            try:
                changeset.confirm_changeset(session, changeset_id, owner_id=owner)
                session.commit()
                return "confirmed"
            except InvalidTransitionError:
                session.rollback()
                return "conflict"

    try:
        with factory() as first:
            changeset.confirm_changeset(first, changeset_id, owner_id=owner)
            with ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(confirm_in_second_session)
                assert started.wait(timeout=2)
                sleep(0.2)
                assert not future.done()
                first.commit()
                assert future.result(timeout=5) == "conflict"
    finally:
        with factory() as cleanup:
            _delete_conversation_and_graph(
                cleanup, conversation_id=conversation_id, graph_id=graph_id
            )


def test_record_op_waits_for_the_changeset_lock(engine):
    factory = build_session_factory(engine)
    owner = _default_user()
    with factory() as setup:
        conv = conversation.create_conversation(session=setup, owner_id=owner, title="并发记录")
        cs = changeset.create_changeset(session=setup, graph_id=conv.resource_id, owner_id=owner)
        setup.commit()
        conversation_id = conv.id
        graph_id = conv.resource_id
        changeset_id = cs.id

    started = Event()

    def record_in_second_session() -> str:
        with factory() as session:
            started.set()
            try:
                changeset.record_op(
                    session,
                    changeset_id=changeset_id,
                    op="create",
                    target_id=uuid.uuid4(),
                    payload={"kind": "object_type"},
                    owner_id=owner,
                )
                session.commit()
                return "recorded"
            except InvalidTransitionError:
                session.rollback()
                return "closed"

    try:
        with factory() as first:
            changeset.confirm_changeset(first, changeset_id, owner_id=owner)
            with ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(record_in_second_session)
                assert started.wait(timeout=2)
                sleep(0.2)
                assert not future.done()
                first.commit()
                assert future.result(timeout=5) == "closed"
    finally:
        with factory() as cleanup:
            _delete_conversation_and_graph(
                cleanup, conversation_id=conversation_id, graph_id=graph_id
            )
