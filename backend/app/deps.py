import uuid
from collections.abc import Iterator
from typing import Any

from fastapi import Request
from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.config import Settings, get_settings


def get_session(request: Request) -> Iterator[Session]:
    """单元事务：整个请求成功才提交，抛异常就回滚。事务边界在这一处定义。"""
    factory = request.app.state.session_factory
    with factory() as session:
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise


def get_current_user_id() -> uuid.UUID:
    """一期没有用户系统，统一返回配置里的默认用户。"""
    return uuid.UUID(get_settings().default_user_id)


def get_agent_checkpointer(state: Any, settings: Settings) -> BaseCheckpointSaver | None:
    """懒建并缓存模型线程的检查点。

    没有它，interrupt_on / ask_user / 定稿回写线程都无法工作；关掉开关时返回
    None，调用方据此跳过。连接池由应用关闭时统一释放（见 main.lifespan）。
    """
    if state.checkpointer is None and state.enable_agent_checkpoints:
        from app.agent import graph as agent_graph

        state.checkpointer, state.checkpointer_pool = agent_graph.build_checkpointer(
            settings.database_url
        )
    return state.checkpointer
