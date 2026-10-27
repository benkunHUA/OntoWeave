"""数据连接工具的运行上下文。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from app.agent.tools.context import ToolContext
from app.clients.data_service.client import DataServiceClient


@dataclass(frozen=True, kw_only=True)
class DataConnectionToolContext(ToolContext):
    conversation_id: uuid.UUID
    data_connection_id: uuid.UUID
    client: DataServiceClient
