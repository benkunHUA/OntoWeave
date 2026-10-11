# 本体建模后端地基 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭出一个可运行、可测试的后端，能建图、建对象类型、建属性与关系，把对象和关系放进图里，并跑通 草案 → 已确认 → 已批准 的状态机。

**Architecture:** FastAPI 薄 HTTP 层 + `app/services/` 领域层 + SQLAlchemy 2.0 同步 ORM + Alembic 迁移。对象类型与关系是**共享池实体**，不挂 `graph_id`；图通过 `graph_object` / `graph_relation` 引用它们。所有新建元素默认落 `draft`。

**Tech Stack:** Python 3.12、FastAPI、SQLAlchemy 2.0、Alembic、psycopg3、PostgreSQL 16（pgvector 镜像）、pytest、Docker Compose。

**对应设计文档：** `docs/superpowers/specs/2026-10-07-conversational-ontology-modeling-design.md` 第 4 节。

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/pyproject.toml` | 依赖与 pytest 配置 |
| `backend/docker-compose.yml` | 本地 Postgres（pgvector 镜像） |
| `backend/.env.example` | 环境变量样例 |
| `backend/alembic.ini`、`backend/alembic/env.py` | 迁移配置 |
| `backend/alembic/versions/0001_initial.py` | 初始 schema |
| `backend/app/config.py` | 配置读取 |
| `backend/app/db.py` | engine / session 工厂 |
| `backend/app/models.py` | SQLAlchemy 模型 |
| `backend/app/schemas.py` | Pydantic 出入参 |
| `backend/app/errors.py` | 领域异常 |
| `backend/app/services/ontology.py` | 对象类型 / 属性 / 关系 |
| `backend/app/services/graph.py` | 图 / 图成员 / 完整性校验 |
| `backend/app/api/routes.py` | REST 路由 |
| `backend/app/deps.py` | 依赖注入 |
| `backend/app/main.py` | 应用装配 |
| `backend/tests/` | 测试 |

---

## Task 1: 项目脚手架与健康检查

**Files:**
- Create: `backend/pyproject.toml`
- Create: `backend/app/__init__.py`
- Create: `backend/app/main.py`
- Test: `backend/tests/test_health.py`

- [ ] **Step 1: Write the failing test**

```python
# backend/tests/test_health.py
from fastapi.testclient import TestClient

from app.main import create_app


def test_health_returns_ok():
    client = TestClient(create_app())
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && python -m pytest tests/test_health.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app'`（`app` 与依赖都还不存在）

- [ ] **Step 3: Create the package metadata**

```toml
# backend/pyproject.toml
[project]
name = "ontoweave-backend"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi==0.115.6",
    "uvicorn[standard]==0.34.0",
    "sqlalchemy==2.0.36",
    "psycopg[binary]==3.2.3",
    "alembic==1.14.0",
    "pydantic-settings==2.7.0",
]

[project.optional-dependencies]
dev = ["pytest==8.3.4", "httpx==0.28.1"]

[tool.pytest.ini_options]
testpaths = ["tests"]
pythonpath = ["."]
```

- [ ] **Step 4: Create the app package**

```python
# backend/app/__init__.py
```

```python
# backend/app/main.py
from fastapi import FastAPI


def create_app() -> FastAPI:
    app = FastAPI(title="OntoWeave", version="0.1.0")

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return app


app = create_app()
```

- [ ] **Step 5: Install and run the test**

Run:
```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
python -m pytest tests/test_health.py -v
```
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/pyproject.toml backend/app backend/tests/test_health.py
git commit -m "feat(backend): scaffold FastAPI app with health endpoint"
```

---

## Task 2: 配置与数据库会话

**Files:**
- Create: `backend/app/config.py`
- Create: `backend/app/db.py`
- Create: `backend/.env.example`
- Test: `backend/tests/test_config.py`

- [ ] **Step 1: Write the failing test**

```python
# backend/tests/test_config.py
import uuid

from app.config import DEFAULT_USER_ID, Settings


def test_settings_have_defaults():
    settings = Settings()
    assert settings.database_url.startswith("postgresql+psycopg://")
    assert settings.default_user_id == DEFAULT_USER_ID


def test_default_user_id_is_a_valid_uuid_literal():
    assert uuid.UUID(DEFAULT_USER_ID)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && python -m pytest tests/test_config.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.config'`

- [ ] **Step 3: Implement config**

