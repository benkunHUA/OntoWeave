"""查询工具的运行上下文。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.agent.tools.context import ToolContext
from app.services.embedding import Embedder


@dataclass(frozen=True, kw_only=True)
class QueryToolContext(ToolContext):
    client: Any
    embedder: Embedder
