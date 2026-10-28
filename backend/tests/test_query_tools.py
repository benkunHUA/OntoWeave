from __future__ import annotations

import uuid

from langchain_core.utils.function_calling import convert_to_openai_tool

from app.agent.tools.query.binding import build_query_instance_tools
from app.agent.tools.query.context import QueryToolContext
from app.services import embedding, graph, ontology
from tests.support.query_fixtures import FakeQueryClient, _object_with_id


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


def test_query_tools_expose_only_the_two_controlled_operations(session):
    owner = uuid.uuid4()
    tools = _tools(session, owner, FakeQueryClient())

    assert set(tools) == {"query_objects", "get_related_objects"}
    assert "graph_id" in tools["query_objects"].args
    assert "primary_keys" in tools["query_objects"].args
    assert "relation_id" in tools["get_related_objects"].args


def test_related_tool_schema_exposes_direction_enum(session):
    owner = uuid.uuid4()
    tools = _tools(session, owner, FakeQueryClient())
    schema = convert_to_openai_tool(tools["get_related_objects"])["function"]["parameters"]

    assert schema["properties"]["direction"]["enum"] == ["outgoing", "incoming"]


def test_query_objects_returns_batch_items_without_agent_state(session):
    owner = uuid.uuid4()
    obj, key_prop, title_prop = _object_with_id(session, owner, "客户")
    row = graph.create_graph(session, owner_id=owner, name="客户图")
    graph.add_object_to_graph(session, row.id, obj.id, owner_id=owner)
    client = FakeQueryClient()
    client.objects[obj.id] = {
        "index_version_id": uuid.uuid4(),
        "total": 1,
        "truncated": False,
        "items": [
            {
                "object_type_id": str(obj.id),
                "primary_key": "c-1",
                "title": "客户 1",
                "properties": [
                    {"attribute_id": str(key_prop.id), "value": "c-1"},
                    {"attribute_id": str(title_prop.id), "value": "客户 1"},
                ],
            }
        ],
    }
    tools = _tools(session, owner, client)

    result = tools["query_objects"].func(
        graph_id=str(row.id),
        object_type_id=str(obj.id),
        primary_keys=["c-1"],
    )

    assert result["status"] == "OK"
    assert result["items"][0]["primary_key"] == "c-1"
    assert client.last_list_args["primary_keys"] == ["c-1"]


def test_query_objects_rejects_type_outside_selected_graph(session):
    owner = uuid.uuid4()
    obj, _, _ = _object_with_id(session, owner, "客户")
    row = graph.create_graph(session, owner_id=owner, name="客户图")
    tools = _tools(session, owner, FakeQueryClient())

    result = tools["query_objects"].func(
        graph_id=str(row.id),
        object_type_id=str(obj.id),
    )

    assert result["error"] == "InvalidRequestError"
    assert "not in the selected graph" in result["message"]


def test_related_objects_rejects_unknown_graph_relation(session):
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
    tools = _tools(session, owner, FakeQueryClient())

    result = tools["get_related_objects"].func(
        graph_id=str(row.id),
        relation_id=str(relation.id),
        direction="outgoing",
        inputs=[{"object_type_id": str(source.id), "primary_key": "c-1"}],
    )

    assert result["error"] == "InvalidRequestError"
    assert "not in the selected graph" in result["message"]