```python
# backend/app/config.py
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict

# 一期没有用户系统，所有 owner_id / user_id 都用这个固定值。
DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = (
        "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave"
    )
    default_user_id: str = DEFAULT_USER_ID


@lru_cache
def get_settings() -> Settings:
    return Settings()
```

- [ ] **Step 4: Implement the engine / session factory**

```python
# backend/app/db.py
from sqlalchemy import Engine, create_engine
from sqlalchemy.orm import Session, sessionmaker


def build_engine(database_url: str) -> Engine:
    return create_engine(database_url, pool_pre_ping=True, future=True)


def build_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(
        bind=engine,
        autoflush=False,
        expire_on_commit=False,
        class_=Session,
    )
```

- [ ] **Step 5: Add the env sample**

```bash
# backend/.env.example
DATABASE_URL=postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave
DEFAULT_USER_ID=00000000-0000-0000-0000-000000000001
```

- [ ] **Step 6: Run tests**

Run: `cd backend && python -m pytest tests/test_config.py -v`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/app/config.py backend/app/db.py backend/.env.example backend/tests/test_config.py
git commit -m "feat(backend): add settings and database session factory"
```

---

## Task 3: 数据模型与测试夹具

**Files:**
- Create: `backend/docker-compose.yml`
- Create: `backend/tests/conftest.py`
- Create: `backend/app/models.py`
- Test: `backend/tests/test_models.py`

- [ ] **Step 1: Add Docker Compose for the database**

```yaml
# backend/docker-compose.yml
services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_USER: ontoweave
      POSTGRES_PASSWORD: ontoweave
      POSTGRES_DB: ontoweave
    ports:
      - "5432:5432"
    volumes:
      - ontoweave_db:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ontoweave"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  ontoweave_db:
```

- [ ] **Step 2: Write the test fixture**

```python
# backend/tests/conftest.py
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
```

- [ ] **Step 3: Write the failing test**

```python
# backend/tests/test_models.py
import uuid

from app.models import Graph, ObjectType, Property, Relation


def test_object_type_defaults_to_draft(session):
    graph = Graph(owner_id=uuid.uuid4(), name="销售域建模")
    session.add(graph)
    session.flush()

    obj = ObjectType(owner_id=uuid.uuid4(), name="客户", home_graph_id=graph.id)
    session.add(obj)
    session.flush()
    session.refresh(obj)

    assert obj.status == "draft"
    assert obj.id is not None
    assert obj.created_at is not None


def test_property_belongs_to_object_type(session):
    obj = ObjectType(owner_id=uuid.uuid4(), name="订单")
    session.add(obj)
    session.flush()

    prop = Property(owner_type_id=obj.id, name="数量", datatype="int")
    session.add(prop)
    session.flush()

    assert prop.id is not None
    assert prop.required is False
    assert prop.status == "draft"


def test_relation_connects_two_object_types(session):
    customer = ObjectType(owner_id=uuid.uuid4(), name="客户")
    order = ObjectType(owner_id=uuid.uuid4(), name="订单")
    session.add_all([customer, order])
    session.flush()

    rel = Relation(
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    session.add(rel)
    session.flush()

    assert rel.id is not None
    assert rel.status == "draft"
    assert rel.direction == "forward"
```

- [ ] **Step 4: Run test to verify it fails**

Run:
```bash
cd backend
docker compose up -d db
python -m pytest tests/test_models.py -v
```
Expected: FAIL — `ModuleNotFoundError: No module named 'app.models'`

- [ ] **Step 5: Implement the models**

```python
# backend/app/models.py
from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, CheckConstraint, DateTime, ForeignKey, Text, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

STATUS_VALUES = ("draft", "confirmed", "approved")


class Base(DeclarativeBase):
    pass


class Graph(Base):
    __tablename__ = "graph"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    name: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class Conversation(Base):
    __tablename__ = "conversation"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("graph.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
    )
    title: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class ObjectType(Base):
    __tablename__ = "object_type"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_object_type_status",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    name: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )
    home_graph_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="SET NULL")
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class Property(Base):
    __tablename__ = "property"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_property_status",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    owner_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    datatype: Mapped[str] = mapped_column(Text, nullable=False, default="text")
    required: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )


