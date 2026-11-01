from __future__ import annotations

import uuid
from typing import Any

import pytest
from langchain_core.utils.function_calling import convert_to_openai_tool
from langgraph.errors import GraphBubbleUp
from sqlalchemy.orm import Session

from app.agent.tools.business.action import build_action_tools
from app.agent.tools.business.context import BusinessToolContext
from app.services import embedding
from app.services.action import execution
from app.services.graph import publication as graph_publication
from tests.support.action_execution_fixtures import CloseTicketEditClient
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


def _setup(
    session: Session, *, include_current_time: bool = False
) -> tuple[dict[str, uuid.UUID], BusinessToolContext, CloseTicketEditClient, dict[str, Any]]:
    ids = graph_with_close_ticket_action(session, include_current_time=include_current_time)
    graph_publication.publish_graph(
        session,
        graph_id=ids["graph_id"],
        owner_id=ids["owner"],
        embedder=embedding.HashingEmbedder(),
    )
    conversation_id = uuid.uuid4()
    context = execution.load_action_context(
        session,
        graph_id=ids["graph_id"],
        action_type_id=ids["action_id"],
        owner_id=ids["owner"],
        conversation_id=conversation_id,
    )
    client = CloseTicketEditClient(ids)
    client.objects[(ids["ticket_id"], "T-1")] = {
        "properties": {str(ids["ticket_status_id"]): "处理中"}
    }
    client.objects[(ids["part_id"], "P-1")] = {"properties": {}}
    tool_context = BusinessToolContext(
        session=session,
        owner_id=ids["owner"],
        conversation_id=conversation_id,
        client=client,
        embedder=embedding.HashingEmbedder(),
    )
    tools = {item.name: item for item in build_action_tools(tool_context)}
    parameter_ids = {item.name: item.id for item in context.detail.parameters}
    parameters = {
        str(parameter_ids["工单"]): {
            "object_type_id": str(ids["ticket_id"]),
            "primary_key": "T-1",
        },
        str(parameter_ids["配件"]): {
            "object_type_id": str(ids["part_id"]),
            "primary_key": "P-1",
        },
        str(parameter_ids["解决说明"]): "已修复",
    }
    return ids, tool_context, client, {"tools": tools, "parameters": parameters}


def _execute(ids: dict[str, uuid.UUID], fixture: dict[str, Any], *, call_id: str = "call-1"):
    return fixture["tools"]["execute_action"].func(
        graph_id=str(ids["graph_id"]),
        action_type_id=str(ids["action_id"]),
        parameters=fixture["parameters"],
        tool_call_id=call_id,
    )


def _approve(payload: dict[str, Any]) -> dict[str, Any]:
    return payload["confirmation"]["options"][0]["answer"]


def test_execute_action_schema_hides_injected_tool_call_id(session: Session):
    _, _, _, fixture = _setup(session)

    schema = convert_to_openai_tool(fixture["tools"]["execute_action"])["function"]["parameters"]

    assert set(schema["properties"]) == {"graph_id", "action_type_id", "parameters"}
    assert set(schema["required"]) == {"graph_id", "action_type_id"}


def test_first_interrupt_does_not_apply(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)
    payloads: list[dict[str, Any]] = []

    def suspend(payload: dict[str, Any]) -> Any:
        payloads.append(payload)
        raise GraphBubbleUp()

    monkeypatch.setattr("app.agent.tools.business.action._interrupt", suspend)

    with pytest.raises(GraphBubbleUp):
        _execute(ids, fixture)

    assert len(payloads) == 1
    assert payloads[0]["confirmation"]["kind"] == "action_confirmation"
    assert len(client.preview_requests) == 1
    assert client.apply_requests == []


