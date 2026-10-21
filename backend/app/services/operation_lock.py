"""跨进程操作锁：用 PostgreSQL session advisory lock 串行化同一幂等操作。"""

from __future__ import annotations

import hashlib
from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import text
from sqlalchemy.engine import Connection, Engine
from sqlalchemy.orm import Session


def _lock_key(*parts: object) -> int:
    raw = "\x1f".join(str(part) for part in parts).encode()
    digest = hashlib.blake2b(raw, digest_size=8).digest()
    return int.from_bytes(digest, byteorder="big", signed=True)


@contextmanager
def operation_lock(session: Session, *parts: object) -> Iterator[None]:
    """持锁跨越 session.commit 和外部调用；连接关闭也会自动释放。"""
    bind = session.get_bind()
    engine: Engine = bind.engine if isinstance(bind, Connection) else bind
    if engine.dialect.name != "postgresql":
        yield
        return

    key = _lock_key(*parts)
    with engine.connect() as connection:
        connection.execute(text("SELECT pg_advisory_lock(:key)"), {"key": key})
        connection.commit()
        try:
            yield
        finally:
            connection.execute(text("SELECT pg_advisory_unlock(:key)"), {"key": key})
            connection.commit()
