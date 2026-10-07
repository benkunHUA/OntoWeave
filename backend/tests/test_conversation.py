import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.errors import NotFoundError
from app.main import create_app
from app.services import conversation

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def test_create_conversation_creates_a_graph(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="销售域建模")
    assert conv.graph_id is not None
    assert conv.title == "销售域建模"


def test_list_conversations_paginates(session):
    owner = uuid.uuid4()
    conversation.create_conversation(session, owner_id=owner, title="c-1")
    conversation.create_conversation(session, owner_id=owner, title="c-2")
    conversation.create_conversation(session, owner_id=owner, title="c-3")

    page = conversation.list_conversations(session, limit=2, offset=1)
    assert len(page) == 2


def test_missing_conversation_raises(session):
    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, uuid.uuid4())


def test_conversation_api_roundtrip(client):
    created = client.post(f"{API}/conversations", json={"title": "供应链梳理"})
    assert created.status_code == 201
    body = created.json()
    assert body["title"] == "供应链梳理"
    assert body["graph_id"]

    fetched = client.get(f"{API}/conversations/{body['id']}")
    assert fetched.status_code == 200
    assert fetched.json()["graph_id"] == body["graph_id"]

    listed = client.get(f"{API}/conversations", params={"limit": 50}).json()
    assert any(item["id"] == body["id"] for item in listed)


def test_list_conversations_only_returns_own(session):
    mine = uuid.uuid4()
    conversation.create_conversation(session, owner_id=mine, title="mine")
    conversation.create_conversation(session, owner_id=uuid.uuid4(), title="other")

    titles = [c.title for c in conversation.list_conversations(session, owner_id=mine)]
    assert titles == ["mine"]


def test_get_conversation_hides_other_owner(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="other")
    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, conv.id, owner_id=uuid.uuid4())
