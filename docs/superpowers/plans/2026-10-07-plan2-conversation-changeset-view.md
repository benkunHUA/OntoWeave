# Plan 2 · 会话、改动集与视图状态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让「一次对话一张图」跑起来，让改动能以改动集为单位确认或撤销，让每个用户对每张图有自己的视图状态。

**Architecture:** 沿用 Plan 1 的分层：`services/` 放领域逻辑，`api/` 放薄路由，`schemas/` 按领域拆。会话与图一对一，建会话即建图。改动集记录操作；`create` 的元素在草稿阶段就入库（`status='draft'`），确认翻状态、撤销删元素；`delete` 在草稿阶段只登记不真删，确认才删。

**Tech Stack:** 同 Plan 1（FastAPI、SQLAlchemy 2.0、Alembic、Postgres、pytest、ruff、mypy）。

**对应设计文档：** 第 4、5、7、9 节。

**前置：** Plan 1 与后端加固已完成，当前 `0004 (head)`，43 个测试通过。

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `app/services/conversation.py` | 会话与图的创建绑定、列表、读取 |
| `app/services/changeset.py` | 改动集记录、确认、撤销 |
| `app/services/view_state.py` | 视图状态读写 |
| `app/schemas/conversation.py`、`changeset.py`、`view_state.py` | 出入参 |
| `app/api/conversations.py`、`changesets.py`、`view_state.py` | 路由 |
| `tests/test_conversation.py`、`test_changeset.py`、`test_view_state.py` | 测试 |

---

## Task 1: 会话与图

**Files:**
- Create: `backend/app/schemas/conversation.py`
- Create: `backend/app/services/conversation.py`
- Create: `backend/app/api/conversations.py`
- Modify: `backend/app/api/router.py`
- Modify: `backend/app/deps.py`（新增 `get_current_user_id`）
- Modify: 现有 `app/api/object_types.py`、`app/api/graphs.py`（改用 `get_current_user_id`）
- Test: `backend/tests/test_conversation.py`

- [ ] **Step 1: Write the failing test**

```python
# backend/tests/test_conversation.py
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.main import create_app
from app.services import conversation

API = "/api/v1"


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def test_create_conversation_creates_a_graph(session):
    conv = conversation.create_conversation(
        session, owner_id=uuid.uuid4(), title="销售域建模"
    )
    assert conv.graph_id is not None
    assert conv.title == "销售域建模"


def test_list_conversations_paginates(session):
    owner = uuid.uuid4()
    conversation.create_conversation(session, owner_id=owner, title="c-1")
    conversation.create_conversation(session, owner_id=owner, title="c-2")
    conversation.create_conversation(session, owner_id=owner, title="c-3")

    page = conversation.list_conversations(session, limit=2, offset=1)
    assert len(page) == 2


def test_missing_conversation_raises(session):
    with pytest.raises(NotFoundError):
        conversation.get_conversation(session, uuid.uuid4())


def test_conversation_api_roundtrip(client):
    created = client.post(f"{API}/conversations", json={"title": "供应链梳理"})
    assert created.status_code == 201
    body = created.json()
    assert body["title"] == "供应链梳理"
    assert body["graph_id"]

    fetched = client.get(f"{API}/conversations/{body['id']}")
    assert fetched.status_code == 200
    assert fetched.json()["graph_id"] == body["graph_id"]

    listed = client.get(f"{API}/conversations", params={"limit": 50}).json()
    assert any(item["id"] == body["id"] for item in listed)
```

（`NotFoundError` 从 `app.errors` 导入；测试文件顶部补上。）

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_conversation.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.services.conversation'`

- [ ] **Step 3: Add the current-user dependency**

```python
# app/deps.py（在文件末尾追加）
import uuid


def get_current_user_id() -> uuid.UUID:
    """一期没有用户系统，返回固定的默认用户。"""
    return uuid.UUID(get_settings().default_user_id)
```

然后把 `app/api/object_types.py` 和 `app/api/graphs.py` 里的
`owner_id=uuid.UUID(get_default_user_id())` 换成 `owner_id=get_current_user_id()`，
导入相应调整。

- [ ] **Step 4: Implement the schemas and service**

