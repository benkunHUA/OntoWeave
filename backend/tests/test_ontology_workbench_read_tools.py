"""映射工作台读工具测试。"""

from __future__ import annotations

import uuid
from dataclasses import replace

from app.services import ontology
from tests.ontology_workbench_support import (
    FakeWorkbenchClient,
    context,
    object_with_properties,
    relation_with_primary_keys,
    tools,
)


def test_tool_names_and_order_are_frozen(session):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    available = tools(context(session, owner, obj, FakeWorkbenchClient()), confirm=False)

    assert list(available) == [
        "list_bindable_datasets",
        "get_dataset_schema",
        "get_subject_definition",
        "get_mapping",
        "suggest_mapping",
        "validate_mapping",
        "preview_mapping",
        "list_index_runs",
        "get_index_run",
        "update_mapping",
        "start_generation",
        "rebind_dataset",
        "unbind",
        "cancel_index_run",
        "ask_user",
    ]


def test_cross_resource_tool_call_is_rejected(session):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    available = tools(context(session, owner, obj, FakeWorkbenchClient()), confirm=True)

    result = available["get_mapping"].invoke(
        {"subject_kind": "relation", "subject_id": str(uuid.uuid4())}
    )

    assert result["error"] == "SUBJECT_MISMATCH"


def test_relation_validate_mapping_uses_preview_validation(session):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    relation = relation_with_primary_keys(session, owner)
    base = context(session, owner, obj, FakeWorkbenchClient())
    ctx = replace(base, subject_kind=ontology.RELATION, subject_id=relation.id)
    available = tools(ctx, confirm=False)

    result = available["validate_mapping"].invoke(
        {"subject_kind": "relation", "subject_id": str(relation.id)}
    )

    assert result == {"blocking": [], "warnings": [], "valid": True}
