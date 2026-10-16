from __future__ import annotations

import uuid
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field

from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE


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


# 抽屉里能直接改的元素类型：对象、字段与关系。
ElementKind = Literal["object_type", "property", "relation"]


class UpdateEdit(BaseModel):
    """改已有元素：只提交 patch，服务层负责采集 before。"""

    op: Literal["update"] = "update"
    element_kind: ElementKind
    element_id: uuid.UUID
    patch: dict = Field(default_factory=dict)


class CreatePropertyEdit(BaseModel):
    """给对象类型新增一个草稿字段。"""

    op: Literal["create_property"] = "create_property"
    owner_type_id: uuid.UUID
    name: str
    datatype: str = DEFAULT_DATATYPE
    required: bool = False
    is_array: bool = False
    is_primary_key: bool = False
    is_title_key: bool = False


class CreateObjectTypeEdit(BaseModel):
    """新建一个草稿对象类型，并挂进当前图。"""

    op: Literal["create_object_type"] = "create_object_type"
    name: str
    description: str | None = None


class ReuseObjectTypeEdit(BaseModel):
    """把共享池里已确认的类型并进当前图（未定稿的不允许复用）。"""

    op: Literal["reuse_object_type"] = "reuse_object_type"
    object_type_id: uuid.UUID


class CreateRelationEdit(BaseModel):
    """新建一条草稿关系；两端都在共享池里，不存在时会挂上。"""

    op: Literal["create_relation"] = "create_relation"
    source_type_id: uuid.UUID
    target_type_id: uuid.UUID
    name: str
    direction: str = "forward"
    cardinality: str = DEFAULT_CARDINALITY
    forward_label: str | None = None
    reverse_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None


class DeleteEdit(BaseModel):
    """标记删除：草稿阶段不真删，定稿时才删。"""

    op: Literal["delete"] = "delete"
    element_kind: ElementKind
    element_id: uuid.UUID


GraphEditOp = Annotated[
    UpdateEdit
    | CreatePropertyEdit
    | CreateObjectTypeEdit
    | ReuseObjectTypeEdit
    | CreateRelationEdit
    | DeleteEdit,
    Field(discriminator="op"),
]


class GraphEditsCreate(BaseModel):
    """一次保存里的全部编辑，整批落在同一个改动集上。"""

    edits: list[GraphEditOp] = Field(min_length=1)
