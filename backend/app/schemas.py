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


class GraphCreate(BaseModel):
    name: str | None = None


class GraphRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str | None


class ObjectMembership(BaseModel):
    object_type_id: uuid.UUID


class RelationMembership(BaseModel):
    relation_id: uuid.UUID


class ErrorBody(BaseModel):
    error: str
    message: str
