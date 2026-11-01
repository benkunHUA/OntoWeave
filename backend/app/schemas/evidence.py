"""业务查询证据：业务图类型层 + 查询过程中命中的对象实例子图。

字段名沿用线格式的 camelCase：这份结构会作为消息块原样落库并推给前端，
前后端不再各做一层映射。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class EvidenceObjectType(BaseModel):
    id: str
    name: str
    properties: list[dict[str, Any]] = Field(default_factory=list)


class EvidenceRelation(BaseModel):
    id: str
    name: str
    sourceTypeId: str
    targetTypeId: str
    cardinality: str
    forwardLabel: str | None = None
    reverseLabel: str | None = None


class EvidenceNode(BaseModel):
    id: str
    objectTypeId: str
    primaryKey: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)


class EvidenceEdge(BaseModel):
    id: str
    relationId: str
    source: str
    target: str
    label: str


class EvidenceGraph(BaseModel):
    graphId: str | None = None
    graphName: str | None = None
    objectTypes: list[EvidenceObjectType] = Field(default_factory=list)
    relations: list[EvidenceRelation] = Field(default_factory=list)
    nodes: list[EvidenceNode] = Field(default_factory=list)
    edges: list[EvidenceEdge] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
