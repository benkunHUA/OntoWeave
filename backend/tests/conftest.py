import os

import pytest
from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from app.models import Base

TEST_DATABASE_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave",
)


@pytest.fixture(scope="session")
def engine() -> Engine:
    eng = create_engine(TEST_DATABASE_URL, future=True)
    with eng.begin() as conn:
        conn.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
    Base.metadata.drop_all(eng)
    Base.metadata.create_all(eng)
    yield eng
    Base.metadata.drop_all(eng)
    eng.dispose()


@pytest.fixture
def session(engine: Engine) -> Session:
    """每个测试跑在一条会被回滚的外层事务里，互不污染。"""
    connection = engine.connect()
    transaction = connection.begin()
    factory = sessionmaker(
        bind=connection, autoflush=False, expire_on_commit=False, class_=Session
    )
    sess = factory()
    try:
        yield sess
    finally:
        sess.close()
        transaction.rollback()
        connection.close()
