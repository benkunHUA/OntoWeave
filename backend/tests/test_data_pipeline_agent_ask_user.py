"""`test_data_pipeline_agent.py` 中 ask_user 相关测试。"""

import threading
import time
import uuid

from sqlalchemy import select

from app.models import ConversationEvent
from tests.support.data_pipeline_agent_fixtures import _context, _tools
from tests.support.pipeline_test_fakes import FakePipelineClient


def test_ask_user_is_limited_once_per_turn(session, monkeypatch):
    ctx = _context(session, FakePipelineClient(), uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: "选 A")
    tools = _tools(ctx)

    assert tools["ask_user"].invoke({"question": "选哪个？"}) == "选 A"
    assert tools["ask_user"].invoke({"question": "再选一个？"}) == {
        "error": "QUESTION_LIMIT_REACHED",
        "message": "每轮最多问一个问题",
    }


def test_confirmation_interrupt_does_not_consume_question_quota(session, monkeypatch):
    ctx = _context(session, FakePipelineClient(), uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: False)
    _tools(ctx)["delete_pipeline_node"].invoke({"node_id": str(uuid.uuid4())})

    assert ctx.questions_asked == []
    rows = list(
        session.scalars(
            select(ConversationEvent).where(
                ConversationEvent.turn_id == ctx.turn_id,
                ConversationEvent.event_type == "pipeline.agent.question",
            )
        )
    )
    assert rows == []


def test_answered_question_is_persisted_for_the_turn(session, monkeypatch):
    ctx = _context(session, FakePipelineClient(), uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: "选择 A")

    assert _tools(ctx)["ask_user"].invoke({"question": "选哪个？"}) == "选择 A"
    rows = list(
        session.scalars(
            select(ConversationEvent).where(
                ConversationEvent.turn_id == ctx.turn_id,
                ConversationEvent.event_type == "pipeline.agent.question",
            )
        )
    )
    assert len(rows) == 1
    assert rows[0].payload["question"] == "选哪个？"


def test_concurrent_ask_user_only_one_enters_interrupt(session, monkeypatch):
    ctx = _context(session, FakePipelineClient(), uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    entered = threading.Event()
    release = threading.Event()
    calls: list[int] = []

    def paused_interrupt(_payload):
        calls.append(1)
        entered.set()
        release.wait(timeout=2)
        raise RuntimeError("interrupt paused")

    monkeypatch.setattr(binding, "interrupt", paused_interrupt)
    tool = _tools(ctx)["ask_user"]
    outcomes: list[object] = []

    def first() -> None:
        try:
            outcomes.append(tool.invoke({"question": "第一个问题"}))
        except Exception as exc:  # noqa: BLE001
            outcomes.append(exc)

    def second() -> None:
        outcomes.append(tool.invoke({"question": "第二个问题"}))

    first_thread = threading.Thread(target=first)
    first_thread.start()
    assert entered.wait(timeout=1)
    second_thread = threading.Thread(target=second)
    second_thread.start()
    time.sleep(0.05)
    release.set()
    first_thread.join(timeout=2)
    second_thread.join(timeout=2)

    assert len(calls) == 1
    assert any(
        isinstance(item, dict) and item.get("error") == "QUESTION_LIMIT_REACHED"
        for item in outcomes
    )


def test_load_error_blocks_every_domain_tool_but_not_ask_user(session, monkeypatch):
    ctx = _context(session, FakePipelineClient(), uuid.uuid4(), load_error="数据服务不可用")
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: "回答")
    tools = _tools(ctx)
    calls = {
        "get_pipeline_state": {},
        "get_pipeline_draft": {},
        "get_pipeline_check_result": {},
        "get_pipeline_events": {},
        "get_node_catalog": {},
        "list_pipeline_input_datasets": {},
        "get_dataset_schema": {"dataset_id": str(uuid.uuid4())},
        "get_output_sources": {"dataset_id": str(uuid.uuid4())},
        "add_pipeline_node": {"type": "filter", "name": "筛选"},
        "update_pipeline_node": {"node_id": str(uuid.uuid4())},
        "delete_pipeline_node": {"node_id": str(uuid.uuid4())},
        "connect_pipeline_nodes": {
            "source_node_id": str(uuid.uuid4()),
            "source_port": "out",
            "target_node_id": str(uuid.uuid4()),
            "target_port": "in",
        },
        "disconnect_pipeline_nodes": {"edge_id": str(uuid.uuid4())},
        "set_pipeline_output": {"node_id": str(uuid.uuid4()), "name": "输出"},
        "preview_pipeline_node": {"node_id": str(uuid.uuid4())},
        "build_pipeline": {},
    }

    for name, args in calls.items():
        assert tools[name].invoke(args)["error"] == "PIPELINE_STATE_UNAVAILABLE"
    assert tools["ask_user"].invoke({"question": "还在吗？"}) == "回答"
