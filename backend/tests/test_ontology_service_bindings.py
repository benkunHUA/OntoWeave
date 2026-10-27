"""对象/关系绑定 payload 的 requiredAttributeIds 补齐测试。"""

from __future__ import annotations

import uuid

from app.services import ontology
from tests.support.ontology_service_support import FakeOntologyClient, relation_with_primary_keys


def test_prepare_object_payload_adds_all_required_property_ids(session):
    owner = uuid.uuid4()
    obj = ontology.create_object_type(session, owner_id=owner, name="客户")
    primary_key = ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="id",
        datatype="text",
        required=True,
        is_primary_key=True,
    )
    required_name = ontology.create_property(
        session, owner_type_id=obj.id, name="name", datatype="text", required=True
    )
    ontology.create_property(session, owner_type_id=obj.id, name="note", datatype="text")
    payload = {
        "base_revision": 0,
        "mapping": {"primary_key_attribute_id": str(primary_key.id), "attributes": []},
    }

    prepared = ontology.prepare_object_payload(
        session,
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=obj.id,
        payload=payload,
    )

    assert prepared is not None
    assert prepared["mapping"]["required_attribute_ids"] == [
        str(primary_key.id),
        str(required_name.id),
    ]


def test_relation_binding_payload_is_not_enriched(session):
    owner = uuid.uuid4()
    relation = relation_with_primary_keys(session, owner)
    client = FakeOntologyClient()
    payload = {
        "base_revision": 0,
        "mapping": {
            "from_key_column": "customer_id",
            "from_key_datatype": "text",
            "to_key_column": "order_id",
            "to_key_datatype": "integer",
        },
    }

    ontology.save_binding(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.RELATION,
        subject_id=relation.id,
        payload=payload,
    )

    saved_payload = client.saved_bindings[0][2]
    assert "required_attribute_ids" not in saved_payload["mapping"]
