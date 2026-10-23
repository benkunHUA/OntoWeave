"""Python 侧映射建议 service 测试。"""

from __future__ import annotations

import uuid

import pytest

from app.errors import InvalidRequestError
from app.services import ontology
from tests.ontology_service_support import FakeOntologyClient, object_with_properties


def test_suggest_mapping_scores_name_and_type_and_picks_primary_key(session):
    owner = uuid.uuid4()
    obj = object_with_properties(session, owner)
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
    obj = object_with_properties(session, owner)
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
    obj = object_with_properties(session, owner)
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
