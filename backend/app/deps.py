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


def get_default_user_id() -> str:
    return get_settings().default_user_id
