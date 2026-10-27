"""`test_data_pipeline_agent.py` 中 reads 相关测试。"""

import uuid

from app.services.conversation import event as conversation_event
from tests.support.data_pipeline_agent_fixtures import _context, _tools
from tests.support.pipeline_test_fakes import FakePipelineClient


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
    for name in ("add_pipeline_node", "update_pipeline_node"):
        assert "node_config" in tools[name].args
        assert "config" not in tools[name].args


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
