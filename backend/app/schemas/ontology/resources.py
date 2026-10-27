"""对象/关系资源、数据集与实例的读模型。"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.ontology.types import DatasetKind, RelationKind
from app.vocabulary import PropertyDatatype


class ObjectTypeCreate(BaseModel):
    name: str
    name_en: str | None = None
    description: str | None = None


class ObjectTypeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    name_en: str | None = None
    description: str | None
    status: str


class DatasetColumn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str
    type: str
    optional: bool
    example: Any | None = None


class OntologyDataset(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: DatasetKind
    id: uuid.UUID
    name: str
    version_id: uuid.UUID
    version_number: int | None = None
    row_count: int
    columns: list[DatasetColumn] = Field(default_factory=list)


class OntologyDatasetPage(BaseModel):
    items: list[OntologyDataset]
    total: int
    page: int
    size: int


class OntologyObject(BaseModel):
    primary_key: str
    title: str | None = None
    index_version_id: uuid.UUID


class OntologyObjectPage(BaseModel):
    items: list[OntologyObject] = Field(default_factory=list)
    total: int
    page: int
    page_size: int
    index_version_id: uuid.UUID | None = None


class OntologyObjectDetail(BaseModel):
    primary_key: str
    title: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)
    index_version_id: uuid.UUID


class OntologyRelatedObject(BaseModel):
    model_config = ConfigDict(extra="allow")

    primary_key: str
    title: str | None = None


class RelationInstance(BaseModel):
    from_object_type_id: uuid.UUID
    from_primary_key: str
    to_object_type_id: uuid.UUID
    to_primary_key: str
    index_version_id: uuid.UUID | None = None


class RelationInstancePage(BaseModel):
    items: list[RelationInstance] = Field(default_factory=list)
    total: int
    page: int
    page_size: int


class OntologyResourceRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: uuid.UUID
    name: str
    name_en: str | None = None
    status: str
    kind: str
    binding: dict[str, Any] | None = None
    cardinality: str | None = None
    relation_kind: RelationKind | None = None
    source_type_id: uuid.UUID | None = None
    source_type_name: str | None = None
    target_type_id: uuid.UUID | None = None
    target_type_name: str | None = None
    forward_label: str | None = None
    reverse_label: str | None = None
    direction_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None


class OntologyResourcesRead(BaseModel):
    object_types: list[OntologyResourceRead] = Field(default_factory=list)
    foreign_key_relations: list[OntologyResourceRead] = Field(default_factory=list)
    many_to_many_relations: list[OntologyResourceRead] = Field(default_factory=list)


class OntologyPropertyRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: uuid.UUID
    name: str
    name_en: str | None = None
    datatype: PropertyDatatype
    required: bool
    is_array: bool
    is_primary_key: bool
    is_title_key: bool
    enum_values: list[str] | None = None
    status: str


class OntologyTypeDefinitionRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: uuid.UUID
    name: str
    name_en: str | None = None
    description: str | None = None
    status: str
    properties: list[OntologyPropertyRead] = Field(default_factory=list)


class OntologyResourceDefinitionRead(BaseModel):
    """对象类型返回 properties；关系返回两端完整定义与外键属性。"""

    model_config = ConfigDict(extra="allow")

    id: uuid.UUID
    name: str
    name_en: str | None = None
    kind: str
    status: str
    description: str | None = None
    properties: list[OntologyPropertyRead] = Field(default_factory=list)
    cardinality: str | None = None
    source_type_id: uuid.UUID | None = None
    source_type_name: str | None = None
    target_type_id: uuid.UUID | None = None
    target_type_name: str | None = None
    forward_label: str | None = None
    reverse_label: str | None = None
    foreign_key_property_id: uuid.UUID | None = None
    foreign_key_property: OntologyPropertyRead | None = None
    source: OntologyTypeDefinitionRead | None = None
    target: OntologyTypeDefinitionRead | None = None
