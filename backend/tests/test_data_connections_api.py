import uuid

import pytest
from fastapi.testclient import TestClient

from app.deps import get_data_service_client
from app.errors import DataServiceUnavailableError, NotFoundError
from app.main import _create_app
from app.models import Conversation
from app.services import conversation
from app.services.conversation import event as conversation_event
from app.vocabulary import ConversationKind


@pytest.fixture
def client(session_factory, fake_data_service):
    app = _create_app(session_factory, enable_agent_checkpoints=False)
    app.dependency_overrides[get_data_service_client] = lambda: fake_data_service
    return TestClient(app)


def test_create_returns_connection_without_creating_conversation(client, session):
    response = client.post(
        "/api/v1/data-connections",
        json={"name": "ERP", "config": {"host": "mysql", "database": "erp"}},
    )

    assert response.status_code == 201
    body = response.json()
    assert body["name"] == "ERP"
    assert body["kind"] == "mysql"
    assert session.get(Conversation, uuid.UUID(body["conversation_id"])) is None

    ensured = client.post(f"/api/v1/data-connections/{body['id']}/conversation")

    assert ensured.status_code == 201
    conv = conversation.get_conversation(session, uuid.UUID(ensured.json()["id"]))
    assert conv.kind == ConversationKind.DATA_CONNECTION.value
    assert str(conv.resource_id) == body["id"]


def test_tables_are_snake_case_and_keep_schema_key(client):
    response = client.get(f"/api/v1/data-connections/{uuid.uuid4()}/tables")

    assert response.status_code == 200
    table = response.json()["tables"][0]
    assert table["schema"] == "demo"
    assert table["has_primary_key"] is True


def test_state_hides_iceberg_details(client):
    response = client.get(f"/api/v1/data-connections/{uuid.uuid4()}/state")

    assert response.status_code == 200
    body = response.json()
    assert body["missing_steps"] == ["RUN_SYNC"]
    assert body["recent_runs"][0]["sync_task_id"]
    assert "activeIcebergCatalog" not in response.text
    assert "icebergNamespace" not in response.text


def test_state_returns_local_event_cursor(client, session_factory):
    created = client.post("/api/v1/data-connections", json={"name": "事件游标"}).json()
    connection_id = created["id"]
    client.post(f"/api/v1/data-connections/{connection_id}/conversation")
    conversation_event.ingest_envelope(
        session_factory,
        {
            "eventId": str(uuid.uuid4()),
            "eventType": "sync.run.queued",
            "payload": {"connectionId": connection_id, "summary": "排队"},
        },
    )

    state = client.get(f"/api/v1/data-connections/{connection_id}/state").json()
    with session_factory() as session:
        latest = conversation_event.list_recent_events(
            session, uuid.UUID(created["conversation_id"]), limit=1
        )

    assert state["last_event_id"] == str(latest[0].id)


def test_dataset_preview_returns_rows_and_version(client, fake_data_service):
    response = client.get(
        f"/api/v1/datasets/{fake_data_service.dataset_id}/preview",
        params={"version_id": str(uuid.uuid4()), "limit": 1},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["dataset_id"] == str(fake_data_service.dataset_id)
    assert body["version_number"] == 1
    assert body["columns"] == [
        {"name": "id", "type": "long", "nullable": False},
        {"name": "name", "type": "string", "nullable": True},
    ]
    assert body["row_count"] == 1
    assert body["rows"] == [{"id": 1, "name": "Ada"}]
    assert body["truncated"] is False


def test_dataset_live_preview_has_no_version(client, fake_data_service):
    response = client.get(
        f"/api/v1/datasets/{fake_data_service.dataset_id}/preview",
        params={"live": "true", "limit": 1},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["live"] is True
    assert body["version_id"] is None
    assert body["version_number"] is None
    assert body["snapshot_id"] == 100
    assert body["rows"] == [{"id": 1, "name": "Ada"}]


def test_dataset_preview_rejects_invalid_limit(client, fake_data_service):
    response = client.get(
        f"/api/v1/datasets/{fake_data_service.dataset_id}/preview",
        params={"limit": 0},
    )

    assert response.status_code == 422


def test_trigger_sync_returns_202(client, fake_data_service):
    response = client.post(
        f"/api/v1/data-connections/{fake_data_service.connection_id}/sync-runs",
        json={"sync_task_ids": [fake_data_service.tasks[0]["id"]]},
    )

    assert response.status_code == 202
    body = response.json()
    assert body["operation_id"]
    assert body["runs"][0]["status"] == "queued"


def test_java_not_found_maps_to_404(client, fake_data_service, monkeypatch):
    def missing(*args, **kwargs):  # noqa: ARG001
        raise NotFoundError("连接不存在")

    monkeypatch.setattr(fake_data_service, "get_connection", missing)
    response = client.get(f"/api/v1/data-connections/{uuid.uuid4()}")

    assert response.status_code == 404
    assert response.json()["error"] == "not_found"


def test_java_unavailable_maps_to_503(client, fake_data_service, monkeypatch):
    def down(*args, **kwargs):  # noqa: ARG001
        raise DataServiceUnavailableError("数据服务不可用")

    monkeypatch.setattr(fake_data_service, "get_connection", down)
    response = client.get(f"/api/v1/data-connections/{uuid.uuid4()}")

    assert response.status_code == 503
    assert response.json()["error"] == "data_service_unavailable"
