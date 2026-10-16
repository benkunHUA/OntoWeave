"""动作类型与参数：草稿、状态流转、以及参数那一组不变量。"""

import uuid

import pytest
from sqlalchemy.orm import Session

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.services import action, conversation, ontology, status


def _action(session: Session, owner: uuid.UUID, name: str = "指派工单"):
    return action.create_action_type(session, owner_id=owner, name=name)


def test_action_type_starts_as_an_unembedded_draft(session: Session):
    owner = uuid.uuid4()
    graph_id = conversation.create_conversation(session, owner_id=owner).graph_id

    action_type = action.create_action_type(
        session,
        owner_id=owner,
        name="指派工单",
        description="把工单指派给员工",
        home_graph_id=graph_id,
    )

    assert action_type.status == status.DRAFT
    assert action_type.home_graph_id == graph_id
    assert action_type.embedding is None


def test_action_type_only_moves_draft_to_confirmed(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(InvalidTransitionError):
        action.transition_action_type(session, action_type.id, status.APPROVED)

    assert action.transition_action_type(session, action_type.id, status.CONFIRMED).status == (
        status.CONFIRMED
    )


def test_transition_refuses_another_owner(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(NotFoundError):
        action.transition_action_type(
            session, action_type.id, status.CONFIRMED, owner_id=uuid.uuid4()
        )


def test_unknown_action_type_is_not_found(session: Session):
    with pytest.raises(NotFoundError):
        action.get_action_type(session, uuid.uuid4())


def test_value_parameter_needs_a_datatype(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(InvariantViolationError, match="needs a datatype"):
        action.create_parameter(session, action_type_id=action_type.id, name="金额")


def test_value_parameter_rejects_an_object_type(session: Session):
    owner = uuid.uuid4()
    action_type = _action(session, owner)
    ticket = ontology.create_object_type(session, owner_id=owner, name="工单")

    with pytest.raises(InvariantViolationError, match="cannot point at an object type"):
        action.create_parameter(
            session,
            action_type_id=action_type.id,
            name="金额",
            datatype="decimal",
            target_object_type_id=ticket.id,
        )


def test_object_ref_parameter_needs_an_object_type(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(InvariantViolationError, match="object_ref parameter needs an object type"):
        action.create_parameter(
            session, action_type_id=action_type.id, name="工单", kind="object_ref"
        )


def test_object_ref_parameter_rejects_value_columns(session: Session):
    owner = uuid.uuid4()
    action_type = _action(session, owner)
    ticket = ontology.create_object_type(session, owner_id=owner, name="工单")

    with pytest.raises(InvariantViolationError, match="cannot carry datatype"):
        action.create_parameter(
            session,
            action_type_id=action_type.id,
            name="工单",
            kind="object_ref",
            target_object_type_id=ticket.id,
            datatype="text",
        )


def test_unknown_parameter_kind_is_rejected(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(InvariantViolationError, match="unsupported parameter kind"):
        action.create_parameter(
            session, action_type_id=action_type.id, name="工单", kind="link", datatype="text"
        )


def test_enum_parameter_keeps_candidates_and_requires_them(session: Session):
    action_type = _action(session, uuid.uuid4())

    parameter = action.create_parameter(
        session,
        action_type_id=action_type.id,
        name="渠道",
        datatype="enum",
        enum_values=[" 电话 ", "在线"],
    )
    assert parameter.enum_values == ["电话", "在线"]

    with pytest.raises(InvariantViolationError, match="requires enum_values"):
        action.create_parameter(
            session, action_type_id=action_type.id, name="来源", datatype="enum"
        )
    with pytest.raises(InvariantViolationError, match="must be unique"):
        action.create_parameter(
            session,
            action_type_id=action_type.id,
            name="来源",
            datatype="enum",
            enum_values=["电话", "电话"],
        )


def test_enum_values_are_only_allowed_on_enum_parameters(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(InvariantViolationError, match="only allowed when datatype is 'enum'"):
        action.create_parameter(
            session,
            action_type_id=action_type.id,
            name="备注",
            datatype="text",
            enum_values=["a", "b"],
        )


def test_default_value_must_fit_the_datatype(session: Session):
    action_type = _action(session, uuid.uuid4())

    with pytest.raises(InvariantViolationError, match="does not fit datatype"):
        action.create_parameter(
            session,
            action_type_id=action_type.id,
            name="金额",
            datatype="decimal",
            default_value="很多",
        )

    parameter = action.create_parameter(
        session, action_type_id=action_type.id, name="金额", datatype="decimal", default_value=10
    )
    assert parameter.default_value == 10


def test_parameter_name_and_position_are_unique_per_action(session: Session):
    action_type = _action(session, uuid.uuid4())
    action.create_parameter(session, action_type_id=action_type.id, name="金额", datatype="decimal")

    with pytest.raises(InvariantViolationError, match="already has a parameter named"):
        action.create_parameter(
            session, action_type_id=action_type.id, name="金额", datatype="text"
        )
    with pytest.raises(InvariantViolationError, match="already has a parameter at position"):
        action.create_parameter(
            session, action_type_id=action_type.id, name="备注", datatype="text", order_index=0
        )


def test_new_parameters_append_at_the_end(session: Session):
    action_type = _action(session, uuid.uuid4())
    first = action.create_parameter(
        session, action_type_id=action_type.id, name="金额", datatype="decimal"
    )
    second = action.create_parameter(
        session, action_type_id=action_type.id, name="备注", datatype="text"
    )

    assert (first.order_index, second.order_index) == (0, 1)
    names = [item.name for item in action.list_parameters(session, action_type.id)]
    assert names == ["金额", "备注"]


def test_renaming_does_not_collide_with_itself(session: Session):
    action_type = _action(session, uuid.uuid4())
    parameter = action.create_parameter(
        session, action_type_id=action_type.id, name="金额", datatype="decimal"
    )

    action.validate_parameter_patch(
        session, parameter_id=parameter.id, patch={"name": "金额", "order_index": 0}
    )


def test_parameter_patch_keeps_the_two_column_groups_apart(session: Session):
    owner = uuid.uuid4()
    action_type = _action(session, owner)
    ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
    parameter = action.create_parameter(
        session, action_type_id=action_type.id, name="金额", datatype="decimal"
    )

    with pytest.raises(InvariantViolationError, match="cannot point at an object type"):
        action.validate_parameter_patch(
            session, parameter_id=parameter.id, patch={"target_object_type_id": ticket.id}
        )

    with pytest.raises(InvariantViolationError, match="object_ref parameter needs an object type"):
        action.validate_parameter_patch(
            session, parameter_id=parameter.id, patch={"kind": "object_ref"}
        )

    with pytest.raises(InvariantViolationError, match="cannot carry datatype"):
        action.validate_parameter_patch(
            session,
            parameter_id=parameter.id,
            patch={"kind": "object_ref", "target_object_type_id": ticket.id},
        )
