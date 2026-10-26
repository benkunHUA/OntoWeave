"""映射工作台读工具测试。"""

from __future__ import annotations

import uuid
from dataclasses import replace

from app.services import dataset_search, ontology
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
        "find_bindable_datasets",
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


def test_find_bindable_datasets_uses_semantic_index(session):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    base = context(session, owner, obj, FakeWorkbenchClient())
    dataset_id = uuid.uuid4()
    version_id = uuid.uuid4()
    detail = {
        "kind": "SYNC",
        "id": str(dataset_id),
        "name": "crm.customers",
        "version_id": str(version_id),
        "version_number": 1,
        "row_count": 10,
        "owner_id": str(owner),
        "published_at": "2026-10-24T10:00:00Z",
        "columns": [
            {"name": "id", "type": "string", "optional": False},
            {"name": "name", "type": "string", "optional": True},
        ],
    }

    class DatasetClient:
        def get_bindable_dataset(self, *, dataset_kind, dataset_id, version_id, owner_id=None):
            del dataset_kind, dataset_id, version_id, owner_id
            return detail

    dataset_search.index_dataset_version(
        session,
        client=DatasetClient(),
        embedder=base.embedder,
        dataset_kind="SYNC",
        dataset_id=dataset_id,
        version_id=version_id,
    )
    available = tools(base, confirm=False)

    result = available["find_bindable_datasets"].invoke({"limit": 5})

    assert result["items"][0]["name"] == "crm.customers"
    assert result["items"][0]["status"] == "READY"
    assert result["items"][0]["primary_key_column"] == "id"
