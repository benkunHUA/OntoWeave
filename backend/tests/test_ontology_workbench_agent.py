import uuid
from dataclasses import replace

import pytest

from app.agent.definitions.ontology_workbench import OntologyWorkbenchAgentDefinition
from app.agent.definitions.registry import get_definition
from app.agent.prompts_ontology_workbench import build_prompt
from app.agent.tools.ontology.workbench_binding import build_workbench_tools
from app.agent.tools.ontology.workbench_context import OntologyWorkbenchToolContext
from app.config import Settings
from app.errors import InvariantViolationError, NotFoundError
from app.services import conversation, ontology
from app.services.data_service.client import RevisionConflict
from app.vocabulary import ConversationKind


class FakeWorkbenchClient:
    def __init__(self) -> None:
        self.put_calls = []
        self.generate_calls = []
        self.cancel_calls = []
        self.delete_calls = []
        self.columns = [{"name": "id", "type": "string", "optional": False}]
        self.conflict = False
        self.binding_missing = False

    def get_object_binding(self, object_type_id, *, owner_id):
        if self.binding_missing:
            raise NotFoundError("binding not found")
        return {
            "id": str(uuid.uuid4()),
            "object_type_id": str(object_type_id),
            "revision": 4,
            "draft_dataset_kind": "SYNC",
            "draft_dataset_id": str(uuid.uuid4()),
            "draft_mapping": {
                "primary_key_attribute_id": (
                    str(self.put_calls[-1][2]["mapping"]["primary_key_attribute_id"])
                    if self.put_calls
                    else None
                ),
                "attributes": [],
            },
            "status": "ACTIVE",
        }

    def get_relation_binding(self, relation_id, *, owner_id):
        return {"revision": 1, "draft_dataset_kind": "SYNC", "draft_dataset_id": str(uuid.uuid4())}

    def put_relation_definition(self, relation_id, *, owner_id, payload, operation_id=None):
        return {}

    def put_object_binding(self, object_type_id, *, owner_id, payload, operation_id=None):
        if self.conflict:
            raise RevisionConflict(
                "草稿已变化", code="MAPPING_REVISION_CONFLICT", details={"currentRevision": 9}
            )
        self.put_calls.append((object_type_id, owner_id, payload, operation_id))
        return {"revision": payload["base_revision"] + 1, **payload}

    def put_relation_binding(self, relation_id, *, owner_id, payload, operation_id=None):
        self.put_calls.append((relation_id, owner_id, payload, operation_id))
        return {"revision": payload["base_revision"] + 1, **payload}

    def list_object_runs(self, object_type_id, *, owner_id, limit=20):
        return [{"id": str(uuid.uuid4()), "status": "SUCCEEDED"}]

    def list_relation_runs(self, relation_id, *, owner_id, limit=20):
        return []

    def list_dataset_columns(self, *, owner_id, dataset_kind, dataset_id):
        return list(self.columns)

    def list_bindable_datasets(self, *, owner_id, keyword=None, page=1, size=20):
        return {"items": [], "total": 0, "page": page, "size": size}

    def validate_object_binding(self, object_type_id, *, owner_id, payload=None):
        return {"blocking": [], "warnings": [], "valid": True}

    def preview_object_binding(self, object_type_id, *, owner_id, payload=None):
        return {"rows": [], "validation": {"blocking": [], "warnings": [], "valid": True}}

    def preview_relation_binding(self, relation_id, *, owner_id, payload=None):
        return {"pairs": [], "validation": {"blocking": [], "warnings": [], "valid": True}}

    def generate_object_binding(self, object_type_id, *, owner_id, payload, operation_id=None):
        self.generate_calls.append(payload)
        return {"run_id": str(uuid.uuid4())}

    def generate_relation_binding(self, relation_id, *, owner_id, payload, operation_id=None):
        self.generate_calls.append(payload)
        return {"run_id": str(uuid.uuid4())}

    def delete_object_binding(self, object_type_id, *, owner_id, operation_id=None):
        self.delete_calls.append(object_type_id)
        return {}

    def delete_relation_binding(self, relation_id, *, owner_id, operation_id=None):
        self.delete_calls.append(relation_id)
        return {}

    def get_run(self, run_id, *, owner_id):
        return {"id": str(run_id), "status": "FAILED"}

    def cancel_run(self, run_id, *, owner_id, operation_id=None):
        self.cancel_calls.append(run_id)
        return {"id": str(run_id), "status": "CANCELLED"}


