from __future__ import annotations

import uuid
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ChangesetOpCreate(BaseModel):
    op: Literal["create", "update", "delete"]
    target_id: uuid.UUID | None = None
    payload: dict = Field(default_factory=dict)


class ChangesetOpRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    op: str
    target_id: uuid.UUID | None
    payload: dict


class ChangesetRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    graph_id: uuid.UUID
    status: str


class ChangesetDetail(ChangesetRead):
    ops: list[ChangesetOpRead] = Field(default_factory=list)
