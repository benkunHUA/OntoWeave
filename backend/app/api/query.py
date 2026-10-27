"""查询工作台的会话入口。"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.conversation import ConversationRead
from app.services import conversation
from app.vocabulary import ConversationKind

router = APIRouter(tags=["query"])


@router.post("/query/conversations", response_model=ConversationRead, status_code=201)
def create_query_conversation(
    session: Session = Depends(get_session),
) -> ConversationRead:
    conv = conversation.create_conversation(
        session,
        owner_id=get_current_user_id(),
        title="新问答",
        kind=ConversationKind.QUERY.value,
    )
    return ConversationRead.model_validate(conv)
