import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.errors import InvariantViolationError, NotFoundError
from app.main import create_app
from app.models import ConversationMessage, Graph, ViewState
from app.services import conversation, view_state

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def test_create_conversation_creates_a_graph(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="销售域建模")
    assert session.get(Graph, conv.resource_id) is not None
    assert conv.title == "销售域建模"


def test_modeling_conversation_exposes_resource_id(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="建模会话")
    assert conv.kind == "modeling"
    assert session.get(Graph, conv.resource_id) is not None


def test_create_resource_conversation_does_not_create_a_graph(session):
    owner = uuid.uuid4()
    resource_id = uuid.uuid4()
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=resource_id
    )
    assert conv.kind == "data_connection"
    assert conv.resource_id == resource_id
    assert session.query(Graph).filter(Graph.owner_id == owner).count() == 0


def test_create_modeling_conversation_rejects_resource_id(session):
    with pytest.raises(InvariantViolationError, match="resource_id"):
        conversation.create_conversation(
            session, owner_id=uuid.uuid4(), kind="modeling", resource_id=uuid.uuid4()
        )


def test_create_resource_conversation_requires_resource_id(session):
    with pytest.raises(InvariantViolationError, match="resource_id"):
        conversation.create_conversation(session, owner_id=uuid.uuid4(), kind="data_connection")


def test_service_rejects_unsupported_conversation_kind(session):
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind"):
        conversation.create_conversation(
            session, owner_id=uuid.uuid4(), kind="not_a_kind", resource_id=uuid.uuid4()
        )
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind"):
        conversation.find_conversation_by_resource(
            session, kind="not_a_kind", resource_id=uuid.uuid4()
        )
    with pytest.raises(InvariantViolationError, match="unsupported conversation kind"):
        conversation.list_conversations(session, kind="not_a_kind")


def test_delete_resource_conversation_does_not_touch_graph(session):
    owner = uuid.uuid4()
    graph_conv = conversation.create_conversation(session, owner_id=owner, title="建模")
    view_state.upsert_view_state(
        session,
        graph_id=graph_conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )
    message = conversation.add_message(
        session, conversation_id=conv.id, role="user", content="连接信息"
    )
    message_id = message.id
    session.commit()

    conversation.delete_conversation(session, conv.id, owner_id=owner)
    session.expire_all()

    assert session.get(Graph, graph_conv.resource_id) is not None
    assert session.get(ViewState, (graph_conv.resource_id, owner)).name == "建模"
    assert session.get(ConversationMessage, message_id) is None


def test_find_conversation_by_resource(session):
    owner = uuid.uuid4()
    resource_id = uuid.uuid4()
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=resource_id
    )
    found = conversation.find_conversation_by_resource(
        session, kind="data_connection", resource_id=resource_id, owner_id=owner
    )
    assert found is not None and found.id == conv.id


def test_list_conversations_filters_by_kind(session):
    owner = uuid.uuid4()
    conversation.create_conversation(session, owner_id=owner, title="建模")
    resource = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )

    listed = conversation.list_conversations(session, owner_id=owner, kind="data_connection")

    assert [item.id for item in listed] == [resource.id]


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
    assert body["kind"] == "modeling"
    assert body["resource_id"]
    assert "graph_id" not in body

    fetched = client.get(f"{API}/conversations/{body['id']}")
    assert fetched.status_code == 200
    assert fetched.json()["resource_id"] == body["resource_id"]

    listed = client.get(f"{API}/conversations", params={"limit": 50}).json()
    assert any(item["id"] == body["id"] for item in listed)


def test_create_conversation_api_rejects_resource_fields(client):
    response = client.post(
        f"{API}/conversations",
        json={
            "title": "数据连接",
            "kind": "data_connection",
            "resource_id": str(uuid.uuid4()),
        },
    )

    assert response.status_code == 422
    assert client.get(f"{API}/conversations").json() == []


def test_update_conversation_api_rejects_resource_fields(client):
    created = client.post(f"{API}/conversations", json={"title": "新会话"}).json()

    response = client.patch(
        f"{API}/conversations/{created['id']}",
        json={"title": "改名", "kind": "data_connection"},
    )

    assert response.status_code == 422


