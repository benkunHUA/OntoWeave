import pytest
from pydantic import ValidationError

from app.schemas.query import (
    QueryAgentResult,
    QueryAgentStatus,
    QueryDirection,
    QueryEvidence,
    QueryFilter,
    QueryObjectsInput,
    QuerySort,
    QueryStepInput,
)


def test_primary_key_cannot_be_combined_with_search_or_filters() -> None:
    with pytest.raises(ValidationError):
        QueryObjectsInput(object_type_id="type", primary_key="1", q="订单")


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


def test_expected_cardinality_is_limited_to_one_or_many() -> None:
    with pytest.raises(ValidationError):
        QueryStepInput(
            input_step_id="s0",
            relation_id="r",
            direction="outgoing",
            expected_cardinality="some",
        )


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("one_to_one", "one"),
        ("one_to_many", "many"),
        ("many_to_one", "one"),
        ("many_to_many", "many"),
    ],
)
def test_expected_cardinality_accepts_relation_cardinality_aliases(
    value: str, expected: str
) -> None:
    payload = QueryStepInput(
        input_step_id="s0",
        relation_id="r",
        direction="outgoing",
        expected_cardinality=value,
    )

    assert payload.expected_cardinality == expected


def test_query_direction_accepts_forward_and_backward_aliases() -> None:
    assert (
        QueryStepInput(input_step_id="s0", relation_id="r", direction="forward").direction
        is QueryDirection.OUTGOING
    )
    assert (
        QueryStepInput(input_step_id="s0", relation_id="r", direction="backward").direction
        is QueryDirection.INCOMING
    )
    assert (
        QueryStepInput(input_step_id="s0", relation_id="r", direction=" FORWARD ").direction
        is QueryDirection.OUTGOING
    )


def test_agent_result_requires_fields_for_its_status() -> None:
    with pytest.raises(ValidationError):
        QueryAgentResult(status=QueryAgentStatus.OK)
    with pytest.raises(ValidationError):
        QueryAgentResult(status=QueryAgentStatus.NEEDS_USER)
    with pytest.raises(ValidationError):
        QueryAgentResult(status=QueryAgentStatus.BLOCKED)

    result = QueryAgentResult(status=QueryAgentStatus.NEEDS_USER, question="选哪个客户？")
    assert result.question == "选哪个客户？"


def test_evidence_can_be_partial_until_runner_injects_tool_state() -> None:
    evidence = QueryEvidence.model_validate({"step_id": "s0", "title": "客户 112"})

    assert evidence.step_id == "s0"
    assert evidence.object_type_id is None
    assert evidence.primary_key == ""
