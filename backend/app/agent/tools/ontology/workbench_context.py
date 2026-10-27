"""映射工作台工具的一轮运行上下文。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

import app.agent.tools.ontology.workbench_constants as workbench_constants
from app.agent.tools.context import ToolContext
from app.clients.data_service.client import RevisionConflict
from app.clients.data_service.ontology_client import OntologyDataServiceClient
from app.errors import (
    DataServiceUnavailableError,
    InvalidRequestError,
    NotFoundError,
    UnprocessableEntityError,
)
from app.services import embedding


@dataclass(frozen=True, kw_only=True)
class OntologyWorkbenchToolContext(ToolContext):
    conversation_id: uuid.UUID
    subject_kind: str
    subject_id: uuid.UUID
    client: OntologyDataServiceClient
    embedder: embedding.Embedder
    state: dict[str, Any]
    revision: int | None = None
    load_error: str | None = None

    def error_result(self, exc: Exception) -> dict[str, Any]:
        if isinstance(exc, RevisionConflict):
            result: dict[str, Any] = {
                "error": workbench_constants.REVISION_CONFLICT,
                "message": str(exc),
                "code": exc.code,
            }
            revision = _current_revision(exc.details)
            if revision is not None:
                result["current_revision"] = revision
            if exc.details:
                result["details"] = exc.details
            return result
        if isinstance(exc, UnprocessableEntityError):
            return {
                "error": exc.code or workbench_constants.MAPPING_INVALID,
                "message": str(exc),
                "details": exc.details,
            }
        if isinstance(exc, DataServiceUnavailableError):
            return {"error": workbench_constants.DATA_SERVICE_UNAVAILABLE, "message": str(exc)}
        if isinstance(exc, NotFoundError):
            return {"error": workbench_constants.NOT_FOUND, "message": str(exc)}
        if isinstance(exc, (InvalidRequestError, ValueError)):
            return {"error": workbench_constants.INVALID_ARGUMENT, "message": str(exc)}
        return super().error_result(exc)

    def check_subject(self, subject_kind: str, subject_id: str) -> dict[str, str] | None:
        from app.services.ontology import normalize_resource_kind

        try:
            kind = normalize_resource_kind(subject_kind)
            parsed_id = uuid.UUID(subject_id)
        except (ValueError, InvalidRequestError):
            return {"error": workbench_constants.INVALID_ARGUMENT, "message": "资源标识无效"}
        if kind != self.subject_kind or parsed_id != self.subject_id:
            return workbench_constants.subject_mismatch()
        return None

    def advance_revision(self, revision: int) -> None:
        object.__setattr__(self, "revision", revision)


def _current_revision(details: dict[str, object]) -> int | None:
    for key in ("current_revision", "currentRevision", "revision"):
        value = details.get(key)
        if isinstance(value, int):
            return value
        if isinstance(value, str) and value.isdigit():
            return int(value)
    return None
