"""`test_data_pipeline_agent.py` 中 writes 相关测试。"""

import uuid

import pytest

from app.clients.data_service.client import RevisionConflict
from tests.support.data_pipeline_agent_fixtures import _context, _tools
from tests.support.pipeline_test_fakes import FakePipelineClient


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
        {"type": "filter", "name": "筛选", "node_config": {"condition": "id > 0"}}
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
    assert "position" not in commands[0]
    assert commands[0]["config"] == {"condition": "id > 0"}
    assert all(item["turnId"] == str(turn_id) for item in commands)
    operation_ids = {item["operationId"] for item in commands}
    assert len(operation_ids) == 6
    assert ctx.draft_revision == 9
    assert ctx.last_check_result["revision"] == 9
    checks = [kwargs for name, kwargs in client.calls if name == "check"]
    assert [item["revision"] for item in checks] == [4, 5, 6, 7, 8, 9]


def test_write_tools_parse_json_string_config(session):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())
    tools = _tools(ctx)
    node_id = str(uuid.uuid4())
    dataset_id = str(uuid.uuid4())

    result = tools["update_pipeline_node"].invoke(
        {
            "node_id": node_id,
            "node_config": f'{{"datasetKind":"sync","datasetId":"{dataset_id}"}}',
        }
    )

    assert result["revision"] == 4
    command = next(kwargs["payload"] for name, kwargs in client.calls if name == "command")
    assert command["config"] == {"datasetKind": "sync", "datasetId": dataset_id}


def test_write_tools_reject_invalid_json_string_config(session):
    client = FakePipelineClient()
    ctx = _context(session, client, uuid.uuid4())

    result = _tools(ctx)["update_pipeline_node"].invoke(
        {"node_id": str(uuid.uuid4()), "node_config": "{bad json"}
    )

    assert result["error"] == "INVALID_ARGUMENT"
    assert not [name for name, _ in client.calls if name == "command"]


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
    ("tool_name", "client_method", "args"),
    [
        ("get_pipeline_check_result", "check_pipeline", {}),
        (
            "preview_pipeline_node",
            "preview_node",
            {"node_id": str(uuid.uuid4())},
        ),
        ("build_pipeline", "create_build", {}),
    ],
)
def test_action_tools_normalize_revision_conflict(
    session, monkeypatch, tool_name, client_method, args
):
    client = FakePipelineClient()
    attempts: list[int] = []

    def conflict(*_args, **_kwargs):
        attempts.append(1)
        raise RevisionConflict("draft revision is stale")

    monkeypatch.setattr(client, client_method, conflict)
    ctx = _context(session, client, uuid.uuid4())
    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(binding, "interrupt", lambda payload: True)
    result = _tools(ctx)[tool_name].invoke(args)

    assert result["error"] == "REVISION_CONFLICT"
    assert "stale" in result["message"]
    assert len(attempts) == 1


def test_write_check_conflict_is_normalized(session, monkeypatch):
    client = FakePipelineClient()

    def apply_command(pipeline_id, *, owner_id, payload):  # noqa: ARG001
        return {"pipelineId": str(pipeline_id), "revision": 9, "definition": {}}

    def conflict(*_args, **_kwargs):
        raise RevisionConflict("checked revision is stale")

    monkeypatch.setattr(client, "apply_draft_command", apply_command)
    monkeypatch.setattr(client, "check_pipeline", conflict)
    ctx = _context(session, client, uuid.uuid4())

    result = _tools(ctx)["add_pipeline_node"].invoke({"type": "filter", "name": "筛选"})

    assert result == {"error": "REVISION_CONFLICT", "message": "checked revision is stale"}
    assert ctx.draft_revision == 9


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
    payload = next(kwargs["payload"] for name, kwargs in client.calls if name == "preview")
    assert payload["rowLimit"] == 5
    assert "build" not in [name for name, _ in client.calls]
