"""对象映射工作台的 Agent 定义。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.definitions.base import AgentRunContext, BaseAgentDefinition
from app.agent.prompts.ontology_workbench import build_prompt
from app.agent.tools.ontology import (
    OntologyWorkbenchToolContext,
    build_workbench_tools,
)
from app.clients.data_service.ontology_client import OntologyDataServiceClient
from app.config import Settings
from app.errors import InvariantViolationError, NotFoundError
from app.models import Conversation
from app.services import embedding, ontology
from app.vocabulary import ConversationKind


@dataclass(frozen=True, kw_only=True)
class OntologyWorkbenchRunContext(AgentRunContext):
    subject_kind: str
    subject_id: uuid.UUID


class OntologyWorkbenchAgentDefinition(BaseAgentDefinition):
    kind: str = ConversationKind.ONTOLOGY_WORKBENCH
    visible_subagents: frozenset[str] = frozenset()

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> OntologyWorkbenchRunContext:
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"ontology workbench definition cannot run conversation kind {conversation.kind!r}"
            )
        subject_kind = _subject_kind(session, owner_id, conversation.resource_id)
        return OntologyWorkbenchRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
            subject_kind=subject_kind,
            subject_id=conversation.resource_id,
        )

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any:
        workbench_run = self._ensure_run(run)
        client = OntologyDataServiceClient(settings)
        state = ontology.load_agent_state(
            session,
            client,
            owner_id=workbench_run.owner_id,
            subject_kind=workbench_run.subject_kind,
            subject_id=workbench_run.subject_id,
        )
        context = OntologyWorkbenchToolContext(
            session=session,
            owner_id=workbench_run.owner_id,
            conversation_id=workbench_run.conversation_id,
            subject_kind=workbench_run.subject_kind,
            subject_id=workbench_run.subject_id,
            client=client,
            embedder=embedding.build_embedder(settings),
            state=state,
            revision=_revision(state),
            load_error=state.get("load_error"),
        )
        return agent_graph.build_agent(
            llm.build_chat_model(settings),
            build_workbench_tools(context),
            system_prompt=build_prompt(state),
            checkpointer=checkpointer,
            include_planning=False,
        )

    def build_state_update(
        self, session: Session, settings: Settings, run: AgentRunContext
    ) -> dict[str, Any]:
        workbench_run = self._ensure_run(run)
        state = ontology.load_agent_state(
            session,
            OntologyDataServiceClient(settings),
            owner_id=workbench_run.owner_id,
            subject_kind=workbench_run.subject_kind,
            subject_id=workbench_run.subject_id,
        )
        return {
            "subjectKind": workbench_run.subject_kind,
            "subjectId": str(workbench_run.subject_id),
            "binding": state.get("binding"),
            "mapping": state.get("mapping"),
            "latestRun": state.get("latest_run"),
            "loadError": state.get("load_error"),
        }

    def _ensure_run(self, run: AgentRunContext) -> OntologyWorkbenchRunContext:
        if not isinstance(run, OntologyWorkbenchRunContext):
            raise InvariantViolationError(
                "ontology workbench definition requires OntologyWorkbenchRunContext"
            )
        if run.kind != self.kind:
            raise InvariantViolationError(
                f"ontology workbench definition cannot run conversation kind {run.kind!r}"
            )
        if run.resource_id != run.subject_id:
            raise InvariantViolationError("ontology workbench resource_id must match subject_id")
        return run


def _subject_kind(session: Session, owner_id: uuid.UUID, resource_id: uuid.UUID) -> str:
    try:
        ontology.resource_subject(
            session,
            owner_id=owner_id,
            resource_kind=ontology.OBJECT_TYPE,
            resource_id=resource_id,
        )
        return ontology.OBJECT_TYPE
    except NotFoundError:
        pass
    try:
        ontology.resource_subject(
            session,
            owner_id=owner_id,
            resource_kind=ontology.RELATION,
            resource_id=resource_id,
        )
        return ontology.RELATION
    except NotFoundError as exc:
        raise NotFoundError(f"ontology workbench resource {resource_id} not found") from exc


def _revision(state: dict[str, Any]) -> int | None:
    binding = state.get("binding") or {}
    revision = binding.get("revision")
    if isinstance(revision, int):
        return revision
    mapping = state.get("mapping") or {}
    revision = mapping.get("revision") if isinstance(mapping, dict) else None
    return revision if isinstance(revision, int) else None


__all__ = ["OntologyWorkbenchAgentDefinition", "OntologyWorkbenchRunContext"]
