# Plan 3 · 工具层与相似检索 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 做出智能体要用的两套工具箱和相似检索——主智能体的建模工具、绘图子智能体的视图工具、基于 pgvector 的「这个新对象像不像已有的」。这一层不依赖任何大模型，可以完整测试。

**Architecture:** 工具是**普通 Python 函数**，放在 `app/agent/`，直接调用 Plan 1/2 的服务层；Plan 4 再给它们套上 LangChain 包装并交给智能体编排。写工具一律通过改动集记录操作；护栏（查重、字段白名单、归属）放在工具内部，不靠调用方自觉。

**Tech Stack:** 同前，新增 `pgvector`（Python 包）。

**对应设计文档：** 第 6、9、10 节。

**前置：** Plan 1、加固、Plan 2 完成，`0005 (head)`，75 个测试通过。

**为什么拆两份：** 本环境没有任何模型 API key，`langchain` / `langgraph` 也未安装。工具层与相似检索是纯确定性代码，能真跑真测；智能体编排与 SSE 需要模型配置，留到 Plan 4。

## 文件结构

| 文件 | 职责 |
|---|---|
| `app/services/embedding.py` | `Embedder` 协议、本地确定性实现、相似检索 |
| `app/agent/schemas.py` | 工具清单元数据，Plan 4 用它导出 tool schema |
| `app/agent/ontology_tools.py` | 主智能体：读工具 + 写工具 |
| `app/agent/view_tools.py` | 绘图子智能体：视图规格操作（纯函数） |
| `tests/test_embedding.py`、`test_ontology_tools.py`、`test_view_tools.py`、`test_agent_schemas.py` | 测试 |

---

## Task 1: 向量列、嵌入与相似检索

**Files:**
- Modify: `backend/pyproject.toml`、`backend/app/models.py`
- Create: `backend/alembic/versions/0006_object_type_embedding.py`
- Create: `backend/app/services/embedding.py`
- Test: `backend/tests/test_embedding.py`

- [ ] **Step 1: Add the dependency and the column**

`pyproject.toml` 的 `dependencies` 追加 `"pgvector==0.3.6",`；`models.py` 里：

```python
from pgvector.sqlalchemy import Vector

EMBEDDING_DIM = 1536


class ObjectType(Base):
    # ...既有列...
    embedding: Mapped[list[float] | None] = mapped_column(
        Vector(EMBEDDING_DIM), nullable=True
    )
```

- [ ] **Step 2: Hand-write the migration**

自动生成的迁移不含 HNSW 索引，这步手工写：

```python
# backend/alembic/versions/0006_object_type_embedding.py
"""object type embedding

Revision ID: 0006
Revises: 0005

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector

revision: str = "0006"
down_revision: Union[str, None] = "0005"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

EMBEDDING_DIM = 1536


def upgrade() -> None:
    op.add_column(
        "object_type",
        sa.Column("embedding", Vector(EMBEDDING_DIM), nullable=True),
    )
    # HNSW 不需要预先训练，适合边写边查。
    op.create_index(
        "ix_object_type_embedding_hnsw",
        "object_type",
        ["embedding"],
        postgresql_using="hnsw",
        postgresql_ops={"embedding": "vector_cosine_ops"},
    )


def downgrade() -> None:
    op.drop_index("ix_object_type_embedding_hnsw", table_name="object_type")
    op.drop_column("object_type", "embedding")
```

安装并应用：

```bash
cd backend
./.venv/bin/pip install -e ".[dev]"
./.venv/bin/alembic upgrade head
./.venv/bin/alembic current      # 期望 0006 (head)
```

- [ ] **Step 3: Write the failing test**

