"""数据整理 Agent 的轮次与提问事件。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ConversationEvent
from app.services import conversation_event

TURN_EVENT_TYPE = "pipeline.agent.turn"
QUESTION_EVENT_TYPE = "pipeline.agent.question"
TURN_EVENT_SUMMARY = "本轮数据整理已完成"
TURN_EVENT_LOAD_ERROR_SUMMARY = "本轮数据整理未能读取最新状态"
QUESTION_EVENT_SUMMARY = "数据整理助手已提问"

_NAMESPACE = uuid.uuid5(uuid.NAMESPACE_URL, "ontoweave:data-pipeline-agent-events")


def _event_id(event_type: str, conversation_id: uuid.UUID, turn_id: uuid.UUID) -> uuid.UUID:
    return uuid.uuid5(_NAMESPACE, f"{event_type}:{conversation_id}:{turn_id}")


def record_turn(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    turn_id: uuid.UUID,
    pipeline_id: uuid.UUID,
    draft_revision: int | None,
    load_error: str | None,
) -> bool:
    conversation_event.upsert_event(
        session,
        conversation_id=conversation_id,
        event_id=_event_id(TURN_EVENT_TYPE, conversation_id, turn_id),
        event_type=TURN_EVENT_TYPE,
        summary=TURN_EVENT_LOAD_ERROR_SUMMARY if load_error else TURN_EVENT_SUMMARY,
        turn_id=turn_id,
        payload={
            "pipelineId": str(pipeline_id),
            "turnId": str(turn_id),
            "draftRevision": draft_revision,
            "loadError": load_error,
        },
    )
    return True


def record_question(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    turn_id: uuid.UUID,
    question: str,
) -> bool:
    return conversation_event.append_event(
        session,
        conversation_id=conversation_id,
        event_id=_event_id(QUESTION_EVENT_TYPE, conversation_id, turn_id),
        event_type=QUESTION_EVENT_TYPE,
        summary=QUESTION_EVENT_SUMMARY,
        turn_id=turn_id,
        payload={"turnId": str(turn_id), "question": question},
    )


def list_questions(
    session: Session,
    *,
    conversation_id: uuid.UUID,
    turn_id: uuid.UUID,
) -> list[str]:
    rows = session.scalars(
        select(ConversationEvent)
        .where(
            ConversationEvent.conversation_id == conversation_id,
            ConversationEvent.turn_id == turn_id,
            ConversationEvent.event_type == QUESTION_EVENT_TYPE,
        )
        .order_by(ConversationEvent.created_at, ConversationEvent.id)
    )
    questions: list[str] = []
    for row in rows:
        question = row.payload.get("question") if isinstance(row.payload, dict) else None
        if isinstance(question, str) and question:
            questions.append(question)
    return questions
