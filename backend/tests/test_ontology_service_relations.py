"""`test_ontology_service.py` 中 relations 相关测试。"""

import uuid

import pytest
from sqlalchemy import select

from app.errors import InvalidTransitionError, InvariantViolationError, NotFoundError
from app.models import Relation
from app.services import ontology
from tests.support.ontology_service_fixtures import _customer_order


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


def test_create_relation_binds_foreign_key(session):
    customer, order = _customer_order(session)
    ontology.create_property(session, owner_type_id=customer.id, name="客户号", is_primary_key=True)
    fk = ontology.create_property(session, owner_type_id=order.id, name="客户号", datatype="text")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
        cardinality="one_to_many",
        foreign_key_property_id=fk.id,
    )
    assert rel.foreign_key_property_id == fk.id


def test_relation_foreign_key_rejected_for_many_to_many(session):
    customer, order = _customer_order(session)
    fk = ontology.create_property(session, owner_type_id=order.id, name="客户号")
    with pytest.raises(InvariantViolationError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
            cardinality="many_to_many",
            foreign_key_property_id=fk.id,
        )


def test_relation_foreign_key_must_belong_to_an_endpoint(session):
    customer, order = _customer_order(session)
    product = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="产品")
    stray = ontology.create_property(session, owner_type_id=product.id, name="客户号")
    with pytest.raises(InvariantViolationError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
            cardinality="one_to_one",
            foreign_key_property_id=stray.id,
        )


def test_relation_foreign_key_must_be_single_valued(session):
    customer, order = _customer_order(session)
    fk = ontology.create_property(session, owner_type_id=order.id, name="客户号", is_array=True)
    with pytest.raises(InvariantViolationError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
            cardinality="one_to_many",
            foreign_key_property_id=fk.id,
        )


def test_relation_foreign_key_datatype_must_match_primary_key(session):
    customer, order = _customer_order(session)
    ontology.create_property(
        session, owner_type_id=customer.id, name="客户号", datatype="integer", is_primary_key=True
    )
    fk = ontology.create_property(session, owner_type_id=order.id, name="客户号", datatype="text")
    with pytest.raises(InvariantViolationError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
            cardinality="one_to_many",
            foreign_key_property_id=fk.id,
        )


def test_validate_relation_patch_rejects_many_to_many_with_foreign_key(session):
    customer, order = _customer_order(session)
    fk = ontology.create_property(session, owner_type_id=order.id, name="客户号")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
        cardinality="one_to_many",
        foreign_key_property_id=fk.id,
    )
    with pytest.raises(InvariantViolationError):
        ontology.validate_relation_patch(
            session, relation_id=rel.id, patch={"cardinality": "many_to_many"}
        )