def _object(session, owner):
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    pk = ontology.create_property(
        session, owner_type_id=obj.id, name="id", datatype="text", is_primary_key=True
    )
    ontology.create_property(
        session, owner_type_id=obj.id, name="name", datatype="text", is_title_key=True
    )
    return obj, pk


def _context(session, owner, obj, client, *, revision=4):
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="客户映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=obj.id,
    )
    return OntologyWorkbenchToolContext(
        session=session,
        owner_id=owner,
        conversation_id=conv.id,
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=obj.id,
        client=client,
        state={"binding": None, "mapping": None},
        revision=revision,
    )


def _relation(session, owner):
    source = ontology.create_object_type(session, owner_id=owner, name="客户")
    target = ontology.create_object_type(session, owner_id=owner, name="订单")
    ontology.create_property(
        session, owner_type_id=source.id, name="id", datatype="text", is_primary_key=True
    )
    ontology.create_property(
        session, owner_type_id=target.id, name="id", datatype="integer", is_primary_key=True
    )
    return ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="many_to_many",
    )


def _tools(ctx, confirm):
    return {item.name: item for item in build_workbench_tools(ctx, interrupt_fn=lambda _: confirm)}


def test_registry_and_prepare_run_use_workbench_kind(session):
    owner = uuid.uuid4()
    obj, _ = _object(session, owner)
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="客户映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=obj.id,
    )

    definition = get_definition(ConversationKind.ONTOLOGY_WORKBENCH)
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)

    assert isinstance(definition, OntologyWorkbenchAgentDefinition)
    assert run.subject_kind == ontology.OBJECT_TYPE
    assert run.subject_id == obj.id


def test_prepare_run_rejects_another_conversation_kind(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="建模")

    with pytest.raises(InvariantViolationError, match="cannot run conversation kind"):
        OntologyWorkbenchAgentDefinition().prepare_run(session, conversation=conv, owner_id=owner)


