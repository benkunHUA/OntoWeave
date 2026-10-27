"""`test_conversation.py` 中 api 相关测试。"""

import uuid

import pytest

from app.services import conversation
from tests.support.conversation_fixtures import API, build_client


@pytest.fixture
def client(session_factory):
    return build_client(session_factory)


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


def test_conversation_messages_api_returns_history(client):
    created = client.post(f"{API}/conversations", json={"title": "消息接口"}).json()
    conversation_id = created["id"]

    empty = client.get(f"{API}/conversations/{conversation_id}/messages")
    assert empty.status_code == 200
    assert empty.json() == []


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


def test_archived_conversation_api_rejects_patch_and_delete(client, session):
    from app.config import get_settings

    owner = uuid.UUID(get_settings().default_user_id)
    conv = conversation.create_conversation(session, owner_id=owner, title="归档接口")
    conversation.archive_conversation(session, conv.id, owner_id=owner)
    session.commit()

    assert client.get(f"{API}/conversations/{conv.id}").status_code == 200
    assert client.get(f"{API}/conversations").json() == []
    assert client.patch(f"{API}/conversations/{conv.id}", json={"title": "改"}).status_code == 409
    assert client.delete(f"{API}/conversations/{conv.id}").status_code == 409
