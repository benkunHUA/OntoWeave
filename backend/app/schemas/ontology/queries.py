"""对象查询过滤与关系分页模型。"""

from __future__ import annotations

import uuid
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.schemas.ontology.resources import OntologyRelatedObject
from app.schemas.ontology.types import (
    ORDERED_FILTER_OPS,
    UNARY_FILTER_OPS,
    FilterOp,
)


class FilterCondition(BaseModel):
    model_config = ConfigDict(extra="forbid")

    attribute_id: uuid.UUID
    op: FilterOp
    value: Any | None = None

    @model_validator(mode="after")
    def validate_operator_shape(self) -> FilterCondition:
        has_value = "value" in self.model_fields_set
        if self.op in UNARY_FILTER_OPS:
            if has_value:
                raise ValueError(f"{self.op.value} does not accept a value")
            return self
        if not has_value or self.value is None:
            raise ValueError(f"{self.op.value} requires a value")
        if self.op is FilterOp.CONTAINS and not isinstance(self.value, str):
            raise ValueError("contains requires a string value")
        if self.op in ORDERED_FILTER_OPS and isinstance(self.value, bool):
            raise ValueError(f"{self.op.value} does not accept a boolean value")
        return self


class ObjectRelationGroupRead(BaseModel):
    model_config = ConfigDict(extra="allow")

    relation_id: uuid.UUID
    direction: str
    label: str | None = None
    direction_label: str | None = None
    source_type_id: uuid.UUID | None = None
    source_type_name: str | None = None
    target_type_id: uuid.UUID | None = None
    target_type_name: str | None = None
    total: int = 0
    items: list[OntologyRelatedObject] = Field(default_factory=list)


class ObjectRelationPage(BaseModel):
    items: list[OntologyRelatedObject] = Field(default_factory=list)
    total: int = 0
    page: int = 1
    page_size: int = 0
