"""建模会话的 Agent 定义。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langchain.agents.middleware import InterruptOnConfig
from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.definitions.base import AgentRunContext, BaseAgentDefinition
from app.agent.prompts.base import SYSTEM_PROMPT
from app.agent.tools import (
    OntologyToolContext,
    build_action_subagent,
    build_model_tools,
    build_view_subagent,
)
from app.agent.tools.action.subagent import ACTION_AGENT_NAME
from app.config import Settings
from app.errors import InvariantViolationError
from app.models import Conversation
from app.services import changeset, embedding, english_names, view_state
from app.vocabulary import ConversationKind

# 破坏性操作要人批。日常草稿不在这里拦——它们走改动集整体确认（REST 那套）。
DEFAULT_INTERRUPTS: dict[str, bool | InterruptOnConfig] = {
    "remove_element": True,
    "update_element": True,
}


@dataclass(frozen=True, kw_only=True)
class OntologyRunContext(AgentRunContext):
    """建模运行额外需要的图与改动集标识。"""

    graph_id: uuid.UUID
    changeset_id: uuid.UUID


class OntologyAgentDefinition(BaseAgentDefinition):
    kind: str = ConversationKind.MODELING
    visible_subagents: frozenset[str] = frozenset({ACTION_AGENT_NAME})

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> OntologyRunContext:
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"ontology definition cannot run conversation kind {conversation.kind!r}"
            )
        graph_id = conversation.resource_id
        open_sets = [
            item
            for item in changeset.list_changesets(
                session,
                graph_id=graph_id,
                owner_id=owner_id,
            )
            if item.status == changeset.OPEN
        ]
        current = (
            open_sets[0]
            if open_sets
            else changeset.create_changeset(session, graph_id=graph_id, owner_id=owner_id)
        )
        return OntologyRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
            graph_id=graph_id,
            changeset_id=current.id,
        )

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any:
        ontology_run = self._ensure_ontology_run(run)
        context = OntologyToolContext(
            session=session,
            owner_id=ontology_run.owner_id,
            graph_id=ontology_run.graph_id,
            changeset_id=ontology_run.changeset_id,
            embedder=embedding.build_embedder(settings),
            name_generator=english_names.build_english_name_generator(settings),
        )
        return agent_graph.build_agent(
            llm.build_chat_model(settings),
            build_model_tools(context),
            system_prompt=SYSTEM_PROMPT,
            subagents=[build_view_subagent(context), build_action_subagent(context)],
            interrupt_on=DEFAULT_INTERRUPTS if checkpointer is not None else None,
            checkpointer=checkpointer,
        )

    def build_state_update(
        self, session: Session, settings: Settings, run: AgentRunContext
    ) -> dict[str, Any]:
        del settings
        ontology_run = self._ensure_ontology_run(run)
        ops = [
            {"op": op.op, "kind": op.payload.get("kind")}
            for op in changeset.list_ops(session, ontology_run.changeset_id)
        ]
        saved_view = view_state.get_view_state(
            session,
            graph_id=ontology_run.graph_id,
            user_id=ontology_run.owner_id,
            owner_id=ontology_run.owner_id,
        )
        return {
            "changesetId": str(ontology_run.changeset_id),
            "ops": ops,
            "view": saved_view.spec if saved_view is not None else {},
        }

    def _ensure_ontology_run(self, run: AgentRunContext) -> OntologyRunContext:
        if not isinstance(run, OntologyRunContext):
            raise InvariantViolationError("ontology definition requires OntologyRunContext")
        if run.kind != self.kind:
            raise InvariantViolationError(
                f"ontology definition cannot run conversation kind {run.kind!r}"
            )
        if run.resource_id != run.graph_id:
            raise InvariantViolationError("ontology run resource_id must match graph_id")
        return run


__all__ = [
    "DEFAULT_INTERRUPTS",
    "OntologyAgentDefinition",
    "OntologyRunContext",
]
