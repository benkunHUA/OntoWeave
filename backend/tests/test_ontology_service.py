import uuid

import pytest
from sqlalchemy import select

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.models import Relation
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
        session, owner_type_id=obj.id, name="数量", datatype="integer", required=True
    )
    assert prop.name == "数量"
    assert prop.required is True
    assert prop.status == "draft"


def test_create_property_rejects_unknown_owner(session):
    with pytest.raises(NotFoundError):
        ontology.create_property(
            session, owner_type_id=uuid.uuid4(), name="数量", datatype="integer"
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


def test_create_relation_is_idempotent(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")

    first = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    second = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )

    assert first.id == second.id
    assert len(session.scalars(select(Relation)).all()) == 1


def test_self_relation_is_allowed(session):
    """本体里自反关系是合法的（公司—控股→公司），不能被查重逻辑误伤。"""
    company = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="公司")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=company.id,
        target_type_id=company.id,
        name="控股",
    )
    assert rel.source_type_id == rel.target_type_id


def test_list_properties_filters_by_owner_and_sorts(session):
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    other = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.create_property(session, owner_type_id=order.id, name="quantity")
    ontology.create_property(session, owner_type_id=order.id, name="amount")
    ontology.create_property(session, owner_type_id=other.id, name="grade")

    names = [p.name for p in ontology.list_properties(session, order.id)]
    assert names == ["amount", "quantity"]


def test_relation_status_machine(session):
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
    assert ontology.transition_relation(session, rel.id, "confirmed").status == "confirmed"
    assert ontology.transition_relation(session, rel.id, "approved").status == "approved"
    with pytest.raises(InvalidTransitionError):
        ontology.transition_relation(session, rel.id, "confirmed")


def test_relation_cannot_skip_confirmed(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    with pytest.raises(InvalidTransitionError):
        ontology.transition_relation(session, rel.id, "approved")


def test_missing_relation_raises(session):
    with pytest.raises(NotFoundError):
        ontology.transition_relation(session, uuid.uuid4(), "confirmed")


def test_create_property_rejects_unknown_datatype(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    with pytest.raises(InvariantViolationError):
        ontology.create_property(session, owner_type_id=obj.id, name="数量", datatype="int")


def test_create_property_defaults_and_flags(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    prop = ontology.create_property(
        session,
        owner_type_id=obj.id,
        name="订单号",
        is_array=False,
        is_primary_key=True,
        is_title_key=True,
    )
    assert prop.datatype == "text"
    assert prop.is_primary_key is True
    assert prop.is_title_key is True


def test_object_type_allows_only_one_primary_key(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    ontology.create_property(session, owner_type_id=obj.id, name="订单号", is_primary_key=True)
    with pytest.raises(InvariantViolationError):
        ontology.create_property(session, owner_type_id=obj.id, name="备选号", is_primary_key=True)


def test_create_relation_carries_cardinality_and_labels(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
        cardinality="one_to_many",
        forward_label="订单",
        reverse_label="客户",
    )
    assert rel.cardinality == "one_to_many"
    assert rel.forward_label == "订单"
    assert rel.reverse_label == "客户"


def test_create_relation_rejects_unknown_cardinality(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    with pytest.raises(InvariantViolationError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
            cardinality="lots",
        )
