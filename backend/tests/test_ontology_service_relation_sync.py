"""关系定义同步与生成门禁 service 测试。"""

from __future__ import annotations

import uuid

import pytest

from app.errors import DataServiceUnavailableError
from app.services import ontology
from tests.ontology_service_support import FakeOntologyClient, relation_with_primary_keys


def test_relation_definition_payload_contains_both_primary_keys(session):
    owner = uuid.uuid4()
    source = ontology.create_object_type(session, owner_id=owner, name="客户")
    target = ontology.create_object_type(session, owner_id=owner, name="订单")
    source_pk = ontology.create_property(
        session, owner_type_id=source.id, name="id", datatype="text", is_primary_key=True
    )
    target_pk = ontology.create_property(
        session, owner_type_id=target.id, name="id", datatype="integer", is_primary_key=True
    )
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="many_to_many",
    )

    payload = ontology.relation_definition_payload(session, relationship=relation)

    assert payload["source_primary_key_attribute_id"] == source_pk.id
    assert payload["source_primary_key_datatype"] == "text"
    assert payload["target_primary_key_attribute_id"] == target_pk.id
    assert payload["target_primary_key_datatype"] == "integer"
    assert payload["definition_revision"] > 0


def test_save_and_preview_sync_relation_definition(session):
    owner = uuid.uuid4()
    relation = relation_with_primary_keys(session, owner)
    client = FakeOntologyClient()

    ontology.save_binding(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.RELATION,
        subject_id=relation.id,
        payload={
            "base_revision": 1,
            "dataset_kind": "SYNC",
            "dataset_id": uuid.uuid4(),
            "mapping": {
                "from_key_column": "customer_id",
                "from_key_datatype": "text",
                "to_key_column": "order_id",
                "to_key_datatype": "integer",
            },
        },
    )
    ontology.preview_binding(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.RELATION,
        subject_id=relation.id,
        payload={"limit": 5},
    )

    assert [item[0] for item in client.saved_definitions] == [relation.id, relation.id]
    assert len(client.saved_bindings) == 1
    assert client.preview_calls[0][0] == relation.id


def test_relation_sync_failure_blocks_generation_with_503(session):
    owner = uuid.uuid4()
    relation = relation_with_primary_keys(session, owner)
    client = FakeOntologyClient()
    client.fail_definition = True

    with pytest.raises(DataServiceUnavailableError, match="关系定义同步失败"):
        ontology.generate_binding(
            session,
            client,
            owner_id=owner,
            subject_kind=ontology.RELATION,
            subject_id=relation.id,
            payload={"mapping_revision": 1},
        )

    assert client.generate_calls == []


def test_relation_sync_failure_does_not_block_binding_read(session):
    owner = uuid.uuid4()
    relation = relation_with_primary_keys(session, owner)
    client = FakeOntologyClient()
    client.fail_definition = True

    binding = ontology.get_binding(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.RELATION,
        subject_id=relation.id,
    )

    assert binding["revision"] == 3