def test_confirm_repreviews_and_applies_with_stable_identity(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)
    monkeypatch.setattr(
        "app.agent.tools.business.action._interrupt",
        _approve,
    )

    result = _execute(ids, fixture)

    assert result["status"] == "APPLIED"
    assert len(client.preview_requests) == 2
    first, second = client.preview_requests
    assert first["execution_id"] == second["execution_id"]
    assert first["idempotency_key"] == second["idempotency_key"]
    assert second["expected"]["objects"] == [
        {
            "object_type_id": str(ids["ticket_id"]),
            "primary_key": "T-1",
            "version": 4,
        }
    ]
    assert len(client.apply_requests) == 1
    assert client.apply_requests[0]["execution_id"] == first["execution_id"]
    assert result["execution_id"] == str(first["execution_id"])
    readback = result["readback"]["objects"][0]
    assert readback["title"] == "打印机故障"
    resolution = next(item for item in readback["fields"] if item["name"] == "解决说明")
    assert resolution["value"] == "已修复"


def test_reject_cancels_without_apply(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)
    monkeypatch.setattr(
        "app.agent.tools.business.action._interrupt",
        lambda payload: {"decision": "reject"},
    )

    result = _execute(ids, fixture)

    assert result["status"] == "CANCELLED"
    assert len(client.preview_requests) == 1
    assert client.apply_requests == []


def test_preview_change_after_confirmation_returns_stale(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)

    def approve_and_change(payload: dict[str, Any]) -> Any:
        answer = _approve(payload)
        client.version = 5
        return answer

    monkeypatch.setattr("app.agent.tools.business.action._interrupt", approve_and_change)

    result = _execute(ids, fixture)

    assert result["error"] == "PREVIEW_STALE"
    assert len(client.preview_requests) == 2
    assert client.apply_requests == []


def test_resume_compares_against_the_preview_the_user_confirmed(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)
    answers: list[dict[str, Any]] = []

    def suspend(payload: dict[str, Any]) -> Any:
        answers.append(_approve(payload))
        raise GraphBubbleUp()

    monkeypatch.setattr("app.agent.tools.business.action._interrupt", suspend)
    with pytest.raises(GraphBubbleUp):
        _execute(ids, fixture)

    client.version = 5
    monkeypatch.setattr(
        "app.agent.tools.business.action._interrupt",
        lambda payload: answers[0],
    )
    result = _execute(ids, fixture)

    assert result["error"] == "PREVIEW_STALE"
    assert client.apply_requests == []


def test_resume_reuses_current_time_from_the_confirmed_preview(
    session: Session, monkeypatch: pytest.MonkeyPatch
):
    ids, _, client, fixture = _setup(session, include_current_time=True)
    times = iter(["2026-11-01T08:00:00Z", "2026-11-01T09:00:00Z"])
    monkeypatch.setattr(
        "app.services.action.execution.planner_values.current_time_value",
        lambda datatype: next(times),
    )
    answers: list[dict[str, Any]] = []

    def suspend(payload: dict[str, Any]) -> Any:
        answers.append(_approve(payload))
        raise GraphBubbleUp()

    monkeypatch.setattr("app.agent.tools.business.action._interrupt", suspend)
    with pytest.raises(GraphBubbleUp):
        _execute(ids, fixture)

    assert list(answers[0]["generated_values"].values()) == ["2026-11-01T08:00:00Z"]

    monkeypatch.setattr(
        "app.agent.tools.business.action._interrupt",
        lambda payload: answers[0],
    )
    result = _execute(ids, fixture)

    assert result["status"] == "APPLIED"
    applied_update = client.apply_requests[0]["object_updates"][0]
    assert applied_update["set"][ids["closed_at_id"]] == "2026-11-01T08:00:00Z"


def test_apply_retry_reuses_the_same_idempotency_key(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)
    client.apply_failures = 1
    monkeypatch.setattr(
        "app.agent.tools.business.action._interrupt",
        _approve,
    )

    result = _execute(ids, fixture)

    assert result["status"] == "APPLIED"
    assert len(client.apply_requests) == 2
    assert client.apply_requests[0] == client.apply_requests[1]


def test_apply_retry_exhausted_returns_uncertain(session: Session, monkeypatch):
    ids, _, client, fixture = _setup(session)
    client.apply_failures = 2
    monkeypatch.setattr(
        "app.agent.tools.business.action._interrupt",
        _approve,
    )

    result = _execute(ids, fixture)

    assert result["error"] == "UNCERTAIN"
    assert result["details"]["idempotency_key"] == client.apply_requests[0]["idempotency_key"]