def test_tool_names_and_order_are_frozen(session):
    owner = uuid.uuid4()
    obj, _ = _object(session, owner)
    tools = _tools(_context(session, owner, obj, FakeWorkbenchClient()), confirm=False)

    assert list(tools) == [
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


def test_update_mapping_without_key_change_writes_and_returns_previous(session):
    owner = uuid.uuid4()
    obj, pk = _object(session, owner)
    client = FakeWorkbenchClient()
    client.put_calls.append(
        (
            obj.id,
            owner,
            {"mapping": {"primary_key_attribute_id": str(pk.id), "attributes": []}},
            uuid.uuid4(),
        )
    )
    tools = _tools(_context(session, owner, obj, client), confirm=False)

    result = tools["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 4,
            "mapping": {
                "primary_key_attribute_id": str(pk.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["revision"] == 5
    assert result["previousMapping"]["primary_key_attribute_id"] == str(pk.id)
    assert len(client.put_calls) == 2


def test_update_mapping_requires_confirmation_when_primary_key_changes(session):
    owner = uuid.uuid4()
    obj, pk = _object(session, owner)
    client = FakeWorkbenchClient()
    client.put_calls.append(
        (
            obj.id,
            owner,
            {"mapping": {"primary_key_attribute_id": str(uuid.uuid4()), "attributes": []}},
            uuid.uuid4(),
        )
    )
    tools = _tools(_context(session, owner, obj, client), confirm=False)

    result = tools["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 4,
            "mapping": {
                "primary_key_attribute_id": str(pk.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["interrupted"] is True
    assert len(client.put_calls) == 1


def test_start_generation_is_interrupted_without_side_effect(session):
    owner = uuid.uuid4()
    obj, _ = _object(session, owner)
    client = FakeWorkbenchClient()
    tools = _tools(_context(session, owner, obj, client), confirm=False)

    result = tools["start_generation"].invoke(
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
    obj, _ = _object(session, owner)
    client = FakeWorkbenchClient()
    tools = _tools(_context(session, owner, obj, client), confirm=False)
    if tool_name != "cancel_index_run":
        arguments["subject_id"] = str(obj.id)

    result = tools[tool_name].invoke(arguments)

    assert result["interrupted"] is True
    assert getattr(client, side_effect) == []


def test_update_mapping_without_binding_does_not_ask_key_confirmation(session):
    owner = uuid.uuid4()
    obj, pk = _object(session, owner)
    client = FakeWorkbenchClient()
    client.binding_missing = True
    tools = _tools(_context(session, owner, obj, client), confirm=False)

    result = tools["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 0,
            "mapping": {
                "primary_key_attribute_id": str(pk.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["error"] == "INVALID_ARGUMENT"
    assert client.put_calls == []


def test_revision_conflict_returns_current_revision(session):
    owner = uuid.uuid4()
    obj, pk = _object(session, owner)
    client = FakeWorkbenchClient()
    client.conflict = True
    tools = _tools(_context(session, owner, obj, client), confirm=True)

    result = tools["update_mapping"].invoke(
        {
            "subject_kind": "object_type",
            "subject_id": str(obj.id),
            "base_revision": 3,
            "mapping": {
                "primary_key_attribute_id": str(pk.id),
                "title_key_attribute_id": None,
                "attributes": [],
            },
        }
    )

    assert result["error"] == "REVISION_CONFLICT"
    assert result["current_revision"] == 9


def test_cross_resource_tool_call_is_rejected(session):
    owner = uuid.uuid4()
    obj, _ = _object(session, owner)
    tools = _tools(_context(session, owner, obj, FakeWorkbenchClient()), confirm=True)

    result = tools["get_mapping"].invoke(
        {"subject_kind": "relation", "subject_id": str(uuid.uuid4())}
    )

    assert result["error"] == "SUBJECT_MISMATCH"


def test_relation_validate_mapping_uses_preview_validation(session):
    owner = uuid.uuid4()
    obj, _ = _object(session, owner)
    relation = _relation(session, owner)
    base = _context(session, owner, obj, FakeWorkbenchClient())
    ctx = replace(base, subject_kind=ontology.RELATION, subject_id=relation.id)
    tools = _tools(ctx, confirm=False)

    result = tools["validate_mapping"].invoke(
        {"subject_kind": "relation", "subject_id": str(relation.id)}
    )

    assert result == {"blocking": [], "warnings": [], "valid": True}


def test_build_graph_reloads_state_and_injects_prompt(session, monkeypatch):
    owner = uuid.uuid4()
    obj, _ = _object(session, owner)
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="客户映射",
        kind=ConversationKind.ONTOLOGY_WORKBENCH.value,
        resource_id=obj.id,
    )
    definition = OntologyWorkbenchAgentDefinition()
    run = definition.prepare_run(session, conversation=conv, owner_id=owner)
    run = replace(run, turn_id=uuid.uuid4())
    client = FakeWorkbenchClient()
    monkeypatch.setattr(
        "app.agent.definitions.ontology_workbench.OntologyDataServiceClient",
        lambda settings: client,
    )
    captured = {}

    def fake_build_agent(*args, **kwargs):
        captured["tools"] = {item.name for item in args[1]}
        captured["prompt"] = kwargs["system_prompt"]
        return "agent"

    monkeypatch.setattr(
        "app.agent.definitions.ontology_workbench.agent_graph.build_agent", fake_build_agent
    )

    assert (
        definition.build_graph(session, Settings(chat_api_key="test"), run, checkpointer=None)
        == "agent"
    )
    assert "get_mapping" in captured["tools"]
    assert "update_mapping" in captured["tools"]
    assert "当前映射（每轮重新读取）" in captured["prompt"]
    assert str(obj.id) in captured["prompt"]


def test_prompt_states_confirmation_and_run_first_rules():
    prompt = build_prompt(
        {
            "subject_kind": "object_type",
            "subject_id": str(uuid.uuid4()),
            "binding": None,
            "mapping": None,
            "latest_run": {"status": "FAILED"},
            "load_error": None,
        }
    )

    assert "主键" in prompt and "标题键" in prompt
    assert "生成、换源、解绑、取消运行都必须先确认" in prompt
    assert "先看运行记录" in prompt or "先读取失败运行" in prompt
