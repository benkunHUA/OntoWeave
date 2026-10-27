"""业务问答会话的 Agent 定义。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.definitions.base import AgentRunContext, BaseAgentDefinition
from app.agent.prompts_query import MAIN_QUERY_AGENT_PROMPT
from app.agent.tools.common import build_ask_user_tool
from app.agent.tools.query.context import QueryToolContext
from app.agent.tools.query.rewrite import build_rewrite_tool
from app.agent.tools.query.selection import build_query_selection_tools
from app.agent.tools.query.subagent import QUERY_AGENT_NAME, build_query_subagent
from app.agent.tools.query.summary import summarize_query_tool_call
from app.config import Settings
from app.errors import InvariantViolationError
from app.models import Conversation
from app.services import embedding
from app.services.data_service.ontology_client import OntologyDataServiceClient
from app.vocabulary import ConversationKind


@dataclass(frozen=True, kw_only=True)
class QueryRunContext(AgentRunContext):
    pass


class QueryAgentDefinition(BaseAgentDefinition):
    kind: str = ConversationKind.QUERY
    visible_subagents: frozenset[str] = frozenset({QUERY_AGENT_NAME})

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> QueryRunContext:
        del session
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"query definition cannot run conversation kind {conversation.kind!r}"
            )
        return QueryRunContext(
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
        if not isinstance(run, QueryRunContext):
            raise InvariantViolationError("query definition requires QueryRunContext")
        if checkpointer is None:
            raise InvariantViolationError("query agent requires a checkpointer")
        model = llm.build_chat_model(settings)
        context = QueryToolContext(
            session=session,
            owner_id=run.owner_id,
            client=OntologyDataServiceClient(settings),
            embedder=embedding.build_embedder(settings),
        )
        subagent = build_query_subagent(
            session,
            settings,
            conversation_id=run.conversation_id,
            owner_id=run.owner_id,
            checkpointer=checkpointer,
            model=model,
        )
        return agent_graph.build_agent(
            model,
            [
                build_rewrite_tool(settings, model=model),
                *build_query_selection_tools(context),
                build_ask_user_tool(),
            ],
            system_prompt=MAIN_QUERY_AGENT_PROMPT,
            subagents=[subagent],
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
