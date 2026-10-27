"""`test_data_pipeline_service.py` 的共享 fixture、假客户端与构造 helper。"""

import uuid

from app.models import Conversation


def _pipeline_row(pipeline_id: uuid.UUID, conversation_id: uuid.UUID, name: str = "流程") -> dict:
    return {
        "id": str(pipeline_id),
        "conversationId": str(conversation_id),
        "name": name,
        "status": "draft",
        "draftRevision": 0,
        "currentVersionId": None,
        "createdAt": "2026-10-18T00:00:00Z",
        "updatedAt": "2026-10-18T00:00:00Z",
    }


class _CreateClient:
    def __init__(self, session_factory, *, failure: Exception | None = None) -> None:
        self.session_factory = session_factory
        self.failure = failure
        self.calls: list[dict] = []
        self.conversation_visible = False

    def create_pipeline(self, *, owner_id, payload, operation_id=None):  # noqa: ANN001
        self.calls.append(dict(payload))
        if self.failure is not None:
            raise self.failure
        with self.session_factory() as check:
            row = check.get(Conversation, uuid.UUID(payload["conversationId"]))
            self.conversation_visible = row is not None
        return _pipeline_row(
            uuid.UUID(payload["pipelineId"]),
            uuid.UUID(payload["conversationId"]),
            payload["name"],
        )


class _NoLookupClient:
    def create_pipeline(self, *, owner_id, payload, operation_id=None):  # noqa: ANN001, ARG002
        return _pipeline_row(
            uuid.UUID(payload["pipelineId"]),
            uuid.UUID(payload["conversationId"]),
            payload["name"],
        )
