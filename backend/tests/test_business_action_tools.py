from __future__ import annotations

import uuid

from langchain_core.utils.function_calling import convert_to_openai_tool

from app.agent.tools.business.action import build_action_tools
from app.agent.tools.business.context import BusinessToolContext
from app.services import action, embedding
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action
from tests.support.query_fixtures import FakeQueryClient


def _tools(session, owner):
    return {
        item.name: item
        for item in build_action_tools(
            BusinessToolContext(
                session=session,
                owner_id=owner,
                client=FakeQueryClient(),
                embedder=embedding.HashingEmbedder(),
            )
        )
    }


def test_get_action_detail_tool_schema(session):
    ids = graph_with_close_ticket_action(session)
    tools = _tools(session, ids["owner"])

    assert set(tools) == {"get_action_detail", "execute_action"}
    schema = convert_to_openai_tool(tools["get_action_detail"])["function"]["parameters"]
    assert set(schema["properties"]) == {"graph_id", "action_type_id"}


def test_get_action_detail_returns_full_definition_in_graph(session):
    ids = graph_with_close_ticket_action(session)
    tools = _tools(session, ids["owner"])

    result = tools["get_action_detail"].func(
        graph_id=str(ids["graph_id"]), action_type_id=str(ids["action_id"])
    )

    assert result["name"] == "关闭工单"
    assert result["parameters"][0]["name"] == "工单"
    modify = next(rule for rule in result["rules"] if rule["rule_kind"] == "modify_object")
    assert modify["assignments"][0]["target_property_name"] == "解决说明"
    assert result["criteria"][0]["children"][0]["text"] == "工单.状态 是 处理中"


def test_get_action_detail_rejects_action_outside_graph(session):
    ids = graph_with_close_ticket_action(session)
    outside = action.create_action_type(session, owner_id=ids["owner"], name="图外动作")
    tools = _tools(session, ids["owner"])

    result = tools["get_action_detail"].func(
        graph_id=str(ids["graph_id"]), action_type_id=str(outside.id)
    )

    assert result == {
        "error": "ACTION_NOT_IN_GRAPH",
        "message": "action is not in the selected graph",
    }


def test_get_action_detail_hides_another_owners_action(session):
    ids = graph_with_close_ticket_action(session)
    tools = _tools(session, uuid.uuid4())

    result = tools["get_action_detail"].func(
        graph_id=str(ids["graph_id"]), action_type_id=str(ids["action_id"])
    )

    assert result["error"] == "NotFoundError"
