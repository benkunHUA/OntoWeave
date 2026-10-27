from __future__ import annotations

import uuid

from langchain_core.utils.function_calling import convert_to_openai_tool
from langgraph.prebuilt.tool_node import ToolRuntime

from app.agent.tools.query.binding import build_query_instance_tools
from app.agent.tools.query.context import QueryToolContext
from app.services import embedding, graph, ontology
from tests.test_query_services import FakeQueryClient, _object_with_id


def _tools(session, owner, client):
    return {
        item.name: item
        for item in build_query_instance_tools(
            QueryToolContext(
                session=session,
                owner_id=owner,
                client=client,
                embedder=embedding.HashingEmbedder(),
            )
        )
    }


def _runtime(state, call_id="call-1"):
    return ToolRuntime(
        state=state,
        context=None,
        config={},
        stream_writer=lambda _: None,
        tool_call_id=call_id,
        store=None,
    )


def test_query_tools_expose_only_the_two_controlled_operations(session):
    owner = uuid.uuid4()
    tools = _tools(session, owner, FakeQueryClient())

    assert set(tools) == {"query_objects", "execute_query_step"}
    assert "object_type_id" in tools["query_objects"].args
    assert "relation_id" in tools["execute_query_step"].args


def test_execute_query_step_schema_exposes_allowed_enums(session):
    owner = uuid.uuid4()
    tools = _tools(session, owner, FakeQueryClient())
    schema = convert_to_openai_tool(tools["execute_query_step"])["function"]["parameters"]
    properties = schema["properties"]

    assert properties["direction"]["enum"] == ["outgoing", "incoming"]
    assert properties["expected_cardinality"]["anyOf"][0]["enum"] == ["one", "many"]


def test_query_objects_assigns_step_ids_and_keeps_state(session):
    owner = uuid.uuid4()
    obj, key_prop, title_prop = _object_with_id(session, owner, "客户")
    row = graph.create_graph(session, owner_id=owner, name="客户图")
    graph.add_object_to_graph(session, row.id, obj.id, owner_id=owner)
    client = FakeQueryClient()
    version_id = uuid.uuid4()
    client.details[(obj.id, "c-1")] = {
        "primary_key": "c-1",
        "title": "客户 1",
        "index_version_id": version_id,
        "properties": {str(key_prop.id): "c-1", str(title_prop.id): "客户 1"},
    }
    tools = _tools(session, owner, client)

    first = tools["query_objects"].func(
        _runtime({"graph_id": str(row.id)}),
        object_type_id=str(obj.id),
        primary_key="c-1",
    )

    assert first.update["steps"]["s0"][0]["primary_key"] == "c-1"
    assert first.update["anchor_refs"][0]["primary_key"] == "c-1"
    assert first.update["step_counter"] == 0

    second = tools["query_objects"].func(
        _runtime({**first.update, "graph_id": str(row.id)}, call_id="call-2"),
        object_type_id=str(obj.id),
        primary_key="c-1",
    )
    assert "s1" in second.update["steps"]
    assert second.update["step_counter"] == 1


def test_execute_query_step_rejects_unknown_input_step_as_structured_error(session):
    owner = uuid.uuid4()
    source, _, _ = _object_with_id(session, owner, "客户")
    target, _, _ = _object_with_id(session, owner, "订单")
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
    )
    row = graph.create_graph(session, owner_id=owner, name="客户图")
    for item in (source, target):
        graph.add_object_to_graph(session, row.id, item.id, owner_id=owner)
    graph.add_relation_to_graph(session, row.id, relation.id, owner_id=owner)
    tools = _tools(session, owner, FakeQueryClient())

    result = tools["execute_query_step"].func(
        _runtime({"graph_id": str(row.id)}),
        input_step_id="missing",
        relation_id=str(relation.id),
        direction="forward",
        expected_cardinality="one_to_many",
    )

    assert result.update["messages"][0].status == "error"
    assert "unknown input step" in result.update["messages"][0].content
