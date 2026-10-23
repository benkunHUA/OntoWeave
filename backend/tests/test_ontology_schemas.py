from __future__ import annotations

import uuid

import pytest
from pydantic import ValidationError

from app.schemas.ontology import (
    AttributeMapping,
    DatasetKind,
    FilterCondition,
    FilterOp,
    ObjectMapping,
    OntologyDataset,
    RelationMapping,
)
from app.vocabulary import ConversationKind


def test_ontology_workbench_conversation_kind_is_available() -> None:
    assert ConversationKind.ONTOLOGY_WORKBENCH.value == "ontology_workbench"


def test_dataset_and_mapping_contracts_accept_snake_case_payloads() -> None:
    required_attribute_id = uuid.uuid4()
    dataset = OntologyDataset.model_validate(
        {
            "kind": "PIPELINE",
            "id": str(uuid.uuid4()),
            "name": "orders",
            "version_id": str(uuid.uuid4()),
            "version_number": 3,
            "row_count": 42,
            "columns": [{"name": "order_id", "type": "long", "optional": False, "example": 1}],
        }
    )
    mapping = ObjectMapping.model_validate(
        {
            "primary_key_attribute_id": str(uuid.uuid4()),
            "title_key_attribute_id": None,
            "attributes": [
                {
                    "attribute_id": str(uuid.uuid4()),
                    "column": "order_id",
                    "datatype": "integer",
                    "required": True,
                    "is_array": False,
                    "enum_values": None,
                }
            ],
            "required_attribute_ids": [str(required_attribute_id)],
        }
    )
    relation = RelationMapping.model_validate(
        {
            "from_key_column": "customer_id",
            "from_key_datatype": "text",
            "to_key_column": "order_id",
            "to_key_datatype": "integer",
        }
    )

    assert dataset.kind is DatasetKind.PIPELINE
    assert mapping.attributes[0].datatype.value == "integer"
    assert mapping.required_attribute_ids == [required_attribute_id]
    assert relation.to_key_datatype.value == "integer"


def test_attribute_mapping_rejects_unknown_datatypes() -> None:
    with pytest.raises(ValidationError):
        AttributeMapping.model_validate(
            {
                "attribute_id": str(uuid.uuid4()),
                "column": "id",
                "datatype": "array",
                "required": True,
                "is_array": False,
            }
        )


@pytest.mark.parametrize(
    ("payload", "valid"),
    [
        ({"attribute_id": str(uuid.uuid4()), "op": "is_null"}, True),
        ({"attribute_id": str(uuid.uuid4()), "op": "is_not_null"}, True),
        ({"attribute_id": str(uuid.uuid4()), "op": "eq", "value": False}, True),
        ({"attribute_id": str(uuid.uuid4()), "op": "gte", "value": 100}, True),
        (
            {"attribute_id": str(uuid.uuid4()), "op": "contains", "value": "Ada"},
            True,
        ),
        (
            {"attribute_id": str(uuid.uuid4()), "op": "is_null", "value": "x"},
            False,
        ),
        ({"attribute_id": str(uuid.uuid4()), "op": "eq"}, False),
        (
            {"attribute_id": str(uuid.uuid4()), "op": "contains", "value": 3},
            False,
        ),
        (
            {"attribute_id": str(uuid.uuid4()), "op": "gt", "value": True},
            False,
        ),
    ],
)
def test_filter_condition_validates_operator_shape(payload: dict[str, object], valid: bool) -> None:
    if valid:
        condition = FilterCondition.model_validate(payload)
        assert condition.op in FilterOp
    else:
        with pytest.raises(ValidationError):
            FilterCondition.model_validate(payload)
