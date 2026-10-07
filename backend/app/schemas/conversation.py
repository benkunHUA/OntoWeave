from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class ConversationCreate(BaseModel):
    title: str | None = None


class ConversationRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    graph_id: uuid.UUID
    title: str | None
