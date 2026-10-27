"""`test_api_agent.py` 中 validation 相关测试。"""

import uuid

import pytest

from app.config import get_settings
from app.errors import InvariantViolationError
from app.services import conversation
from tests.support.api_agent_fixtures import build_client


@pytest.fixture
def client(session_factory, monkeypatch):
    return build_client(session_factory, monkeypatch)


def test_validate_conversation_accepts_data_connection(session):
    from app.agent import runtime

    conv = conversation.create_conversation(
        session,
        owner_id=uuid.UUID(get_settings().default_user_id),
        title="数据连接",
        kind="data_connection",
        resource_id=uuid.uuid4(),
    )
    session.commit()

    checked = runtime.validate_conversation(
        session,
        conversation_id=conv.id,
        owner_id=uuid.UUID(get_settings().default_user_id),
    )

    assert checked.id == conv.id


def test_validate_conversation_rejects_archived(session):
    from app.agent import runtime

    owner_id = uuid.UUID(get_settings().default_user_id)
    conv = conversation.create_conversation(
        session,
        owner_id=owner_id,
        title="已归档",
        kind="data_connection",
        resource_id=uuid.uuid4(),
    )
    conversation.archive_conversation(session, conv.id, owner_id=owner_id)
    session.commit()

    with pytest.raises(InvariantViolationError, match="archived"):
        runtime.validate_conversation(session, conversation_id=conv.id, owner_id=owner_id)


@pytest.mark.parametrize(
    ("path", "body"),
    [
        ("messages", {"text": "继续"}),
        ("resume", {"answer": "客户"}),
    ],
)
def test_agent_write_endpoints_reject_archived_before_sse(client, session, path, body):
    conv = client.post("/api/v1/conversations", json={"title": "归档写"}).json()
    conversation.archive_conversation(
        session,
        uuid.UUID(conv["id"]),
        owner_id=uuid.UUID(get_settings().default_user_id),
    )
    session.commit()

    response = client.post(f"/api/v1/conversations/{conv['id']}/{path}", json=body)

    assert response.status_code == 409
    assert response.headers["content-type"].startswith("application/json")
    assert response.json()["error"] == "invariant_violation"


def test_message_endpoint_rejects_unknown_conversation(client):
    resp = client.post(f"/api/v1/conversations/{uuid.uuid4()}/messages", json={"text": "hi"})
    assert resp.status_code == 404