```python
# backend/tests/test_embedding.py
import uuid

import pytest

from app.errors import NotFoundError
from app.services import embedding, ontology


def test_hashing_embedder_is_deterministic_and_normalised():
    emb = embedding.HashingEmbedder(dim=64)
    assert emb.embed(["客户"])[0] == emb.embed(["客户"])[0]
    vector = emb.embed(["客户"])[0]
    assert len(vector) == 64
    assert abs(sum(v * v for v in vector) - 1.0) < 1e-9


def test_similar_text_scores_higher_than_unrelated_text():
    emb = embedding.HashingEmbedder(dim=256)
    co = emb.embed(["客户"])[0]
    close = emb.embed(["客户公司"])[0]
    far = emb.embed(["完全无关的词汇"])[0]
    assert embedding.cosine(co, close) > embedding.cosine(co, far)


def test_save_and_search_embeddings(session):
    emb = embedding.HashingEmbedder(dim=256)
    owner = uuid.uuid4()
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    for obj in (customer, order):
        embedding.save_embedding(session, object_type_id=obj.id, embedder=emb)

    hits = embedding.search_object_types(
        session, query="客户公司", embedder=emb, owner_id=owner, limit=5
    )
    assert hits[0][0].name == "客户"
    assert hits[0][1] > hits[-1][1]


def test_search_skips_rows_without_embedding(session):
    emb = embedding.HashingEmbedder(dim=256)
    owner = uuid.uuid4()
    ontology.create_object_type(session, owner_id=owner, name="没有向量的类型")
    assert embedding.search_object_types(
        session, query="客户", embedder=emb, owner_id=owner, limit=5
    ) == []


def test_search_filters_by_owner(session):
    emb = embedding.HashingEmbedder(dim=256)
    mine, other = uuid.uuid4(), uuid.uuid4()
    mine_obj = ontology.create_object_type(session, owner_id=mine, name="客户")
    other_obj = ontology.create_object_type(session, owner_id=other, name="客户")
    for obj in (mine_obj, other_obj):
        embedding.save_embedding(session, object_type_id=obj.id, embedder=emb)
    hits = embedding.search_object_types(
        session, query="客户", embedder=emb, owner_id=mine, limit=5
    )
    assert [obj.id for obj, _ in hits] == [mine_obj.id]


def test_save_embedding_rejects_unknown_object(session):
    with pytest.raises(NotFoundError):
        embedding.save_embedding(
            session,
            object_type_id=uuid.uuid4(),
            embedder=embedding.HashingEmbedder(dim=256),
        )
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_embedding.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.services.embedding'`

- [ ] **Step 5: Implement**

```python
# backend/app/services/embedding.py
from __future__ import annotations

import hashlib
import math
import uuid
from collections.abc import Sequence
from typing import Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import EMBEDDING_DIM, ObjectType


class Embedder(Protocol):
    """嵌入模型的可替换接口。真实实现走外部 API，测试与开发用本地实现。"""

    dim: int

    def embed(self, texts: Sequence[str]) -> list[list[float]]: ...


def _tokenize(text: str) -> list[str]:
    """英文按词切、中文按字切。确定性、无依赖，够做词面相似度。"""
    tokens: list[str] = []
    buffer: list[str] = []
    for char in text.lower():
        if char.isascii() and char.isalnum():
            buffer.append(char)
            continue
        if buffer:
            tokens.append("".join(buffer))
            buffer = []
        if not char.isspace():
            tokens.append(char)
    if buffer:
        tokens.append("".join(buffer))
    return tokens


class HashingEmbedder:
    """把词哈希进固定维度再归一化。不依赖网络，用于开发和测试。"""

    def __init__(self, dim: int = EMBEDDING_DIM) -> None:
        self.dim = dim

    def embed(self, texts: Sequence[str]) -> list[list[float]]:
        return [self._embed_one(text) for text in texts]

    def _embed_one(self, text: str) -> list[float]:
        vector = [0.0] * self.dim
        for token in _tokenize(text):
            digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
            vector[int.from_bytes(digest, "big") % self.dim] += 1.0
        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0.0:
            return vector
        return [value / norm for value in vector]


def cosine(left: Sequence[float], right: Sequence[float]) -> float:
    dot = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(a * a for a in left))
    right_norm = math.sqrt(sum(b * b for b in right))
    if left_norm == 0.0 or right_norm == 0.0:
        return 0.0
    return dot / (left_norm * right_norm)


def save_embedding(
    session: Session, *, object_type_id: uuid.UUID, embedder: Embedder
) -> ObjectType:
    obj = session.get(ObjectType, object_type_id)
    if obj is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    text = obj.name if not obj.description else f"{obj.name} {obj.description}"
    obj.embedding = embedder.embed([text])[0]
    session.flush()
    return obj


def search_object_types(
    session: Session,
    *,
    query: str,
    embedder: Embedder,
    owner_id: uuid.UUID | None = None,
    limit: int = 10,
    exclude_ids: Sequence[uuid.UUID] = (),
) -> list[tuple[ObjectType, float]]:
    """按语义远近返回候选 (对象, 相似度)，相似度是 1 - 余弦距离。"""
    vector = embedder.embed([query])[0]
    distance = ObjectType.embedding.cosine_distance(vector)
    stmt = select(ObjectType, distance.label("distance")).where(
        ObjectType.embedding.isnot(None)
    )
    if owner_id is not None:
        stmt = stmt.where(ObjectType.owner_id == owner_id)
    if exclude_ids:
        stmt = stmt.where(ObjectType.id.notin_(list(exclude_ids)))
    stmt = stmt.order_by(distance).limit(limit)
    return [(row[0], 1.0 - float(row[1])) for row in session.execute(stmt)]
```

