import os
from pathlib import Path

import pytest
from alembic.config import Config
from sqlalchemy import Engine, create_engine, text
from sqlalchemy.engine import Connection, make_url
from sqlalchemy.orm import Session, sessionmaker

from alembic import command

BACKEND_DIR = Path(__file__).resolve().parents[1]

TEST_DATABASE_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave_test",
)

# 防呆：测试会重建 public schema，库名不以 _test 结尾就直接拒绝启动。
_db_name = make_url(TEST_DATABASE_URL).database or ""
if not _db_name.endswith("_test"):
    pytest.exit(f"拒绝在非测试库上跑测试：{_db_name}", returncode=1)


@pytest.fixture(scope="session")
def alembic_config() -> Config:
    cfg = Config(str(BACKEND_DIR / "alembic.ini"))
    cfg.set_main_option("script_location", str(BACKEND_DIR / "alembic"))
    # env.py 优先读 attributes 里的地址，避免被应用配置无条件覆盖。
    cfg.attributes["sqlalchemy_url"] = TEST_DATABASE_URL
    return cfg


@pytest.fixture(scope="session")
def engine(alembic_config: Config) -> Engine:
    """schema 一律用 Alembic 迁移建，保证测试验证的是真迁移，而不是 create_all。"""
    eng = create_engine(TEST_DATABASE_URL, future=True)
    with eng.begin() as conn:
        conn.execute(text("DROP SCHEMA IF EXISTS public CASCADE"))
        conn.execute(text("CREATE SCHEMA public"))
    command.upgrade(alembic_config, "head")
    yield eng
    eng.dispose()


@pytest.fixture
def connection(engine: Engine) -> Connection:
    """每个测试一条会被回滚的外层连接，测试之间互不污染。"""
    conn = engine.connect()
    transaction = conn.begin()
    try:
        yield conn
    finally:
        transaction.rollback()
        conn.close()


@pytest.fixture
def session_factory(connection: Connection) -> sessionmaker[Session]:
    """绑在测试连接上的 session 工厂，可以原样交给 create_app()。"""
    return sessionmaker(
        bind=connection,
        autoflush=False,
        expire_on_commit=False,
        class_=Session,
        # 让 session 的 commit 只释放 SAVEPOINT，外层事务仍由 fixture 回滚，
        # 这样 app 里真实的提交行为也能被测到，同时不污染库。
        join_transaction_mode="create_savepoint",
    )


@pytest.fixture
def session(session_factory: sessionmaker[Session]) -> Session:
    sess = session_factory()
    try:
        yield sess
    finally:
        sess.close()
