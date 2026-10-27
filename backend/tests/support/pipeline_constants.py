"""数据整理测试共用的固定 id 与 Java 响应模板。"""

import uuid

from app.config import DEFAULT_USER_ID

API = "/api/v1/pipelines"
OWNER_ID = uuid.UUID(DEFAULT_USER_ID)
PIPELINE_ID = uuid.UUID("40000000-0000-4000-8000-000000000001")
CONVERSATION_ID = uuid.UUID("40000000-0000-4000-8000-000000000002")
BUILD_ID = uuid.UUID("40000000-0000-4000-8000-000000000003")
DATASET_ID = uuid.UUID("40000000-0000-4000-8000-000000000004")
TURN_ID = uuid.UUID("40000000-0000-4000-8000-000000000005")
NOW = "2026-10-18T00:00:00Z"


def _pipeline(name: str = "订单清洗") -> dict:
    return {
        "id": str(PIPELINE_ID),
        "conversationId": str(CONVERSATION_ID),
        "name": name,
        "status": "draft",
        "draftRevision": 3,
        "currentVersionId": None,
        "currentVersionNumber": None,
        "currentVersionDraftRevision": None,
        "createdAt": NOW,
        "updatedAt": NOW,
    }
