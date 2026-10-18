import json
import uuid

from fastapi.testclient import TestClient

from app.config import DEFAULT_USER_ID
from app.main import _create_app
from app.services import conversation, conversation_event
from app.services.conversation_event_stream import iter_event_stream
from app.services.event_bus import ConversationEventBus
from app.vocabulary import ConversationKind

OWNER_ID = uuid.UUID(DEFAULT_USER_ID)


def _create(session, connection_id: uuid.UUID) -> None:
    conversation.create_conversation(
        session,
        owner_id=OWNER_ID,
        title="ERP",
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=connection_id,
    )


def _envelope(event_type: str, payload: dict) -> dict:
    return {
        "schemaVersion": 1,
        "eventId": str(uuid.uuid4()),
        "eventType": event_type,
        "occurredAt": "2026-10-18T00:00:00Z",
        "payload": payload,
    }


def test_ingest_is_idempotent_and_uses_connection_id(session_factory):
    connection_id = uuid.uuid4()
    with session_factory() as session:
        _create(session, connection_id)
        session.commit()

    envelope = _envelope(
        "sync.run.queued",
        {"connectionId": str(connection_id), "runId": str(uuid.uuid4()), "summary": "已进入队列"},
    )
    assert conversation_event.ingest_envelope(session_factory, envelope) is True
    assert conversation_event.ingest_envelope(session_factory, envelope) is False

    with session_factory() as session:
        rows = conversation_event.list_recent_events(session, _conversation_id(session))
    assert len(rows) == 1
    assert rows[0].event_type == "sync.run.queued"
    assert rows[0].summary == "已进入队列"


def _conversation_id(session) -> uuid.UUID:
    return session.query(conversation.Conversation).one().id


def test_test_event_uses_conversation_id(session_factory):
    connection_id = uuid.uuid4()
    with session_factory() as session:
        _create(session, connection_id)
        conversation_id = conversation.find_conversation_by_resource(
            session,
            kind=ConversationKind.DATA_CONNECTION.value,
            resource_id=connection_id,
        ).id
        session.commit()

    envelope = _envelope(
        "connection.tested",
        {"conversationId": str(conversation_id), "summary": "连接测试成功"},
    )

    assert conversation_event.ingest_envelope(session_factory, envelope) is True


def test_events_api_paginates_by_event_cursor(session_factory, session):
    connection_id = uuid.uuid4()
    _create(session, connection_id)
    session.commit()
    conversation_id = conversation.find_conversation_by_resource(
        session,
        kind=ConversationKind.DATA_CONNECTION.value,
        resource_id=connection_id,
    ).id

    for index in range(2):
        conversation_event.ingest_envelope(
            session_factory,
            _envelope(
                "sync.run.queued",
                {"connectionId": str(connection_id), "summary": f"事件 {index}"},
            ),
        )

    client = TestClient(_create_app(session_factory, enable_agent_checkpoints=False))
    first = client.get(f"/api/v1/conversations/{conversation_id}/events").json()
    assert [item["summary"] for item in first] == ["事件 0", "事件 1"]

    second = client.get(
        f"/api/v1/conversations/{conversation_id}/events",
        params={"after_event_id": first[0]["id"]},
    ).json()
    assert [item["summary"] for item in second] == ["事件 1"]


def test_stream_replays_and_stops(session_factory):
    connection_id = uuid.uuid4()
    with session_factory() as session:
        _create(session, connection_id)
        conversation_id = conversation.find_conversation_by_resource(
            session,
            kind=ConversationKind.DATA_CONNECTION.value,
            resource_id=connection_id,
        ).id
        session.commit()

    bus = ConversationEventBus()
    for index in range(2):
        conversation_event.ingest_envelope(
            session_factory,
            _envelope(
                "sync.run.queued", {"connectionId": str(connection_id), "summary": str(index)}
            ),
            bus=bus,
        )

    stop = {"value": False}
    frames = []
    for frame in iter_event_stream(
        session_factory,
        bus,
        conversation_id=conversation_id,
        owner_id=OWNER_ID,
        after_event_id=None,
        should_stop=lambda: stop["value"],
    ):
        frames.append(frame)
        if len(frames) == 2:
            stop["value"] = True

    assert len(frames) == 2
    assert all(frame.startswith("event: conversation.event") for frame in frames)
    payload = json.loads(frames[0].split("data: ", 1)[1])
    assert payload["summary"] == "0"
