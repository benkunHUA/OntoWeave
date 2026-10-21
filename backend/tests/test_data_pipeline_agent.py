import uuid

import pytest

from app.agent.tools.data_pipeline import (
    DataPipelineToolContext,
    build_data_pipeline_tools,
)
from app.services import conversation, conversation_event
from app.services.data_service.client import RevisionConflict
from app.vocabulary import ConversationKind
from tests.pipeline_test_fakes import PIPELINE_ID, FakePipelineClient


def _conversation(session, owner, pipeline_id=PIPELINE_ID):
    return conversation.create_conversation(
        session,
        owner_id=owner,
        title="订单清洗",
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=pipeline_id,
    )


def _context(session, client, owner, *, turn_id=None, load_error=None):
    conv = _conversation(session, owner)
    return DataPipelineToolContext(
        session=session,
        owner_id=owner,
        conversation_id=conv.id,
        pipeline_id=conv.resource_id,
        client=client,
        draft_revision=3,
        turn_id=turn_id or uuid.uuid4(),
        last_check_result=None,
        load_error=load_error,
    )


def _tools(ctx):
    return {item.name: item for item in build_data_pipeline_tools(ctx)}


def test_data_pipeline_tools_include_exact_order(session):
    ctx = _context(session, FakePipelineClient(), uuid.uuid4())
    tools = _tools(ctx)
    assert list(tools) == [
        "get_pipeline_state",
        "get_pipeline_draft",
        "get_pipeline_check_result",
        "get_pipeline_events",
        "get_node_catalog",
        "list_pipeline_input_datasets",
        "get_dataset_schema",
        "get_output_sources",
        "add_pipeline_node",
        "update_pipeline_node",
        "delete_pipeline_node",
        "connect_pipeline_nodes",
        "disconnect_pipeline_nodes",
        "set_pipeline_output",
        "preview_pipeline_node",
        "build_pipeline",
        "ask_user",
    ]
    for name in ("delete_pipeline_node", "set_pipeline_output", "build_pipeline"):
        assert "confirmed" not in tools[name].args


def test_read_only_tools_map_java_payloads(session):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())
    conversation_event.append_event(
        ctx.session,
        conversation_id=ctx.conversation_id,
        event_id=uuid.uuid4(),
        event_type="pipeline.draft.changed",
        summary="草稿已修改",
        payload={},
    )
    tools = _tools(ctx)

    assert tools["get_pipeline_state"].invoke({})["name"] == "订单清洗"
    assert tools["get_pipeline_draft"].invoke({})["revision"] == 3
    assert tools["get_pipeline_check_result"].invoke({})["stage"] == "draft"
    assert tools["get_pipeline_events"].invoke({"limit": 5})[0]["summary"] == "草稿已修改"
    assert tools["get_node_catalog"].invoke({})["catalog_version"] == "v1"

    datasets = tools["list_pipeline_input_datasets"].invoke({"limit": 10, "offset": 0})
    assert datasets["items"][0]["source_table"] == "orders"
    schema = tools["get_dataset_schema"].invoke({"dataset_id": str(uuid.uuid4())})
    assert schema["column_count"] == 2
    sources = tools["get_output_sources"].invoke({"dataset_id": str(uuid.uuid4())})
    assert sources["inputs"][0]["source_table_name"] == "orders"


