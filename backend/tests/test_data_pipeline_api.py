import uuid

from pipeline_test_fakes import (
    API,
    CONVERSATION_ID,
    DATASET_ID,
    OWNER_ID,
    PIPELINE_ID,
)

from app.errors import InvariantViolationError
from app.models import Conversation
from app.services import conversation
from app.vocabulary import ConversationKind


def test_create_requires_operation_id(pipeline_client):
    response = pipeline_client.post(API, json={"name": "流程"})

    assert response.status_code == 400
    assert response.json()["error"] == "invalid_request"


def test_create_maps_response_and_forwards_stable_binding(
    pipeline_client, fake_pipeline_client, session
):
    operation_id = uuid.uuid4()

    response = pipeline_client.post(
        API,
        json={"name": "订单清洗"},
        headers={"X-Operation-Id": str(operation_id)},
    )

    assert response.status_code == 201
    body = response.json()
    assert body["conversation_id"] == str(CONVERSATION_ID)
    assert body["draft_revision"] == 3
    assert "conversationId" not in response.text
    call = fake_pipeline_client.calls[0][1]
    assert call["payload"]["pipelineId"]
    assert call["payload"]["conversationId"]
    assert call["operation_id"] == operation_id
    assert (
        session.query(Conversation).filter_by(kind=ConversationKind.DATA_PIPELINE.value).count()
        == 0
    )


def test_pipeline_conversation_is_created_on_demand(pipeline_client, session):
    assert pipeline_client.get(f"{API}/{PIPELINE_ID}/conversation").json() is None

    ensured = pipeline_client.post(f"{API}/{PIPELINE_ID}/conversation")

    assert ensured.status_code == 201
    assert ensured.json()["id"] == str(CONVERSATION_ID)
    assert (
        session.query(Conversation).filter_by(kind=ConversationKind.DATA_PIPELINE.value).count()
        == 1
    )


def test_static_routes_are_registered_before_dynamic_pipeline_route(pipeline_client):
    catalog = pipeline_client.get(f"{API}/node-catalog")
    datasets = pipeline_client.get(f"{API}/input-datasets")

    assert catalog.status_code == 200
    assert catalog.json()["catalog_version"] == "v1"
    assert catalog.json()["nodes"][0]["config_schema"] == {"type": "object"}
    assert datasets.status_code == 200
    assert datasets.json()["items"][0]["current_version"]["version_number"] == 2


def test_dataset_schema_route_is_not_captured_as_pipeline_id(pipeline_client):
    response = pipeline_client.get(f"{API}/input-datasets/{DATASET_ID}/schema")

    assert response.status_code == 200
    body = response.json()
    assert body["dataset_id"] == str(DATASET_ID)
    assert body["version_number"] == 2
    assert body["schema"] == {"fields": [{"name": "id", "type": "long"}]}


def test_copy_uses_operation_id_for_new_binding(pipeline_client, fake_pipeline_client, session):
    operation_id = uuid.uuid4()

    response = pipeline_client.post(
        f"{API}/{PIPELINE_ID}/copy",
        json={"name": "副本"},
        headers={"X-Operation-Id": str(operation_id)},
    )

    assert response.status_code == 201
    call = [item for item in fake_pipeline_client.calls if item[0] == "copy"][-1][1]
    assert call["payload"]["pipelineId"] != str(PIPELINE_ID)
    assert call["payload"]["conversationId"]
    assert (
        session.query(Conversation).filter_by(kind=ConversationKind.DATA_PIPELINE.value).count()
        == 0
    )


def test_java_409_maps_to_bff_conflict(pipeline_client, fake_pipeline_client, monkeypatch):
    def conflict(*args, **kwargs):  # noqa: ANN002, ANN003
        raise InvariantViolationError("流程已归档")

    monkeypatch.setattr(fake_pipeline_client, "rename_pipeline", conflict)

    response = pipeline_client.patch(f"{API}/{PIPELINE_ID}", json={"name": "改名"})

    assert response.status_code == 409
    assert response.json()["error"] == "invariant_violation"


def test_delete_archives_java_then_conversation(
    pipeline_client, fake_pipeline_client, session, session_factory, monkeypatch
):
    conv = conversation.create_conversation(
        session,
        owner_id=OWNER_ID,
        title="流程会话",
        kind=ConversationKind.DATA_PIPELINE.value,
        resource_id=PIPELINE_ID,
        conversation_id=CONVERSATION_ID,
    )
    session.commit()
    observed = []

    def archive(*args, **kwargs):  # noqa: ANN002, ANN003
        with session_factory() as check:
            observed.append(check.get(Conversation, CONVERSATION_ID).archived_at)
        return {}

    monkeypatch.setattr(fake_pipeline_client, "archive_pipeline", archive)

    response = pipeline_client.delete(f"{API}/{PIPELINE_ID}")

    assert response.status_code == 204
    assert observed == [None]
    session.expire_all()
    assert conversation.get_conversation(session, conv.id).archived_at is not None
    assert pipeline_client.get(f"/api/v1/conversations/{conv.id}").status_code == 200
    assert pipeline_client.get("/api/v1/conversations").json() == []
