import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.config import get_settings
from app.main import create_app
from app.services import ontology

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    """不覆盖依赖：app 直接拿到绑在测试连接上的 session 工厂，走真实装配链路。"""
    return TestClient(create_app(session_factory=session_factory))


def test_create_and_confirm_object_type(client):
    created = client.post(f"{API}/object-types", json={"name": "客户"})
    assert created.status_code == 201
    body = created.json()
    assert body["status"] == "draft"

    confirmed = client.post(f"{API}/object-types/{body['id']}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_confirm_twice_is_rejected(client):
    body = client.post(f"{API}/object-types", json={"name": "订单"}).json()
    client.post(f"{API}/object-types/{body['id']}/confirm")
    again = client.post(f"{API}/object-types/{body['id']}/confirm")
    assert again.status_code == 409
    assert again.json()["error"] == "invalid_transition"


def test_unknown_object_type_returns_404(client):
    resp = client.post(f"{API}/object-types/{uuid.uuid4()}/confirm")
    assert resp.status_code == 404
    assert resp.json()["error"] == "not_found"


def test_graph_relation_endpoint_invariant_returns_409(client, session_factory):
    with session_factory() as s:
        customer = ontology.create_object_type(s, owner_id=uuid.uuid4(), name="客户")
        order = ontology.create_object_type(s, owner_id=uuid.uuid4(), name="订单")
        rel = ontology.create_relation(
            s,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=order.id,
            name="下订单",
        )
        s.commit()
        customer_id, relation_id = customer.id, rel.id

    gid = client.post(f"{API}/graphs", json={"name": "销售域"}).json()["id"]
    added = client.post(f"{API}/graphs/{gid}/objects", json={"object_type_id": str(customer_id)})
    assert added.status_code == 204

    resp = client.post(f"{API}/graphs/{gid}/relations", json={"relation_id": str(relation_id)})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invariant_violation"


def test_object_type_response_has_no_internal_fields(client):
    body = client.post(f"{API}/object-types", json={"name": "产品"}).json()
    assert set(body) == {"id", "name", "description", "status"}


def test_response_carries_request_id_header(client):
    resp = client.get("/health")
    assert resp.headers["x-request-id"]


def test_request_id_is_propagated_when_supplied(client):
    resp = client.get("/health", headers={"X-Request-ID": "trace-me-123"})
    assert resp.headers["x-request-id"] == "trace-me-123"


def test_default_app_wires_factory_from_settings():
    """不带参数装配时，连接串必须来自应用配置，而不是被写死在别处。"""
    app = create_app()
    engine = app.state.session_factory.kw["bind"]
    assert engine.url.render_as_string(hide_password=False) == (get_settings().database_url)
