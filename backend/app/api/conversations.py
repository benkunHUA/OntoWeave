from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query, Response
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.conversation import (
    ConversationCreate,
    ConversationMessageRead,
    ConversationRead,
    ConversationUpdate,
)
from app.services import conversation, graph_cleanup

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


@router.patch("/conversations/{conversation_id}", response_model=ConversationRead)
def rename_conversation(
    conversation_id: uuid.UUID,
    payload: ConversationUpdate,
    session: Session = Depends(get_session),
) -> ConversationRead:
    conv = conversation.rename_conversation(
        session,
        conversation_id,
        title=payload.title,
        owner_id=get_current_user_id(),
    )
    return ConversationRead.model_validate(conv)


@router.delete("/conversations/{conversation_id}", status_code=204)
def delete_conversation(
    conversation_id: uuid.UUID,
    scope: graph_cleanup.DeleteScope = Query(
        graph_cleanup.DEFAULT_DELETE_SCOPE,
        description=(
            "共享池清理范围：graph-only 只删图；graph-and-drafts 连本图草稿一起删；"
            "graph-and-unreferenced 连没被其它图引用的元素一起删。"
        ),
    ),
    session: Session = Depends(get_session),
) -> Response:
    conversation.delete_conversation(
        session,
        conversation_id,
        owner_id=get_current_user_id(),
        scope=scope,
    )
    return Response(status_code=204)


@router.get(
    "/conversations/{conversation_id}/messages",
    response_model=list[ConversationMessageRead],
)
def list_messages(
    conversation_id: uuid.UUID,
    limit: int = Query(200, ge=1, le=500),
    session: Session = Depends(get_session),
) -> list[ConversationMessageRead]:
    messages = conversation.list_messages(
        session,
        conversation_id,
        owner_id=get_current_user_id(),
        limit=limit,
    )
    return [ConversationMessageRead.model_validate(message) for message in messages]
