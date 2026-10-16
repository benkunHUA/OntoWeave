from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict, Field


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


class GraphPropertyRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    datatype: str
    required: bool
    is_array: bool
    is_primary_key: bool
    is_title_key: bool
    enum_values: list[str] | None
    status: str


class GraphNodeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    description: str | None
    status: str
    properties: list[GraphPropertyRead]


class GraphRelationRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    source_type_id: uuid.UUID
    target_type_id: uuid.UUID
    direction: str
    cardinality: str
    forward_label: str | None
    reverse_label: str | None
    foreign_key_property_id: uuid.UUID | None
    status: str


class GraphActionInvolvesRead(BaseModel):
    """动作涉及的一个本体元素：画布据此连动作节点到它的 `involves` 边。"""

    kind: str
    id: uuid.UUID


class GraphActionBriefRead(BaseModel):
    """画布上的动作节点：基本信息 + 涉及的本体元素；全量定义走动作详情接口。"""

    id: uuid.UUID
    name: str
    description: str | None
    status: str
    involves: list[GraphActionInvolvesRead]


class GraphSnapshotRead(BaseModel):
    graph: GraphRead
    nodes: list[GraphNodeRead]
    relations: list[GraphRelationRead]
    actions: list[GraphActionBriefRead] = Field(default_factory=list)