- [ ] **Step 6: Run tests, then full checks and commit**

```bash
cd backend
./.venv/bin/python -m pytest tests/test_embedding.py -q     # 期望 6 passed
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
cd .. && git add backend && git commit -m "feat(backend): add embedding column, embedder interface and similarity search"
```

---

## Task 2: 主智能体读工具

**Files:**
- Create: `backend/app/agent/__init__.py`、`backend/app/agent/ontology_tools.py`
- Test: `backend/tests/test_ontology_tools.py`

- [ ] **Step 1: Write the failing test**

```python
# backend/tests/test_ontology_tools.py
import uuid

import pytest

from app.agent import ontology_tools as tools
from app.errors import NotFoundError
from app.services import conversation, embedding, graph, ontology


def _setup(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="工具测试")
    emb = embedding.HashingEmbedder(dim=256)
    customer = ontology.create_object_type(session, owner_id=owner, name="客户")
    order = ontology.create_object_type(session, owner_id=owner, name="订单")
    for obj in (customer, order):
        embedding.save_embedding(session, object_type_id=obj.id, embedder=emb)
    rel = ontology.create_relation(
        session,
        owner_id=owner,
        source_type_id=customer.id,
        target_type_id=order.id,
        name="下订单",
    )
    graph.add_object_to_graph(session, conv.graph_id, customer.id, owner_id=owner)
    graph.add_object_to_graph(session, conv.graph_id, order.id, owner_id=owner)
    graph.add_relation_to_graph(session, conv.graph_id, rel.id, owner_id=owner)
    return conv, emb, customer, order, rel


def test_search_ontology_returns_similar_with_scores(session):
    owner = uuid.uuid4()
    _setup(session, owner)
    hits = tools.search_ontology(
        session, query="客户公司", embedder=embedding.HashingEmbedder(dim=256), owner_id=owner
    )
    assert hits[0]["name"] == "客户"
    assert "similarity" in hits[0]


def test_search_ontology_only_returns_own(session):
    owner, other = uuid.uuid4(), uuid.uuid4()
    emb = embedding.HashingEmbedder(dim=256)
    mine = ontology.create_object_type(session, owner_id=owner, name="客户")
    for obj in (
        mine,
        ontology.create_object_type(session, owner_id=other, name="客户"),
    ):
        embedding.save_embedding(session, object_type_id=obj.id, embedder=emb)
    hits = tools.search_ontology(session, query="客户", embedder=emb, owner_id=owner)
    assert [hit["id"] for hit in hits] == [str(mine.id)]


def test_get_subgraph_walks_one_hop(session):
    owner = uuid.uuid4()
    conv, _, customer, _, _ = _setup(session, owner)
    result = tools.get_subgraph(
        session, graph_id=conv.graph_id, object_type_id=customer.id, owner_id=owner
    )
    assert [node["name"] for node in result["nodes"]] == ["客户", "订单"]
    assert result["relations"][0]["name"] == "下订单"


def test_get_subgraph_rejects_unknown_node(session):
    owner = uuid.uuid4()
    conv, *_ = _setup(session, owner)
    with pytest.raises(NotFoundError):
        tools.get_subgraph(
            session, graph_id=conv.graph_id, object_type_id=uuid.uuid4(), owner_id=owner
        )


def test_list_types_only_returns_own(session):
    owner, other = uuid.uuid4(), uuid.uuid4()
    ontology.create_object_type(session, owner_id=owner, name="mine")
    ontology.create_object_type(session, owner_id=other, name="other")
    assert [item["name"] for item in tools.list_types(session, owner_id=owner)] == ["mine"]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_ontology_tools.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.agent'`

- [ ] **Step 3: Implement**

```python
# backend/app/agent/__init__.py
```

