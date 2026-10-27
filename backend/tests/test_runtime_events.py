import json

from app.agent.runtime.events import PROTOCOL_VERSION, TOOL_RESULT_LIMIT, TurnBuilder, sse
from app.schemas.agent_events import RunEndEvent


def test_sse_frames_an_event_with_the_protocol_version():
    frame = sse(RunEndEvent(messageId="m1", paused=False))

    assert frame.startswith("data: ")
    assert frame.endswith("\n\n")
    assert json.loads(frame[len("data: ") :]) == {
        "v": PROTOCOL_VERSION,
        "type": "run.end",
        "messageId": "m1",
        "paused": False,
    }


def test_blocks_follow_the_order_the_model_emits_them():
    turn = TurnBuilder("m1")
    turn.reasoning("想一下")
    turn.reasoning("再想一下")
    turn.text("好的")
    turn.tool_start(call_id="c1", name="list_types", args={})
    turn.tool_end(call_id="c1", status="ok", result="[]", duration_ms=3)
    turn.text("完成")
    turn.finish()

    assert [block.kind for block in turn.blocks] == ["reasoning", "text", "tool", "text"]
    assert turn.blocks[0].text == "想一下再想一下"
    assert turn.blocks[0].done is True
    assert turn.blocks[2].status == "ok"
    assert turn.text_content == "好的完成"


def test_payload_is_the_wire_shape_the_frontend_reads_back():
    turn = TurnBuilder("m1")
    turn.text("正文")
    turn.finish()

    assert turn.payload() == [{"kind": "text", "id": turn.blocks[0].id, "text": "正文"}]


def test_switching_block_kind_closes_the_previous_one():
    turn = TurnBuilder("m1")
    turn.reasoning("推理")
    events = turn.text("正文")

    assert [event.type for event in events] == ["block.end", "block.start", "block.delta"]
    assert events[1].kind == "text"


def test_tool_result_is_truncated_before_it_reaches_storage():
    turn = TurnBuilder("m1")
    turn.tool_start(call_id="c1", name="list_types", args={})
    events = turn.tool_end(
        call_id="c1", status="ok", result="x" * (TOOL_RESULT_LIMIT + 10), duration_ms=1
    )

    assert events[0].truncated is True
    assert len(events[0].result) == TOOL_RESULT_LIMIT
    assert turn.blocks[0].truncated is True


def test_tool_result_without_a_started_block_is_left_for_the_next_turn():
    """中断那轮起了块却没等到结果，结果在下一轮才回来。"""
    turn = TurnBuilder("m1")
    turn.tool_end(call_id="c1", status="ok", result="[]", duration_ms=0)

    assert turn.blocks == []
    assert turn.orphan_tools == {"c1": {"status": "ok", "result": "[]", "durationMs": None}}


def test_tool_start_keeps_the_human_summary_with_the_machine_args():
    turn = TurnBuilder("m1")
    events = turn.tool_start(
        call_id="c1",
        name="query_objects",
        args={"object_type_id": "type-1"},
        summary="客户 · 搜索「张三」",
    )

    assert events[0].summary == "客户 · 搜索「张三」"
    assert turn.payload()[0]["summary"] == "客户 · 搜索「张三」"
