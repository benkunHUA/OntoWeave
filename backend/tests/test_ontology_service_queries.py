"""本体映射查询与资源汇总 service 测试。"""

from __future__ import annotations

import uuid

from app.services import ontology
from tests.ontology_service_support import FakeOntologyClient, object_with_properties


def test_merge_detail_keeps_definition_order_and_fills_null(session):
    owner = uuid.uuid4()
    obj = object_with_properties(session, owner)
    props = ontology.list_properties(session, obj.id)
    first, second = props[0], props[1]

    merged = ontology.merge_object_detail(
        session,
        object_type_id=obj.id,
        detail={
            "primary_key": "1",
            "title": "Ada",
            "index_version_id": str(uuid.uuid4()),
            "properties": [
                {"attribute_id": str(second.id), "value": "Ada"},
                {"attribute_id": str(first.id), "value": "1"},
            ],
        },
    )

    assert list(merged["properties"]) == [str(prop.id) for prop in props]
    assert merged["properties"][str(first.id)] == "1"
    assert merged["properties"][str(second.id)] == "Ada"
    assert merged["properties"][str(props[2].id)] is None


def test_resources_keeps_items_when_binding_summary_fails(session):
    owner = uuid.uuid4()
    source = ontology.create_object_type(session, owner_id=owner, name="客户")
    target = ontology.create_object_type(session, owner_id=owner, name="订单")
    many = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="many_to_many",
    )
    client = FakeOntologyClient()
    client.fail_binding = True

    resources = ontology.list_resources(session, client, owner_id=owner)

    assert [item["id"] for item in resources["object_types"]] == [source.id, target.id]
    assert resources["object_types"][0]["binding"] is None
    assert [item["id"] for item in resources["many_to_many_relations"]] == [many.id]
    assert resources["many_to_many_relations"][0]["binding"] is None


def test_relation_groups_add_readable_label_and_direction(session):
    owner = uuid.uuid4()
    source = ontology.create_object_type(session, owner_id=owner, name="客户")
    target = ontology.create_object_type(session, owner_id=owner, name="订单")
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="one_to_many",
        forward_label="下单",
        reverse_label="被下单",
    )
    client = FakeOntologyClient()
    client.relation_groups = [
        {
            "relation_id": relation.id,
            "direction": "incoming",
            "items": [{"primary_key": "o-1", "title": "订单 1"}],
        }
    ]

    groups = ontology.relation_groups(
        session,
        client,
        owner_id=owner,
        object_type_id=target.id,
        primary_key="o-1",
        direction="incoming",
    )

    assert groups[0]["label"] == "被下单"
    assert groups[0]["direction_label"] == "入向"
    assert groups[0]["source_type_name"] == "客户"
    assert groups[0]["target_type_name"] == "订单"
    assert groups[0]["total"] == 1