```python
# backend/app/agent/ontology_tools.py
from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ObjectType, Relation
from app.services import embedding, graph, ontology


def _type_brief(obj: ObjectType) -> dict:
    return {
        "id": str(obj.id),
        "name": obj.name,
        "description": obj.description,
        "status": obj.status,
    }


def search_ontology(
    session: Session,
    *,
    query: str,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int = 10,
) -> list[dict]:
    """按语义找已有对象类型。新建之前应该先调这个，避免造重复。"""
    hits = embedding.search_object_types(
        session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
    )
    return [{**_type_brief(obj), "similarity": round(score, 4)} for obj, score in hits]


def list_types(session: Session, *, owner_id: uuid.UUID, limit: int = 50) -> list[dict]:
    return [
        _type_brief(obj)
        for obj in ontology.list_object_types(session, owner_id=owner_id, limit=limit)
    ]


def get_subgraph(
    session: Session,
    *,
    graph_id: uuid.UUID,
    object_type_id: uuid.UUID,
    owner_id: uuid.UUID,
    max_nodes: int = 50,
) -> dict:
    """从给定节点出发，沿图内的关系走一跳，返回周围的节点与关系。"""
    graph.get_graph(session, graph_id, owner_id=owner_id)
    present = set(graph.list_graph_objects(session, graph_id))
    if object_type_id not in present:
        raise NotFoundError(f"object type {object_type_id} is not in graph {graph_id}")

    relation_ids = set(graph.list_graph_relations(session, graph_id))
    relations = (
        list(session.scalars(select(Relation).where(Relation.id.in_(relation_ids))))
        if relation_ids
        else []
    )
    neighbours = {
        rel.target_type_id if rel.source_type_id == object_type_id else rel.source_type_id
        for rel in relations
        if object_type_id in (rel.source_type_id, rel.target_type_id)
    }
    node_ids = sorted({object_type_id, *neighbours}, key=str)[:max_nodes]
    nodes = list(session.scalars(select(ObjectType).where(ObjectType.id.in_(node_ids))))
    connected = [
        rel
        for rel in relations
        if rel.source_type_id in node_ids and rel.target_type_id in node_ids
    ]
    return {
        "nodes": [_type_brief(node) for node in nodes],
        "relations": [
            {
                "id": str(rel.id),
                "name": rel.name,
                "source_type_id": str(rel.source_type_id),
                "target_type_id": str(rel.target_type_id),
                "direction": rel.direction,
            }
            for rel in connected
        ],
    }
```

- [ ] **Step 4: Run tests, then full checks and commit**

```bash
cd backend
./.venv/bin/python -m pytest tests/test_ontology_tools.py -q     # 期望 5 passed
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
cd .. && git add backend && git commit -m "feat(backend): add read tools for the ontology agent"
```

---

## Task 3: 主智能体写工具（带护栏）

**Files:**
- Modify: `backend/app/services/changeset.py`（把 `_load_element` 与字段白名单公开）
- Modify: `backend/app/agent/ontology_tools.py`
- Test: `backend/tests/test_ontology_tools.py`

- [ ] **Step 1: 先把服务层要复用的两样东西公开**

```python
# backend/app/services/changeset.py（在 _RESTORABLE_FIELDS 定义之后加一行）
RESTORABLE_FIELDS = _RESTORABLE_FIELDS

# 并把 _load_element 改名为 load_element，文件末尾保留别名：
load_element = _load_element
```

- [ ] **Step 2: Write the failing tests**

```python
# backend/tests/test_ontology_tools.py（在文件末尾追加，并补 import）
from app.errors import InvariantViolationError
from app.services import changeset


def test_propose_type_creates_draft_and_records_op(session):
    owner = uuid.uuid4()
    conv, emb, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)

    result = tools.propose_type(
        session, changeset_id=cs.id, owner_id=owner, name="供应商", embedder=emb
    )
    obj = ontology.get_object_type(session, uuid.UUID(result["id"]))
    assert obj.status == "draft"
    assert obj.embedding is not None
    assert [op.op for op in changeset.list_ops(session, cs.id)] == ["create"]


def test_propose_type_reuses_existing_type(session):
    owner = uuid.uuid4()
    conv, emb, customer, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)

    result = tools.propose_type(
        session, changeset_id=cs.id, owner_id=owner, name="客户", embedder=emb
    )
    assert result["id"] == str(customer.id)
    assert result["reused"] is True
    assert changeset.list_ops(session, cs.id) == []


def test_propose_type_reports_near_duplicates(session):
    owner = uuid.uuid4()
    conv, emb, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)

    result = tools.propose_type(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        name="客户信息",
        embedder=emb,
        reuse_threshold=0.999,
    )
    assert result["similar"]
    assert result["reused"] is False


def test_propose_relation_requires_existing_endpoints(session):
    owner = uuid.uuid4()
    conv, emb, customer, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    with pytest.raises(NotFoundError):
        tools.propose_relation(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            source_type_id=customer.id,
            target_type_id=uuid.uuid4(),
            name="供应",
        )


def test_update_element_requires_before_snapshot(session):
    owner = uuid.uuid4()
    conv, _, customer, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    with pytest.raises(InvariantViolationError):
        tools.update_element(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            element_kind="object_type",
            element_id=customer.id,
            patch={"name": "终端客户"},
            before={},
        )


def test_update_element_applies_and_records_snapshot(session):
    owner = uuid.uuid4()
    conv, _, customer, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)

    tools.update_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
        patch={"name": "终端客户"},
        before={"name": "客户"},
    )
    assert ontology.get_object_type(session, customer.id).name == "终端客户"
    assert changeset.list_ops(session, cs.id)[0].payload["before"] == {"name": "客户"}


def test_update_element_rejects_field_outside_whitelist(session):
    owner = uuid.uuid4()
    conv, _, customer, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    with pytest.raises(InvariantViolationError):
        tools.update_element(
            session,
            changeset_id=cs.id,
            owner_id=owner,
            element_kind="object_type",
            element_id=customer.id,
            patch={"status": "approved"},
            before={"status": "draft"},
        )


def test_remove_element_only_marks(session):
    owner = uuid.uuid4()
    conv, _, customer, *_ = _setup(session, owner)
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)

    tools.remove_element(
        session,
        changeset_id=cs.id,
        owner_id=owner,
        element_kind="object_type",
        element_id=customer.id,
    )
    assert ontology.get_object_type(session, customer.id) is not None
    assert changeset.list_ops(session, cs.id)[0].op == "delete"
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_ontology_tools.py -q`
Expected: FAIL — `AttributeError: module 'app.agent.ontology_tools' has no attribute 'propose_type'`

