import importlib.util
import uuid
from pathlib import Path

from sqlalchemy import text
from sqlalchemy.orm import Session, sessionmaker

from app.services import conversation

MIGRATION_PATH = (
    Path(__file__).resolve().parents[1]
    / "alembic"
    / "versions"
    / "0011_close_answered_tool_blocks.py"
)


def _repair_sql() -> str:
    """迁移是按版本号命名的，import 不进来，直接按路径加载。"""
    spec = importlib.util.spec_from_file_location("repair_0011", MIGRATION_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module.REPAIR_SQL


def _asked_blocks(call_id: str) -> list[dict]:
    return [
        {
            "id": uuid.uuid4().hex,
            "kind": "tool",
            "callId": call_id,
            "name": "ask_user",
            "args": {"question": "客户还是供应商？"},
            "status": "running",
            "result": None,
            "truncated": False,
            "durationMs": None,
        },
        {
            "id": uuid.uuid4().hex,
            "kind": "interrupt",
            "question": "客户还是供应商？",
            "options": [{"label": "客户", "answer": "客户"}],
            "freeText": {"wrap": "raw"},
        },
    ]


def _statuses(session: Session, conversation_id: uuid.UUID) -> list[str]:
    return [
        block["status"]
        for message in conversation.list_messages(session, conversation_id)
        for block in (message.blocks or [])
        if block["kind"] == "tool"
    ]


def test_repair_closes_interrupt_tool_blocks_once_answered(
    session_factory: sessionmaker[Session], session: Session
) -> None:
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="待修")
    conversation.add_message(
        session,
        conversation_id=conv.id,
        role="assistant",
        content="客户还是供应商？",
        blocks=_asked_blocks("c1"),
    )
    conversation.add_message(session, conversation_id=conv.id, role="user", content="客户")
    session.flush()

    session.execute(text(_repair_sql()))

    assert _statuses(session, conv.id) == ["ok"]


def test_repair_leaves_a_still_waiting_conversation_alone(
    session_factory: sessionmaker[Session], session: Session
) -> None:
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="还在等")
    conversation.add_message(
        session,
        conversation_id=conv.id,
        role="assistant",
        content="客户还是供应商？",
        blocks=_asked_blocks("c2"),
    )
    session.flush()

    session.execute(text(_repair_sql()))

    assert _statuses(session, conv.id) == ["running"]
