"""对话事件卡片（数据连接 / 同步状态）的对外形状。"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict


class ConversationEventRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    event_id: uuid.UUID
    operation_id: uuid.UUID | None = None
    run_id: uuid.UUID | None = None
    turn_id: uuid.UUID | None = None
    event_type: str
    summary: str
    payload: dict[str, Any]
    created_at: datetime
