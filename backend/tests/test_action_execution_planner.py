from __future__ import annotations

import uuid
from typing import Any

import pytest
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.services.action import execution
from tests.support.action_execution_fixtures import (
    ActionExecutionFixture,
    build_action_execution_fixture,
)


class _Client:
    def __init__(self) -> None:
        self.objects: dict[tuple[uuid.UUID, str], dict] = {}

    def get_object(self, object_type_id, *, owner_id, primary_key):
        del owner_id
        try:
            return self.objects[(object_type_id, primary_key)]
        except KeyError as exc:
            raise NotFoundError("object not found") from exc


def _ref(object_type_id: uuid.UUID, primary_key: str) -> execution.ObjectReference:
    return execution.ObjectReference(object_type_id=object_type_id, primary_key=primary_key)


def _params(
    *,
    values: dict[uuid.UUID, Any] | None = None,
    objects: dict[uuid.UUID, execution.ObjectReference | None] | None = None,
) -> execution.ResolvedParameters:
    return execution.ResolvedParameters(
        values=values or {},
        objects=objects or {},
        normalized={},
    )


def _plan(
    session: Session,
    fixture: ActionExecutionFixture,
    parameters: execution.ResolvedParameters,
    *,
    client: _Client | None = None,
) -> execution.EditSetDraft:
    return execution.plan_edit_set(
        session,
        fixture.detail(session),
        parameters,
        owner_id=fixture.owner,
        client=client or _Client(),
    )


def test_create_object_maps_all_value_sources_and_explicit_primary_key(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "create_object",
        target_object_type_id=fixture.ticket.id,
        assignments=[
            {
                "target_property_id": fixture.ticket_key.id,
                "value_source": "static",
                "static_value": "T-100",
            },
            {
                "target_property_id": fixture.amount.id,
                "value_source": "parameter",
                "value_parameter_id": fixture.amount_param.id,
            },
            {
                "target_property_id": fixture.status.id,
                "value_source": "static",
                "static_value": "open",
            },
        ],
    )

    draft = _plan(
        session,
        fixture,
        _params(values={fixture.amount_param.id: 12.5}),
    )

    assert len(draft.object_creates) == 1
    create = draft.object_creates[0]
    assert create.object_type_id == fixture.ticket.id
    assert create.primary_key == "T-100"
    assert create.values == {
        fixture.ticket_key.id: "T-100",
        fixture.amount.id: 12.5,
        fixture.status.id: "open",
    }
    assert draft.object_updates == ()
    assert draft.object_deletes == ()
    assert draft.relation_creates == ()
    assert draft.relation_deletes == ()


def test_create_object_generates_a_stable_text_primary_key(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "create_object",
        target_object_type_id=fixture.ticket.id,
        assignments=[
            {
                "target_property_id": fixture.amount.id,
                "value_source": "parameter",
                "value_parameter_id": fixture.amount_param.id,
            }
        ],
    )
    parameters = _params(values={fixture.amount_param.id: 1})

    first = _plan(session, fixture, parameters).object_creates[0]
    second = _plan(session, fixture, parameters).object_creates[0]

    assert first.primary_key == second.primary_key
    assert str(uuid.UUID(first.primary_key)) == first.primary_key
    assert first.values == {fixture.amount.id: 1}


def test_create_object_requires_an_explicit_non_text_primary_key(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "create_object",
        target_object_type_id=fixture.employee.id,
        assignments=[
            {
                "target_property_id": fixture.employee_name.id,
                "value_source": "static",
                "static_value": "张三",
            }
        ],
    )

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(session, fixture, _params())

    assert raised.value.code == "PRIMARY_KEY_REQUIRED"
    assert raised.value.details["object_type_id"] == str(fixture.employee.id)


