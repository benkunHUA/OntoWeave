"""本体映射 service 测试的共享假客户端与本体构造。"""

from __future__ import annotations

import uuid

from app.errors import DataServiceUnavailableError
from app.services import ontology


class FakeOntologyClient:
    def __init__(self) -> None:
        self.saved_definitions = []
        self.saved_bindings = []
        self.preview_calls = []
        self.generate_calls = []
        self.fail_definition = False
        self.fail_binding = False
        self.columns = []
        self.relation_groups = []

    def put_relation_definition(self, relation_id, *, owner_id, payload, operation_id=None):
        if self.fail_definition:
            raise DataServiceUnavailableError("java down")
        self.saved_definitions.append((relation_id, owner_id, payload, operation_id))
        return {"relationId": str(relation_id)}

    def list_dataset_columns(self, *, owner_id, dataset_kind, dataset_id):
        return list(self.columns)

    def get_object_binding(self, object_type_id, *, owner_id):
        if self.fail_binding:
            raise DataServiceUnavailableError("binding unavailable")
        return {"revision": 2, "draft_dataset_kind": "SYNC", "draft_dataset_id": str(uuid.uuid4())}

    def get_relation_binding(self, relation_id, *, owner_id):
        if self.fail_binding:
            raise DataServiceUnavailableError("binding unavailable")
        return {"revision": 3, "draft_dataset_kind": "SYNC", "draft_dataset_id": str(uuid.uuid4())}

    def put_object_binding(self, object_type_id, *, owner_id, payload, operation_id=None):
        self.saved_bindings.append((object_type_id, owner_id, payload, operation_id))
        return {"revision": payload["base_revision"] + 1, **payload}

    def put_relation_binding(self, relation_id, *, owner_id, payload, operation_id=None):
        self.saved_bindings.append((relation_id, owner_id, payload, operation_id))
        return {"revision": payload["base_revision"] + 1, **payload}

    def preview_relation_binding(self, relation_id, *, owner_id, payload=None):
        self.preview_calls.append((relation_id, owner_id, payload))
        return {"sample": [], "blocking": [], "warnings": []}

    def list_object_relation_groups(
        self, object_type_id, *, owner_id, primary_key, direction="both"
    ):
        return list(self.relation_groups)

    def generate_relation_binding(self, relation_id, *, owner_id, payload, operation_id=None):
        self.generate_calls.append((relation_id, payload, operation_id))
        return {"run_id": str(uuid.uuid4())}


def object_with_properties(session, owner):
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="id",
        datatype="text",
        required=True,
        is_primary_key=True,
    )
    ontology.create_property(
        session, owner_type_id=obj.id, name="name", datatype="text", is_title_key=True
    )
    ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="status",
        datatype="enum",
        enum_values=["active", "closed"],
    )
    return obj


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


__all__ = ["FakeOntologyClient", "object_with_properties", "relation_with_primary_keys"]
