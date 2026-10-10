from __future__ import annotations

import uuid

from pydantic import BaseModel, Field


class ViewStateWrite(BaseModel):
    spec: dict = Field(default_factory=dict)


class ViewStateRead(BaseModel):
    graph_id: uuid.UUID
    name: str | None
    spec: dict
