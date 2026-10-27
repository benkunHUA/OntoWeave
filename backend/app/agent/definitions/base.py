"""Agent 定义的公共协议与运行上下文。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any, Protocol

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.config import Settings
from app.models import Conversation


@dataclass(frozen=True, kw_only=True)
class AgentRunContext:
    """一轮运行与具体领域无关的身份信息。"""

    conversation_id: uuid.UUID
    owner_id: uuid.UUID
    kind: str
    resource_id: uuid.UUID
    turn_id: uuid.UUID | None = None


class BaseAgentDefinition:
    """定义侧默认能力：不需要额外工具摘要的领域直接继承这一份。"""

    def summarize_tool_call(
        self,
        session: Session,
        *,
        owner_id: uuid.UUID,
        name: str,
        args: object,
    ) -> str | None:
        del session, owner_id, name, args
        return None


class AgentDefinition(Protocol):
    """一种会话类型的完整运行策略。"""

    kind: str
    visible_subagents: frozenset[str]

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> AgentRunContext: ...

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any: ...

    def build_state_update(
        self, session: Session, settings: Settings, run: AgentRunContext
    ) -> dict[str, Any]: ...

    def summarize_tool_call(
        self,
        session: Session,
        *,
        owner_id: uuid.UUID,
        name: str,
        args: object,
    ) -> str | None: ...
