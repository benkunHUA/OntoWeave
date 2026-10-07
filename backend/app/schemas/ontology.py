from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class ObjectTypeCreate(BaseModel):
    name: str
    description: str | None = None


class ObjectTypeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    description: str | None
    status: str