- [ ] **Step 4: Implement**

```python
# backend/app/agent/ontology_tools.py（在文件末尾追加，并补 import）
from app.errors import InvariantViolationError
from app.services import changeset

DEFAULT_REUSE_THRESHOLD = 0.98


def _candidates(session, *, name, embedder, owner_id, limit=5):
    return search_ontology(
        session, query=name, embedder=embedder, owner_id=owner_id, limit=limit
    )


def propose_type(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    name: str,
    embedder: embedding.Embedder,
    description: str | None = None,
    home_graph_id: uuid.UUID | None = None,
    reuse_threshold: float = DEFAULT_REUSE_THRESHOLD,
) -> dict:
    """新建对象类型。护栏：先查近邻——同名或够像就复用，否则把候选一并返回。"""
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    candidates = _candidates(
        session, name=name, embedder=embedder, owner_id=owner_id
    )
    existing = next((c for c in candidates if c["name"] == name), None) or next(
        (c for c in candidates if c["similarity"] >= reuse_threshold), None
    )
    if existing is not None:
        return {**existing, "reused": True, "similar": candidates}

    obj = ontology.create_object_type(
        session,
        owner_id=owner_id,
        name=name,
        description=description,
        home_graph_id=home_graph_id or cs.graph_id,
    )
    embedding.save_embedding(session, object_type_id=obj.id, embedder=embedder)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=obj.id,
        payload={"kind": "object_type"},
    )
    return {**_type_brief(obj), "reused": False, "similar": candidates}


def propose_property(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    owner_type_id: uuid.UUID,
    name: str,
    datatype: str = "text",
    required: bool = False,
) -> dict:
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, "object_type", owner_type_id, owner_id=owner_id)
    prop = ontology.create_property(
        session,
        owner_type_id=owner_type_id,
        name=name,
        datatype=datatype,
        required=required,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=prop.id,
        payload={"kind": "property"},
    )
    return {
        "id": str(prop.id),
        "name": prop.name,
        "datatype": prop.datatype,
        "required": prop.required,
        "status": prop.status,
    }


def propose_relation(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    source_type_id: uuid.UUID,
    target_type_id: uuid.UUID,
    name: str,
    direction: str = "forward",
    home_graph_id: uuid.UUID | None = None,
) -> dict:
    cs = changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    rel = ontology.create_relation(
        session,
        owner_id=owner_id,
        source_type_id=source_type_id,
        target_type_id=target_type_id,
        name=name,
        direction=direction,
        home_graph_id=home_graph_id or cs.graph_id,
    )
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="create",
        target_id=rel.id,
        payload={"kind": "relation"},
    )
    return {
        "id": str(rel.id),
        "name": rel.name,
        "source_type_id": str(rel.source_type_id),
        "target_type_id": str(rel.target_type_id),
        "status": rel.status,
    }


def update_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
    patch: dict,
    before: dict,
) -> dict:
    """改已有元素。before 是改动前的字段快照，撤销时靠它还原，必填。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    allowed = changeset.RESTORABLE_FIELDS.get(element_kind)
    if allowed is None:
        raise InvariantViolationError(f"unsupported element kind: {element_kind!r}")
    if not before:
        raise InvariantViolationError("update requires a 'before' snapshot for undo")
    unknown = set(patch) - allowed
    if unknown:
        raise InvariantViolationError(f"cannot patch fields: {sorted(unknown)}")

    element = changeset.load_element(
        session, element_kind, element_id, owner_id=owner_id
    )
    for field, value in patch.items():
        setattr(element, field, value)
    session.flush()
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="update",
        target_id=element_id,
        payload={"kind": element_kind, "before": before},
    )
    return {"id": str(element_id), "patched": sorted(patch), "before": before}


def remove_element(
    session: Session,
    *,
    changeset_id: uuid.UUID,
    owner_id: uuid.UUID,
    element_kind: str,
    element_id: uuid.UUID,
) -> dict:
    """标记删除。草稿阶段不真删，确认时才删——对应界面上的幽灵态。"""
    changeset.get_changeset(session, changeset_id, owner_id=owner_id)
    changeset.load_element(session, element_kind, element_id, owner_id=owner_id)
    changeset.record_op(
        session,
        changeset_id=changeset_id,
        op="delete",
        target_id=element_id,
        payload={"kind": element_kind},
    )
    return {"id": str(element_id), "marked_for_deletion": True}
```

