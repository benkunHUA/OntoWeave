"""映射 Agent 工具测试的共享上下文与假客户端。"""

from __future__ import annotations

import uuid

from app.agent.tools.ontology.workbench_binding import build_workbench_tools
from app.agent.tools.ontology.workbench_context import OntologyWorkbenchToolContext
from app.clients.data_service.client import RevisionConflict
from app.errors import NotFoundError
from app.services import conversation, embedding, ontology
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
            "binding_id": str(uuid.uuid4()),
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
        return {
            "binding_id": str(uuid.uuid4()),
            "relation_id": str(relation_id),
            "revision": 1,
            "draft_dataset_kind": "SYNC",
            "draft_dataset_id": str(uuid.uuid4()),
        }

    def put_relation_definition(self, relation_id, *, owner_id, payload, operation_id=None):
        return {}

    def put_object_binding(self, object_type_id, *, owner_id, payload, operation_id=None):
        if self.conflict:
            raise RevisionConflict(
                "草稿已变化", code="MAPPING_REVISION_CONFLICT", details={"currentRevision": 9}
            )
        self.put_calls.append((object_type_id, owner_id, payload, operation_id))
        return {
            "binding_id": str(uuid.uuid4()),
            "revision": payload["base_revision"] + 1,
            "mapping": payload["mapping"],
        }

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
        return {"sample": [], "blocking": [], "warnings": []}

    def preview_relation_binding(self, relation_id, *, owner_id, payload=None):
        return {"sample": [], "blocking": [], "warnings": []}

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


def object_with_properties(session, owner):
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    primary = ontology.create_property(
        session, owner_type_id=obj.id, name="id", datatype="text", is_primary_key=True
    )
    ontology.create_property(
        session, owner_type_id=obj.id, name="name", datatype="text", is_title_key=True
    )
    return obj, primary


def relation_with_primary_keys(session, owner):
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


def context(session, owner, obj, client, *, revision=4):
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
        embedder=embedding.HashingEmbedder(),
        state={"binding": None, "mapping": None},
        revision=revision,
    )


def tools(ctx, confirm):
    return {item.name: item for item in build_workbench_tools(ctx, interrupt_fn=lambda _: confirm)}


__all__ = [
    "FakeWorkbenchClient",
    "context",
    "object_with_properties",
    "relation_with_primary_keys",
    "tools",
]
