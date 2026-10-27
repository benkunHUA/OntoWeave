"""API 组合根：把可选的外部能力装配成服务层依赖。"""

from __future__ import annotations

from fastapi import Request
from sqlalchemy.orm import Session

from app.agent import thread
from app.config import get_settings
from app.deps import get_agent_checkpointer, get_current_user_id
from app.services import embedding, english_names
from app.services.changeset import review as changeset_review


def request_embedder() -> embedding.Embedder:
    """定稿时给已确认对象补向量；没配 key 时回落到本地实现。"""
    return embedding.build_embedder(get_settings())


def request_name_generator() -> english_names.EnglishNameGenerator | None:
    return english_names.build_english_name_generator(get_settings())


def thread_notifier(request: Request) -> changeset_review.ThreadNotifier:
    """把定稿结果写回模型线程；服务层不感知 agent。"""
    settings = get_settings()
    owner_id = get_current_user_id()

    def notify(session: Session, notice: changeset_review.Notice) -> None:
        thread.record_decision(
            session,
            settings,
            owner_id,
            conversation_id=notice.conversation_id,
            graph_id=notice.graph_id,
            changeset_id=notice.changeset_id,
            text=notice.text,
            checkpointer=get_agent_checkpointer(request.app.state, settings),
        )

    return notify


__all__ = ["request_embedder", "request_name_generator", "thread_notifier"]
