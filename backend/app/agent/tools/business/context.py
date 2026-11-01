"""业务工具的运行上下文。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from app.agent.tools.context import ToolContext
from app.clients.data_service.ontology_client import EditConflict
from app.services.action.execution import ActionExecutionError
from app.services.action.presentation import ActionNotInGraphError
from app.services.embedding import Embedder


@dataclass(frozen=True, kw_only=True)
class BusinessToolContext(ToolContext):
    client: Any
    embedder: Embedder
    conversation_id: uuid.UUID | None = None

    def error_result(self, exc: Exception) -> dict[str, Any]:
        if isinstance(exc, ActionNotInGraphError):
            return {"error": "ACTION_NOT_IN_GRAPH", "message": str(exc)}
        if isinstance(exc, ActionExecutionError):
            return {"error": exc.code, "message": str(exc), "details": exc.details}
        if isinstance(exc, EditConflict):
            return {
                "error": exc.code,
                "message": str(exc),
                "details": exc.details,
                "conflicts": exc.conflicts,
            }
        return super().error_result(exc)