- [ ] **Step 5: Run tests, then full checks and commit**

```bash
cd backend
./.venv/bin/python -m pytest tests/test_ontology_tools.py -q     # 期望 13 passed
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
cd .. && git add backend && git commit -m "feat(backend): add write tools with reuse and field guards"
```

---

## Task 4: 绘图子智能体工具与工具清单

**Files:**
- Create: `backend/app/agent/view_tools.py`、`backend/app/agent/schemas.py`
- Modify: `backend/README.md`
- Test: `backend/tests/test_view_tools.py`、`backend/tests/test_agent_schemas.py`

- [ ] **Step 1: Write the failing tests**

```python
# backend/tests/test_view_tools.py
import uuid

import pytest

from app.agent import view_tools as tools
from app.errors import InvariantViolationError


def test_set_layout_writes_declarative_spec():
    spec = tools.set_layout({}, kind="domain-grouped", options={"spacing": 80})
    assert spec["layout"] == {"kind": "domain-grouped", "options": {"spacing": 80}}


def test_set_layout_rejects_unknown_kind():
    with pytest.raises(InvariantViolationError):
        tools.set_layout({}, kind="spiral")


def test_highlight_is_idempotent_and_sorted_dedup():
    node = str(uuid.uuid4())
    spec = tools.highlight({}, node_ids=[node, node])
    assert [entry["nodeIds"] for entry in spec["highlight"]] == [[node]]


def test_group_moves_nodes_out_of_previous_group():
    first, second = str(uuid.uuid4()), str(uuid.uuid4())
    spec = tools.group({}, group_id="g1", label="销售域", node_ids=[first, second])
    spec = tools.group(spec, group_id="g2", label="供应链域", node_ids=[second])
    assert {g["id"]: g["nodeIds"] for g in spec["groups"]} == {
        "g1": [first],
        "g2": [second],
    }


def test_collapse_expand_and_filter_round_trip():
    spec = tools.collapse({}, group_ids=["g1", "g2"])
    assert tools.expand(spec, group_ids=["g1"])["collapsed"] == ["g2"]
    assert tools.filter_view({}, predicate=None)["filters"] == {}


def test_focus_and_reset_are_local_to_spec():
    node = str(uuid.uuid4())
    spec = tools.focus({}, node_ids=[node])
    assert spec["camera"] == {"focus": [node]}
    assert tools.reset_view(spec) == {}


def test_annotate_rejects_blank_text():
    with pytest.raises(InvariantViolationError):
        tools.annotate({}, node_id=str(uuid.uuid4()), text="   ")


def test_tools_do_not_mutate_the_input_spec():
    original = {"layout": {"kind": "hierarchical"}}
    tools.set_layout(original, kind="force")
    assert original == {"layout": {"kind": "hierarchical"}}
```

```python
# backend/tests/test_agent_schemas.py
from app.agent import schemas


def test_ontology_tool_names_are_stable():
    assert [tool.name for tool in schemas.ONTOLOGY_TOOLS] == [
        "search_ontology",
        "list_types",
        "get_subgraph",
        "propose_type",
        "propose_property",
        "propose_relation",
        "update_element",
        "remove_element",
    ]


def test_view_tool_names_are_stable():
    assert [tool.name for tool in schemas.VIEW_TOOLS] == [
        "set_layout",
        "highlight",
        "dim",
        "group",
        "collapse",
        "expand",
        "filter_view",
        "focus",
        "reset_view",
        "annotate",
    ]


def test_every_tool_has_a_description():
    for tool in [*schemas.ONTOLOGY_TOOLS, *schemas.VIEW_TOOLS]:
        assert tool.name
        assert tool.description
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_view_tools.py tests/test_agent_schemas.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.agent.view_tools'`

