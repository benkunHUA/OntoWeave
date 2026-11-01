from __future__ import annotations

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.services import criteria
from app.services.action.detail import load_action_detail
from app.services.action.execution import (
    ActionExecutionError,
    ObjectReference,
    ResolvedParameters,
    evaluate_criteria,
)
from tests.support.action_criteria_fixtures import _Fixture, build_fixture


class _Client:
    def __init__(self) -> None:
        self.objects: dict[tuple[uuid.UUID, str], dict] = {}

    def get_object(self, object_type_id, *, owner_id, primary_key):
        del owner_id
        try:
            return self.objects[(object_type_id, primary_key)]
        except KeyError as exc:
            raise NotFoundError("object not found") from exc


_UNSET = object()


def _params(
    fixture: _Fixture,
    *,
    amount: object = _UNSET,
    status: object = _UNSET,
    ticket: ObjectReference | None = None,
) -> ResolvedParameters:
    values: dict[uuid.UUID, object] = {}
    if amount is not _UNSET:
        values[fixture.amount_param.id] = amount
    if status is not _UNSET:
        values[fixture.status_param.id] = status
    objects: dict[uuid.UUID, ObjectReference | None] = {}
    if ticket is not None:
        objects[fixture.ticket_param.id] = ticket
    return ResolvedParameters(values=values, objects=objects, normalized={})


def _set_criteria(session: Session, fixture: _Fixture, condition: dict) -> None:
    criteria.replace_criteria(
        session,
        action_type_id=fixture.action_type_id,
        conditions=[condition],
    )


def test_parameter_static_comparison_passes(session: Session):
    fixture = build_fixture(session)
    _set_criteria(
        session,
        fixture,
        {
            "operator": ">=",
            "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
            "right": {"kind": "static", "static_value": 10},
        },
    )
    detail = load_action_detail(session, fixture.action_type_id)

    evaluate_criteria(
        detail,
        _params(fixture, amount=12),
        owner_id=fixture.owner,
        client=_Client(),
    )


def test_object_property_comparison_uses_effective_object(session: Session):
    fixture = build_fixture(session)
    _set_criteria(
        session,
        fixture,
        {
            "operator": "is",
            "left": {
                "kind": "object_property",
                "object_parameter_id": fixture.ticket_param.id,
                "object_property_id": fixture.status_prop.id,
            },
            "right": {"kind": "static", "static_value": "open"},
        },
    )
    detail = load_action_detail(session, fixture.action_type_id)
    client = _Client()
    client.objects[(fixture.ticket.id, "T-1")] = {
        "properties": {str(fixture.status_prop.id): "open"}
    }

    evaluate_criteria(
        detail,
        _params(
            fixture,
            ticket=ObjectReference(object_type_id=fixture.ticket.id, primary_key="T-1"),
        ),
        owner_id=fixture.owner,
        client=client,
    )


def test_failed_condition_reports_id_message_and_values(session: Session):
    fixture = build_fixture(session)
    _set_criteria(
        session,
        fixture,
        {
            "operator": "is",
            "failure_message": "只有进行中的工单才能操作",
            "left": {
                "kind": "object_property",
                "object_parameter_id": fixture.ticket_param.id,
                "object_property_id": fixture.status_prop.id,
            },
            "right": {"kind": "static", "static_value": "open"},
        },
    )
    detail = load_action_detail(session, fixture.action_type_id)
    client = _Client()
    client.objects[(fixture.ticket.id, "T-1")] = {
        "properties": {str(fixture.status_prop.id): "closed"}
    }

    with pytest.raises(ActionExecutionError) as raised:
        evaluate_criteria(
            detail,
            _params(
                fixture,
                ticket=ObjectReference(object_type_id=fixture.ticket.id, primary_key="T-1"),
            ),
            owner_id=fixture.owner,
            client=client,
        )

    assert raised.value.code == "CRITERIA_FAILED"
    assert raised.value.details == {
        "condition_id": str(detail.criteria[0].children[0].id),
        "failure_message": "只有进行中的工单才能操作",
        "actual": "closed",
        "expected": "open",
    }


def test_is_null_and_is_not_null_are_supported(session: Session):
    fixture = build_fixture(session)
    _set_criteria(
        session,
        fixture,
        {
            "operator": "is null",
            "left": {"kind": "parameter", "value_parameter_id": fixture.amount_param.id},
        },
    )
    detail = load_action_detail(session, fixture.action_type_id)

    evaluate_criteria(
        detail,
        _params(fixture, amount=None),
        owner_id=fixture.owner,
        client=_Client(),
    )


def test_unsupported_operator_and_missing_object_are_structured_errors(session: Session):
    fixture = build_fixture(session)
    _set_criteria(
        session,
        fixture,
        {
            "operator": "is",
            "left": {
                "kind": "object_property",
                "object_parameter_id": fixture.ticket_param.id,
                "object_property_id": fixture.status_prop.id,
            },
            "right": {"kind": "static", "static_value": "open"},
        },
    )
    detail = load_action_detail(session, fixture.action_type_id)

    with pytest.raises(ActionExecutionError) as raised:
        evaluate_criteria(
            detail,
            _params(
                fixture,
                ticket=ObjectReference(object_type_id=fixture.ticket.id, primary_key="T-1"),
            ),
            owner_id=fixture.owner,
            client=_Client(),
        )

    assert raised.value.code == "OBJECT_NOT_FOUND"
