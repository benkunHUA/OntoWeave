"""数据连接会话的 Agent 定义。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.definitions.base import AgentRunContext, BaseAgentDefinition
from app.agent.prompts_data_connection import build_prompt
from app.agent.tools.data_connection import (
    DataConnectionToolContext,
    build_data_connection_tools,
)
from app.config import Settings
from app.errors import DomainError, InvariantViolationError
from app.models import Conversation
from app.services import conversation_event
from app.services.data_service import mapping
from app.services.data_service.client import DataServiceClient
from app.vocabulary import ConversationKind

#: 注入提示词的最近事件条数；更早历史由 get_connection_events 工具按需读取。
PROMPT_EVENT_LIMIT = 10


@dataclass(frozen=True, kw_only=True)
class DataConnectionRunContext(AgentRunContext):
    data_connection_id: uuid.UUID


class DataConnectionAgentDefinition(BaseAgentDefinition):
    kind: str = ConversationKind.DATA_CONNECTION
    visible_subagents: frozenset[str] = frozenset()

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> DataConnectionRunContext:
        del session
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"data connection definition cannot run conversation kind {conversation.kind!r}"
            )
        return DataConnectionRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
            data_connection_id=conversation.resource_id,
        )

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any:
        data_run = self._ensure_run(run)
        client = DataServiceClient(settings)
        state = _safe_state(client, data_run)
        events = conversation_event.list_recent_events(
            session, data_run.conversation_id, limit=PROMPT_EVENT_LIMIT
        )
        context = DataConnectionToolContext(
            session=session,
            owner_id=data_run.owner_id,
            conversation_id=data_run.conversation_id,
            data_connection_id=data_run.data_connection_id,
            client=client,
        )
        return agent_graph.build_agent(
            llm.build_chat_model(settings),
            build_data_connection_tools(context),
            system_prompt=build_prompt(state, events),
            checkpointer=checkpointer,
            include_planning=False,
        )

    def build_state_update(
        self, session: Session, settings: Settings, run: AgentRunContext
    ) -> dict[str, Any]:
        del session
        data_run = self._ensure_run(run)
        client = DataServiceClient(settings)
        return {"dataConnection": _safe_state(client, data_run)}

    def _ensure_run(self, run: AgentRunContext) -> DataConnectionRunContext:
        if not isinstance(run, DataConnectionRunContext):
            raise InvariantViolationError(
                "data connection definition requires DataConnectionRunContext"
            )
        if run.kind != self.kind:
            raise InvariantViolationError(
                f"data connection definition cannot run conversation kind {run.kind!r}"
            )
        if run.resource_id != run.data_connection_id:
            raise InvariantViolationError(
                "data connection run resource_id must match data_connection_id"
            )
        return run


def _safe_state(client: DataServiceClient, run: DataConnectionRunContext) -> dict | None:
    try:
        return mapping.state_summary(
            client.get_state(run.data_connection_id, owner_id=run.owner_id)
        )
    except DomainError:
        return None


__all__ = [
    "DataConnectionAgentDefinition",
    "DataConnectionRunContext",
    "PROMPT_EVENT_LIMIT",
]
