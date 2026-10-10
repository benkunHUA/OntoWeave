import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.errors import NotFoundError
from app.main import create_app
from app.models import Graph, ViewState
from app.services import conversation, view_state

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


def test_rename_conversation_updates_conversation_and_graph(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="新会话")
    view = view_state.upsert_view_state(
        session,
        graph_id=conv.graph_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    assert view.name == "新会话"

    renamed = conversation.rename_conversation(session, conv.id, title="民航本体", owner_id=owner)

    assert renamed.title == "民航本体"
    assert conversation.get_conversation(session, conv.id, owner_id=owner).title == "民航本体"
    graph = session.get(Graph, conv.graph_id)
    assert graph.name == "民航本体"
    assert session.get(ViewState, (conv.graph_id, owner)).name == "民航本体"


def test_rename_conversation_api_returns_updated_title(client):
    created = client.post(f"{API}/conversations", json={"title": "新会话"}).json()

    renamed = client.patch(
        f"{API}/conversations/{created['id']}",
        json={"title": "供应链本体"},
    )

    assert renamed.status_code == 200
    assert renamed.json()["title"] == "供应链本体"
    snapshot = client.get(f"{API}/graphs/{created['graph_id']}/snapshot").json()
    assert snapshot["graph"]["name"] == "供应链本体"


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


def test_conversation_messages_are_persisted_in_order(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="历史")
    conversation.add_message(session, conversation_id=conv.id, role="user", content="客户会下订单")
    conversation.add_message(
        session,
        conversation_id=conv.id,
        role="assistant",
        content="我把它整理成两个类型。",
    )

    messages = conversation.list_messages(session, conv.id, owner_id=owner)

    assert [(message.role, message.content) for message in messages] == [
        ("user", "客户会下订单"),
        ("assistant", "我把它整理成两个类型。"),
    ]


def test_conversation_messages_are_scoped_to_owner(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="私有")
    conversation.add_message(session, conversation_id=conv.id, role="user", content="secret")

    with pytest.raises(NotFoundError):
        conversation.list_messages(session, conv.id, owner_id=uuid.uuid4())


def test_conversation_messages_api_returns_history(client):
    created = client.post(f"{API}/conversations", json={"title": "消息接口"}).json()
    conversation_id = created["id"]

    empty = client.get(f"{API}/conversations/{conversation_id}/messages")
    assert empty.status_code == 200
    assert empty.json() == []
