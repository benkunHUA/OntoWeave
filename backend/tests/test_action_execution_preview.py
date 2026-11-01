from __future__ import annotations

import uuid
from typing import Any

import pytest
from sqlalchemy.orm import Session

from app.services import embedding
from app.services.action import execution
from app.services.graph import publication as graph_publication
from tests.support.action_execution_fixtures import CloseTicketEditClient
from tests.support.action_presentation_fixtures import graph_with_close_ticket_action


def _fixture(
    session: Session,
) -> tuple[
    dict[str, uuid.UUID],
    execution.ActionContext,
    CloseTicketEditClient,
    execution.ResolvedParameters,
    execution.EditSetDraft,
]:
    ids = graph_with_close_ticket_action(session)
    graph_publication.publish_graph(
        session,
        graph_id=ids["graph_id"],
        owner_id=ids["owner"],
        embedder=embedding.HashingEmbedder(),
    )
    context = execution.load_action_context(
        session,
        graph_id=ids["graph_id"],
        action_type_id=ids["action_id"],
        owner_id=ids["owner"],
        conversation_id=uuid.uuid4(),
    )
    client = CloseTicketEditClient(ids)
    client.objects[(ids["ticket_id"], "T-1")] = {
        "properties": {str(ids["ticket_status_id"]): "处理中"}
    }
    client.objects[(ids["part_id"], "P-1")] = {"properties": {}}
    parameter_ids = {item.name: item.id for item in context.detail.parameters}
    resolved = execution.resolve_parameters(
        context.detail,
        {
            str(parameter_ids["工单"]): {
                "object_type_id": str(ids["ticket_id"]),
                "primary_key": "T-1",
            },
            str(parameter_ids["配件"]): {
                "object_type_id": str(ids["part_id"]),
                "primary_key": "P-1",
            },
            str(parameter_ids["解决说明"]): "已修复",
        },
        owner_id=ids["owner"],
        client=client,
    )
    draft = execution.plan_edit_set(
        session,
        context.detail,
        resolved,
        owner_id=ids["owner"],
        client=client,
        execution_key="conversation:tool-call",
    )
    return ids, context, client, resolved, draft


def _preview(
    session: Session,
    context: execution.ActionContext,
    client: CloseTicketEditClient,
    parameters: execution.ResolvedParameters,
    draft: execution.EditSetDraft,
    *,
    execution_id: uuid.UUID,
    expected: dict[str, Any] | None = None,
    previous_hash: str | None = None,
) -> execution.ActionPreview:
    return execution.preview_action(
        session,
        context,
        parameters,
        draft,
        execution_id=execution_id,
        idempotency_key="conversation:tool-call",
        client=client,
        expected=expected,
        previous_hash=previous_hash,
    )


def test_preview_builds_edit_set_request_and_confirmation(session: Session):
    ids, context, client, parameters, draft = _fixture(session)
    execution_id = uuid.uuid4()

    result = _preview(
        session,
        context,
        client,
        parameters,
        draft,
        execution_id=execution_id,
    )

    sent = client.preview_requests[0]
    assert sent["execution_id"] == execution_id
    assert sent["owner_id"] == ids["owner"]
    assert sent["conversation_id"] == context.conversation_id
    assert sent["graph_id"] == ids["graph_id"]
    assert sent["action_type_id"] == ids["action_id"]
    assert sent["action_definition_hash"] == context.definition_hash
    assert sent["action_snapshot"] == context.snapshot
    assert sent["parameters"] == parameters.normalized
    assert sent["expected"] == {"objects": [], "relations": []}
    assert sent["object_updates"][0]["primary_key"] == "T-1"
    assert sent["relation_creates"][0]["to_primary_key"] == "P-1"

    assert result.expected == {
        "objects": [
            {
                "object_type_id": str(ids["ticket_id"]),
                "primary_key": "T-1",
                "version": 4,
            }
        ],
        "relations": [
            {
                "relation_id": str(ids["relation_id"]),
                "from_primary_key": "T-1",
                "to_primary_key": "P-1",
                "version": 0,
            }
        ],
    }
    assert result.request["expected"] == result.expected
    confirmation = result.confirmation
    assert confirmation["kind"] == "action_confirmation"
    assert confirmation["title"] == "执行「关闭工单」？"
    assert confirmation["summary"] == "修改 1 个对象；新增 1 条关系"
    assert confirmation["execution_id"] == str(execution_id)
    assert confirmation["preview_hash"] == result.preview_hash
    assert confirmation["object_changes"][0]["title"] == "打印机故障"
    assert confirmation["object_changes"][0]["object_type_name"] == "工单"
    assert confirmation["object_changes"][0]["fields"] == [
        {
            "property_id": str(ids["resolution_id"]),
            "name": "解决说明",
            "before": None,
            "after": "已修复",
        }
    ]
    assert confirmation["relation_changes"][0]["relation_name"] == "使用配件"
    assert confirmation["relation_changes"][0]["from_object_type_name"] == "工单"
    assert confirmation["relation_changes"][0]["to_object_type_name"] == "配件"
    approve, reject = confirmation["options"]
    assert approve["label"] == "确认执行"
    assert approve["answer"] == {
        "decision": "approve",
        "preview_hash": result.preview_hash,
        "execution_id": str(execution_id),
        "expected": result.expected,
    }
    assert reject == {"label": "取消", "answer": {"decision": "reject"}}


