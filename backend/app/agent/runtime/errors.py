"""把运行时异常转换为用户可读的提示。"""

from __future__ import annotations

from psycopg import OperationalError as PsycopgOperationalError

_DATABASE_UNAVAILABLE = "数据存储连接暂时中断，请重试。"


def _caused_by_database_disconnect(exc: BaseException) -> bool:
    current: BaseException | None = exc
    visited: set[int] = set()
    while current is not None and id(current) not in visited:
        if isinstance(current, PsycopgOperationalError):
            return True
        visited.add(id(current))
        current = current.__cause__ or current.__context__
    return False


def user_facing_run_error(exc: Exception) -> str:
    """底层数据库断连不把驱动原文透给用户；其它错误保留现有诊断信息。"""
    if _caused_by_database_disconnect(exc):
        return _DATABASE_UNAVAILABLE
    return f"{type(exc).__name__}: {exc}"
