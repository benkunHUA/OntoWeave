"""数据整理工具的一轮运行上下文。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field

from app.agent.tools.context import ToolContext
from app.services.data_service.client import DataServiceClient


@dataclass(frozen=True, kw_only=True)
class DataPipelineToolContext(ToolContext):
    conversation_id: uuid.UUID
    pipeline_id: uuid.UUID
    client: DataServiceClient
    draft_revision: int | None
    turn_id: uuid.UUID
    questions_asked: list[str] = field(default_factory=list)
    last_check_result: dict | None = None
    load_error: str | None = None

    def advance_revision(self, revision: int) -> None:
        """同一轮后续写入必须看到刚写入后的 revision。"""
        object.__setattr__(self, "draft_revision", revision)

    def set_check_result(self, result: dict) -> None:
        object.__setattr__(self, "last_check_result", result)
