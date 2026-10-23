import uuid

import pytest

from app.errors import DataServiceUnavailableError, InvalidRequestError
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
        return {"pairs": [], "validation": {"blocking": [], "warnings": [], "valid": True}}

    def list_object_relation_groups(
        self, object_type_id, *, owner_id, primary_key, direction="both"
    ):
        return list(self.relation_groups)

    def generate_relation_binding(self, relation_id, *, owner_id, payload, operation_id=None):
        self.generate_calls.append((relation_id, payload, operation_id))
        return {"run_id": str(uuid.uuid4())}


def _object_with_properties(session, owner):
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


def _relation_with_primary_keys(session, owner):
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


def test_merge_detail_keeps_definition_order_and_fills_null(session):
    owner = uuid.uuid4()
    obj = _object_with_properties(session, owner)
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


def test_suggest_mapping_scores_name_and_type_and_picks_primary_key(session):
    owner = uuid.uuid4()
    obj = _object_with_properties(session, owner)
    client = FakeOntologyClient()
    client.columns = [
        {"name": "id", "type": "string", "optional": False},
        {"name": "customer_name", "type": "string", "optional": True},
        {"name": "state", "type": "string", "optional": True},
    ]

    result = ontology.suggest_mapping(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=obj.id,
        dataset_kind="SYNC",
        dataset_id=uuid.uuid4(),
    )

    mapping = result["mapping"]
    columns = {str(item["attribute_id"]): item["column"] for item in mapping["attributes"]}
    assert mapping["primary_key_attribute_id"] == next(
        prop.id for prop in ontology.list_properties(session, obj.id) if prop.name == "id"
    )
    assert list(columns.values())[0] == "id"
    assert "customer_name" in columns.values()
    assert {item["column"] for item in result["reasons"]} == {"id", "customer_name", "state"}


def test_suggest_mapping_infers_dataset_kind_from_current_binding(session):
    owner = uuid.uuid4()
    obj = _object_with_properties(session, owner)
    client = FakeOntologyClient()
    client.columns = [{"name": "id", "type": "string", "optional": False}]
    dataset_id = uuid.uuid4()

    result = ontology.suggest_mapping(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.OBJECT_TYPE,
        subject_id=obj.id,
        dataset_id=dataset_id,
    )

    assert result["dataset_kind"] == "SYNC"
    assert result["dataset_id"] == dataset_id


def test_suggest_mapping_without_type_compatible_primary_key_is_invalid(session):
    owner = uuid.uuid4()
    obj = _object_with_properties(session, owner)
    client = FakeOntologyClient()
    client.columns = [{"name": "amount", "type": "decimal", "optional": False}]

    with pytest.raises(InvalidRequestError, match="主键"):
        ontology.suggest_mapping(
            session,
            client,
            owner_id=owner,
            subject_kind=ontology.OBJECT_TYPE,
            subject_id=obj.id,
            dataset_kind="SYNC",
            dataset_id=uuid.uuid4(),
        )


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
    relation = _relation_with_primary_keys(session, owner)
    client = FakeOntologyClient()
    dataset_id = uuid.uuid4()

    ontology.save_binding(
        session,
        client,
        owner_id=owner,
        subject_kind=ontology.RELATION,
        subject_id=relation.id,
        payload={
            "base_revision": 1,
            "dataset_kind": "SYNC",
            "dataset_id": dataset_id,
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
    relation = _relation_with_primary_keys(session, owner)
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
    relation = _relation_with_primary_keys(session, owner)
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