- [ ] **Step 3: Implement the view tools**

```python
# backend/app/agent/view_tools.py
from __future__ import annotations

import uuid
from collections.abc import Sequence

from app.errors import InvariantViolationError

LAYOUT_KINDS = {"hierarchical", "force", "domain-grouped", "grid"}


def _copy(spec: dict) -> dict:
    """浅拷贝够用：每个操作都是整块替换某个顶层键，不做嵌套改写。"""
    return dict(spec)


def set_layout(spec: dict, *, kind: str, options: dict | None = None) -> dict:
    if kind not in LAYOUT_KINDS:
        raise InvariantViolationError(f"unsupported layout kind: {kind!r}")
    result = _copy(spec)
    result["layout"] = {"kind": kind, "options": options or {}}
    return result


def highlight(spec: dict, *, node_ids: Sequence[uuid.UUID | str]) -> dict:
    unique = sorted({str(node_id) for node_id in node_ids})
    others = [entry for entry in spec.get("highlight", []) if entry["style"] != "focus"]
    result = _copy(spec)
    result["highlight"] = [{"nodeIds": unique, "style": "focus"}, *others]
    return result


def dim(spec: dict, *, node_ids: Sequence[uuid.UUID | str]) -> dict:
    result = _copy(spec)
    result["dim"] = sorted({str(node_id) for node_id in node_ids})
    return result


def group(
    spec: dict, *, group_id: str, label: str, node_ids: Sequence[uuid.UUID | str]
) -> dict:
    members = {str(node_id) for node_id in node_ids}
    groups = []
    for existing in spec.get("groups", []):
        if existing["id"] == group_id:
            continue
        remaining = [n for n in existing["nodeIds"] if n not in members]
        if remaining:
            groups.append({**existing, "nodeIds": remaining})
    groups.append({"id": group_id, "label": label, "nodeIds": sorted(members)})
    result = _copy(spec)
    result["groups"] = groups
    return result


def collapse(spec: dict, *, group_ids: Sequence[str]) -> dict:
    result = _copy(spec)
    result["collapsed"] = sorted(
        {*spec.get("collapsed", []), *(str(g) for g in group_ids)}
    )
    return result


def expand(spec: dict, *, group_ids: Sequence[str]) -> dict:
    removing = {str(g) for g in group_ids}
    result = _copy(spec)
    result["collapsed"] = [g for g in spec.get("collapsed", []) if g not in removing]
    return result


def filter_view(spec: dict, *, predicate: dict | None) -> dict:
    result = _copy(spec)
    result["filters"] = predicate or {}
    return result


def focus(spec: dict, *, node_ids: Sequence[uuid.UUID | str]) -> dict:
    result = _copy(spec)
    result["camera"] = {"focus": [str(node_id) for node_id in node_ids]}
    return result


def reset_view(spec: dict) -> dict:
    return {}


def annotate(spec: dict, *, node_id: uuid.UUID | str, text: str) -> dict:
    if not text.strip():
        raise InvariantViolationError("annotation text must not be empty")
    target = str(node_id)
    others = [a for a in spec.get("annotations", []) if a["nodeId"] != target]
    result = _copy(spec)
    result["annotations"] = [*others, {"nodeId": target, "text": text.strip()}]
    return result
```

- [ ] **Step 4: Implement the tool catalogue**

