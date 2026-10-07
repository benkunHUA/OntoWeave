import uuid
from collections.abc import Iterator

from fastapi import Request
from sqlalchemy.orm import Session

from app.config import get_settings


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
