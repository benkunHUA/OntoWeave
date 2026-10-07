from collections.abc import Iterator

from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import build_engine, build_session_factory

_settings = get_settings()
_engine = build_engine(_settings.database_url)
_session_factory = build_session_factory(_engine)


def get_session() -> Iterator[Session]:
    with _session_factory() as session:
        yield session


def get_default_user_id() -> str:
    return _settings.default_user_id
