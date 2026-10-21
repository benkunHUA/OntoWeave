"""跨进程操作锁：用 PostgreSQL session advisory lock 串行化同一幂等操作。"""

from __future__ import annotations

import atexit
import hashlib
import threading
from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Connection, Engine
from sqlalchemy.orm import Session
from sqlalchemy.pool import NullPool

_lock_engines: dict[str, Engine] = {}
_lock_engines_guard = threading.Lock()


def _lock_key(*parts: object) -> int:
    raw = "\x1f".join(str(part) for part in parts).encode()
    digest = hashlib.blake2b(raw, digest_size=8).digest()
    return int.from_bytes(digest, byteorder="big", signed=True)


def _lock_engine(engine: Engine) -> Engine:
    """锁连接使用独立 NullPool，绝不占用请求 session 的连接池。"""
    key = engine.url.render_as_string(hide_password=False)
    with _lock_engines_guard:
        cached = _lock_engines.get(key)
        if cached is None:
            cached = create_engine(engine.url, poolclass=NullPool, pool_pre_ping=True)
            _lock_engines[key] = cached
        return cached


def dispose_lock_engines() -> None:
    with _lock_engines_guard:
        engines = list(_lock_engines.values())
        _lock_engines.clear()
    for engine in engines:
        engine.dispose()


atexit.register(dispose_lock_engines)


@contextmanager
def operation_lock(session: Session, *parts: object) -> Iterator[None]:
    """持锁跨越 session.commit 和外部调用；连接关闭也会自动释放。"""
    bind = session.get_bind()
    engine: Engine = bind.engine if isinstance(bind, Connection) else bind
    if engine.dialect.name != "postgresql":
        yield
        return

    key = _lock_key(*parts)
    lock_engine = _lock_engine(engine)
    with lock_engine.connect() as connection:
        connection.execute(text("SELECT pg_advisory_lock(:key)"), {"key": key})
        connection.commit()
        try:
            yield
        finally:
            connection.execute(text("SELECT pg_advisory_unlock(:key)"), {"key": key})
            connection.commit()
