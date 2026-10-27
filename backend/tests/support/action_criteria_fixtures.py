"""`test_action_criteria.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

import pytest
from sqlalchemy.orm import Session

from app.services import action, criteria, ontology


class _Fixture:
    def __init__(self, session: Session, owner: uuid.UUID) -> None:
        self.owner = owner
        self.action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
        self.ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
        self.employee = ontology.create_object_type(session, owner_id=owner, name="员工")
        self.status_prop = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.employee_name = ontology.create_property(
            session, owner_type_id=self.employee.id, name="姓名", datatype="text"
        )
        self.amount_param = action.create_parameter(
            session, action_type_id=self.action_type.id, name="金额", datatype="decimal"
        )
        self.status_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.other_status_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="别的状态",
            datatype="enum",
            enum_values=["open", "pending"],
        )
        self.ticket_param = action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name="工单",
            kind="object_ref",
            target_object_type_id=self.ticket.id,
        )

    @property
    def action_type_id(self) -> uuid.UUID:
        return self.action_type.id


def build_fixture(session: Session) -> _Fixture:
    return _Fixture(session, uuid.uuid4())


@pytest.fixture
def fixture(session: Session) -> _Fixture:
    return build_fixture(session)


def _replace(session: Session, fixture: _Fixture, conditions):
    return criteria.replace_criteria(
        session, action_type_id=fixture.action_type_id, conditions=conditions
    )