```python
# app/schemas/conversation.py
from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class ConversationCreate(BaseModel):
    title: str | None = None


class ConversationRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    graph_id: uuid.UUID
    title: str | None
```

```python
# app/services/conversation.py
from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import Conversation
from app.services import graph


def create_conversation(
    session: Session, *, owner_id: uuid.UUID, title: str | None = None
) -> Conversation:
    """一次对话一张图：建会话的同时把图建出来，两者一对一。"""
    g = graph.create_graph(session, owner_id=owner_id, name=title)
    conv = Conversation(owner_id=owner_id, graph_id=g.id, title=title)
    session.add(conv)
    session.flush()
    return conv


def get_conversation(session: Session, conversation_id: uuid.UUID) -> Conversation:
    conv = session.get(Conversation, conversation_id)
    if conv is None:
        raise NotFoundError(f"conversation {conversation_id} not found")
    return conv


def list_conversations(
    session: Session, *, limit: int = 50, offset: int = 0
) -> list[Conversation]:
    return list(
        session.scalars(
            select(Conversation)
            .order_by(Conversation.created_at.desc(), Conversation.id)
            .limit(limit)
            .offset(offset)
        )
    )
```

- [ ] **Step 5: Implement the routes and register them**

```python
# app/api/conversations.py
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.conversation import ConversationCreate, ConversationRead
from app.services import conversation

router = APIRouter(tags=["conversations"])


@router.post("/conversations", response_model=ConversationRead, status_code=201)
def create_conversation(
    payload: ConversationCreate, session: Session = Depends(get_session)
) -> ConversationRead:
    conv = conversation.create_conversation(
        session, owner_id=get_current_user_id(), title=payload.title
    )
    return ConversationRead.model_validate(conv)


@router.get("/conversations", response_model=list[ConversationRead])
def list_conversations(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ConversationRead]:
    items = conversation.list_conversations(session, limit=limit, offset=offset)
    return [ConversationRead.model_validate(item) for item in items]


@router.get("/conversations/{conversation_id}", response_model=ConversationRead)
def get_conversation(
    conversation_id: uuid.UUID, session: Session = Depends(get_session)
) -> ConversationRead:
    conv = conversation.get_conversation(session, conversation_id)
    return ConversationRead.model_validate(conv)
```

```python
# app/api/router.py
from fastapi import APIRouter

from app.api import changesets, conversations, graphs, object_types, view_state

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(object_types.router)
api_router.include_router(graphs.router)
api_router.include_router(conversations.router)
api_router.include_router(changesets.router)
api_router.include_router(view_state.router)
```

（Task 1 阶段先只加 `conversations`，其余三个在各自任务里补上，保持每步可运行。）

- [ ] **Step 6: Run tests**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_conversation.py -q`
Expected: PASS（4 个用例）

- [ ] **Step 7: 全量检查并提交**

```bash
cd backend
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
cd ..
git add backend && git commit -m "feat(backend): conversation creates its graph, list and fetch endpoints"
```

---

## Task 2: 改动集记录

**Files:**
- Create: `backend/app/schemas/changeset.py`
- Create: `backend/app/services/changeset.py`
- Test: `backend/tests/test_changeset.py`

- [ ] **Step 1: Write the failing test**

```python
# backend/tests/test_changeset.py
import uuid

import pytest

from app.errors import InvalidTransitionError, NotFoundError
from app.services import changeset, conversation, ontology


def _graph_id(session) -> uuid.UUID:
    return conversation.create_conversation(
        session, owner_id=uuid.uuid4(), title="测试"
    ).graph_id


