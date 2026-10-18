import uuid

import pytest

from app.errors import InvalidRequestError
from app.schemas.data_connection import (
    DataConnectionCreate,
    DataConnectionUpdate,
    SyncRunTriggerRequest,
)
from app.services import conversation, data_connection
from app.vocabulary import ConversationKind


def test_create_connection_binds_conversation_to_java_id(session, fake_data_service):
    owner = uuid.uuid4()

    created = data_connection.create_connection(
        session,
        owner_id=owner,
        client=fake_data_service,
        payload=DataConnectionCreate(name="ERP", config={"host": "mysql"}),
    )

    conv = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=uuid.UUID(created["id"]),
        owner_id=owner,
    )
    assert conv is not None
    assert conv.id == uuid.UUID(created["conversation_id"])
    assert conv.title == "ERP"


def test_create_draft_omits_empty_config(session, fake_data_service):
    data_connection.create_connection(
        session,
        owner_id=uuid.uuid4(),
        client=fake_data_service,
        payload=DataConnectionCreate(name="草稿连接"),
    )

    assert "config" not in fake_data_service.last_create_payload


def test_update_connection_renames_conversation(session, fake_data_service):
    owner = uuid.uuid4()
    created = data_connection.create_connection(
        session,
        owner_id=owner,
        client=fake_data_service,
        payload=DataConnectionCreate(name="ERP"),
    )

    updated = data_connection.update_connection(
        session,
        owner_id=owner,
        client=fake_data_service,
        connection_id=uuid.UUID(created["id"]),
        payload=DataConnectionUpdate(name="ERP 生产库"),
    )

    conv = conversation.get_conversation(
        session, uuid.UUID(created["conversation_id"]), owner_id=owner
    )
    assert updated["name"] == "ERP 生产库"
    assert conv.title == "ERP 生产库"


def test_trigger_sync_maps_task_ids_to_table_refs(fake_data_service):
    owner = uuid.uuid4()
    connection_id = fake_data_service.connection_id
    operation_id = uuid.uuid4()

    result = data_connection.trigger_sync(
        fake_data_service,
        owner_id=owner,
        connection_id=connection_id,
        payload=SyncRunTriggerRequest(
            sync_task_ids=[uuid.UUID(fake_data_service.tasks[0]["id"])],
            operation_id=operation_id,
        ),
    )

    assert result["operation_id"] == str(operation_id)
    assert result["runs"][0]["status"] == "queued"


def test_trigger_sync_rejects_unknown_task(fake_data_service):
    with pytest.raises(InvalidRequestError, match="同步任务不存在"):
        data_connection.trigger_sync(
            fake_data_service,
            owner_id=uuid.uuid4(),
            connection_id=fake_data_service.connection_id,
            payload=SyncRunTriggerRequest(sync_task_ids=[uuid.uuid4()]),
        )
