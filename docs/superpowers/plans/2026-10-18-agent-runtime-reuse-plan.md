# 数据连接与同步 · 第一阶段：会话模型与 Agent 运行时

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把会话资源标识统一为 `kind + resource_id`，并把 Agent 运行时抽成可复用的 `AgentDefinition`，为后续数据连接 Agent 提供同一套流式、会话、检查点和中断底座。

**Architecture:** 数据库层把 `conversation.graph_id` 迁移为 `kind + resource_id`；运行时层用 `AgentDefinition` 按会话类型装配；建模 Agent 作为第一个实现，保留现有行为和测试覆盖。数据连接 Agent 本阶段只保留扩展点，不实现工具和 Java 客户端。

**Tech Stack:** Python 3.12、FastAPI、SQLAlchemy、Alembic、LangGraph/deepagents、pytest、ruff、mypy。

**Scope:** 这是数据连接与同步的总计划中的第 1 个子计划。后续依次是：2）Java 数据服务与连接器；3）Flink CDC + Iceberg 同步；4）Python 数据连接 API、事件与 Agent；5）前端三栏工作台与端到端验收。

> **实施状态（2026-10-18）：Task 1-8 全部完成并通过复审。**
> - 代码已随提交 `9524589` 合并到 `main`（基线 `ee14504`）。
> - 后端：`413 passed`；`ruff check`、`ruff format --check`、`mypy app` 全部通过。
> - 前端：`tsc -b`、`oxlint`、`vitest`（73 passed）、`vite build` 全部通过。
> - 下方任务步骤保留为执行记录，完成标准以本节和文末「实施确认」为准。

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/alembic/versions/0018_conversation_resource.py` | 迁移会话表到 `kind + resource_id`。 |
| `backend/app/models.py` | `Conversation` 使用统一资源标识，保留建模兼容属性。 |
| `backend/app/schemas/conversation.py` | API 返回 `kind + resource_id`。 |
| `backend/app/services/conversation.py` | 建模/数据连接会话的创建、查询、重命名和删除策略。 |
| `backend/app/agent/definitions/base.py` | `AgentDefinition` 与通用 `AgentRunContext`。 |
| `backend/app/agent/definitions/ontology.py` | 建模 Agent 的提示词、工具、改动集和结束状态。 |
| `backend/app/agent/definitions/registry.py` | `Conversation.kind` 到定义的注册表。 |
| `backend/app/agent/runtime/stream.py` | 通用 SSE 编排，不再认识 graph/changeset。 |
| `backend/app/agent/runtime/runs.py` | 会话校验与 LangGraph 线程键。 |
| `backend/app/agent/tools/context.py` | 通用 `ToolContext`：Session、owner、锁、错误兜底。 |
| `backend/app/agent/tools/ontology/context.py` | 建模专用工具上下文。 |
| `backend/app/api/agent.py` | 流式入口只做会话校验和转发。 |
| `backend/app/agent/thread.py` | 定稿回写改为通过建模定义装配。 |
| `backend/tests/test_agent_definitions.py` | 定义注册表和通用上下文的测试。 |
| `backend/tests/test_runtime_generic.py` | 用假定义验证运行时不再依赖建模。 |
| `backend/tests/test_agent_dependency_direction.py` | 通用运行时依赖方向测试。 |

---

### Task 1: 迁移会话资源标识

**Files:**
- Create: `backend/alembic/versions/0018_conversation_resource.py`
- Test: `backend/tests/test_migrations.py`

- [ ] **Step 1: 写迁移测试**

在 `backend/tests/test_migrations.py` 追加：

```python
from sqlalchemy import inspect