def test_rename_conversation_updates_conversation_and_graph(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="新会话")
    view = view_state.upsert_view_state(
        session,
        graph_id=conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    assert view.name == "新会话"

    renamed = conversation.rename_conversation(session, conv.id, title="民航本体", owner_id=owner)

    assert renamed.title == "民航本体"
    assert conversation.get_conversation(session, conv.id, owner_id=owner).title == "民航本体"
    graph = session.get(Graph, conv.resource_id)
    assert graph.name == "民航本体"
    assert session.get(ViewState, (conv.resource_id, owner)).name == "民航本体"


def test_rename_resource_conversation_does_not_touch_graph_or_view_state(session):
    owner = uuid.uuid4()
    graph_conv = conversation.create_conversation(session, owner_id=owner, title="建模")
    view_state.upsert_view_state(
        session,
        graph_id=graph_conv.resource_id,
        user_id=owner,
        spec={"layout": {"kind": "grid"}},
        owner_id=owner,
    )
    conv = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )

    renamed = conversation.rename_conversation(session, conv.id, title="生产库连接", owner_id=owner)

    assert renamed.title == "生产库连接"
    assert session.get(Graph, graph_conv.resource_id).name == "建模"
    assert session.get(ViewState, (graph_conv.resource_id, owner)).name == "建模"


def test_rename_conversation_api_returns_updated_title(client):
    created = client.post(f"{API}/conversations", json={"title": "新会话"}).json()

    renamed = client.patch(
        f"{API}/conversations/{created['id']}",
        json={"title": "供应链本体"},
    )

    assert renamed.status_code == 200
    assert renamed.json()["title"] == "供应链本体"
    snapshot = client.get(f"{API}/graphs/{created['resource_id']}/snapshot").json()
    assert snapshot["graph"]["name"] == "供应链本体"


def test_list_conversations_only_returns_own(session):
    mine = uuid.uuid4()
    conversation.create_conversation(session, owner_id=mine, title="mine")
    conversation.create_conversation(session, owner_id=uuid.uuid4(), title="other")

    titles = [c.title for c in conversation.list_conversations(session, owner_id=mine)]
    assert titles == ["mine"]


def test_list_conversations_api_filters_by_kind(client, session):
    from app.config import get_settings

    owner = uuid.UUID(get_settings().default_user_id)
    conversation.create_conversation(session, owner_id=owner, title="建模")
    resource = conversation.create_conversation(
        session, owner_id=owner, title="数据连接", kind="data_connection", resource_id=uuid.uuid4()
    )
    session.commit()

    listed = client.get(f"{API}/conversations", params={"kind": "data_connection"}).json()

    assert [item["id"] for item in listed] == [str(resource.id)]


def test_list_conversations_api_rejects_unknown_kind(client):
    response = client.get(f"{API}/conversations", params={"kind": "not_a_kind"})

    assert response.status_code == 422


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


def test_delete_conversation_removes_graph_and_children(session):
    from app.models import Changeset
    from app.services import changeset as changeset_service

    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="待删")
    graph_id = conv.resource_id
    conversation.add_message(session, conversation_id=conv.id, role="user", content="你好")
    cs = changeset_service.create_changeset(session, graph_id=graph_id, owner_id=owner)
    cs_id = cs.id

    conversation.delete_conversation(session, conv.id, owner_id=owner)
    # 级联是数据库做的，ORM 身份映射里还留着对象；过期后按库里的真实状态断言。
    session.expire_all()

    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, conv.id)
    assert session.get(Graph, graph_id) is None
    assert session.get(Changeset, cs_id) is None


def test_delete_conversation_rejects_other_owner(session):
    owner = uuid.uuid4()
    conv = conversation.create_conversation(session, owner_id=owner, title="别人的")
    with pytest.raises(NotFoundError):
        conversation.delete_conversation(session, conv.id, owner_id=uuid.uuid4())
    # 没被误删
    assert conversation.get_conversation(session, conv.id) is not None


def test_delete_conversation_api_scope_cleans_local_drafts(client, session):
    from app.config import get_settings
    from app.models import ObjectType
    from app.services import ontology

    created = client.post(f"{API}/conversations", json={"title": "接口连草稿删"}).json()
    owner = uuid.UUID(get_settings().default_user_id)
    graph_id = uuid.UUID(created["resource_id"])
    # API 建不出绑定 home_graph_id 的草稿，借 service 造一条贴近真实的数据。
    draft = ontology.create_object_type(
        session, owner_id=owner, name="接口草稿", home_graph_id=graph_id
    )
    draft_id = draft.id
    session.flush()

    resp = client.delete(
        f"{API}/conversations/{created['id']}", params={"scope": "graph-and-drafts"}
    )
    assert resp.status_code == 204
    assert client.get(f"{API}/conversations/{created['id']}").status_code == 404

    session.expire_all()
    assert session.get(ObjectType, draft_id) is None


def test_delete_conversation_api(client):
    conv = client.post(f"{API}/conversations", json={"title": "删除我"}).json()
    resp = client.delete(f"{API}/conversations/{conv['id']}")
    assert resp.status_code == 204
    assert client.get(f"{API}/conversations/{conv['id']}").status_code == 404
    remaining = [item["id"] for item in client.get(f"{API}/conversations").json()]
    assert conv["id"] not in remaining


def test_delete_missing_conversation_api_404(client):
    assert client.delete(f"{API}/conversations/{uuid.uuid4()}").status_code == 404
