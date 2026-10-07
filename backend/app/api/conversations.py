from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.conversation import ConversationCreate, ConversationRead
from app.services import conversation

router = APIRouter(tags=["conversations"])


@router.post("/conversations", response_model=ConversationRead, status_code=201)
def create_conversation(
    payload: ConversationCreate, session: Session = Depends(get_session)
) -> ConversationRead:
    conv = conversation.create_conversation(
        session, owner_id=get_current_user_id(), title=payload.title
    )
    return ConversationRead.model_validate(conv)


@router.get("/conversations", response_model=list[ConversationRead])
def list_conversations(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ConversationRead]:
    items = conversation.list_conversations(
        session, owner_id=get_current_user_id(), limit=limit, offset=offset
    )
    return [ConversationRead.model_validate(item) for item in items]


@router.get("/conversations/{conversation_id}", response_model=ConversationRead)
def get_conversation(
    conversation_id: uuid.UUID, session: Session = Depends(get_session)
) -> ConversationRead:
    conv = conversation.get_conversation(session, conversation_id, owner_id=get_current_user_id())
    return ConversationRead.model_validate(conv)
