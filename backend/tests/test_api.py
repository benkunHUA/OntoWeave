import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.deps import get_session
from app.main import create_app
from app.services import ontology


@pytest.fixture
def client(session: Session) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_session] = lambda: session
    return TestClient(app)


def test_create_and_confirm_object_type(client):
    created = client.post("/object-types", json={"name": "客户"})
    assert created.status_code == 201
    body = created.json()
    assert body["status"] == "draft"

    confirmed = client.post(f"/object-types/{body['id']}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_confirm_twice_is_rejected(client):
    body = client.post("/object-types", json={"name": "订单"}).json()
    client.post(f"/object-types/{body['id']}/confirm")
    again = client.post(f"/object-types/{body['id']}/confirm")
    assert again.status_code == 409
    assert again.json()["error"] == "invalid_transition"


def test_unknown_object_type_returns_404(client):
    resp = client.post(f"/object-types/{uuid.uuid4()}/confirm")
    assert resp.status_code == 404
    assert resp.json()["error"] == "not_found"


def test_graph_relation_endpoint_invariant_returns_409(client, session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    gid = client.post("/graphs", json={"name": "销售域"}).json()["id"]
    client.post(f"/graphs/{gid}/objects", json={"object_type_id": str(customer.id)})

    resp = client.post(f"/graphs/{gid}/relations", json={"relation_id": str(rel.id)})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invariant_violation"


def test_object_type_response_has_no_internal_fields(client):
    body = client.post("/object-types", json={"name": "产品"}).json()
    assert set(body) == {"id", "name", "description", "status"}
