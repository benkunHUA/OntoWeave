"""映射工作台写工具、确认中断与冲突测试。"""

from __future__ import annotations

import uuid

import pytest

from tests.ontology_workbench_support import (
    FakeWorkbenchClient,
    context,
    object_with_properties,
    tools,
)


def test_update_mapping_without_key_change_writes_and_returns_previous(session):
    owner = uuid.uuid4()
    obj, primary = object_with_properties(session, owner)
    client = FakeWorkbenchClient()
    client.put_calls.append(
        (
            obj.id,
            owner,
            {"mapping": {"primary_key_attribute_id": str(primary.id), "attributes": []}},
            uuid.uuid4(),
        )
    )
    available = tools(context(session, owner, obj, client), confirm=False)

    result = available["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 4,
            "mapping": {
                "primary_key_attribute_id": str(primary.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["revision"] == 5
    assert result["previousMapping"]["primary_key_attribute_id"] == str(primary.id)
    assert len(client.put_calls) == 2


def test_update_mapping_requires_confirmation_when_primary_key_changes(session):
    owner = uuid.uuid4()
    obj, primary = object_with_properties(session, owner)
    client = FakeWorkbenchClient()
    client.put_calls.append(
        (
            obj.id,
            owner,
            {"mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []}},
            uuid.uuid4(),
        )
    )
    available = tools(context(session, owner, obj, client), confirm=False)

    result = available["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 4,
            "mapping": {
                "primary_key_attribute_id": str(primary.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["interrupted"] is True
    assert len(client.put_calls) == 1


def test_start_generation_is_interrupted_without_side_effect(session):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    client = FakeWorkbenchClient()
    available = tools(context(session, owner, obj, client), confirm=False)

    result = available["start_generation"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "mapping_revision": 4,
        }
    )

    assert result["interrupted"] is True
    assert client.generate_calls == []


@pytest.mark.parametrize(
    ("tool_name", "arguments", "side_effect"),
    [
        (
            "rebind_dataset",
            {
                "subject_kind": "object_type",
                "dataset_kind": "SYNC",
                "dataset_id": str(uuid.uuid4()),
                "base_revision": 4,
            },
            "put_calls",
        ),
        ("unbind", {"subject_kind": "object_type"}, "delete_calls"),
        ("cancel_index_run", {"run_id": str(uuid.uuid4())}, "cancel_calls"),
    ],
)
def test_write_tools_interrupt_without_side_effect(session, tool_name, arguments, side_effect):
    owner = uuid.uuid4()
    obj, _ = object_with_properties(session, owner)
    client = FakeWorkbenchClient()
    available = tools(context(session, owner, obj, client), confirm=False)
    if tool_name != "cancel_index_run":
        arguments["subject_id"] = str(obj.id)

    result = available[tool_name].invoke(arguments)

    assert result["interrupted"] is True
    assert getattr(client, side_effect) == []


def test_update_mapping_without_binding_does_not_ask_key_confirmation(session):
    owner = uuid.uuid4()
    obj, primary = object_with_properties(session, owner)
    client = FakeWorkbenchClient()
    client.binding_missing = True
    available = tools(context(session, owner, obj, client), confirm=False)

    result = available["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 0,
            "mapping": {
                "primary_key_attribute_id": str(primary.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["error"] == "INVALID_ARGUMENT"
    assert client.put_calls == []


def test_revision_conflict_returns_current_revision(session):
    owner = uuid.uuid4()
    obj, primary = object_with_properties(session, owner)
    client = FakeWorkbenchClient()
    client.conflict = True
    available = tools(context(session, owner, obj, client), confirm=True)

    result = available["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 3,
            "mapping": {
                "primary_key_attribute_id": str(primary.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["error"] == "REVISION_CONFLICT"
    assert result["current_revision"] == 9