def test_conversation_uses_kind_and_resource_id(engine) -> None:
    columns = {column["name"] for column in inspect(engine).get_columns("conversation")}
    assert {"kind", "resource_id"} <= columns
    assert "graph_id" not in columns
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py::test_conversation_uses_kind_and_resource_id`

Expected: FAIL，提示 `graph_id` 仍存在或 `kind/resource_id` 不存在。

- [ ] **Step 3: 写迁移**

创建 `backend/alembic/versions/0018_conversation_resource.py`：

```python
"""conversation resource identity

Revision ID: 0018
Revises: 0017
"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "0018"
down_revision: str | None = "0017"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "conversation",
        sa.Column("kind", sa.Text(), server_default="modeling", nullable=False),
    )
    op.add_column(
        "conversation",
        sa.Column("resource_id", postgresql.UUID(as_uuid=True), nullable=True),
    )
    op.execute("UPDATE conversation SET resource_id = graph_id")
    op.alter_column("conversation", "resource_id", nullable=False)
    op.create_unique_constraint(
        "uq_conversation_kind_resource",
        "conversation",
        ["kind", "resource_id"],
    )
    op.drop_constraint("conversation_graph_id_key", "conversation", type_="unique")
    op.drop_constraint("conversation_graph_id_fkey", "conversation", type_="foreignkey")
    op.drop_column("conversation", "graph_id")


def downgrade() -> None:
    bind = op.get_bind()
    non_modeling = bind.execute(
        sa.text("SELECT count(*) FROM conversation WHERE kind <> 'modeling'")
    ).scalar_one()
    if non_modeling:
        raise RuntimeError("cannot downgrade while non-modeling conversations exist")

    op.add_column(
        "conversation",
        sa.Column("graph_id", postgresql.UUID(as_uuid=True), nullable=True),
    )
    op.execute("UPDATE conversation SET graph_id = resource_id")
    op.alter_column("conversation", "graph_id", nullable=False)
    op.create_unique_constraint("conversation_graph_id_key", "conversation", ["graph_id"])
    op.create_foreign_key(
        "conversation_graph_id_fkey",
        "conversation",
        "graph",
        ["graph_id"],
        ["id"],
        ondelete="CASCADE",
    )
    op.drop_constraint("uq_conversation_kind_resource", "conversation", type_="unique")
    op.drop_column("conversation", "resource_id")
    op.drop_column("conversation", "kind")
```

- [ ] **Step 4: 运行迁移测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py::test_conversation_uses_kind_and_resource_id`

Expected: PASS。

- [ ] **Step 5: 验证模型与迁移没有漂移**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py`

Expected: PASS；`alembic check` 不报漂移。

---

### Task 2: 更新 Conversation 模型与 API Schema

**Files:**
- Modify: `backend/app/models.py:64-84`
- Modify: `backend/app/schemas/conversation.py`
- Test: `backend/tests/test_conversation.py`

- [ ] **Step 1: 先改测试断言**

把 `backend/tests/test_conversation.py` 中直接依赖 `graph_id` 的建模会话断言保留，新增：

```python
def test_modeling_conversation_exposes_resource_id(session):
    conv = conversation.create_conversation(session, owner_id=uuid.uuid4(), title="资源标识")

    assert conv.kind == "modeling"
    assert conv.resource_id == conv.graph_id
```

把 API roundtrip 的断言改为：

```python
assert body["kind"] == "modeling"
assert body["resource_id"]
assert "graph_id" not in body
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_conversation.py::test_modeling_conversation_exposes_resource_id tests/test_conversation.py::test_conversation_api_roundtrip`

Expected: FAIL，`Conversation` 还没有 `kind/resource_id`。

- [ ] **Step 3: 改模型**

把 `backend/app/models.py` 的 `Conversation` 定义改为：

```python
class Conversation(Base):
    __tablename__ = "conversation"
    __table_args__ = (
        UniqueConstraint("kind", "resource_id", name="uq_conversation_kind_resource"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False, index=True)
    kind: Mapped[str] = mapped_column(
        Text, nullable=False, default="modeling", server_default="modeling"
    )
    resource_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
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

    @property
    def graph_id(self) -> uuid.UUID:
        """建模代码的兼容读取口；数据库里已经不再存 graph_id。"""
        if self.kind != "modeling":
            raise AttributeError("graph_id is only available for modeling conversations")
        return self.resource_id
```

- [ ] **Step 4: 改 Schema**

把 `backend/app/schemas/conversation.py` 的 `ConversationRead` 改为：

```python
class ConversationRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    kind: str
    resource_id: uuid.UUID
    title: str | None
```

`ConversationCreate` 本阶段仍只接受 `title`；数据连接会话由后续的数据连接服务直接创建，不开放任意 `resource_id` 的公开入口。

- [ ] **Step 5: 运行测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_conversation.py`

Expected: PASS；建模代码仍能通过 `conv.graph_id` 读取兼容属性。

---

### Task 3: 让会话服务支持通用资源

**Files:**
- Modify: `backend/app/services/conversation.py`
- Modify: `backend/app/api/conversations.py`
- Test: `backend/tests/test_conversation.py`

- [ ] **Step 1: 写新行为测试**

在 `backend/tests/test_conversation.py` 追加：

```python
def test_resource_conversation_does_not_create_a_graph(session):
    owner = uuid.uuid4()
    resource_id = uuid.uuid4()

    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="数据连接",
        kind="data_connection",
        resource_id=resource_id,
    )

    assert conv.kind == "data_connection"
    assert conv.resource_id == resource_id
    assert session.query(Graph).count() == 0


def test_resource_conversation_delete_does_not_touch_graph(session):
    owner = uuid.uuid4()
    graph_id = conversation.create_conversation(session, owner_id=owner, title="建模").graph_id
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="数据连接",
        kind="data_connection",
        resource_id=uuid.uuid4(),
    )

    conversation.delete_conversation(session, conv.id, owner_id=owner)

    assert session.get(Graph, graph_id) is not None


def test_find_conversation_by_resource(session):
    owner = uuid.uuid4()
    resource_id = uuid.uuid4()
    conv = conversation.create_conversation(
        session,
        owner_id=owner,
        title="数据连接",
        kind="data_connection",
        resource_id=resource_id,
    )

    found = conversation.find_conversation_by_resource(
        session, kind="data_connection", resource_id=resource_id, owner_id=owner
    )

    assert found is not None
    assert found.id == conv.id
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_conversation.py::test_resource_conversation_does_not_create_a_graph tests/test_conversation.py::test_resource_conversation_delete_does_not_touch_graph tests/test_conversation.py::test_find_conversation_by_resource`

Expected: FAIL，服务还不接受 `kind/resource_id`。

- [ ] **Step 3: 改会话服务**

把 `backend/app/services/conversation.py` 的创建逻辑改为：

```python
def create_conversation(
    session: Session,
    *,
    owner_id: uuid.UUID,
    title: str | None = None,
    kind: str = "modeling",
    resource_id: uuid.UUID | None = None,
) -> Conversation:
    """建模会话自己建图；其它类型由调用方提供已有资源 id。"""
    if kind == "modeling":
        if resource_id is not None:
            raise InvariantViolationError(
                "modeling conversation resource_id is derived from its graph"
            )
        g = graph.create_graph(session, owner_id=owner_id, name=title)
        resource_id = g.id
    elif resource_id is None:
        raise InvariantViolationError(f"conversation kind {kind!r} requires resource_id")

    conv = Conversation(owner_id=owner_id, kind=kind, resource_id=resource_id, title=title)
    session.add(conv)
    session.flush()
    return conv
```

新增通用查询，并保留建模旧入口：

```python
def find_conversation_by_resource(
    session: Session,
    *,
    kind: str,
    resource_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
) -> Conversation | None:
    stmt = select(Conversation).where(
        Conversation.kind == kind,
        Conversation.resource_id == resource_id,
    )
    if owner_id is not None:
        stmt = stmt.where(Conversation.owner_id == owner_id)
    return session.scalar(stmt)


def find_conversation_by_graph(
    session: Session, graph_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Conversation | None:
    return find_conversation_by_resource(
        session, kind="modeling", resource_id=graph_id, owner_id=owner_id
    )
```

把重命名和删除改为按 `kind` 分支：只有 `modeling` 才更新/删除 `Graph` 和 `ViewState`；其它类型只处理会话本身。

`list_conversations` 增加可选 `kind` 过滤：

```python
def list_conversations(
    session: Session,
    *,
    owner_id: uuid.UUID | None = None,
    kind: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[Conversation]:
    stmt = select(Conversation)
    if owner_id is not None:
        stmt = stmt.where(Conversation.owner_id == owner_id)
    if kind is not None:
        stmt = stmt.where(Conversation.kind == kind)
    return list(
        session.scalars(
            stmt.order_by(Conversation.created_at.desc(), Conversation.id)
            .limit(limit)
            .offset(offset)
        )
    )
```

- [ ] **Step 4: 改 API**

在 `backend/app/api/conversations.py` 的列表接口增加 `kind` 查询参数：

```python
@router.get("/conversations", response_model=list[ConversationRead])
def list_conversations(
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    kind: str | None = Query(None),
    session: Session = Depends(get_session),
) -> list[ConversationRead]:
    items = conversation.list_conversations(
        session,
        owner_id=get_current_user_id(),
        kind=kind,
        limit=limit,
        offset=offset,
    )
    return [ConversationRead.model_validate(item) for item in items]
```

- [ ] **Step 5: 运行会话测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_conversation.py tests/test_changeset_review.py`

Expected: PASS。

---

### Task 4: 拆分通用 ToolContext 与建模 ToolContext

**Files:**
- Modify: `backend/app/agent/tools/context.py`
- Create: `backend/app/agent/tools/ontology/context.py`
- Modify: `backend/app/agent/tools/__init__.py`
- Modify: `backend/app/agent/tools/ontology/binding.py`
- Modify: `backend/app/agent/tools/search.py`
- Modify: `backend/app/agent/tools/view/binding.py`
- Modify: `backend/app/agent/tools/view/subagent.py`
- Modify: `backend/app/agent/tools/action/binding.py`
- Modify: `backend/app/agent/tools/action/subagent.py`
- Test: `backend/tests/test_tools_context.py`
- Test: `backend/tests/test_ontology_binding.py`

- [ ] **Step 1: 改测试，先固定分层**

`backend/tests/test_tools_context.py` 的 `_context` 改为只构造通用字段：

```python
def _context(session) -> ToolContext:
    return ToolContext(session=session, owner_id=uuid.uuid4())
```

新增一个断言，确保通用上下文不接受建模字段：

```python
def test_generic_context_has_no_graph_fields() -> None:
    assert "graph_id" not in ToolContext.__dataclass_fields__
    assert "changeset_id" not in ToolContext.__dataclass_fields__
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_tools_context.py`

Expected: FAIL，`ToolContext` 仍要求 graph/changeset/embedder。

- [ ] **Step 3: 改通用上下文**

把 `backend/app/agent/tools/context.py` 改为：

```python
@dataclass(frozen=True, kw_only=True)
class ToolContext:
    """一次运行里所有工具共享的外部依赖与调用策略。"""

    session: Session
    owner_id: uuid.UUID
    lock: threading.Lock = field(default_factory=threading.Lock, compare=False)

    def invoke(self, call: Callable[[], Any]) -> Any:
        try:
            with self.lock:
                return call()
        except Exception as exc:
            return {"error": type(exc).__name__, "message": str(exc)}
```

创建 `backend/app/agent/tools/ontology/context.py`：

```python
from __future__ import annotations

import uuid
from dataclasses import dataclass

from app.agent.tools.context import ToolContext
from app.services import embedding


@dataclass(frozen=True, kw_only=True)
class OntologyToolContext(ToolContext):
    graph_id: uuid.UUID
    changeset_id: uuid.UUID
    embedder: embedding.Embedder
```

在 `backend/app/agent/tools/__init__.py` 导出 `OntologyToolContext`。

- [ ] **Step 4: 把建模工具的类型提示改到专用上下文**

以下文件把 `ToolContext` 替换为 `OntologyToolContext`：

- `backend/app/agent/tools/ontology/binding.py`
- `backend/app/agent/tools/search.py`
- `backend/app/agent/tools/view/binding.py`
- `backend/app/agent/tools/view/subagent.py`
- `backend/app/agent/tools/action/binding.py`
- `backend/app/agent/tools/action/subagent.py`

保留通用 `invoke()` 调用不变；不要把 `embedder` 等字段加回 `ToolContext`。

- [ ] **Step 5: 更新建模测试 helper**

把测试里需要 `graph_id/changeset_id/embedder` 的 `ToolContext(...)` 改为：

```python
OntologyToolContext(
    session=session,
    owner_id=owner,
    graph_id=conv.resource_id,
    changeset_id=cs.id,
    embedder=embedding.HashingEmbedder(),
)
```

涉及文件：`test_agent_search.py`、`test_ontology_binding.py`、`test_view_reject.py`、`test_agent_thread.py`、`test_action_agent_tools.py`、`test_view_binding.py`、`test_agent_graph.py`。

- [ ] **Step 6: 运行工具测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_tools_context.py tests/test_ontology_binding.py tests/test_view_binding.py tests/test_action_agent_tools.py`

Expected: PASS。

---

### Task 5: 定义 AgentDefinition 与建模实现

**Files:**
- Create: `backend/app/agent/definitions/__init__.py`
- Create: `backend/app/agent/definitions/base.py`
- Create: `backend/app/agent/definitions/registry.py`
- Create: `backend/app/agent/definitions/ontology.py`
- Modify: `backend/app/agent/graph.py`
- Delete: `backend/app/agent/runtime/assembly.py`
- Test: `backend/tests/test_agent_definitions.py`

- [ ] **Step 1: 写定义测试**

创建 `backend/tests/test_agent_definitions.py`：

```python
import uuid

import pytest

from app.agent.definitions.base import AgentRunContext
from app.agent.definitions.ontology import OntologyRunContext
from app.agent.definitions.registry import get_definition
from app.errors import InvariantViolationError


def test_registry_resolves_modeling_definition() -> None:
    assert get_definition("modeling").kind == "modeling"


def test_registry_rejects_unknown_kind() -> None:
    with pytest.raises(InvariantViolationError):
        get_definition("unknown")


def test_ontology_context_extends_generic_context() -> None:
    context = OntologyRunContext(
        conversation_id=uuid.uuid4(),
        owner_id=uuid.uuid4(),
        kind="modeling",
        resource_id=uuid.uuid4(),
        graph_id=uuid.uuid4(),
        changeset_id=uuid.uuid4(),
    )

    assert isinstance(context, AgentRunContext)
    assert context.kind == "modeling"


def test_ontology_definition_kind() -> None:
    assert OntologyAgentDefinition.kind == "modeling"
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_agent_definitions.py`

Expected: FAIL，模块不存在。

- [ ] **Step 3: 写通用定义**

`backend/app/agent/definitions/base.py`：

```python
from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any, Protocol

from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.config import Settings
from app.models import Conversation


@dataclass(frozen=True, kw_only=True)
class AgentRunContext:
    conversation_id: uuid.UUID
    owner_id: uuid.UUID
    kind: str
    resource_id: uuid.UUID


class AgentDefinition(Protocol):
    kind: str

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> AgentRunContext: ...

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any: ...

    def build_state_update(self, session: Session, run: AgentRunContext) -> dict[str, Any]: ...
```

`backend/app/agent/definitions/registry.py`：

```python
from __future__ import annotations

from app.agent.definitions.base import AgentDefinition
from app.agent.definitions.ontology import OntologyAgentDefinition
from app.errors import InvariantViolationError

_DEFINITIONS: dict[str, AgentDefinition] = {
    OntologyAgentDefinition.kind: OntologyAgentDefinition(),
}


def get_definition(kind: str) -> AgentDefinition:
    try:
        return _DEFINITIONS[kind]
    except KeyError as exc:
        raise InvariantViolationError(f"unsupported conversation kind: {kind}") from exc
```

- [ ] **Step 4: 写建模定义**

`backend/app/agent/definitions/ontology.py`：

```python
from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from langchain.agents.middleware import InterruptOnConfig
from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.definitions.base import AgentDefinition, AgentRunContext
from app.agent.prompts import SYSTEM_PROMPT
from app.agent.tools import build_action_subagent, build_model_tools, build_view_subagent
from app.agent.tools.ontology.context import OntologyToolContext
from app.config import Settings
from app.errors import InvariantViolationError
from app.models import Conversation
from app.services import changeset, embedding, view_state

DEFAULT_INTERRUPTS: dict[str, bool | InterruptOnConfig] = {
    "remove_element": True,
    "update_element": True,
}


@dataclass(frozen=True, kw_only=True)
class OntologyRunContext(AgentRunContext):
    graph_id: uuid.UUID
    changeset_id: uuid.UUID


class OntologyAgentDefinition(AgentDefinition):
    kind = "modeling"

    def prepare_run(
        self,
        session: Session,
        *,
        conversation: Conversation,
        owner_id: uuid.UUID,
    ) -> OntologyRunContext:
        if conversation.kind != self.kind:
            raise InvariantViolationError(
                f"ontology definition cannot run conversation kind {conversation.kind!r}"
            )
        open_sets = [
            item
            for item in changeset.list_changesets(
                session, graph_id=conversation.resource_id, owner_id=owner_id
            )
            if item.status == changeset.OPEN
        ]
        cs = (
            open_sets[0]
            if open_sets
            else changeset.create_changeset(
                session, graph_id=conversation.resource_id, owner_id=owner_id
            )
        )
        return OntologyRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
            graph_id=conversation.resource_id,
            changeset_id=cs.id,
        )

    def build_graph(
        self,
        session: Session,
        settings: Settings,
        run: AgentRunContext,
        *,
        checkpointer: BaseCheckpointSaver | None,
    ) -> Any:
        if not isinstance(run, OntologyRunContext):
            raise InvariantViolationError("ontology definition requires OntologyRunContext")
        ctx = OntologyToolContext(
            session=session,
            owner_id=run.owner_id,
            graph_id=run.graph_id,
            changeset_id=run.changeset_id,
            embedder=embedding.build_embedder(settings),
        )
        return agent_graph.build_agent(
            llm.build_chat_model(settings),
            build_model_tools(ctx),
            system_prompt=SYSTEM_PROMPT,
            subagents=[build_view_subagent(ctx), build_action_subagent(ctx)],
            interrupt_on=DEFAULT_INTERRUPTS if checkpointer is not None else None,
            checkpointer=checkpointer,
        )

    def build_state_update(self, session: Session, run: AgentRunContext) -> dict[str, Any]:
        if not isinstance(run, OntologyRunContext):
            raise InvariantViolationError("ontology definition requires OntologyRunContext")
        ops = [
            {"op": op.op, "kind": op.payload.get("kind")}
            for op in changeset.list_ops(session, run.changeset_id)
        ]
        saved_view = view_state.get_view_state(
            session,
            graph_id=run.graph_id,
            user_id=run.owner_id,
            owner_id=run.owner_id,
        )
        return {
            "changesetId": str(run.changeset_id),
            "ops": ops,
            "view": saved_view.spec if saved_view is not None else {},
        }
```

- [ ] **Step 5: 把 graph.py 变成通用装配**

从 `backend/app/agent/graph.py` 删除 `SYSTEM_PROMPT` 和 `DEFAULT_INTERRUPTS`，`build_agent` 默认不安装建模中断：

```python
def build_agent(
    model: Any,
    tools: list[BaseTool],
    *,
    checkpointer: BaseCheckpointSaver | None = None,
    system_prompt: str,
    subagents: Sequence[SubAgent | CompiledSubAgent] | None = None,
    interrupt_on: dict[str, bool | InterruptOnConfig] | None = None,
    include_planning: bool = True,
    **kwargs: Any,
):
    middleware = [TodoListMiddleware()] if include_planning else []
    return create_deep_agent(
        model=model,
        tools=tools,
        system_prompt=system_prompt,
        middleware=middleware,
        subagents=subagents or None,
        interrupt_on=interrupt_on,
        checkpointer=checkpointer,
        **kwargs,
    )
```

`agent_graph.SYSTEM_PROMPT` 的测试改为直接从 `app.agent.prompts` 导入。

- [ ] **Step 6: 删除旧装配模块**

删除 `backend/app/agent/runtime/assembly.py`。所有调用方改用 `get_definition(...).build_graph(...)`。

- [ ] **Step 7: 运行定义与装配测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_agent_definitions.py tests/test_agent_graph.py tests/test_view_subagent.py`

Expected: PASS。

---

### Task 6: 重构通用运行时

**Files:**
- Modify: `backend/app/agent/runtime/runs.py`
- Modify: `backend/app/agent/runtime/__init__.py`
- Modify: `backend/app/agent/runtime/stream.py`
- Modify: `backend/app/api/agent.py`
- Modify: `backend/app/agent/thread.py`
- Test: `backend/tests/test_runtime_generic.py`

- [ ] **Step 1: 写通用运行时测试**

创建 `backend/tests/test_runtime_generic.py`，用一个假定义证明 `stream_run` 不需要 graph/changeset：

```python
import json
import uuid

from langchain_core.messages import AIMessage

from app.agent.definitions.base import AgentRunContext
from app.agent.runtime import stream_run
from app.config import Settings
from app.services import conversation


class _Agent:
    def stream(self, payload, config=None, stream_mode=None, subgraphs=False):
        yield {"model": {"messages": [AIMessage(content="通用运行时正常")]}}


class _Definition:
    kind = "fake"

    def prepare_run(self, session, *, conversation, owner_id):
        return AgentRunContext(
            conversation_id=conversation.id,
            owner_id=owner_id,
            kind=self.kind,
            resource_id=conversation.resource_id,
        )

    def build_graph(self, session, settings, run, *, checkpointer):
        return _Agent()

    def build_state_update(self, session, run):
        return {"view": {}}


def _events(text):
    return [
        json.loads(line.removeprefix("data: "))
        for block in text.split("\n\n")
        for line in block.splitlines()
        if line.startswith("data: ")
    ]


def test_stream_run_uses_definition_without_modeling_fields(
    session_factory, session, monkeypatch
):
    conv = conversation.create_conversation(
        session,
        owner_id=uuid.uuid4(),
        title="数据连接",
        kind="fake",
        resource_id=uuid.uuid4(),
    )
    session.commit()
    monkeypatch.setattr("app.agent.runtime.stream.get_definition", lambda kind: _Definition())

    body = "".join(
        stream_run(
            session_factory,
            Settings(chat_api_key="fake-key"),
            conv.owner_id,
            conversation_id=conv.id,
            text="你好",
        )
    )

    events = _events(body)
    assert events[0]["type"] == "run.start"
    assert events[-1]["type"] == "run.end"
    assert any(event["type"] == "state.update" for event in events)
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_runtime_generic.py`

Expected: FAIL，`stream_run` 仍调用 `prepare_run` 和 `assembly`。

- [ ] **Step 3: 改 runs.py**

把 `backend/app/agent/runtime/runs.py` 改为只保留线程键和会话校验：

```python
from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.agent.definitions.registry import get_definition
from app.models import Conversation
from app.services import conversation


def thread_config(conversation_id: uuid.UUID) -> dict:
    return {"configurable": {"thread_id": str(conversation_id)}}


def validate_conversation(
    session: Session, *, conversation_id: uuid.UUID, owner_id: uuid.UUID
) -> Conversation:
    conv = conversation.get_conversation(session, conversation_id, owner_id=owner_id)
    get_definition(conv.kind)
    return conv
```

- [ ] **Step 4: 改 stream.py 的入口与收尾**

把导入改为：

```python
from app.agent.definitions.base import AgentRunContext
from app.agent.definitions.registry import get_definition
from app.agent.runtime.runs import thread_config
from app.services import conversation
```

把 `stream_run` 中准备运行、装配和结束状态的三段改为：

```python
conv = conversation.get_conversation(session, conversation_id, owner_id=owner_id)
definition = get_definition(conv.kind)
run = definition.prepare_run(session, conversation=conv, owner_id=owner_id)
...
agent = definition.build_graph(session, settings, run, checkpointer=checkpointer)
...
state_payload = definition.build_state_update(session, run)
yield sse(StateUpdateEvent(**state_payload))
```

删除 `changeset`、`view_state` 和 `assembly` 的导入；工具调用、消息落库、中断、错误处理和 SSE 逻辑保持不变。

- [ ] **Step 5: 改 API 入口校验**

`backend/app/api/agent.py` 中两处 `runtime.prepare_run(...)` 改为：

```python
runtime.validate_conversation(
    session,
    conversation_id=conversation_id,
    owner_id=get_current_user_id(),
)
```

- [ ] **Step 6: 改定稿回写**

`backend/app/agent/thread.py` 用建模定义装配，不再调用 `runtime.build_agent_for_run`：

```python
from app.agent.definitions.ontology import OntologyRunContext
from app.agent.definitions.registry import get_definition

run = OntologyRunContext(
    conversation_id=conversation_id,
    owner_id=owner_id,
    kind="modeling",
    resource_id=graph_id,
    graph_id=graph_id,
    changeset_id=changeset_id,
)
agent = get_definition("modeling").build_graph(
    session, settings, run, checkpointer=checkpointer
)
```

只导入实际使用的 `OntologyRunContext` 与 `get_definition`，不要留下未使用的 `OntologyAgentDefinition` 导入。同时更新 `backend/app/agent/runtime/__init__.py`，导出 `AgentDefinition`、`AgentRunContext`、`stream_run`、`thread_config`、`validate_conversation`。

- [ ] **Step 7: 运行通用运行时测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_runtime_generic.py tests/test_api_agent.py tests/test_agent_thread.py`

Expected: PASS；若旧测试仍 monkeypatch `assembly`，把目标改成 `app.agent.definitions.ontology.OntologyAgentDefinition.build_graph`。

---

### Task 7: 补依赖方向与回归测试

**Files:**
- Create: `backend/tests/test_agent_dependency_direction.py`
- Modify: `backend/tests/test_agent_graph.py`
- Modify: `backend/tests/test_observability.py`
- Modify: `backend/tests/test_subagent_trace.py`

- [ ] **Step 1: 写依赖方向测试**

```python
from pathlib import Path


def test_generic_runtime_does_not_import_domain_modules() -> None:
    source = Path("app/agent/runtime/stream.py").read_text()

    assert "tools.ontology" not in source
    assert "tools.data_connection" not in source
    assert "services.changeset" not in source
    assert "services.view_state" not in source
```

- [ ] **Step 2: 更新旧测试的 monkeypatch 目标**

把所有 `app.agent.runtime.assembly.build_agent_for_run` 替换为：

```python
app.agent.definitions.ontology.OntologyAgentDefinition.build_graph
```

替换时保持函数签名一致：

```python
def build_with_view(self, session, settings, run, *, checkpointer=None):
    ...
```

`test_api_agent.py`、`test_observability.py` 的假函数也要删掉原来的 `owner_id` 参数；新签名固定为 `(self, session, settings, run, *, checkpointer=None)`。

- [ ] **Step 3: 给所有直接调用 `build_agent` 的测试补提示词**

`build_agent` 现在要求显式传入提示词。以下调用统一补 `system_prompt=SYSTEM_PROMPT`：

- `backend/tests/test_agent_graph.py` 的两处调用
- `backend/tests/test_agent_thread.py` 的 `_agent`
- `backend/tests/test_view_binding.py` 的编译测试
- `backend/tests/test_subagent_trace.py` 的脚本模型装配

示例：

```python
from app.agent.prompts import SYSTEM_PROMPT

agent = agent_graph.build_agent(
    _fake_model(),
    build_model_tools(ctx),
    system_prompt=SYSTEM_PROMPT,
    checkpointer=InMemorySaver(),
)
```

- [ ] **Step 4: 更新提示词测试**

`test_agent_graph.py` 中 `agent_graph.SYSTEM_PROMPT` 改为：

```python
from app.agent.prompts import SYSTEM_PROMPT

assert "用户明确说" in SYSTEM_PROMPT
```

- [ ] **Step 5: 运行受影响测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_agent_dependency_direction.py tests/test_agent_graph.py tests/test_observability.py tests/test_subagent_trace.py tests/test_api_agent.py`

Expected: PASS。

---

### Task 8: 全量校验

**Files:**
- Verify: `backend/app/**`
- Verify: `backend/tests/**`

- [ ] **Step 1: 跑后端测试**

Run: `cd backend && .venv/bin/python -m pytest -q`

Expected: 全部通过。

- [ ] **Step 2: 跑静态检查**

Run: `cd backend && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app`

Expected: 无错误、无格式差异。

- [ ] **Step 3: 检查迁移链**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py`

Expected: PASS。

---

## 本阶段完成标准

- `Conversation` 数据库只保留 `kind + resource_id`；`graph_id` 兼容属性已移除，建模代码统一使用 `resource_id`，只在 `OntologyRunContext` 内部映射为 `graph_id`。
- `AgentDefinition` 成为唯一装配扩展点，`Conversation.kind` 决定使用哪个 Agent。
- `stream_run` 不再导入 `changeset`、`view_state` 或任何具体领域工具。
- 建模 Agent 的 SSE、消息、检查点、中断、改动集和视图行为保持不变。
- 通用运行时用一个假定义即可跑通，不依赖图或改动集。
- 后端全量测试、ruff、mypy 通过。

## 实施确认

### 会话与资源标识

- `Conversation.kind` 是受控枚举，数据库有 CHECK 约束；`(kind, resource_id)` 唯一。
- `resource_id` 是跨资源类型的多态标识，不设外键；图的级联清理仍由会话/图服务负责。
- 公开创建接口仍只创建 modeling 会话；数据连接会话由后续数据连接服务创建。
- 非 modeling 会话不创建 Graph/ViewState；进入建模 Agent 前由定义层拒绝，不会把 `resource_id` 误当 graph id。

### 工具上下文

- 通用 `ToolContext` 只保留 `session / owner_id / lock / invoke()`。
- `OntologyToolContext` 继承通用上下文，增加 `graph_id / changeset_id / embedder`。
- 数据连接 Agent 后续直接复用通用上下文，不需要携带建模字段。

### Agent 定义与运行时

- `AgentDefinition` 是唯一装配扩展点，注册表按 `Conversation.kind` 解析。
- `build_agent` 不再暗含建模提示词或审批策略，必须由定义显式传入。
- 子智能体过程可见性由 `AgentDefinition.visible_subagents` 提供，通用 runtime 不认识任何建模子智能体。
- `stream_run` 只负责线程、SSE、工具事件、消息落库、中断与错误兜底；运行上下文、图装配和结束状态由定义提供。
- `thread.record_decision` 写回线程前校验会话归属、kind 与 `resource_id == graph_id`，错误时只告警，不影响定稿主流程。
- 依赖方向测试使用 AST 检查 `runtime/stream.py` 与 `runtime/subagent_trace.py`，防止领域模块重新渗入通用运行时。

### 后续入口

下一子计划从 Java 数据服务开始：`DataConnection`、加密凭据、连接测试、表发现与内部 REST。Python 侧再按同一 `AgentDefinition` 模式新增数据连接定义与只读工具。

## 后续子计划

1. **Java 数据服务**：`DataConnection`、加密凭据、连接测试、表发现、内部 REST。
2. **Flink CDC + Iceberg**：同步任务/运行、pipeline YAML、作业提交与状态跟踪、版本发布。
3. **Python 数据连接 API 与 Agent**：资源化接口、事件消费/SSE、数据连接只读工具。
4. **前端工作台**：三栏布局、右侧上下文面板、表选择、数据集详情。
5. **端到端验收**：Docker Compose、MySQL → Iceberg → 数据集 → 对话事件。
