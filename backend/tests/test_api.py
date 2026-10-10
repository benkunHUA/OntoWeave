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


def test_graph_snapshot_endpoint_returns_canvas_data(client):
    graph_id = client.post(f"{API}/conversations", json={"title": "快照"}).json()["graph_id"]
    customer = client.post(f"{API}/object-types", json={"name": "客户"}).json()
    client.post(
        f"{API}/graphs/{graph_id}/objects",
        json={"object_type_id": customer["id"]},
    )

    snapshot = client.get(f"{API}/graphs/{graph_id}/snapshot")

    assert snapshot.status_code == 200
    body = snapshot.json()
    assert body["graph"]["name"] == "快照"
    assert [node["name"] for node in body["nodes"]] == ["客户"]
    assert body["nodes"][0]["properties"] == []
    assert body["relations"] == []


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


def test_list_object_types_paginates(client):
    for name in ("pg-a", "pg-b", "pg-c"):
        client.post(f"{API}/object-types", json={"name": name})

    all_items = client.get(f"{API}/object-types", params={"limit": 200}).json()
    assert len(all_items) >= 3

    page1 = client.get(f"{API}/object-types", params={"limit": 2}).json()
    page2 = client.get(f"{API}/object-types", params={"limit": 2, "offset": 2}).json()

    # 分页结果必须与整体列表的前缀一致，顺序也要稳定。
    assert page1 == all_items[:2]
    assert page2 == all_items[2:4]


def test_list_object_types_rejects_invalid_paging(client):
    assert client.get(f"{API}/object-types", params={"limit": 1000}).status_code == 422
    assert client.get(f"{API}/object-types", params={"limit": 0}).status_code == 422
    assert client.get(f"{API}/object-types", params={"offset": -1}).status_code == 422
