from __future__ import annotations

import uuid

import pytest

from app.errors import InvalidRequestError
from app.schemas.query import QueryObjectsInput, RelatedObjectsInput
from app.services import graph, ontology
from app.services.query import get_related_objects, query_objects
from tests.support.query_fixtures import FakeQueryClient, _object_with_id


def test_query_objects_lists_sorts_and_truncates(session):
    owner = uuid.uuid4()
    obj, _, _ = _object_with_id(session, owner, "订单")
    client = FakeQueryClient()
    client.objects[obj.id] = {
        "index_version_id": uuid.uuid4(),
        "total": 2,
        "truncated": True,
        "items": [
            {"object_type_id": str(obj.id), "primary_key": "o-2", "title": "订单 2"},
        ],
    }

    result = query_objects(
        session,
        client,
        owner_id=owner,
        payload=QueryObjectsInput(object_type_id=str(obj.id), q="订单", limit=1),
    )

    assert result.status == "TRUNCATED"
    assert result.total == 2
    assert [item.primary_key for item in result.items] == ["o-2"]
    assert client.last_list_args["q"] == "订单"
    assert client.last_list_args["limit"] == 1


def test_query_objects_reads_primary_keys_in_one_batch(session):
    owner = uuid.uuid4()
    obj, _, _ = _object_with_id(session, owner, "客户")
    client = FakeQueryClient()
    client.objects[obj.id] = {
        "index_version_id": uuid.uuid4(),
        "total": 2,
        "truncated": False,
        "items": [
            {"object_type_id": str(obj.id), "primary_key": "c-1", "title": "客户 1"},
            {"object_type_id": str(obj.id), "primary_key": "c-2", "title": "客户 2"},
        ],
    }

    result = query_objects(
        session,
        client,
        owner_id=owner,
        payload=QueryObjectsInput(
            object_type_id=str(obj.id),
            primary_keys=["c-1", "c-2"],
        ),
    )

    assert result.status == "OK"
    assert [item.primary_key for item in result.items] == ["c-1", "c-2"]
    assert client.last_list_args["primary_keys"] == ["c-1", "c-2"]


def test_query_objects_accepts_property_names_in_project(session):
    owner = uuid.uuid4()
    obj, _, title = _object_with_id(session, owner, "客户")
    client = FakeQueryClient()
    client.objects[obj.id] = {
        "index_version_id": uuid.uuid4(),
        "total": 1,
        "items": [
            {
                "object_type_id": str(obj.id),
                "primary_key": "c-1",
                "title": "客户 1",
                "properties": [{"attribute_id": str(title.id), "value": "客户 1"}],
            }
        ],
    }

    result = query_objects(
        session,
        client,
        owner_id=owner,
        payload=QueryObjectsInput(object_type_id=str(obj.id), q="客户 1", project=["name"]),
    )

    assert result.items[0].properties[str(title.id)] == "客户 1"
    assert client.last_list_args["project"] == [title.id]


def test_get_related_objects_batches_inputs_and_dedupes_peers(session):
    owner = uuid.uuid4()
    source, _, _ = _object_with_id(session, owner, "客户")
    target, _, _ = _object_with_id(session, owner, "订单")
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
        cardinality="many_to_many",
    )
    graph_row = graph.create_graph(session, owner_id=owner, name="销售")
    for item in (source, target):
        graph.add_object_to_graph(session, graph_row.id, item.id, owner_id=owner)
    graph.add_relation_to_graph(session, graph_row.id, relation.id, owner_id=owner)
    client = FakeQueryClient()
    client.related = {
        "index_version_id": uuid.uuid4(),
        "total": 1,
        "truncated": False,
        "items": [
            {"object_type_id": str(target.id), "primary_key": "o-1", "title": "订单 1"},
            {"object_type_id": str(target.id), "primary_key": "o-1", "title": "订单 1"},
        ],
    }

    result = get_related_objects(
        session,
        client,
        owner_id=owner,
        graph_id=graph_row.id,
        payload=RelatedObjectsInput(
            relation_id=str(relation.id),
            direction="outgoing",
            inputs=[
                {"object_type_id": str(source.id), "primary_key": "c-1"},
                {"object_type_id": str(source.id), "primary_key": "c-2"},
            ],
        ),
    )

    assert result.status == "OK"
    assert result.target_type_id == target.id
    assert [item.primary_key for item in result.items] == ["o-1"]
    assert client.last_related_args["inputs"] == [
        {"object_type_id": str(source.id), "primary_key": "c-1"},
        {"object_type_id": str(source.id), "primary_key": "c-2"},
    ]


def test_get_related_objects_rejects_wrong_input_type(session):
    owner = uuid.uuid4()
    source, _, _ = _object_with_id(session, owner, "客户")
    target, _, _ = _object_with_id(session, owner, "订单")
    relation = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=source.id,
        target_type_id=target.id,
        name="下订单",
    )
    graph_row = graph.create_graph(session, owner_id=owner, name="销售")
    for item in (source, target):
        graph.add_object_to_graph(session, graph_row.id, item.id, owner_id=owner)
    graph.add_relation_to_graph(session, graph_row.id, relation.id, owner_id=owner)

    with pytest.raises(InvalidRequestError, match="does not match"):
        get_related_objects(
            session,
            FakeQueryClient(),
            owner_id=owner,
            graph_id=graph_row.id,
            payload=RelatedObjectsInput(
                relation_id=str(relation.id),
                direction="outgoing",
                inputs=[{"object_type_id": str(target.id), "primary_key": "o-1"}],
            ),
        )
