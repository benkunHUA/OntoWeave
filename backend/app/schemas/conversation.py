from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.vocabulary import ConversationKind


class ConversationCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str | None = None


class ConversationUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=120)


class ConversationRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    kind: ConversationKind
    resource_id: uuid.UUID
    title: str | None


class ConversationMessageRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    role: str
    content: str
    kind: str
    # 原样返回落库的块（形状见 app.schemas.message_blocks 的 MessageBlock）。
    blocks: list[dict] | None = None
    created_at: datetime
