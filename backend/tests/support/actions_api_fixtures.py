"""`test_actions_api.py` 的共享 fixture、假客户端与构造 helper。"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.main import _create_app

API = "/api/v1"


def build_client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(_create_app(session_factory, enable_agent_checkpoints=False))


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return build_client(session_factory)


def _graph_id(client: TestClient) -> str:
    return client.post(f"{API}/conversations", json={"title": "动作"}).json()["resource_id"]


def _edits(client: TestClient, graph_id: str, *edits: dict) -> dict:
    response = client.post(f"{API}/graphs/{graph_id}/edits", json={"edits": list(edits)})
    assert response.status_code == 200, response.text
    return response.json()


def _ops(changeset: dict) -> list[tuple[str, str]]:
    return [(op["op"], op["payload"]["kind"]) for op in changeset["ops"]]


def _create_type(client: TestClient, graph_id: str, name: str) -> str:
    """建一个草稿对象类型，返回它的 id。"""
    return _edits(client, graph_id, {"op": "create_object_type", "name": name})["ops"][-1][
        "target_id"
    ]


def _create_action(client: TestClient, graph_id: str, name: str) -> str:
    return _edits(client, graph_id, {"op": "create_action_type", "name": name})["ops"][-1][
        "target_id"
    ]


def _confirm(client: TestClient, graph_id: str) -> None:
    """确认图上那一个未定稿的改动集，把草稿翻成已确认。"""
    changeset_id = client.get(f"{API}/graphs/{graph_id}/changesets?limit=1").json()[0]["id"]
    response = client.post(f"{API}/changesets/{changeset_id}/confirm")
    assert response.status_code == 200, response.text


def _snapshot_actions(client: TestClient, graph_id: str) -> list[dict]:
    return client.get(f"{API}/graphs/{graph_id}/snapshot").json()["actions"]
