"""本体 BFF 的共享依赖与请求转换。"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from typing import Annotated

from fastapi import Depends, Header
from pydantic import BaseModel

from app.config import Settings, get_settings
from app.services.data_service.ontology_client import OntologyDataServiceClient

OperationId = Annotated[uuid.UUID | None, Header(alias="X-Operation-Id")]


def get_ontology_client(settings: Settings = Depends(get_settings)) -> OntologyDataServiceClient:
    return OntologyDataServiceClient(settings)


def operation_id(value: uuid.UUID | None) -> uuid.UUID:
    return value or uuid.uuid4()


def mapping_payload(mapping: BaseModel | Mapping[str, object]) -> dict:
    if isinstance(mapping, BaseModel):
        return mapping.model_dump(mode="json")
    return dict(mapping)


__all__ = [
    "OperationId",
    "get_ontology_client",
    "mapping_payload",
    "operation_id",
]
