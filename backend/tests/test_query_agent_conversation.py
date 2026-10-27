"""`test_query_agent.py` 中 conversation 相关测试。"""

from __future__ import annotations

import uuid

import pytest

from app.models import Graph
from app.services import conversation
from tests.support.query_agent_fixtures import API, build_client


@pytest.fixture
def client(session_factory):
    return build_client(session_factory)


def test_query_conversation_has_random_resource_and_no_graph(session):
    owner = uuid.uuid4()

    conv = conversation.create_conversation(session, owner_id=owner, title="新问答", kind="query")

    assert conv.kind == "query"
    assert session.get(Graph, conv.resource_id) is None


def test_query_conversation_api_creates_a_query_session(client):
    response = client.post(f"{API}/query/conversations")

    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "query"
    assert body["title"] == "新问答"

    listed = client.get(f"{API}/conversations", params={"kind": "query"})
    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [body["id"]]
