"""数据整理会话的 Agent 定义。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import data_pipeline_events, llm
from app.agent import graph as agent_graph
from app.agent.definitions.base import AgentRunContext, BaseAgentDefinition
from app.agent.prompts_data_pipeline import build_prompt
from app.agent.tools.data_pipeline import (
    DataPipelineToolContext,
    build_data_pipeline_tools,
)
from app.config import Settings, get_settings
from app.errors import DomainError, InvariantViolationError
from app.models import Conversation
from app.services import conversation_event
from app.services.data_service import pipeline_mapping as mapping
from app.services.data_service.client import DataServiceClient
from app.vocabulary import ConversationKind

PROMPT_EVENT_LIMIT = 10


@dataclass(frozen=True, kw_only=True)
class DataPipelineRunContext(AgentRunContext):
    pipeline_id: uuid.UUID
    pipeline_state: dict | None
    draft: dict | None
    check_result: dict | None
    load_error: str | None = None


class DataPipelineAgentDefinition(BaseAgentDefinition):
    kind: str = ConversationKind.DATA_PIPELINE
    visible_subagents: frozenset[str] = frozenset()

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> DataPipelineRunContext:
        del session
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"data pipeline definition cannot run conversation kind {conversation.kind!r}"
            )
        pipeline_id = conversation.resource_id
        client = DataServiceClient(get_settings())
        pipeline_state, draft, check_result, load_error = _load_state(
            client,
            owner_id=owner_id,
            pipeline_id=pipeline_id,
        )
        return DataPipelineRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=pipeline_id,
            pipeline_id=pipeline_id,
            pipeline_state=pipeline_state,
            draft=draft,
            check_result=check_result,
            load_error=load_error,
        )

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any:
        pipeline_run = self._ensure_run(run)
        client = DataServiceClient(settings)
        events = conversation_event.list_recent_events(
            session, pipeline_run.conversation_id, limit=PROMPT_EVENT_LIMIT
        )
        turn_id = pipeline_run.turn_id or uuid.uuid4()
        questions = data_pipeline_events.list_questions(
            session,
            conversation_id=pipeline_run.conversation_id,
            turn_id=turn_id,
        )
        context = DataPipelineToolContext(
            session=session,
            owner_id=pipeline_run.owner_id,
            conversation_id=pipeline_run.conversation_id,
            pipeline_id=pipeline_run.pipeline_id,
            client=client,
            draft_revision=_draft_revision(pipeline_run.draft),
            turn_id=turn_id,
            questions_asked=questions,
            last_check_result=pipeline_run.check_result,
            load_error=pipeline_run.load_error,
        )
        return agent_graph.build_agent(
            llm.build_chat_model(settings),
            build_data_pipeline_tools(context),
            system_prompt=build_prompt(_prompt_state(pipeline_run), events),
            checkpointer=checkpointer,
            include_planning=False,
        )

    def build_state_update(
        self, session: Session, settings: Settings, run: AgentRunContext
    ) -> dict[str, Any]:
        pipeline_run = self._ensure_run(run)
        pipeline_state, draft, check_result, load_error = _load_state(
            DataServiceClient(settings),
            owner_id=pipeline_run.owner_id,
            pipeline_id=pipeline_run.pipeline_id,
        )
        del pipeline_state
        draft_revision = _draft_revision(draft)
        if pipeline_run.turn_id is not None:
            data_pipeline_events.record_turn(
                session,
                conversation_id=pipeline_run.conversation_id,
                turn_id=pipeline_run.turn_id,
                pipeline_id=pipeline_run.pipeline_id,
                draft_revision=draft_revision,
                load_error=load_error,
            )
            session.commit()
        return {
            "pipelineId": str(pipeline_run.pipeline_id),
            "draftRevision": draft_revision,
            "check": check_result,
            "loadError": load_error,
        }

    def _ensure_run(self, run: AgentRunContext) -> DataPipelineRunContext:
        if not isinstance(run, DataPipelineRunContext):
            raise InvariantViolationError(
                "data pipeline definition requires DataPipelineRunContext"
            )
        if run.kind != self.kind:
            raise InvariantViolationError(
                f"data pipeline definition cannot run conversation kind {run.kind!r}"
            )
        if run.resource_id != run.pipeline_id:
            raise InvariantViolationError("resource_id must match pipeline_id")
        return run


def _draft_revision(draft: dict | None) -> int | None:
    if not isinstance(draft, dict):
        return None
    revision = draft.get("revision")
    return revision if isinstance(revision, int) else None


def _pipeline_revision(pipeline_state: dict | None) -> int | None:
    if not isinstance(pipeline_state, dict):
        return None
    revision = pipeline_state.get("draft_revision")
    return revision if isinstance(revision, int) else None


def _prompt_state(run: DataPipelineRunContext) -> dict[str, Any]:
    return {
        "pipelineId": str(run.pipeline_id),
        "draftRevision": _draft_revision(run.draft),
        "pipeline": run.pipeline_state,
        "check": run.check_result,
        "loadError": run.load_error,
    }


def _load_state(
    client: DataServiceClient,
    *,
    owner_id: uuid.UUID,
    pipeline_id: uuid.UUID,
) -> tuple[dict | None, dict | None, dict | None, str | None]:
    error: str | None = None
    pipeline_state: dict | None = None
    draft: dict | None = None
    check_result: dict | None = None

    try:
        pipeline_state = mapping.pipeline(client.get_pipeline(pipeline_id, owner_id=owner_id))
    except DomainError as exc:
        error = str(exc)

    try:
        draft = mapping.draft(client.get_draft(pipeline_id, owner_id=owner_id))
    except DomainError as exc:
        error = error or str(exc)

    revision = _draft_revision(draft)
    if revision is None:
        revision = _pipeline_revision(pipeline_state)
    if revision is not None:
        try:
            check_result = mapping.check_result(
                client.check_pipeline(pipeline_id, owner_id=owner_id, revision=revision)
            )
        except DomainError as exc:
            error = error or str(exc)

    if error is not None:
        return None, None, None, error
    return pipeline_state, draft, check_result, None


__all__ = [
    "DataPipelineAgentDefinition",
    "DataPipelineRunContext",
    "PROMPT_EVENT_LIMIT",
]