def test_preview_conflict_does_not_build_confirmation(session: Session):
    ids, context, client, parameters, draft = _fixture(session)
    client.conflicts = [
        {
            "code": "EDIT_CONFLICT",
            "message": "对象版本已变化",
            "details": {"id": str(ids["ticket_id"])},
        }
    ]

    with pytest.raises(execution.ActionExecutionError) as raised:
        _preview(
            session,
            context,
            client,
            parameters,
            draft,
            execution_id=uuid.uuid4(),
        )

    assert raised.value.code == "PREVIEW_CONFLICT"
    assert raised.value.details["conflicts"] == client.conflicts


def test_preview_hash_is_stable_after_expected_roundtrip(session: Session):
    _, context, client, parameters, draft = _fixture(session)
    execution_id = uuid.uuid4()
    first = _preview(
        session,
        context,
        client,
        parameters,
        draft,
        execution_id=execution_id,
    )

    second = _preview(
        session,
        context,
        client,
        parameters,
        draft,
        execution_id=execution_id,
        expected=first.expected,
        previous_hash=first.preview_hash,
    )

    assert second.preview_hash == first.preview_hash
    assert second.request["expected"] == first.expected


def test_preview_returns_stale_when_preview_changes_after_confirmation(session: Session):
    _, context, client, parameters, draft = _fixture(session)
    execution_id = uuid.uuid4()
    first = _preview(
        session,
        context,
        client,
        parameters,
        draft,
        execution_id=execution_id,
    )
    client.version = 5

    with pytest.raises(execution.ActionExecutionError) as raised:
        _preview(
            session,
            context,
            client,
            parameters,
            draft,
            execution_id=execution_id,
            expected=first.expected,
            previous_hash=first.preview_hash,
        )

    assert raised.value.code == "PREVIEW_STALE"
    assert raised.value.details["previous_hash"] == first.preview_hash


def test_preview_hash_changes_with_parameters(session: Session):
    ids, context, client, parameters, draft = _fixture(session)
    execution_id = uuid.uuid4()
    first = _preview(
        session,
        context,
        client,
        parameters,
        draft,
        execution_id=execution_id,
    )
    parameter_ids = {item.name: item.id for item in context.detail.parameters}
    changed = execution.resolve_parameters(
        context.detail,
        {
            str(parameter_ids["工单"]): {
                "object_type_id": str(ids["ticket_id"]),
                "primary_key": "T-1",
            },
            str(parameter_ids["配件"]): {
                "object_type_id": str(ids["part_id"]),
                "primary_key": "P-1",
            },
            str(parameter_ids["解决说明"]): "另一个说明",
        },
        owner_id=ids["owner"],
        client=client,
    )
    changed_draft = execution.plan_edit_set(
        session,
        context.detail,
        changed,
        owner_id=ids["owner"],
        client=client,
        execution_key="conversation:tool-call",
    )

    second = _preview(
        session,
        context,
        client,
        changed,
        changed_draft,
        execution_id=execution_id,
    )

    assert second.preview_hash != first.preview_hash


def test_expected_skips_object_creates_and_reads_relation_versions():
    preview = {
        "objects": [
            {"kind": "OBJECT_CREATE", "object_type_id": "t", "primary_key": "T-1"},
            {
                "kind": "OBJECT_DELETE",
                "object_type_id": "t",
                "primary_key": "T-2",
                "before": {"version": 7},
            },
        ],
        "relations": [
            {
                "relation_id": "r",
                "from_primary_key": "A",
                "to_primary_key": "B",
                "before_version": 3,
            }
        ],
    }

    assert execution.expected_from_preview(preview) == {
        "objects": [{"object_type_id": "t", "primary_key": "T-2", "version": 7}],
        "relations": [
            {
                "relation_id": "r",
                "from_primary_key": "A",
                "to_primary_key": "B",
                "version": 3,
            }
        ],
    }
