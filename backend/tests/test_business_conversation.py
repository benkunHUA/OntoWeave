"""业务会话的创建、迁移与列表测试。"""

from __future__ import annotations

import uuid

import pytest
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import Engine, text

from alembic import command
from app.main import create_app
from app.models import Graph
from app.services import conversation
from app.vocabulary import ConversationKind

API = "/api/v1"


@pytest.fixture
def client(session_factory):
    return TestClient(create_app(session_factory=session_factory))


def test_business_conversation_has_random_resource_and_no_graph(session):
    owner = uuid.uuid4()

    conv = conversation.create_conversation(
        session, owner_id=owner, title="新问答", kind="business"
    )

    assert conv.kind == "business"
    assert session.get(Graph, conv.resource_id) is None


def test_business_conversation_api_creates_a_business_session(client):
    response = client.post(f"{API}/business/conversations")

    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "business"
    assert body["title"] == "新业务会话"

    listed = client.get(f"{API}/conversations", params={"kind": "business"})
    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [body["id"]]


def test_legacy_query_routes_and_filters_remain_compatible(client):
    created = client.post(f"{API}/query/conversations")

    assert created.status_code == 201
    body = created.json()
    assert body["kind"] == "business"

    for kind in ("business", "query"):
        listed = client.get(f"{API}/conversations", params={"kind": kind})
        assert listed.status_code == 200
        assert [item["id"] for item in listed.json()] == [body["id"]]


def test_conversation_kind_parses_legacy_query_alias():
    assert ConversationKind("query") is ConversationKind.BUSINESS
    assert ConversationKind.BUSINESS.value == "business"


def test_migration_0025_renames_query_conversations(engine: Engine, alembic_config: Config) -> None:
    from tests.test_migrations import _isolated_migration_schema

    with _isolated_migration_schema(engine, alembic_config) as (
        schema_config,
        schema_engine,
    ):
        command.upgrade(schema_config, "0024")
        conversation_id = uuid.uuid4()
        with schema_engine.begin() as connection:
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, kind, resource_id) "
                    "VALUES (:id, :owner_id, 'query', :resource_id)"
                ),
                {
                    "id": conversation_id,
                    "owner_id": uuid.uuid4(),
                    "resource_id": uuid.uuid4(),
                },
            )

        command.upgrade(schema_config, "0025")
        with schema_engine.connect() as connection:
            kind = connection.execute(
                text("SELECT kind FROM conversation WHERE id = :id"),
                {"id": conversation_id},
            ).scalar_one()
        assert kind == "business"

        with pytest.raises(RuntimeError, match="business conversations"):
            command.downgrade(schema_config, "0024")

        with schema_engine.begin() as connection:
            connection.execute(
                text("DELETE FROM conversation WHERE id = :id"),
                {"id": conversation_id},
            )
        command.downgrade(schema_config, "0024")

        with schema_engine.begin() as connection:
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, kind, resource_id) "
                    "VALUES (:id, :owner_id, 'query', :resource_id)"
                ),
                {
                    "id": uuid.uuid4(),
                    "owner_id": uuid.uuid4(),
                    "resource_id": uuid.uuid4(),
                },
            )
