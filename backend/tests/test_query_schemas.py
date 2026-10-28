import pytest
from pydantic import ValidationError

from app.schemas.query import (
    QueryDirection,
    QueryFilter,
    QueryObjectsInput,
    QuerySort,
    RelatedObjectsInput,
)


def test_primary_keys_are_deduped_and_can_combine_with_filters() -> None:
    payload = QueryObjectsInput(
        object_type_id="type",
        primary_keys=["c-1", "c-1", "c-2"],
        filters=[{"property_id": "p", "op": "eq", "value": "open"}],
    )

    assert payload.primary_keys == ["c-1", "c-2"]


def test_primary_keys_reject_empty_values() -> None:
    with pytest.raises(ValidationError):
        QueryObjectsInput(object_type_id="type", primary_keys=[""])


def test_unary_filter_rejects_a_value() -> None:
    with pytest.raises(ValidationError):
        QueryFilter(property_id="p", op="is_null", value=None)


@pytest.mark.parametrize("alias", ["property", "attribute_id", "attributeId", "field"])
def test_filter_and_sort_accept_property_id_aliases(alias: str) -> None:
    query_filter = QueryFilter.model_validate({alias: "p", "op": "eq", "value": "v"})
    query_sort = QuerySort.model_validate({alias: "p"})

    assert query_filter.property_id == "p"
    assert query_sort.property_id == "p"


def test_filter_accepts_operator_alias() -> None:
    query_filter = QueryFilter.model_validate({"property_id": "p", "operator": "eq", "value": 1003})

    assert query_filter.op == "eq"


def test_related_inputs_require_at_least_one_reference() -> None:
    with pytest.raises(ValidationError):
        RelatedObjectsInput(relation_id="r", direction="outgoing", inputs=[])


def test_query_direction_accepts_forward_and_backward_aliases() -> None:
    assert (
        RelatedObjectsInput(
            relation_id="r",
            direction="forward",
            inputs=[{"object_type_id": "t", "primary_key": "1"}],
        ).direction
        is QueryDirection.OUTGOING
    )
    assert (
        RelatedObjectsInput(
            relation_id="r",
            direction="backward",
            inputs=[{"object_type_id": "t", "primary_key": "1"}],
        ).direction
        is QueryDirection.INCOMING
    )