class Relation(Base):
    __tablename__ = "relation"
    __table_args__ = (
        CheckConstraint(
            "status IN ('draft', 'confirmed', 'approved')",
            name="ck_relation_status",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    source_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
    )
    target_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    direction: Mapped[str] = mapped_column(Text, nullable=False, default="forward")
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="draft", server_default="draft"
    )
    home_graph_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="SET NULL")
    )


class GraphObject(Base):
    __tablename__ = "graph_object"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    object_type_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("object_type.id", ondelete="CASCADE"),
        primary_key=True,
    )
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class GraphRelation(Base):
    __tablename__ = "graph_relation"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    relation_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("relation.id", ondelete="CASCADE"),
        primary_key=True,
    )
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class ViewState(Base):
    __tablename__ = "view_state"

    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), primary_key=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    name: Mapped[str | None] = mapped_column(Text)
    spec: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )


class Changeset(Base):
    __tablename__ = "changeset"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    graph_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("graph.id", ondelete="CASCADE"), nullable=False
    )
    status: Mapped[str] = mapped_column(
        Text, nullable=False, default="open", server_default="open"
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )


class ChangesetOp(Base):
    __tablename__ = "changeset_op"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    changeset_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("changeset.id", ondelete="CASCADE"),
        nullable=False,
    )
    op: Mapped[str] = mapped_column(Text, nullable=False)
    target_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    payload: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && python -m pytest tests/test_models.py -v`
Expected: PASS（3 个测试全过）

- [ ] **Step 7: Commit**

```bash
git add backend/docker-compose.yml backend/tests/conftest.py backend/app/models.py backend/tests/test_models.py
git commit -m "feat(backend): define ontology and graph models with test fixtures"
```

---

## Task 4: 数据库迁移

**Files:**
- Create: `backend/alembic.ini`
- Create: `backend/alembic/env.py`
- Create: `backend/alembic/versions/0001_initial.py`

- [ ] **Step 1: Add the Alembic config**

```ini
# backend/alembic.ini
[alembic]
script_location = alembic
prepend_sys_path = .

[loggers]
keys = root,sqlalchemy,alembic

[handlers]
keys = console

[formatters]
keys = generic

[logger_root]
level = WARN
handlers = console
qualname =

[logger_sqlalchemy]
level = WARN
handlers =
qualname = sqlalchemy.engine

[logger_alembic]
level = INFO
handlers =
qualname = alembic

[handler_console]
class = StreamHandler
args = (sys.stderr,)
level = NOTSET
formatter = generic

[formatter_generic]
format = %(levelname)-5.5s [%(name)s] %(message)s
datefmt = %H:%M:%S
```

- [ ] **Step 2: Add the migration environment**

```python
# backend/alembic/env.py
from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from app.config import get_settings
from app.models import Base

config = context.config
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

config.set_main_option("sqlalchemy.url", get_settings().database_url)
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
    )
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
```

- [ ] **Step 3: Autogenerate the initial migration**

Run:
```bash
cd backend
alembic revision --autogenerate -m "initial schema" --rev-id 0001
mv alembic/versions/0001_initial_schema.py alembic/versions/0001_initial.py
```

- [ ] **Step 4: Verify the generated file**

打开 `backend/alembic/versions/0001_initial.py`，确认 `upgrade()` 里包含 `graph`、`conversation`、`object_type`、`property`、`relation`、`graph_object`、`graph_relation`、`view_state`、`changeset`、`changeset_op` 十张表。若缺表，说明 `app.models` 没被 `env.py` 正确导入，先修再继续。同时确认文件开头有：

```python
op.execute("CREATE EXTENSION IF NOT EXISTS vector")
```

没有就手动加到 `upgrade()` 第一行（pgvector 镜像支持它，后面做相似检索要用）。

- [ ] **Step 5: Apply the migration**

Run:
```bash
cd backend
alembic upgrade head
alembic current
```
Expected: `alembic current` 输出中包含 `0001 (head)`

- [ ] **Step 6: Commit**

```bash
git add backend/alembic.ini backend/alembic
git commit -m "feat(backend): add initial alembic migration"
```

---

## Task 5: 对象类型服务（含状态机）

**Files:**
- Create: `backend/app/errors.py`
- Create: `backend/app/services/__init__.py`
- Create: `backend/app/services/ontology.py`
- Test: `backend/tests/test_ontology_service.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_ontology_service.py
import uuid

import pytest

from app.errors import InvalidTransitionError, NotFoundError
from app.services import ontology


