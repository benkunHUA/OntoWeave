from __future__ import annotations

import uuid

from app.errors import InvalidRequestError
from app.schemas.query import QueryObjectsInput, QueryStepInput
from app.services import graph, ontology
from app.services.query import execute_query_step, query_objects
from tests.support.query_fixtures import FakeQueryClient, _object_with_id


def test_query_objects_lists_sorts_and_truncates(session):
    owner = uuid.uuid4()
    obj, key_prop, title_prop = _object_with_id(session, owner, "订单")
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
        payload=QueryObjectsInput(
            object_type_id=str(obj.id),
            sort=[{"property_id": str(key_prop.id), "direction": "desc"}],
            limit=1,
        ),
        step_id="s0",
    )

    assert [item.primary_key for item in result.items] == ["o-2"]
    assert result.status == "TRUNCATED"
    assert result.truncated is True
    assert result.total == 2
    assert client.last_list_args["limit"] == 1
    assert client.last_list_args["sort"] == [
        {"attribute_id": str(key_prop.id), "direction": "desc"}
    ]


def test_query_objects_reads_one_primary_key(session):
    owner = uuid.uuid4()
    obj, key_prop, title_prop = _object_with_id(session, owner, "客户")
    client = FakeQueryClient()
    client.details[(obj.id, "c-1")] = {
        "primary_key": "c-1",
        "title": "客户 1",
        "index_version_id": uuid.uuid4(),
        "properties": {str(key_prop.id): "c-1", str(title_prop.id): "客户 1"},
    }

    result = query_objects(
        session,
        client,
        owner_id=owner,
        payload=QueryObjectsInput(
            object_type_id=str(obj.id),
            primary_key="c-1",
            project=[str(title_prop.id)],
        ),
        step_id="s0",
    )

    assert result.total == 1
    assert result.items[0].properties[str(title_prop.id)] == "客户 1"


def test_query_objects_accepts_property_names_in_project(session):
    owner = uuid.uuid4()
    obj, _, title_prop = _object_with_id(session, owner, "客户")
    client = FakeQueryClient()
    client.objects[obj.id] = {
        "index_version_id": uuid.uuid4(),
        "total": 1,
        "items": [
            {
                "object_type_id": str(obj.id),
                "primary_key": "c-1",
                "title": "客户 1",
                "properties": {str(title_prop.id): "客户 1"},
            }
        ],
    }

    result = query_objects(
        session,
        client,
        owner_id=owner,
        payload=QueryObjectsInput(object_type_id=str(obj.id), q="客户 1", project=["name"]),
        step_id="s0",
    )

    assert client.last_list_args["project"] == [title_prop.id]
    assert result.items[0].properties[str(title_prop.id)] == "客户 1"


def test_execute_query_step_deduplicates_many_to_many_peers(session):
    owner = uuid.uuid4()
    source, _, _ = _object_with_id(session, owner, "客户")
    target, target_key, target_title = _object_with_id(session, owner, "订单")
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
    version_id = uuid.uuid4()
    client.details[(target.id, "o-1")] = {
        "primary_key": "o-1",
        "title": "订单 1",
        "index_version_id": version_id,
        "properties": {str(target_key.id): "o-1", str(target_title.id): "订单 1"},
    }
    client.steps = {
        "index_version_id": version_id,
        "total": 1,
        "truncated": False,
        "items": [
            {
                "object_type_id": str(target.id),
                "primary_key": "o-1",
                "title": "订单 1",
                "properties": [{"attribute_id": str(target_title.id), "value": "订单 1"}],
            }
        ],
    }

    result = execute_query_step(
        session,
        client,
        owner_id=owner,
        graph_id=graph_row.id,
        payload=QueryStepInput(
            input_step_id="s0",
            relation_id=str(relation.id),
            direction="outgoing",
        ),
        input_items=[
            {"object_type_id": str(source.id), "primary_key": "c-1"},
            {"object_type_id": str(source.id), "primary_key": "c-2"},
        ],
        step_id="s1",
    )

    assert result.target_type_id == target.id
    assert [item.primary_key for item in result.items] == ["o-1"]
    assert result.total == 1
    assert client.last_step_args["inputs"] == [
        {"object_type_id": str(source.id), "primary_key": "c-1"},
        {"object_type_id": str(source.id), "primary_key": "c-2"},
    ]


def test_execute_query_step_rejects_wrong_input_type(session):
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

    try:
        execute_query_step(
            session,
            FakeQueryClient(),
            owner_id=owner,
            graph_id=graph_row.id,
            payload=QueryStepInput(
                input_step_id="s0",
                relation_id=str(relation.id),
                direction="outgoing",
            ),
            input_items=[{"object_type_id": str(target.id), "primary_key": "o-1"}],
            step_id="s1",
        )
    except InvalidRequestError as exc:
        assert "does not match" in str(exc)
    else:
        raise AssertionError("expected InvalidRequestError")


def test_execute_query_step_enforces_expected_single_cardinality(session):
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
    client = FakeQueryClient()
    client.steps = {
        "index_version_id": uuid.uuid4(),
        "total": 2,
        "truncated": False,
        "items": [
            {"object_type_id": str(target.id), "primary_key": "o-1", "title": "订单 1"},
            {"object_type_id": str(target.id), "primary_key": "o-2", "title": "订单 2"},
        ],
    }

    try:
        execute_query_step(
            session,
            client,
            owner_id=owner,
            graph_id=graph_row.id,
            payload=QueryStepInput(
                input_step_id="s0",
                relation_id=str(relation.id),
                direction="outgoing",
                expected_cardinality="one",
            ),
            input_items=[{"object_type_id": str(source.id), "primary_key": "c-1"}],
            step_id="s1",
        )
    except InvalidRequestError as exc:
        assert "expected_cardinality" in str(exc)
    else:
        raise AssertionError("expected InvalidRequestError")
