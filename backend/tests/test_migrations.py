import copy
import uuid
from collections.abc import Iterator
from contextlib import contextmanager

import pytest
from alembic.config import Config
from sqlalchemy import Engine, create_engine, inspect, text
from sqlalchemy.engine import make_url

from alembic import command


@contextmanager
def _isolated_migration_schema(
    engine: Engine, alembic_config: Config
) -> Iterator[tuple[Config, Engine]]:
    schema = f"migration_test_{uuid.uuid4().hex}"
    with engine.begin() as connection:
        connection.execute(text(f'CREATE SCHEMA "{schema}"'))

    schema_url = make_url(engine.url).update_query_dict(
        {"options": f"-csearch_path={schema},public"}
    )
    schema_engine = create_engine(schema_url)
    schema_config = copy.copy(alembic_config)
    rendered_url = schema_url.render_as_string(hide_password=False)
    schema_config.attributes = {
        **alembic_config.attributes,
        "sqlalchemy_url": rendered_url,
        "version_table_schema": schema,
    }
    try:
        yield schema_config, schema_engine
    finally:
        schema_engine.dispose()
        with engine.begin() as connection:
            connection.execute(text(f'DROP SCHEMA IF EXISTS "{schema}" CASCADE'))


def test_migrations_match_models(engine: Engine, alembic_config: Config) -> None:
    """模型和迁移一旦漂移，alembic check 会抛异常，测试随之失败。"""
    command.check(alembic_config)


def test_conversation_uses_kind_and_resource_id(engine: Engine) -> None:
    columns = {column["name"] for column in inspect(engine).get_columns("conversation")}
    assert {"kind", "resource_id"} <= columns
    assert "graph_id" not in columns


def test_conversation_event_table_exists(engine: Engine) -> None:
    columns = {column["name"] for column in inspect(engine).get_columns("conversation_event")}
    assert {
        "id",
        "conversation_id",
        "event_id",
        "operation_id",
        "run_id",
        "event_type",
        "summary",
        "payload",
        "created_at",
    } <= columns


def test_migration_0018_backfills_and_restores_graph_id(
    engine: Engine, alembic_config: Config
) -> None:
    with _isolated_migration_schema(engine, alembic_config) as (
        schema_config,
        schema_engine,
    ):
        command.upgrade(schema_config, "0017")

        graph_id = uuid.uuid4()
        conversation_id = uuid.uuid4()
        with schema_engine.begin() as connection:
            connection.execute(
                text("INSERT INTO graph (id, owner_id) VALUES (:id, :owner_id)"),
                {"id": graph_id, "owner_id": uuid.uuid4()},
            )
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, graph_id) "
                    "VALUES (:id, :owner_id, :graph_id)"
                ),
                {
                    "id": conversation_id,
                    "owner_id": uuid.uuid4(),
                    "graph_id": graph_id,
                },
            )

        command.upgrade(schema_config, "0018")
        with schema_engine.connect() as connection:
            row = connection.execute(
                text("SELECT kind, resource_id FROM conversation WHERE id = :id"),
                {"id": conversation_id},
            ).one()
        assert row.kind == "modeling"
        assert row.resource_id == graph_id

        command.downgrade(schema_config, "0017")
        with schema_engine.connect() as connection:
            restored = connection.execute(
                text("SELECT graph_id FROM conversation WHERE id = :id"),
                {"id": conversation_id},
            ).scalar_one()
        assert restored == graph_id


def test_migration_0018_downgrade_rejects_non_modeling_conversation(
    engine: Engine, alembic_config: Config
) -> None:
    with _isolated_migration_schema(engine, alembic_config) as (
        schema_config,
        schema_engine,
    ):
        command.upgrade(schema_config, "0018")
        with schema_engine.begin() as connection:
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, kind, resource_id) "
                    "VALUES (:id, :owner_id, 'data_connection', :resource_id)"
                ),
                {
                    "id": uuid.uuid4(),
                    "owner_id": uuid.uuid4(),
                    "resource_id": uuid.uuid4(),
                },
            )

        with pytest.raises(RuntimeError, match="non-modeling conversations"):
            command.downgrade(schema_config, "0017")