def test_create_object_type_defaults_to_draft(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    assert obj.status == "draft"
    assert obj.name == "客户"


def test_transition_draft_to_confirmed(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    moved = ontology.transition_object_type(session, obj.id, "confirmed")
    assert moved.status == "confirmed"


def test_transition_confirmed_to_approved(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed")
    moved = ontology.transition_object_type(session, obj.id, "approved")
    assert moved.status == "approved"


def test_cannot_skip_confirmed(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    with pytest.raises(InvalidTransitionError):
        ontology.transition_object_type(session, obj.id, "approved")


def test_cannot_leave_approved(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.transition_object_type(session, obj.id, "confirmed")
    ontology.transition_object_type(session, obj.id, "approved")
    with pytest.raises(InvalidTransitionError):
        ontology.transition_object_type(session, obj.id, "confirmed")


def test_missing_object_type_raises(session):
    with pytest.raises(NotFoundError):
        ontology.transition_object_type(session, uuid.uuid4(), "confirmed")


def test_list_object_types(session):
    ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    names = {o.name for o in ontology.list_object_types(session)}
    assert {"客户", "订单"} <= names
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_ontology_service.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.services'`

- [ ] **Step 3: Implement the errors module**

```python
# backend/app/errors.py
class DomainError(Exception):
    """Base class for business rule violations."""


class NotFoundError(DomainError):
    pass


class InvalidTransitionError(DomainError):
    pass


class InvariantViolationError(DomainError):
    pass
```

- [ ] **Step 4: Implement the object type service**

```python
# backend/app/services/__init__.py
```

```python
# backend/app/services/ontology.py
from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidTransitionError, NotFoundError
from app.models import ObjectType, Property, Relation

DRAFT = "draft"
CONFIRMED = "confirmed"
APPROVED = "approved"

ALLOWED_TRANSITIONS: dict[str, set[str]] = {
    DRAFT: {CONFIRMED},
    CONFIRMED: {APPROVED},
    APPROVED: set(),
}


def create_object_type(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
) -> ObjectType:
    obj = ObjectType(
        owner_id=owner_id,
        name=name,
        description=description,
        status=DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(obj)
    session.flush()
    return obj


def get_object_type(session: Session, object_type_id: uuid.UUID) -> ObjectType:
    obj = session.get(ObjectType, object_type_id)
    if obj is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    return obj


def list_object_types(session: Session) -> list[ObjectType]:
    return list(session.scalars(select(ObjectType).order_by(ObjectType.name)))


def transition_object_type(
    session: Session, object_type_id: uuid.UUID, target: str
) -> ObjectType:
    obj = get_object_type(session, object_type_id)
    if target not in ALLOWED_TRANSITIONS.get(obj.status, set()):
        raise InvalidTransitionError(
            f"object type {object_type_id} cannot move {obj.status} -> {target}"
        )
    obj.status = target
    session.flush()
    return obj
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_ontology_service.py -v`
Expected: PASS（7 个测试全过）

- [ ] **Step 6: Commit**

```bash
git add backend/app/errors.py backend/app/services backend/tests/test_ontology_service.py
git commit -m "feat(backend): add object type service with status machine"
```

---

## Task 6: 属性与关系服务

**Files:**
- Modify: `backend/app/services/ontology.py`
- Test: `backend/tests/test_ontology_service.py`

- [ ] **Step 1: Append the failing tests**

```python
# backend/tests/test_ontology_service.py（在文件末尾追加）


def test_create_property_requires_existing_owner(session):
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    prop = ontology.create_property(
        session, owner_type_id=obj.id, name="数量", datatype="int", required=True
    )
    assert prop.name == "数量"
    assert prop.required is True
    assert prop.status == "draft"


def test_create_property_rejects_unknown_owner(session):
    with pytest.raises(NotFoundError):
        ontology.create_property(
            session, owner_type_id=uuid.uuid4(), name="数量", datatype="int"
        )


def test_create_relation_requires_both_endpoints(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    assert rel.status == "draft"
    assert rel.direction == "forward"


def test_create_relation_rejects_missing_endpoint(session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    with pytest.raises(NotFoundError):
        ontology.create_relation(
            session,
            owner_id=uuid.uuid4(),
            source_type_id=customer.id,
            target_type_id=uuid.uuid4(),
            name="下订单",
        )
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_ontology_service.py -v`
Expected: FAIL — `AttributeError: module 'app.services.ontology' has no attribute 'create_property'`

- [ ] **Step 3: Append the property and relation services**

```python
# backend/app/services/ontology.py（在文件末尾追加）


def create_property(
    session: Session,
    *,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = "text",
    required: bool = False,
) -> Property:
    get_object_type(session, owner_type_id)
    prop = Property(
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
        status=DRAFT,
    )
    session.add(prop)
    session.flush()
    return prop


def list_properties(session: Session, owner_type_id: uuid.UUID) -> list[Property]:
    return list(
        session.scalars(
            select(Property)
            .where(Property.owner_type_id == owner_type_id)
            .order_by(Property.name)
        )
    )


def create_relation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    home_graph_id: uuid.UUID | None = None,
) -> Relation:
    get_object_type(session, source_type_id)
    get_object_type(session, target_type_id)
    rel = Relation(
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        status=DRAFT,
        home_graph_id=home_graph_id,
    )
    session.add(rel)
    session.flush()
    return rel


def get_relation(session: Session, relation_id: uuid.UUID) -> Relation:
    rel = session.get(Relation, relation_id)
    if rel is None:
        raise NotFoundError(f"relation {relation_id} not found")
    return rel


def transition_relation(
    session: Session, relation_id: uuid.UUID, target: str
) -> Relation:
    rel = get_relation(session, relation_id)
    if target not in ALLOWED_TRANSITIONS.get(rel.status, set()):
        raise InvalidTransitionError(
            f"relation {relation_id} cannot move {rel.status} -> {target}"
        )
    rel.status = target
    session.flush()
    return rel
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_ontology_service.py -v`
Expected: PASS（11 个测试全过）

- [ ] **Step 5: Commit**

```bash
git add backend/app/services/ontology.py backend/tests/test_ontology_service.py
git commit -m "feat(backend): add property and relation services"
```

---

## Task 7: 图服务与完整性约束

**Files:**
- Create: `backend/app/services/graph.py`
- Test: `backend/tests/test_graph_service.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_graph_service.py
import uuid

import pytest

from app.errors import InvariantViolationError, NotFoundError
from app.services import graph, ontology


def test_create_graph(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="销售域建模")
    assert g.name == "销售域建模"
    assert g.id is not None


def test_add_object_to_graph_is_idempotent(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    graph.add_object_to_graph(session, g.id, obj.id)
    graph.add_object_to_graph(session, g.id, obj.id)

    assert graph.list_graph_objects(session, g.id) == [obj.id]


def test_add_object_to_graph_rejects_unknown_object(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    with pytest.raises(NotFoundError):
        graph.add_object_to_graph(session, g.id, uuid.uuid4())


def test_add_relation_requires_both_endpoints_in_graph(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )

    graph.add_object_to_graph(session, g.id, customer.id)
    with pytest.raises(InvariantViolationError):
        graph.add_relation_to_graph(session, g.id, rel.id)

    graph.add_object_to_graph(session, g.id, order.id)
    graph.add_relation_to_graph(session, g.id, rel.id)
    assert graph.list_graph_relations(session, g.id) == [rel.id]


def test_relation_can_be_used_by_two_graphs_differently(session):
    g1 = graph.create_graph(session, owner_id=uuid.uuid4(), name="g1")
    g2 = graph.create_graph(session, owner_id=uuid.uuid4(), name="g2")
    a = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="供货方")
    b = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="采购方")
    supplies = ontology.create_relation(
        session, owner_id=uuid.uuid4(), source_type_id=a.id, target_type_id=b.id, name="供应"
    )
    orders = ontology.create_relation(
        session, owner_id=uuid.uuid4(), source_type_id=a.id, target_type_id=b.id, name="下订单"
    )
    for g in (g1, g2):
        graph.add_object_to_graph(session, g.id, a.id)
        graph.add_object_to_graph(session, g.id, b.id)
    graph.add_relation_to_graph(session, g1.id, supplies.id)
    graph.add_relation_to_graph(session, g2.id, orders.id)

    assert graph.list_graph_relations(session, g1.id) == [supplies.id]
    assert graph.list_graph_relations(session, g2.id) == [orders.id]


def test_removing_object_clears_its_graph_relations(session):
    g = graph.create_graph(session, owner_id=uuid.uuid4(), name="g")
    a = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    b = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session, owner_id=uuid.uuid4(), source_type_id=a.id, target_type_id=b.id, name="下订单"
    )
    for obj in (a, b):
        graph.add_object_to_graph(session, g.id, obj.id)
    graph.add_relation_to_graph(session, g.id, rel.id)

    graph.remove_object_from_graph(session, g.id, a.id)

    assert a.id not in graph.list_graph_objects(session, g.id)
    assert graph.list_graph_relations(session, g.id) == []
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_graph_service.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.services.graph'`

- [ ] **Step 3: Implement the graph service**

```python
# backend/app/services/graph.py
from __future__ import annotations

import uuid

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Graph, GraphObject, GraphRelation, ObjectType, Relation


def create_graph(session: Session, *, owner_id: uuid.UUID, name: str | None = None) -> Graph:
    g = Graph(owner_id=owner_id, name=name)
    session.add(g)
    session.flush()
    return g


def get_graph(session: Session, graph_id: uuid.UUID) -> Graph:
    g = session.get(Graph, graph_id)
    if g is None:
        raise NotFoundError(f"graph {graph_id} not found")
    return g


def add_object_to_graph(
    session: Session, graph_id: uuid.UUID, object_type_id: uuid.UUID
) -> None:
    get_graph(session, graph_id)
    if session.get(ObjectType, object_type_id) is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    if session.get(GraphObject, (graph_id, object_type_id)) is not None:
        return
    session.add(GraphObject(graph_id=graph_id, object_type_id=object_type_id))
    session.flush()


def list_graph_objects(session: Session, graph_id: uuid.UUID) -> list[uuid.UUID]:
    rows = session.scalars(
        select(GraphObject.object_type_id)
        .where(GraphObject.graph_id == graph_id)
        .order_by(GraphObject.added_at, GraphObject.object_type_id)
    )
    return list(rows)


def add_relation_to_graph(
    session: Session, graph_id: uuid.UUID, relation_id: uuid.UUID
) -> None:
    get_graph(session, graph_id)
    rel = session.get(Relation, relation_id)
    if rel is None:
        raise NotFoundError(f"relation {relation_id} not found")

    present = set(list_graph_objects(session, graph_id))
    missing = {rel.source_type_id, rel.target_type_id} - present
    if missing:
        raise InvariantViolationError(
            "relation endpoints must be in the graph before the relation: "
            + ", ".join(sorted(str(m) for m in missing))
        )

    if session.get(GraphRelation, (graph_id, relation_id)) is not None:
        return
    session.add(GraphRelation(graph_id=graph_id, relation_id=relation_id))
    session.flush()


def list_graph_relations(session: Session, graph_id: uuid.UUID) -> list[uuid.UUID]:
    rows = session.scalars(
        select(GraphRelation.relation_id)
        .where(GraphRelation.graph_id == graph_id)
        .order_by(GraphRelation.added_at, GraphRelation.relation_id)
    )
    return list(rows)


def remove_object_from_graph(
    session: Session, graph_id: uuid.UUID, object_type_id: uuid.UUID
) -> None:
    """把对象移出图时，必须同时清掉引用它的图关系，否则会留下悬空边。"""
    relations = session.scalars(
        select(Relation).where(
            (Relation.source_type_id == object_type_id)
            | (Relation.target_type_id == object_type_id)
        )
    ).all()
    for rel in relations:
        session.execute(
            delete(GraphRelation).where(
                GraphRelation.graph_id == graph_id,
                GraphRelation.relation_id == rel.id,
            )
        )
    session.execute(
        delete(GraphObject).where(
            GraphObject.graph_id == graph_id,
            GraphObject.object_type_id == object_type_id,
        )
    )
    session.flush()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && python -m pytest tests/test_graph_service.py -v`
Expected: PASS（6 个测试全过）

- [ ] **Step 5: Commit**

```bash
git add backend/app/services/graph.py backend/tests/test_graph_service.py
git commit -m "feat(backend): add graph service with relation endpoint invariant"
```

---

## Task 8: REST 接口

**Files:**
- Create: `backend/app/schemas.py`
- Create: `backend/app/deps.py`
- Create: `backend/app/api/__init__.py`
- Create: `backend/app/api/routes.py`
- Modify: `backend/app/main.py`
- Test: `backend/tests/test_api.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_api.py
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from app.deps import get_session
from app.main import create_app
from app.services import ontology


@pytest.fixture
def client(session: Session) -> TestClient:
    app = create_app()
    app.dependency_overrides[get_session] = lambda: session
    return TestClient(app)


def test_create_and_confirm_object_type(client):
    created = client.post("/object-types", json={"name": "客户"})
    assert created.status_code == 201
    body = created.json()
    assert body["status"] == "draft"

    confirmed = client.post(f"/object-types/{body['id']}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_confirm_twice_is_rejected(client):
    body = client.post("/object-types", json={"name": "订单"}).json()
    client.post(f"/object-types/{body['id']}/confirm")
    again = client.post(f"/object-types/{body['id']}/confirm")
    assert again.status_code == 409
    assert again.json()["error"] == "invalid_transition"


def test_unknown_object_type_returns_404(client):
    resp = client.post(f"/object-types/{uuid.uuid4()}/confirm")
    assert resp.status_code == 404
    assert resp.json()["error"] == "not_found"


def test_graph_relation_endpoint_invariant_returns_409(client, session):
    customer = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    order = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="订单")
    rel = ontology.create_relation(
        session,
        owner_id=uuid.uuid4(),
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    gid = client.post("/graphs", json={"name": "销售域"}).json()["id"]
    client.post(f"/graphs/{gid}/objects", json={"object_type_id": str(customer.id)})

    resp = client.post(f"/graphs/{gid}/relations", json={"relation_id": str(rel.id)})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invariant_violation"


def test_object_type_response_has_no_internal_fields(client):
    body = client.post("/object-types", json={"name": "产品"}).json()
    assert set(body) == {"id", "name", "description", "status"}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && python -m pytest tests/test_api.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.deps'`

- [ ] **Step 3: Implement the Pydantic schemas**

```python
# backend/app/schemas.py
from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class ObjectTypeCreate(BaseModel):
    name: str
    description: str | None = None


class ObjectTypeRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    description: str | None
    status: str


class GraphCreate(BaseModel):
    name: str | None = None


class GraphRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str | None


class ObjectMembership(BaseModel):
    object_type_id: uuid.UUID


class RelationMembership(BaseModel):
    relation_id: uuid.UUID


class ErrorBody(BaseModel):
    error: str
    message: str
```

- [ ] **Step 4: Implement the dependency and routes**

```python
# backend/app/deps.py
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
```

```python
# backend/app/api/__init__.py
```

```python
# backend/app/api/routes.py
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.deps import get_default_user_id, get_session
from app.schemas import (
    GraphCreate,
    GraphRead,
    ObjectMembership,
    ObjectTypeCreate,
    ObjectTypeRead,
    RelationMembership,
)
from app.services import graph, ontology

router = APIRouter()


@router.post("/object-types", response_model=ObjectTypeRead, status_code=201)
def create_object_type(
    payload: ObjectTypeCreate, session: Session = Depends(get_session)
) -> ObjectTypeRead:
    obj = ontology.create_object_type(
        session,
        owner_id=uuid.UUID(get_default_user_id()),
        name=payload.name,
        description=payload.description,
    )
    session.commit()
    return ObjectTypeRead.model_validate(obj)


@router.post("/object-types/{object_type_id}/confirm", response_model=ObjectTypeRead)
def confirm_object_type(
    object_type_id: uuid.UUID, session: Session = Depends(get_session)
) -> ObjectTypeRead:
    obj = ontology.transition_object_type(session, object_type_id, "confirmed")
    session.commit()
    return ObjectTypeRead.model_validate(obj)


@router.post("/graphs", response_model=GraphRead, status_code=201)
def create_graph(
    payload: GraphCreate, session: Session = Depends(get_session)
) -> GraphRead:
    g = graph.create_graph(
        session, owner_id=uuid.UUID(get_default_user_id()), name=payload.name
    )
    session.commit()
    return GraphRead.model_validate(g)


@router.post("/graphs/{graph_id}/objects", status_code=204)
def add_object(
    graph_id: uuid.UUID,
    payload: ObjectMembership,
    session: Session = Depends(get_session),
) -> None:
    graph.add_object_to_graph(session, graph_id, payload.object_type_id)
    session.commit()


@router.post("/graphs/{graph_id}/relations", status_code=204)
def add_relation(
    graph_id: uuid.UUID,
    payload: RelationMembership,
    session: Session = Depends(get_session),
) -> None:
    graph.add_relation_to_graph(session, graph_id, payload.relation_id)
    session.commit()
```

- [ ] **Step 5: Wire the error handlers and router**

```python
# backend/app/main.py
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.routes import router
from app.errors import InvariantViolationError, InvalidTransitionError, NotFoundError


def create_app() -> FastAPI:
    app = FastAPI(title="OntoWeave", version="0.1.0")
    app.include_router(router)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.exception_handler(NotFoundError)
    async def _not_found(_: Request, exc: NotFoundError) -> JSONResponse:
        return JSONResponse(
            status_code=404, content={"error": "not_found", "message": str(exc)}
        )

    @app.exception_handler(InvalidTransitionError)
    async def _invalid(_: Request, exc: InvalidTransitionError) -> JSONResponse:
        return JSONResponse(
            status_code=409,
            content={"error": "invalid_transition", "message": str(exc)},
        )

    @app.exception_handler(InvariantViolationError)
    async def _invariant(_: Request, exc: InvariantViolationError) -> JSONResponse:
        return JSONResponse(
            status_code=409,
            content={"error": "invariant_violation", "message": str(exc)},
        )

    return app


app = create_app()
```

- [ ] **Step 6: Run the whole suite**

Run: `cd backend && python -m pytest -v`
Expected: PASS（全部测试通过）

- [ ] **Step 7: Commit**

```bash
git add backend/app/schemas.py backend/app/deps.py backend/app/api backend/app/main.py backend/tests/test_api.py
git commit -m "feat(backend): expose REST endpoints for object types and graphs"
```

---

## Task 9: 运行说明与收尾

**Files:**
- Create: `backend/README.md`
- Create: `backend/.gitignore`

- [ ] **Step 1: Write the README**

````markdown
# OntoWeave Backend

## 本地起服务

```bash
docker compose up -d db
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head
uvicorn app.main:app --reload
```

打开 http://127.0.0.1:8000/docs 看接口文档。

## 跑测试

```bash
docker compose up -d db
python -m pytest -v
```

测试连 `TEST_DATABASE_URL`（默认就是本地的 `ontoweave` 库），每次会话自建自删 schema，不动生产数据。
````

- [ ] **Step 2: Add the gitignore**

```gitignore
# backend/.gitignore
.venv/
__pycache__/
*.pyc
.env
.pytest_cache/
```

- [ ] **Step 3: Run the whole suite one more time**

Run: `cd backend && python -m pytest -v`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add backend/README.md backend/.gitignore
git commit -m "docs(backend): add local run instructions"
```

---

## 自检记录

- **Spec coverage（设计文档第 4 节）：** 对象类型 / 属性 / 关系 ✅；共享池与图子集 ✅；`graph_object` / `graph_relation` ✅；状态机三态 ✅；关系端点完整性约束 ✅；会话表 ✅（建表，服务在后续计划）；视图状态表 ✅（建表，服务在后续计划）；改动集表 ✅（建表，服务在后续计划）。
- **未覆盖（属于后续计划）：** 会话与图的管理接口、改动集与确认 / 撤销接口、视图状态接口、pgvector 相似检索、智能体与 SSE、前端。
- **类型一致性：** 服务函数名在任务间保持一致——`create_object_type` / `get_object_type` / `list_object_types` / `transition_object_type` / `create_property` / `list_properties` / `create_relation` / `get_relation` / `transition_relation` / `create_graph` / `get_graph` / `add_object_to_graph` / `list_graph_objects` / `add_relation_to_graph` / `list_graph_relations` / `remove_object_from_graph`；异常类 `NotFoundError` / `InvalidTransitionError` / `InvariantViolationError` 统一从 `app.errors` 导入；Pydantic 出入参 `ObjectTypeCreate` / `ObjectTypeRead` / `GraphCreate` / `GraphRead` / `ObjectMembership` / `RelationMembership` 在路由与测试间一致。

## 后续计划（本文件之外）

1. **Plan 2 · 会话、改动集与视图**：`conversation` 与 `graph` 的创建绑定、会话列表接口、`changeset` / `changeset_op` 写入、确认 / 撤销接口、`view_state` 读写与坐标持久化。
2. **Plan 3 · 智能体与通信**：LangChain + LangGraph + deep agent、主智能体工具集、绘图子智能体、SSE 与中断恢复、pgvector 相似检索。
3. **Plan 4 · 前端**：首页菜单、建模三栏布局、React Flow 画布、草图态与幽灵态、视图状态同步。
