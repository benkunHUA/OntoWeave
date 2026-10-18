"""建模工具的运行上下文。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from app.agent.tools.context import ToolContext
from app.services import embedding


@dataclass(frozen=True, kw_only=True)
class OntologyToolContext(ToolContext):
    """通用上下文之外，建模工具还需要图、改动集和嵌入器。"""

    graph_id: uuid.UUID
    changeset_id: uuid.UUID
    embedder: embedding.Embedder
