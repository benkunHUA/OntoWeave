"""业务会话的 Agent 定义。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.definitions.base import AgentRunContext, BaseAgentDefinition
from app.agent.evidence import QueryEvidenceMiddleware
from app.agent.prompts.business import MAIN_BUSINESS_AGENT_PROMPT
from app.agent.state import BusinessAgentState
from app.agent.tools.business.action import build_action_tools
from app.agent.tools.business.binding import build_query_instance_tools
from app.agent.tools.business.context import BusinessToolContext
from app.agent.tools.business.rewrite import build_rewrite_tool
from app.agent.tools.business.selection import build_query_selection_tools
from app.agent.tools.business.summary import summarize_query_tool_call
from app.agent.tools.common import build_ask_user_tool
from app.clients.data_service.ontology_client import OntologyDataServiceClient
from app.config import Settings
from app.errors import InvariantViolationError
from app.models import Conversation
from app.services import embedding
from app.vocabulary import ConversationKind


@dataclass(frozen=True, kw_only=True)
class BusinessRunContext(AgentRunContext):
    pass


class BusinessAgentDefinition(BaseAgentDefinition):
    kind: str = ConversationKind.BUSINESS
    visible_subagents: frozenset[str] = frozenset()

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> BusinessRunContext:
        del session
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"business definition cannot run conversation kind {conversation.kind!r}"
            )
        return BusinessRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
        )

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any:
        if not isinstance(run, BusinessRunContext):
            raise InvariantViolationError("business definition requires BusinessRunContext")
        if checkpointer is None:
            raise InvariantViolationError("business agent requires a checkpointer")
        model = llm.build_chat_model(settings)
        context = BusinessToolContext(
            session=session,
            owner_id=run.owner_id,
            conversation_id=run.conversation_id,
            client=OntologyDataServiceClient(settings),
            embedder=embedding.build_embedder(settings),
        )
        return agent_graph.build_agent(
            model,
            [
                build_rewrite_tool(settings, model=model),
                *build_query_selection_tools(context),
                *build_action_tools(context),
                *build_query_instance_tools(context),
                build_ask_user_tool(),
            ],
            system_prompt=MAIN_BUSINESS_AGENT_PROMPT,
            middleware=[QueryEvidenceMiddleware()],
            state_schema=BusinessAgentState,
            checkpointer=checkpointer,
        )

    def build_state_update(
        self, session: Session, settings: Settings, run: AgentRunContext
    ) -> dict[str, Any]:
        del session, settings, run
        return {}

    def summarize_tool_call(
        self,
        session: Session,
        *,
        owner_id: uuid.UUID,
        name: str,
        args: object,
    ) -> str | None:
        return summarize_query_tool_call(
            session,
            owner_id=owner_id,
            name=name,
            args=args,
        )