def test_modify_object_maps_set_and_clear(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
        assignments=[
            {
                "target_property_id": fixture.amount.id,
                "value_source": "parameter",
                "value_parameter_id": fixture.amount_param.id,
            },
            {
                "target_property_id": fixture.status.id,
                "value_source": "parameter",
                "value_parameter_id": fixture.status_param.id,
            },
            {
                "target_property_id": fixture.channel.id,
                "value_source": "static",
                "static_value": "在线",
            },
        ],
    )

    draft = _plan(
        session,
        fixture,
        _params(
            values={fixture.amount_param.id: 9.5, fixture.status_param.id: None},
            objects={fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1")},
        ),
    )

    assert len(draft.object_updates) == 1
    update = draft.object_updates[0]
    assert update.object_type_id == fixture.ticket.id
    assert update.primary_key == "T-1"
    assert update.set_values == {fixture.amount.id: 9.5, fixture.channel.id: "在线"}
    assert update.clear == (fixture.status.id,)


def test_modify_object_reads_object_property_from_effective_object(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
        assignments=[
            {
                "target_property_id": fixture.amount.id,
                "value_source": "object_property",
                "object_parameter_id": fixture.employee_param.id,
                "object_property_id": fixture.salary.id,
            }
        ],
    )
    client = _Client()
    client.objects[(fixture.employee.id, "E-1")] = {"properties": {str(fixture.salary.id): 18000}}

    draft = _plan(
        session,
        fixture,
        _params(
            objects={
                fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1"),
                fixture.employee_param.id: _ref(fixture.employee.id, "E-1"),
            }
        ),
        client=client,
    )

    assert draft.object_updates[0].set_values == {fixture.amount.id: 18000}


def test_modify_object_rejects_primary_key_changes(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "modify_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
        assignments=[
            {
                "target_property_id": fixture.ticket_key.id,
                "value_source": "static",
                "static_value": "T-2",
            }
        ],
    )

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(
            session,
            fixture,
            _params(objects={fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1")}),
        )

    assert raised.value.code == "PRIMARY_KEY_IMMUTABLE"
    assert raised.value.details["property_id"] == str(fixture.ticket_key.id)


def test_delete_object_emits_object_delete(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "delete_object",
        target_object_type_id=fixture.ticket.id,
        source_parameter_id=fixture.ticket_param.id,
    )

    draft = _plan(
        session,
        fixture,
        _params(objects={fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1")}),
    )

    assert draft.object_deletes == (
        execution.ObjectDeleteEdit(object_type_id=fixture.ticket.id, primary_key="T-1"),
    )


@pytest.mark.parametrize(
    ("rule_kind", "draft_field"),
    [("create_link", "relation_creates"), ("delete_link", "relation_deletes")],
)
def test_link_rules_emit_relation_changes(session: Session, rule_kind: str, draft_field: str):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        rule_kind,
        target_relation_id=fixture.labels.id,
        source_parameter_id=fixture.ticket_param.id,
        target_parameter_id=fixture.tag_param.id,
    )

    draft = _plan(
        session,
        fixture,
        _params(
            objects={
                fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1"),
                fixture.tag_param.id: _ref(fixture.tag.id, "G-1"),
            }
        ),
    )

    changes = getattr(draft, draft_field)
    assert changes == (
        execution.RelationEdit(
            relation_id=fixture.labels.id,
            from_object_type_id=fixture.ticket.id,
            from_primary_key="T-1",
            to_object_type_id=fixture.tag.id,
            to_primary_key="G-1",
        ),
    )


def test_foreign_key_relation_never_becomes_a_link_edit(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "create_link",
        target_relation_id=fixture.labels.id,
        source_parameter_id=fixture.ticket_param.id,
        target_parameter_id=fixture.tag_param.id,
    )
    fixture.labels.cardinality = "one_to_many"
    session.flush()

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(
            session,
            fixture,
            _params(
                objects={
                    fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1"),
                    fixture.tag_param.id: _ref(fixture.tag.id, "G-1"),
                }
            ),
        )

    assert raised.value.code == "INVALID_RELATION_RULE"


def test_duplicate_object_effect_is_rejected(session: Session):
    fixture = build_action_execution_fixture(session)
    for _ in range(2):
        fixture.rule(
            session,
            "modify_object",
            target_object_type_id=fixture.ticket.id,
            source_parameter_id=fixture.ticket_param.id,
            assignments=[
                {
                    "target_property_id": fixture.amount.id,
                    "value_source": "parameter",
                    "value_parameter_id": fixture.amount_param.id,
                }
            ],
        )

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(
            session,
            fixture,
            _params(
                values={fixture.amount_param.id: 1},
                objects={fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1")},
            ),
        )

    assert raised.value.code == "DUPLICATE_EFFECT"


def test_conflicting_link_effect_is_rejected(session: Session):
    fixture = build_action_execution_fixture(session)
    for rule_kind in ("create_link", "delete_link"):
        fixture.rule(
            session,
            rule_kind,
            target_relation_id=fixture.labels.id,
            source_parameter_id=fixture.ticket_param.id,
            target_parameter_id=fixture.tag_param.id,
        )

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(
            session,
            fixture,
            _params(
                objects={
                    fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1"),
                    fixture.tag_param.id: _ref(fixture.tag.id, "G-1"),
                }
            ),
        )

    assert raised.value.code == "DUPLICATE_EFFECT"


def test_array_target_property_is_rejected(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "create_object",
        target_object_type_id=fixture.ticket.id,
        assignments=[
            {
                "target_property_id": fixture.ticket_key.id,
                "value_source": "static",
                "static_value": "T-1",
            },
            {
                "target_property_id": fixture.aliases.id,
                "value_source": "static",
                "static_value": ["alias"],
            },
        ],
    )

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(session, fixture, _params())

    assert raised.value.code == "UNSUPPORTED_ARRAY"
    assert raised.value.details["property_id"] == str(fixture.aliases.id)


def test_link_endpoint_must_be_resolved(session: Session):
    fixture = build_action_execution_fixture(session)
    fixture.rule(
        session,
        "create_link",
        target_relation_id=fixture.labels.id,
        source_parameter_id=fixture.ticket_param.id,
        target_parameter_id=fixture.tag_param.id,
    )

    with pytest.raises(execution.ActionExecutionError) as raised:
        _plan(
            session,
            fixture,
            _params(objects={fixture.ticket_param.id: _ref(fixture.ticket.id, "T-1")}),
        )

    assert raised.value.code == "MISSING_PARAMETER"
    assert raised.value.details["parameter_id"] == str(fixture.tag_param.id)
