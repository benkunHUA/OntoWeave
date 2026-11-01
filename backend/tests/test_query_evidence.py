from __future__ import annotations

import json
from types import SimpleNamespace

from langchain_core.messages import ToolMessage
from langgraph.types import Command

from app.agent.evidence import (
    QueryEvidenceMiddleware,
    build_evidence_graph,
    evidence_delta_from_tool,
)


def _delta(name: str, args: dict, payload: dict, turn_id: str = "t1") -> dict | None:
    return evidence_delta_from_tool(
        name,
        args,
        json.dumps(payload),
        turn_id=turn_id,
    )


def test_instance_graph_uses_business_relations_and_exact_instance_pairs():
    deltas = [
        _delta(
            "get_graph_detail",
            {"graph_id": "g1"},
            {
                "graph_id": "g1",
                "name": "燃气业务图",
                "object_types": [
                    {"id": "owner", "name": "业主", "properties": []},
                    {"id": "work_order", "name": "工单", "properties": []},
                    {"id": "repairer", "name": "维修工", "properties": []},
                ],
                "relations": [
                    {
                        "id": "create",
                        "name": "创建",
                        "source_type_id": "owner",
                        "target_type_id": "work_order",
                        "cardinality": "one_to_many",
                        "forward_label": "创建",
                    },
                    {
                        "id": "handle",
                        "name": "负责",
                        "source_type_id": "repairer",
                        "target_type_id": "work_order",
                        "cardinality": "many_to_many",
                        "forward_label": "负责",
                    },
                ],
            },
        ),
        _delta(
            "query_objects",
            {"object_type_id": "owner"},
            {
                "object_type_id": "owner",
                "status": "OK",
                "items": [{"primary_key": "O-1", "title": "王建国"}],
            },
        ),
        _delta(
            "get_related_objects",
            {
                "relation_id": "create",
                "direction": "outgoing",
                "inputs": [{"object_type_id": "owner", "primary_key": "O-1"}],
            },
            {
                "relation_id": "create",
                "target_type_id": "work_order",
                "status": "OK",
                "items": [
                    {"primary_key": "W-1", "title": "维修工单 1"},
                    {"primary_key": "W-2", "title": "维修工单 2"},
                ],
                "links": [
                    {
                        "sourceTypeId": "owner",
                        "sourcePrimaryKey": "O-1",
                        "targetTypeId": "work_order",
                        "targetPrimaryKey": "W-1",
                    },
                    {
                        "sourceTypeId": "owner",
                        "sourcePrimaryKey": "O-1",
                        "targetTypeId": "work_order",
                        "targetPrimaryKey": "W-2",
                    },
                ],
            },
        ),
        _delta(
            "get_related_objects",
            {
                "relation_id": "handle",
                "direction": "incoming",
                "inputs": [
                    {"object_type_id": "work_order", "primary_key": "W-1"},
                    {"object_type_id": "work_order", "primary_key": "W-2"},
                ],
            },
            {
                "relation_id": "handle",
                "target_type_id": "repairer",
                "status": "OK",
                "items": [
                    {"primary_key": "R-1", "title": "张师傅"},
                    {"primary_key": "R-2", "title": "李师傅"},
                ],
                "links": [
                    {
                        "sourceTypeId": "repairer",
                        "sourcePrimaryKey": "R-1",
                        "targetTypeId": "work_order",
                        "targetPrimaryKey": "W-1",
                    },
                    {
                        "sourceTypeId": "repairer",
                        "sourcePrimaryKey": "R-2",
                        "targetTypeId": "work_order",
                        "targetPrimaryKey": "W-2",
                    },
                ],
            },
        ),
    ]

    graph = build_evidence_graph([item for item in deltas if item], turn_id="t1")

    assert graph is not None
    assert graph.graphName == "燃气业务图"
    assert {node.id for node in graph.nodes} == {
        "owner:O-1",
        "work_order:W-1",
        "work_order:W-2",
        "repairer:R-1",
        "repairer:R-2",
    }
    assert {(edge.source, edge.target, edge.label) for edge in graph.edges} == {
        ("owner:O-1", "work_order:W-1", "创建"),
        ("owner:O-1", "work_order:W-2", "创建"),
        ("repairer:R-1", "work_order:W-1", "负责"),
        ("repairer:R-2", "work_order:W-2", "负责"),
    }


def test_evidence_is_scoped_to_the_current_turn():
    old = _delta(
        "query_objects",
        {"object_type_id": "owner"},
        {"object_type_id": "owner", "items": [{"primary_key": "OLD"}]},
        turn_id="old",
    )
    current = _delta(
        "query_objects",
        {"object_type_id": "owner"},
        {"object_type_id": "owner", "items": [{"primary_key": "NEW"}]},
        turn_id="new",
    )

    graph = build_evidence_graph([old, current], turn_id="new")

    assert graph is not None
    assert [node.primaryKey for node in graph.nodes] == ["NEW"]


def test_unindexed_relation_is_drawn_from_business_graph_schema():
    deltas = [
        _delta(
            "get_graph_detail",
            {"graph_id": "g1"},
            {
                "graph_id": "g1",
                "name": "安检模型",
                "object_types": [
                    {"id": "work_order", "name": "工单", "properties": []},
                    {"id": "repairer", "name": "维修工", "properties": []},
                ],
                "relations": [
                    {
                        "id": "handle",
                        "name": "负责",
                        "source_type_id": "repairer",
                        "target_type_id": "work_order",
                        "cardinality": "many_to_many",
                        "forward_label": "负责",
                    }
                ],
            },
        ),
        _delta(
            "query_objects",
            {"object_type_id": "work_order"},
            {
                "object_type_id": "work_order",
                "items": [
                    {"primary_key": "W-1", "properties": {"handler": "R-1"}},
                    {"primary_key": "W-2", "properties": {"handler": "R-2"}},
                ],
            },
        ),
        _delta(
            "query_objects",
            {"object_type_id": "repairer"},
            {
                "object_type_id": "repairer",
                "items": [{"primary_key": "R-1"}, {"primary_key": "R-2"}],
            },
        ),
        _delta(
            "get_related_objects",
            {
                "relation_id": "handle",
                "direction": "incoming",
                "inputs": [
                    {"object_type_id": "work_order", "primary_key": "W-1"},
                    {"object_type_id": "work_order", "primary_key": "W-2"},
                ],
            },
            {
                "relation_id": "handle",
                "target_type_id": "repairer",
                "status": "NOT_INDEXED",
                "items": [],
            },
        ),
    ]

    graph = build_evidence_graph([item for item in deltas if item], turn_id="t1")

    assert graph is not None
    assert {(edge.source, edge.target, edge.label) for edge in graph.edges} == {
        ("repairer:R-1", "work_order:W-1", "负责"),
        ("repairer:R-2", "work_order:W-2", "负责"),
    }
    assert "relation_not_indexed" not in graph.warnings


def test_middleware_writes_full_tool_result_to_state():
    middleware = QueryEvidenceMiddleware()
    result = ToolMessage(
        content=json.dumps(
            {
                "object_type_id": "owner",
                "items": [{"primary_key": "O-1", "title": "王建国"}],
            }
        ),
        tool_call_id="c1",
    )
    request = SimpleNamespace(
        tool_call={"name": "query_objects", "args": {"object_type_id": "owner"}},
        runtime=SimpleNamespace(config={"configurable": {"turn_id": "t1"}}),
    )

    command = middleware.wrap_tool_call(request, lambda _: result)

    assert isinstance(command, Command)
    assert command.update["messages"] == [result]
    assert command.update["evidence"][0]["items"][0]["primaryKey"] == "O-1"