```python
# backend/app/agent/schemas.py
from __future__ import annotations

from pydantic import BaseModel, Field


class ToolSpec(BaseModel):
    """工具的元数据。Plan 4 用它导出 LangChain 的 tool schema。"""

    name: str
    description: str
    parameters: dict = Field(default_factory=dict)


ONTOLOGY_TOOLS = [
    ToolSpec(
        name="search_ontology",
        description="按语义检索已有对象类型；新建之前必须先调用，避免造重复。",
        parameters={"query": "str", "limit": "int"},
    ),
    ToolSpec(
        name="list_types",
        description="列出现有对象类型。",
        parameters={"limit": "int"},
    ),
    ToolSpec(
        name="get_subgraph",
        description="从给定节点出发走一跳，返回周围的节点与关系。",
        parameters={"graph_id": "uuid", "object_type_id": "uuid"},
    ),
    ToolSpec(
        name="propose_type",
        description="把新的对象类型加进草稿；内部先查重，够像就复用已有的。",
        parameters={"name": "str", "description": "str | None"},
    ),
    ToolSpec(
        name="propose_property",
        description="给对象类型加属性。",
        parameters={"owner_type_id": "uuid", "name": "str", "datatype": "str"},
    ),
    ToolSpec(
        name="propose_relation",
        description="加一条关系，两端必须都已存在。",
        parameters={"source_type_id": "uuid", "target_type_id": "uuid", "name": "str"},
    ),
    ToolSpec(
        name="update_element",
        description="改已有元素，必须带上 before 快照供撤销使用。",
        parameters={"element_kind": "str", "element_id": "uuid", "patch": "dict"},
    ),
    ToolSpec(
        name="remove_element",
        description="标记删除；草稿阶段不真删，确认时才删。",
        parameters={"element_kind": "str", "element_id": "uuid"},
    ),
]

VIEW_TOOLS = [
    ToolSpec(
        name="set_layout",
        description="换布局：层级 / 力导向 / 按域分组 / 网格。",
        parameters={"kind": "str"},
    ),
    ToolSpec(name="highlight", description="高亮若干节点。", parameters={"node_ids": "list[str]"}),
    ToolSpec(name="dim", description="弱化若干节点。", parameters={"node_ids": "list[str]"}),
    ToolSpec(
        name="group",
        description="把节点归进一个命名分组。",
        parameters={"group_id": "str", "label": "str", "node_ids": "list[str]"},
    ),
    ToolSpec(name="collapse", description="折叠分组。", parameters={"group_ids": "list[str]"}),
    ToolSpec(name="expand", description="展开分组。", parameters={"group_ids": "list[str]"}),
    ToolSpec(name="filter_view", description="按条件过滤显示。", parameters={"predicate": "dict | None"}),
    ToolSpec(name="focus", description="把视野移到若干节点。", parameters={"node_ids": "list[str]"}),
    ToolSpec(name="reset_view", description="清空视图设置。", parameters={}),
    ToolSpec(
        name="annotate",
        description="给节点加一条标注。",
        parameters={"node_id": "str", "text": "str"},
    ),
]
```

- [ ] **Step 5: Update the README**

`backend/README.md` 新增一节：

```markdown
## 智能体工具层

工具是普通 Python 函数，位于 `app/agent/`，不依赖大模型，可以直接单测：

- `ontology_tools.py`：主智能体的建模工具。读工具 `search_ontology` / `list_types` /
  `get_subgraph`；写工具 `propose_type` / `propose_property` / `propose_relation` /
  `update_element` / `remove_element`。
- `view_tools.py`：绘图子智能体的视图工具，全部是对视图规格的纯函数操作，不碰本体。
- `schemas.py`：工具清单元数据，Plan 4 用它导出 tool schema。

护栏都在工具内部：`propose_type` 先查近邻、同名或足够像就复用；`update_element`
强制要求 `before` 快照且只允许白名单字段；`remove_element` 只标记不真删。相似检索
走 pgvector，嵌入模型可替换，默认是本地确定性实现，不需要网络。
```

- [ ] **Step 6: Run tests, then full checks and commit**

```bash
cd backend
./.venv/bin/python -m pytest tests/test_view_tools.py tests/test_agent_schemas.py -q   # 期望 11 passed
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
cd .. && git add backend && git commit -m "feat(backend): add view tools and agent tool catalogue"
```

---

## 自检记录

- **Spec coverage（设计文档 6、9、10 节）：** 主智能体工具集 ✅（`ask_user` 属于运行时的中断机制，放 Plan 4）；绘图子智能体工具 ✅；轻拦截（`propose_type` 内建查重）✅；pgvector 相似检索 ✅。
- **未覆盖（属于 Plan 4）：** LangGraph 状态图与工具绑定、`interrupt` / `resume`、线程与检查点、SSE 端点、真实嵌入模型接入。
- **类型一致性：** `Embedder.embed` / `search_object_types` / `save_embedding` / `search_ontology` / `list_types` / `get_subgraph` / `propose_type` / `propose_property` / `propose_relation` / `update_element` / `remove_element` / `set_layout` / `highlight` / `dim` / `group` / `collapse` / `expand` / `filter_view` / `focus` / `reset_view` / `annotate` 在各任务间保持一致。
- **已知取舍：** 嵌入维度固定 1536（写在 DDL 里），换模型需要迁移；`HashingEmbedder` 是词面相似度而非真语义，只用于开发与测试。

## 后续计划

1. **Plan 4 · 智能体编排与 SSE**：LangGraph 状态图、工具绑定、`interrupt` / `resume`、PostgresSaver 检查点、SSE 端点与线程管理、真实嵌入模型接入。
2. **Plan 5 · 前端**：首页菜单、建模三栏布局、React Flow 画布、草图态与幽灵态、视图状态同步。
