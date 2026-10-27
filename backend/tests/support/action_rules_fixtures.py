"""`test_action_rules.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

import pytest
from sqlalchemy.orm import Session

from app.services import action, ontology
from app.services.action import rules as action_rules


class _Fixture:
    """一小组够用的本体：工单（金额 / 状态）、员工、标签，以及两条关系。"""

    def __init__(self, session: Session, owner: uuid.UUID) -> None:
        self.action_type = action.create_action_type(session, owner_id=owner, name="指派工单")
        self.ticket = ontology.create_object_type(session, owner_id=owner, name="工单")
        self.employee = ontology.create_object_type(session, owner_id=owner, name="员工")
        self.tag = ontology.create_object_type(session, owner_id=owner, name="标签")
        self.amount = ontology.create_property(
            session, owner_type_id=self.ticket.id, name="金额", datatype="decimal"
        )
        self.status_prop = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="状态",
            datatype="enum",
            enum_values=["open", "closed"],
        )
        self.channel = ontology.create_property(
            session,
            owner_type_id=self.ticket.id,
            name="渠道",
            datatype="enum",
            enum_values=["电话", "在线"],
        )
        self.employee_name = ontology.create_property(
            session, owner_type_id=self.employee.id, name="姓名", datatype="text"
        )
        self.ticket_param = self._object_parameter(session, "工单", self.ticket.id)
        self.employee_param = self._object_parameter(session, "员工", self.employee.id)
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
        self.labels = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=self.ticket.id,
            target_type_id=self.tag.id,
            name="打标签",
            cardinality="many_to_many",
        )
        self.owns = ontology.create_relation(
            session,
            owner_id=owner,
            source_type_id=self.employee.id,
            target_type_id=self.ticket.id,
            name="负责",
            cardinality="one_to_many",
        )

    def _object_parameter(self, session: Session, name: str, object_type_id: uuid.UUID):
        return action.create_parameter(
            session,
            action_type_id=self.action_type.id,
            name=name,
            kind="object_ref",
            target_object_type_id=object_type_id,
        )


def build_fixture(session: Session) -> _Fixture:
    return _Fixture(session, uuid.uuid4())


@pytest.fixture
def fixture(session: Session) -> _Fixture:
    return build_fixture(session)


def _rule(session: Session, fixture: _Fixture, rule_kind: str, **kwargs):
    return action_rules.create_rule(
        session, action_type_id=fixture.action_type.id, rule_kind=rule_kind, **kwargs
    )
