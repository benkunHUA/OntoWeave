import uuid

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError
from sqlalchemy.orm import Session, sessionmaker

from app.errors import NotFoundError
from app.main import create_app
from app.services import conversation, view_state

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def _graph_id(session) -> uuid.UUID:
    return conversation.create_conversation(session, owner_id=uuid.uuid4(), title="测试").graph_id


def test_absent_view_state_returns_none(session):
    assert (
        view_state.get_view_state(session, graph_id=_graph_id(session), user_id=uuid.uuid4())
        is None
    )


def test_upsert_creates_then_updates(session):
    graph_id = _graph_id(session)
    user_id = uuid.uuid4()

    created = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=user_id,
        spec={"nodes": {"t_customer": {"x": 120, "y": 340}}},
    )
    assert created.name == "测试"

    updated = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=user_id,
        spec={"nodes": {}},
    )
    assert updated.spec == {"nodes": {}}


def test_upsert_rejects_unknown_graph(session):
    with pytest.raises(NotFoundError):
        view_state.upsert_view_state(session, graph_id=uuid.uuid4(), user_id=uuid.uuid4(), spec={})


def test_view_state_api_roundtrip(client):
    graph_id = client.post(f"{API}/conversations", json={"title": "视图测试"}).json()["graph_id"]

    empty = client.get(f"{API}/graphs/{graph_id}/view-state")
    assert empty.status_code == 200
    assert empty.json()["spec"] == {}

    saved = client.put(
        f"{API}/graphs/{graph_id}/view-state",
        json={"spec": {"layout": {"kind": "domain-grouped"}}},
    )
    assert saved.status_code == 200
    assert saved.json()["name"] == "视图测试"

    fetched = client.get(f"{API}/graphs/{graph_id}/view-state")
    assert fetched.json()["spec"] == {"layout": {"kind": "domain-grouped"}}


def test_get_view_state_rejects_unknown_graph(session):
    with pytest.raises(NotFoundError):
        view_state.get_view_state(session, graph_id=uuid.uuid4(), user_id=uuid.uuid4())


def test_api_get_view_state_returns_404_for_unknown_graph(client):
    """GET 和 PUT 对同一个不存在的图必须给出一致的结果。"""
    missing = uuid.uuid4()
    assert client.get(f"{API}/graphs/{missing}/view-state").status_code == 404
    assert client.put(f"{API}/graphs/{missing}/view-state", json={"spec": {}}).status_code == 404


def test_upsert_rejects_a_malformed_spec(session):
    """规格是 agent 工具与前端共写的形状，坏数据在落库前就该挡下。"""
    with pytest.raises(ValidationError):
        view_state.upsert_view_state(
            session,
            graph_id=_graph_id(session),
            user_id=uuid.uuid4(),
            spec={"highlight": [{"style": "focus"}]},
        )


def test_upsert_keeps_unknown_keys_and_adds_none(session):
    graph_id = _graph_id(session)

    saved = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=uuid.uuid4(),
        spec={"layout": {"kind": "grid"}, "somethingNew": {"a": 1}},
    )

    assert saved.spec == {"layout": {"kind": "grid"}, "somethingNew": {"a": 1}}


def test_view_state_api_rejects_a_malformed_spec(client):
    graph_id = client.post(f"{API}/conversations", json={"title": "坏规格"}).json()["graph_id"]

    response = client.put(f"{API}/graphs/{graph_id}/view-state", json={"spec": {"dim": "全部"}})

    assert response.status_code == 422