def test_write_tools_send_current_revision_turn_and_unique_operation(session, monkeypatch):
    client = FakePipelineClient()
    revisions = iter([4, 5, 6, 7, 8, 9])

    def apply_command(pipeline_id, *, owner_id, payload):
        client._record("command", pipeline_id=pipeline_id, owner_id=owner_id, payload=payload)
        return {
            "pipelineId": str(pipeline_id),
            "revision": next(revisions),
            "definition": {"nodes": [], "edges": []},
        }

    monkeypatch.setattr(client, "apply_draft_command", apply_command)
    owner = uuid.uuid4()
    turn_id = uuid.uuid4()
    ctx = _context(session, client, owner, turn_id=turn_id)
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(
        binding, "interrupt", lambda payload: {"confirmed": True, "payload": payload}
    )
    tools = _tools(ctx)
    node_id = str(uuid.uuid4())
    edge_id = str(uuid.uuid4())

    tools["add_pipeline_node"].invoke(
        {"type": "filter", "name": "筛选", "config": {"condition": "id > 0"}}
    )
    tools["update_pipeline_node"].invoke({"node_id": node_id, "name": "重命名"})
    tools["connect_pipeline_nodes"].invoke(
        {
            "source_node_id": node_id,
            "source_port": "out",
            "target_node_id": str(uuid.uuid4()),
            "target_port": "in",
        }
    )
    tools["disconnect_pipeline_nodes"].invoke({"edge_id": edge_id})
    tools["delete_pipeline_node"].invoke({"node_id": node_id})
    tools["set_pipeline_output"].invoke(
        {
            "node_id": node_id,
            "name": "订单输出",
            "primary_key_columns": ["id"],
            "expectations": [{"type": "NOT_NULL", "column": "id"}],
        }
    )

    commands = [kwargs["payload"] for name, kwargs in client.calls if name == "command"]
    assert [item["type"] for item in commands] == [
        "add_node",
        "update_node",
        "connect",
        "disconnect",
        "delete_node",
        "set_output",
    ]
    assert [item["baseRevision"] for item in commands] == [3, 4, 5, 6, 7, 8]
    assert all(item["turnId"] == str(turn_id) for item in commands)
    operation_ids = {item["operationId"] for item in commands}
    assert len(operation_ids) == 6
    assert ctx.draft_revision == 9
    assert ctx.last_check_result["revision"] == 9
    checks = [kwargs for name, kwargs in client.calls if name == "check"]
    assert [item["revision"] for item in checks] == [4, 5, 6, 7, 8, 9]


def test_write_tool_returns_structured_revision_conflict(session, monkeypatch):
    client = FakePipelineClient()

    def conflict(*_args, **_kwargs):
        raise RevisionConflict("draft revision is stale")

    monkeypatch.setattr(client, "apply_draft_command", conflict)
    ctx = _context(session, client, uuid.uuid4())
    tools = _tools(ctx)

    result = tools["add_pipeline_node"].invoke({"type": "filter", "name": "筛选"})
    assert result["error"] == "REVISION_CONFLICT"
    assert "stale" in result["message"]
    assert ctx.draft_revision == 3


@pytest.mark.parametrize(
    ("tool_name", "args"),
    [
        ("delete_pipeline_node", {"node_id": str(uuid.uuid4())}),
        (
            "set_pipeline_output",
            {"node_id": str(uuid.uuid4()), "name": "输出"},
        ),
        ("build_pipeline", {}),
    ],
)
def test_rejected_confirmation_does_not_call_java(session, monkeypatch, tool_name, args):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(
        binding, "interrupt", lambda payload: {"confirmed": False, "payload": payload}
    )
    result = _tools(ctx)[tool_name].invoke(args)

    assert result["interrupted"] is True
    assert not [name for name, _ in client.calls if name in {"command", "build"}]


@pytest.mark.parametrize(
    ("tool_name", "args", "expected_call"),
    [
        ("delete_pipeline_node", {"node_id": str(uuid.uuid4())}, "command"),
        (
            "set_pipeline_output",
            {"node_id": str(uuid.uuid4()), "name": "输出"},
            "command",
        ),
        ("build_pipeline", {}, "build"),
    ],
)
def test_confirmed_tool_calls_java(session, monkeypatch, tool_name, args, expected_call):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(
        binding, "interrupt", lambda payload: {"confirmed": True, "payload": payload}
    )
    _tools(ctx)[tool_name].invoke(args)

    assert [name for name, _ in client.calls if name == expected_call]


def test_build_payload_uses_current_revision_and_operation(session, monkeypatch):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: True)
    _tools(ctx)["build_pipeline"].invoke({})

    payload = next(kwargs["payload"] for name, kwargs in client.calls if name == "build")
    assert payload["draftRevision"] == 3
    assert uuid.UUID(payload["operationId"])
    assert payload["turnId"] == str(ctx.turn_id)


def test_preview_tool_uses_preview_node_without_building(session):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())
    node_id = str(uuid.uuid4())

    result = _tools(ctx)["preview_pipeline_node"].invoke({"node_id": node_id})

    assert result["node_id"] == node_id
    assert result["draft_revision"] == 3
    assert "build" not in [name for name, _ in client.calls]


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