def test_create_changeset_is_open(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    assert cs.status == "open"
    assert cs.id is not None


def test_record_op_and_list(session):
    graph_id = _graph_id(session)
    cs = changeset.create_changeset(session, graph_id=graph_id)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    ops = changeset.list_ops(session, cs.id)
    assert len(ops) == 1
    assert ops[0].op == "create"
    assert ops[0].target_id == obj.id


def test_cannot_record_op_into_closed_changeset(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    changeset.confirm_changeset(session, cs.id)
    with pytest.raises(InvalidTransitionError):
        changeset.record_op(session, changeset_id=cs.id, op="create")


def test_missing_changeset_raises(session):
    with pytest.raises(NotFoundError):
        changeset.get_changeset(session, uuid.uuid4())


def test_list_changesets_filters_by_graph(session):
    graph_id = _graph_id(session)
    other = _graph_id(session)
    ours = changeset.create_changeset(session, graph_id=graph_id)
    changeset.create_changeset(session, graph_id=other)

    ids = [cs.id for cs in changeset.list_changesets(session, graph_id=graph_id)]
    assert ids == [ours.id]
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_changeset.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.services.changeset'`

- [ ] **Step 3: Implement schemas and the recording part of the service**

```python
# app/schemas/changeset.py
from __future__ import annotations

import uuid
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ChangesetOpCreate(BaseModel):
    op: Literal["create", "update", "delete"]
    target_id: uuid.UUID | None = None
    payload: dict = Field(default_factory=dict)


class ChangesetOpRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    op: str
    target_id: uuid.UUID | None
    payload: dict


class ChangesetRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    graph_id: uuid.UUID
    status: str


class ChangesetDetail(ChangesetRead):
    ops: list[ChangesetOpRead]
```

```python
# app/services/changeset.py
from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import InvalidTransitionError, NotFoundError
from app.models import Changeset, ChangesetOp
from app.services import graph

OPEN = "open"
CONFIRMED = "confirmed"
REJECTED = "rejected"


def create_changeset(session: Session, *, graph_id: uuid.UUID) -> Changeset:
    graph.get_graph(session, graph_id)
    cs = Changeset(graph_id=graph_id, status=OPEN)
    session.add(cs)
    session.flush()
    return cs


def get_changeset(session: Session, changeset_id: uuid.UUID) -> Changeset:
    cs = session.get(Changeset, changeset_id)
    if cs is None:
        raise NotFoundError(f"changeset {changeset_id} not found")
    return cs


def list_changesets(
    session: Session, *, graph_id: uuid.UUID, limit: int = 50, offset: int = 0
) -> list[Changeset]:
    return list(
        session.scalars(
            select(Changeset)
            .where(Changeset.graph_id == graph_id)
            .order_by(Changeset.created_at.desc(), Changeset.id)
            .limit(limit)
            .offset(offset)
        )
    )


def list_ops(session: Session, changeset_id: uuid.UUID) -> list[ChangesetOp]:
    return list(
        session.scalars(
            select(ChangesetOp)
            .where(ChangesetOp.changeset_id == changeset_id)
            .order_by(ChangesetOp.created_at, ChangesetOp.id)
        )
    )


def record_op(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    op: str,
    target_id: uuid.UUID | None = None,
    payload: dict | None = None,
) -> ChangesetOp:
    cs = get_changeset(session, changeset_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(
            f"changeset {changeset_id} is {cs.status}, cannot record new ops"
        )
    row = ChangesetOp(
        changeset_id=changeset_id,
        op=op,
        target_id=target_id,
        payload=payload or {},
    )
    session.add(row)
    session.flush()
    return row
```

（`ChangesetOp` 需要 `created_at` 排序键，见 Task 3 的迁移。）

- [ ] **Step 4: Run tests**

Run 前先在 `app/models.py` 给 `ChangesetOp` 加一个 `created_at` 字段（`server_default=func.now()`），并生成迁移 `0005`：

```bash
cd backend
./.venv/bin/alembic revision --autogenerate -m "changeset op created_at" --rev-id 0005
./.venv/bin/alembic upgrade head
./.venv/bin/python -m pytest tests/test_changeset.py -q
```
Expected: PASS（5 个用例；`confirm_changeset` 尚未实现，`test_cannot_record_op_into_closed_changeset` 会先失败 —— 把它调到 Task 4 之后跑，或在本步先实现 `confirm_changeset` 的骨架）

> 说明：这一步的顺序是为了保持每步可运行。实现时把 `confirm_changeset` / `reject_changeset`
> 一并写在 `changeset.py` 里（Task 4 的代码），然后统一跑测试。

- [ ] **Step 5: 全量检查并提交**

```bash
cd backend && ./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
cd .. && git add backend && git commit -m "feat(backend): changeset records granular ops"
```

---

## Task 3: 确认与撤销

**Files:**
- Modify: `backend/app/services/ontology.py`（新增 `transition_property`）
- Modify: `backend/app/services/changeset.py`（新增 `confirm_changeset` / `reject_changeset`）
- Test: `backend/tests/test_changeset.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_changeset.py（在文件末尾追加）


def test_confirm_promotes_created_elements(session):
    graph_id = _graph_id(session)
    cs = changeset.create_changeset(session, graph_id=graph_id)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    changeset.confirm_changeset(session, cs.id)

    assert changeset.get_changeset(session, cs.id).status == "confirmed"
    assert ontology.get_object_type(session, obj.id).status == "confirmed"


def test_reject_deletes_created_elements(session):
    graph_id = _graph_id(session)
    cs = changeset.create_changeset(session, graph_id=graph_id)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )

    changeset.reject_changeset(session, cs.id)

    assert changeset.get_changeset(session, cs.id).status == "rejected"
    with pytest.raises(NotFoundError):
        ontology.get_object_type(session, obj.id)


def test_reject_restores_updated_element(session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    before = {"name": "客户", "description": None}

    obj.name = "终端客户"
    cs = changeset.create_changeset(session, graph_id=graph_id)
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="update",
        target_id=obj.id,
        payload={"kind": "object_type", "before": before},
    )

    changeset.reject_changeset(session, cs.id)

    restored = ontology.get_object_type(session, obj.id)
    assert restored.name == "客户"


def test_confirm_deletes_element_flagged_for_deletion(session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")

    cs = changeset.create_changeset(session, graph_id=graph_id)
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="delete",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )
    # 草稿阶段不真删
    assert ontology.get_object_type(session, obj.id) is not None

    changeset.confirm_changeset(session, cs.id)
    with pytest.raises(NotFoundError):
        ontology.get_object_type(session, obj.id)


def test_cannot_confirm_twice(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    changeset.confirm_changeset(session, cs.id)
    with pytest.raises(InvalidTransitionError):
        changeset.confirm_changeset(session, cs.id)


def test_confirm_rejects_dangling_target(session):
    cs = changeset.create_changeset(session, graph_id=_graph_id(session))
    changeset.record_op(
        session,
        changeset_id=cs.id,
        op="create",
        target_id=uuid.uuid4(),
        payload={"kind": "object_type"},
    )
    with pytest.raises(NotFoundError):
        changeset.confirm_changeset(session, cs.id)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_changeset.py -q`
Expected: FAIL — `AttributeError: module 'app.services.changeset' has no attribute 'confirm_changeset'`

- [ ] **Step 3: Add `transition_property` to the ontology service**

```python
# app/services/ontology.py（在文件末尾追加）


def transition_property(
    session: Session, property_id: uuid.UUID, target: str
) -> Property:
    prop = session.get(Property, property_id)
    if prop is None:
        raise NotFoundError(f"property {property_id} not found")
    if target not in ALLOWED_TRANSITIONS.get(prop.status, set()):
        raise InvalidTransitionError(
            f"property {property_id} cannot move {prop.status} -> {target}"
        )
    prop.status = target
    session.flush()
    return prop
```

- [ ] **Step 4: Implement confirm and reject**

```python
# app/services/changeset.py（在文件末尾追加）
from app.models import ObjectType, Property, Relation
from app.services import ontology

_ELEMENT_MODELS = {
    "object_type": ObjectType,
    "relation": Relation,
    "property": Property,
}


def _load_element(session: Session, kind: str, element_id: uuid.UUID | None):
    model = _ELEMENT_MODELS.get(kind)
    if model is None or element_id is None:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")
    element = session.get(model, element_id)
    if element is None:
        raise NotFoundError(f"{kind} {element_id} not found")
    return element


def _promote(session: Session, kind: str, element_id: uuid.UUID) -> None:
    if kind == "object_type":
        ontology.transition_object_type(session, element_id, "confirmed")
    elif kind == "relation":
        ontology.transition_relation(session, element_id, "confirmed")
    elif kind == "property":
        ontology.transition_property(session, element_id, "confirmed")
    else:
        raise InvariantViolationError(f"unsupported changeset op target: {kind!r}")


def confirm_changeset(session: Session, changeset_id: uuid.UUID) -> Changeset:
    cs = get_changeset(session, changeset_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    # 先全部校验再落动作，避免改到一半失败留下半生不熟的状态。
    ops = list_ops(session, cs.id)
    for op in ops:
        kind = op.payload.get("kind")
        if op.op in {"create", "update", "delete"}:
            _load_element(session, kind, op.target_id)

    for op in ops:
        kind = op.payload["kind"]
        if op.op == "create":
            _promote(session, kind, op.target_id)
        elif op.op == "update":
            pass  # 值在草稿阶段已经写好，确认时无需再动
        elif op.op == "delete":
            session.delete(_load_element(session, kind, op.target_id))

    cs.status = CONFIRMED
    session.flush()
    return cs


def reject_changeset(session: Session, changeset_id: uuid.UUID) -> Changeset:
    cs = get_changeset(session, changeset_id)
    if cs.status != OPEN:
        raise InvalidTransitionError(f"changeset {changeset_id} is already {cs.status}")

    for op in reversed(list_ops(session, cs.id)):
        kind = op.payload.get("kind")
        if op.op == "create":
            session.delete(_load_element(session, kind, op.target_id))
        elif op.op == "update":
            element = _load_element(session, kind, op.target_id)
            for field, value in op.payload.get("before", {}).items():
                setattr(element, field, value)
        elif op.op == "delete":
            pass  # 草稿阶段没真删，撤销即无事发生

    cs.status = REJECTED
    session.flush()
    return cs
```

（`InvariantViolationError` 从 `app.errors` 导入。）

- [ ] **Step 5: Run tests**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_changeset.py -q`
Expected: PASS（11 个用例）

- [ ] **Step 6: 全量检查并提交**

```bash
cd backend && ./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
cd .. && git add backend && git commit -m "feat(backend): confirm promotes draft elements, reject deletes them"
```

---

## Task 4: 改动集接口

**Files:**
- Create: `backend/app/api/changesets.py`
- Modify: `backend/app/api/router.py`
- Test: `backend/tests/test_changeset.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_changeset.py（在文件末尾追加）
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.main import create_app


@pytest.fixture
def api_client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def test_changeset_api_confirm_flow(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="客户")
    session.commit()

    cs = api_client.post(f"/api/v1/graphs/{graph_id}/changesets")
    assert cs.status_code == 201
    cid = cs.json()["id"]

    recorded = api_client.post(
        f"/api/v1/changesets/{cid}/ops",
        json={"op": "create", "target_id": str(obj.id), "payload": {"kind": "object_type"}},
    )
    assert recorded.status_code == 201

    detail = api_client.get(f"/api/v1/changesets/{cid}")
    assert detail.status_code == 200
    assert len(detail.json()["ops"]) == 1

    confirmed = api_client.post(f"/api/v1/changesets/{cid}/confirm")
    assert confirmed.status_code == 200
    assert confirmed.json()["status"] == "confirmed"


def test_changeset_api_reject_flow(api_client, session):
    graph_id = _graph_id(session)
    obj = ontology.create_object_type(session, owner_id=uuid.uuid4(), name="临时")
    session.commit()

    cid = api_client.post(f"/api/v1/graphs/{graph_id}/changesets").json()["id"]
    api_client.post(
        f"/api/v1/changesets/{cid}/ops",
        json={"op": "create", "target_id": str(obj.id), "payload": {"kind": "object_type"}},
    )
    rejected = api_client.post(f"/api/v1/changesets/{cid}/reject")
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"


def test_recording_into_closed_changeset_returns_409(api_client):
    graph_id = api_client.post(
        "/api/v1/conversations", json={"title": "接口测试"}
    ).json()["graph_id"]
    cid = api_client.post(f"/api/v1/graphs/{graph_id}/changesets").json()["id"]
    api_client.post(f"/api/v1/changesets/{cid}/confirm")

    resp = api_client.post(f"/api/v1/changesets/{cid}/ops", json={"op": "create"})
    assert resp.status_code == 409
    assert resp.json()["error"] == "invalid_transition"
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_changeset.py -q`
Expected: FAIL — 404（路由不存在）

- [ ] **Step 3: Implement the routes**

```python
# app/api/changesets.py
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.deps import get_session
from app.schemas.changeset import (
    ChangesetDetail,
    ChangesetOpCreate,
    ChangesetOpRead,
    ChangesetRead,
)
from app.services import changeset

router = APIRouter(tags=["changesets"])


@router.post("/graphs/{graph_id}/changesets", response_model=ChangesetRead, status_code=201)
def create_changeset(
    graph_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetRead:
    cs = changeset.create_changeset(session, graph_id=graph_id)
    return ChangesetRead.model_validate(cs)


@router.get("/graphs/{graph_id}/changesets", response_model=list[ChangesetRead])
def list_changesets(
    graph_id: uuid.UUID,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    session: Session = Depends(get_session),
) -> list[ChangesetRead]:
    items = changeset.list_changesets(session, graph_id=graph_id, limit=limit, offset=offset)
    return [ChangesetRead.model_validate(item) for item in items]


@router.get("/changesets/{changeset_id}", response_model=ChangesetDetail)
def get_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetDetail:
    cs = changeset.get_changeset(session, changeset_id)
    detail = ChangesetDetail.model_validate(cs)
    detail.ops = [
        ChangesetOpRead.model_validate(op) for op in changeset.list_ops(session, cs.id)
    ]
    return detail


@router.post(
    "/changesets/{changeset_id}/ops", response_model=ChangesetOpRead, status_code=201
)
def record_op(
    changeset_id: uuid.UUID,
    payload: ChangesetOpCreate,
    session: Session = Depends(get_session),
) -> ChangesetOpRead:
    op = changeset.record_op(
        session,
        changeset_id=changeset_id,
        op=payload.op,
        target_id=payload.target_id,
        payload=payload.payload,
    )
    return ChangesetOpRead.model_validate(op)


@router.post("/changesets/{changeset_id}/confirm", response_model=ChangesetRead)
def confirm_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetRead:
    return ChangesetRead.model_validate(changeset.confirm_changeset(session, changeset_id))


@router.post("/changesets/{changeset_id}/reject", response_model=ChangesetRead)
def reject_changeset(
    changeset_id: uuid.UUID, session: Session = Depends(get_session)
) -> ChangesetRead:
    return ChangesetRead.model_validate(changeset.reject_changeset(session, changeset_id))
```

同时在 `app/api/router.py` 注册 `changesets.router`。

- [ ] **Step 4: Run tests**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_changeset.py -q`
Expected: PASS（14 个用例）

- [ ] **Step 5: 全量检查并提交**

```bash
cd backend && ./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
cd .. && git add backend && git commit -m "feat(backend): expose changeset endpoints"
```

---

## Task 5: 视图状态

**Files:**
- Create: `backend/app/schemas/view_state.py`
- Create: `backend/app/services/view_state.py`
- Create: `backend/app/api/view_state.py`
- Modify: `backend/app/api/router.py`
- Test: `backend/tests/test_view_state.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_view_state.py
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.errors import NotFoundError
from app.main import create_app
from app.services import conversation, view_state


@pytest.fixture
def client(session_factory: sessionmaker[Session]) -> TestClient:
    return TestClient(create_app(session_factory=session_factory))


def _graph_id(session) -> uuid.UUID:
    return conversation.create_conversation(
        session, owner_id=uuid.uuid4(), title="测试"
    ).graph_id


def test_absent_view_state_returns_none(session):
    assert view_state.get_view_state(
        session, graph_id=_graph_id(session), user_id=uuid.uuid4()
    ) is None


def test_upsert_creates_then_updates(session):
    graph_id = _graph_id(session)
    user_id = uuid.uuid4()

    created = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=user_id,
        name="销售域视图",
        spec={"nodes": {"t_customer": {"x": 120, "y": 340}}},
    )
    assert created.name == "销售域视图"

    updated = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=user_id,
        name="销售域视图",
        spec={"nodes": {}},
    )
    assert updated.spec == {"nodes": {}}


def test_upsert_rejects_unknown_graph(session):
    with pytest.raises(NotFoundError):
        view_state.upsert_view_state(
            session, graph_id=uuid.uuid4(), user_id=uuid.uuid4(), name=None, spec={}
        )


def test_view_state_api_roundtrip(client):
    graph_id = client.post("/api/v1/conversations", json={"title": "视图测试"}).json()[
        "graph_id"
    ]

    empty = client.get(f"/api/v1/graphs/{graph_id}/view-state")
    assert empty.status_code == 200
    assert empty.json()["spec"] == {}

    saved = client.put(
        f"/api/v1/graphs/{graph_id}/view-state",
        json={"name": "我的视图", "spec": {"layout": {"kind": "domain-grouped"}}},
    )
    assert saved.status_code == 200
    assert saved.json()["name"] == "我的视图"

    fetched = client.get(f"/api/v1/graphs/{graph_id}/view-state")
    assert fetched.json()["spec"] == {"layout": {"kind": "domain-grouped"}}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_view_state.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.services.view_state'`

- [ ] **Step 3: Implement schemas and service**

```python
# app/schemas/view_state.py
from __future__ import annotations

import uuid

from pydantic import BaseModel, Field


class ViewStateWrite(BaseModel):
    name: str | None = None
    spec: dict = Field(default_factory=dict)


class ViewStateRead(BaseModel):
    graph_id: uuid.UUID
    name: str | None
    spec: dict
```

```python
# app/services/view_state.py
from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.models import ViewState
from app.services import graph


def get_view_state(
    session: Session, *, graph_id: uuid.UUID, user_id: uuid.UUID
) -> ViewState | None:
    return session.get(ViewState, (graph_id, user_id))


def upsert_view_state(
    session: Session,
    *,
    graph_id: uuid.UUID,
    user_id: uuid.UUID,
    name: str | None,
    spec: dict,
) -> ViewState:
    graph.get_graph(session, graph_id)
    vs = session.get(ViewState, (graph_id, user_id))
    if vs is None:
        vs = ViewState(graph_id=graph_id, user_id=user_id, name=name, spec=spec)
        session.add(vs)
    else:
        vs.name = name
        vs.spec = spec
    session.flush()
    return vs
```

- [ ] **Step 4: Implement the routes**

```python
# app/api/view_state.py
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.deps import get_current_user_id, get_session
from app.schemas.view_state import ViewStateRead, ViewStateWrite
from app.services import view_state

router = APIRouter(tags=["view-state"])


@router.get("/graphs/{graph_id}/view-state", response_model=ViewStateRead)
def get_view_state(
    graph_id: uuid.UUID, session: Session = Depends(get_session)
) -> ViewStateRead:
    vs = view_state.get_view_state(
        session, graph_id=graph_id, user_id=get_current_user_id()
    )
    if vs is None:
        return ViewStateRead(graph_id=graph_id, name=None, spec={})
    return ViewStateRead(graph_id=vs.graph_id, name=vs.name, spec=vs.spec)


@router.put("/graphs/{graph_id}/view-state", response_model=ViewStateRead)
def put_view_state(
    graph_id: uuid.UUID,
    payload: ViewStateWrite,
    session: Session = Depends(get_session),
) -> ViewStateRead:
    vs = view_state.upsert_view_state(
        session,
        graph_id=graph_id,
        user_id=get_current_user_id(),
        name=payload.name,
        spec=payload.spec,
    )
    return ViewStateRead(graph_id=vs.graph_id, name=vs.name, spec=vs.spec)
```

同时在 `app/api/router.py` 注册 `view_state.router`。

- [ ] **Step 5: Run tests**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_view_state.py -q`
Expected: PASS（4 个用例）

- [ ] **Step 6: 全量检查并提交**

```bash
cd backend && ./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
cd .. && git add backend && git commit -m "feat(backend): per-user view state read and write"
```

---

## Task 6: 文档与收尾

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: 更新 README 的接口表**

把新增接口补进 `backend/README.md` 的接口表：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/conversations` | 新建会话（同时建出对应的图） |
| GET | `/api/v1/conversations` | 会话列表（分页） |
| GET | `/api/v1/conversations/{id}` | 会话详情 |
| POST | `/api/v1/graphs/{id}/changesets` | 新建改动集 |
| GET | `/api/v1/graphs/{id}/changesets` | 改动集列表 |
| GET | `/api/v1/changesets/{id}` | 改动集详情（含操作明细） |
| POST | `/api/v1/changesets/{id}/ops` | 往改动集里记一条操作 |
| POST | `/api/v1/changesets/{id}/confirm` | 确认：草案 → 已确认 |
| POST | `/api/v1/changesets/{id}/reject` | 撤销：删掉本次新建的元素、还原改动 |
| GET | `/api/v1/graphs/{id}/view-state` | 读当前用户的视图状态 |
| PUT | `/api/v1/graphs/{id}/view-state` | 覆盖写入视图状态 |

- [ ] **Step 2: 起服务手工过一遍**

```bash
cd backend && docker compose up -d --build
curl -s -X POST http://127.0.0.1:18000/api/v1/conversations \
  -H 'content-type: application/json' -d '{"title":"手工验证"}'
```
Expected: 201，返回带 `graph_id` 的会话

- [ ] **Step 3: Commit**

```bash
git add backend/README.md
git commit -m "docs(backend): document conversation, changeset and view-state endpoints"
```

---

## 自检记录

- **Spec coverage（设计文档 4、5、7、9 节）：** 会话表与图一对一 ✅；改动集与操作明细 ✅；确认 / 撤销 ✅；视图状态读写 ✅；分页 ✅。
- **未覆盖（属于后续计划）：** 智能体与 SSE、草稿态在界面上渲染成虚线/幽灵态（前端）、pgvector 相似检索。
- **有意偏离设计文档：** 视图状态用 `PUT` 整体覆盖，而不是设计里写的 `PATCH` —— `spec` 本来就是整体读写的 JSONB，整体覆盖语义更简单也更不容易写错。
- **已知取舍：** 确认改动集时先做一遍全量校验再落动作，避免改到一半失败；`update` 类型操作的 `before` 快照由调用方提供，服务层不自动采集（Plan 3 的智能体工具会负责采）。
- **类型一致性：** `create_conversation` / `get_conversation` / `list_conversations` / `create_changeset` / `get_changeset` / `list_changesets` / `list_ops` / `record_op` / `confirm_changeset` / `reject_changeset` / `get_view_state` / `upsert_view_state` / `transition_property` 在任务之间保持一致。

## 后续计划

1. **Plan 3 · 智能体与通信**：LangChain + LangGraph + deep agent、主智能体工具集、绘图子智能体、SSE 与中断恢复、pgvector 相似检索。
2. **Plan 4 · 前端**：首页菜单、建模三栏布局、React Flow 画布、草图态与幽灵态、视图状态同步。

---

## 实现后复查发现的缺陷与修复

Plan 2 落地后做了一轮严格复查，发现四类问题，均已修复并补了回归测试（测试数 65 → 75）：

| 问题 | 根因 | 修复 |
|---|---|---|
| `reject` 可以用 `payload.before` 里的任意键名 `setattr`，把 `draft` 元素直接写成 `approved`，绕过状态机 | 把调用方传来的 payload 当数据信任，没做字段白名单 | 按元素种类定义可还原字段白名单（`_RESTORABLE_FIELDS`），记录阶段和撤销阶段各校验一次 |
| `op` 名拼错会被静默跳过，`confirm` 仍返回成功 | `record_op` 不校验操作本身；动作循环没有兜底分支 | `_validate_op` 在记录阶段就校验 op、target、kind、before 字段；确认与撤销前再做一遍全量校验 |
| `confirm` 声称「先全量校验再落动作」，实际只校验了元素存在，没校验状态流转是否合法 | 校验不完整 | 校验阶段补上 `create` 操作要求元素处于 `draft`；不通过时改动集保持 `open` |
| `GET /graphs/{id}/view-state` 对不存在的图返回 200，`PUT` 返回 404 | `get_view_state` 直接查表，没校验图 | 服务层统一校验图存在性，GET 与 PUT 行为一致 |
| 列表与读取完全没有按 `owner_id` 过滤 | 查询形状从一开始就缺少归属维度 | `list_conversations` / `list_object_types` 加 owner 过滤；`get_conversation` / `get_graph` / 改动集 / 视图状态读改查全部校验归属；不匹配一律按 not found 处理，不泄露资源是否存在 |

两个仍未处理的已知项，留给 Plan 3 之前处理：

- **并发**：`confirm` / `reject` 是 read-then-write，没有行锁，两个请求可能同时通过状态检查。
- **`update` 操作的写入没有契约保障**：`confirm` 对它是 `pass`，依赖调用方先把值写好，没有任何机制校验。
