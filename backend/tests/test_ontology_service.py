import uuid

import pytest

from app.errors import InvalidTransitionError, NotFoundError
from app.services import ontology


def test_create_object_type_defaults_to_draft(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    assert obj.status == "draft"
    assert obj.name == "客户"


def test_transition_draft_to_confirmed(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    moved = ontology.transition_object_type(session, obj.id, "confirmed")
    assert moved.status == "confirmed"


def test_transition_confirmed_to_approved(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed")
    moved = ontology.transition_object_type(session, obj.id, "approved")
    assert moved.status == "approved"


def test_cannot_skip_confirmed(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    with pytest.raises(InvalidTransitionError):
        ontology.transition_object_type(session, obj.id, "approved")


def test_cannot_leave_approved(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed")
    ontology.transition_object_type(session, obj.id, "approved")
    with pytest.raises(InvalidTransitionError):
        ontology.transition_object_type(session, obj.id, "confirmed")


def test_missing_object_type_raises(session):
    with pytest.raises(NotFoundError):
        ontology.transition_object_type(session, uuid.uuid4(), "confirmed")


def test_list_object_types(session):
    ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    names = {o.name for o in ontology.list_object_types(session)}
    assert {"客户", "订单"} <= names


def test_create_property_requires_existing_owner(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    prop = ontology.create_property(
        session, owner_type_id=obj.id, name="数量", datatype="int", required=True
    )
    assert prop.name == "数量"
    assert prop.required is True
    assert prop.status == "draft"


def test_create_property_rejects_unknown_owner(session):
    with pytest.raises(NotFoundError):
        ontology.create_property(
            session, owner_type_id=uuid.uuid4(), name="数量", datatype="int"
        )


def test_create_relation_requires_both_endpoints(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    assert rel.status == "draft"
    assert rel.direction == "forward"


def test_create_relation_rejects_missing_endpoint(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    with pytest.raises(NotFoundError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=uuid.uuid4(),
            name="下订单",
        )
