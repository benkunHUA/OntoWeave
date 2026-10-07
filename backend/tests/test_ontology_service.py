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
