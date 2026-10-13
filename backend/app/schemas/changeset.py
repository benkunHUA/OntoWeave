from __future__ import annotations

import uuid
from typing import Annotated, Literal

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
    datatype: str = "text"
    required: bool = False
    is_array: bool = False
    is_primary_key: bool = False
    is_title_key: bool = False


class DeleteEdit(BaseModel):
    """标记删除：草稿阶段不真删，定稿时才删。"""

    op: Literal["delete"] = "delete"
    element_kind: ElementKind
    element_id: uuid.UUID


GraphEditOp = Annotated[
    UpdateEdit | CreatePropertyEdit | DeleteEdit,
    Field(discriminator="op"),
]


class GraphEditsCreate(BaseModel):
    """一次保存里的全部编辑，整批落在同一个改动集上。"""

    edits: list[GraphEditOp] = Field(min_length=1)