def test_migration_0018_downgrade_rejects_orphaned_modeling_conversation(
    engine: Engine, alembic_config: Config
) -> None:
    with _isolated_migration_schema(engine, alembic_config) as (
        schema_config,
        schema_engine,
    ):
        command.upgrade(schema_config, "0018")
        with schema_engine.begin() as connection:
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, kind, resource_id) "
                    "VALUES (:id, :owner_id, 'modeling', :resource_id)"
                ),
                {
                    "id": uuid.uuid4(),
                    "owner_id": uuid.uuid4(),
                    "resource_id": uuid.uuid4(),
                },
            )

        with pytest.raises(RuntimeError, match="missing graph rows"):
            command.downgrade(schema_config, "0017")


def test_data_pipeline_conversation_and_turn_columns(engine: Engine) -> None:
    inspector = inspect(engine)
    conversation_columns = {column["name"] for column in inspector.get_columns("conversation")}
    event_columns = {column["name"] for column in inspector.get_columns("conversation_event")}

    assert "archived_at" in conversation_columns
    assert "turn_id" in event_columns
    assert "ix_conversation_event_turn" in {
        index["name"] for index in inspector.get_indexes("conversation_event")
    }


def test_migration_0020_allows_data_pipeline_and_rejects_unsafe_downgrade(
    engine: Engine, alembic_config: Config
) -> None:
    with _isolated_migration_schema(engine, alembic_config) as (
        schema_config,
        schema_engine,
    ):
        command.upgrade(schema_config, "head")
        conversation_id = uuid.uuid4()
        with schema_engine.begin() as connection:
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, kind, resource_id) "
                    "VALUES (:id, :owner_id, 'data_pipeline', :resource_id)"
                ),
                {
                    "id": conversation_id,
                    "owner_id": uuid.uuid4(),
                    "resource_id": uuid.uuid4(),
                },
            )

        with pytest.raises(RuntimeError, match="data_pipeline conversations"):
            command.downgrade(schema_config, "0019")

        with schema_engine.begin() as connection:
            connection.execute(
                text("DELETE FROM conversation WHERE id = :id"), {"id": conversation_id}
            )
        command.downgrade(schema_config, "0019")
        with schema_engine.connect() as connection:
            columns = {
                row.column_name
                for row in connection.execute(
                    text(
                        "SELECT column_name FROM information_schema.columns "
                        "WHERE table_schema = :schema AND table_name = 'conversation'"
                    ),
                    {"schema": schema_config.attributes["version_table_schema"]},
                )
            }
        assert "archived_at" not in columns


def test_migration_0020_downgrade_rejects_non_null_turn_id(
    engine: Engine, alembic_config: Config
) -> None:
    with _isolated_migration_schema(engine, alembic_config) as (
        schema_config,
        schema_engine,
    ):
        command.upgrade(schema_config, "head")
        conversation_id = uuid.uuid4()
        with schema_engine.begin() as connection:
            connection.execute(
                text(
                    "INSERT INTO conversation (id, owner_id, kind, resource_id) "
                    "VALUES (:id, :owner_id, 'modeling', :resource_id)"
                ),
                {
                    "id": conversation_id,
                    "owner_id": uuid.uuid4(),
                    "resource_id": uuid.uuid4(),
                },
            )
            connection.execute(
                text(
                    "INSERT INTO conversation_event "
                    "(id, conversation_id, event_id, event_type, summary, payload, turn_id) "
                    "VALUES (:id, :conversation_id, :event_id, 'pipeline.draft.changed', "
                    "'changed', '{}'::jsonb, :turn_id)"
                ),
                {
                    "id": uuid.uuid4(),
                    "conversation_id": conversation_id,
                    "event_id": uuid.uuid4(),
                    "turn_id": uuid.uuid4(),
                },
            )

        with pytest.raises(RuntimeError, match="turn ids"):
            command.downgrade(schema_config, "0019")
