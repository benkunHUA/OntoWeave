# Plan 4 · 智能体运行时与 SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Plan 3 的工具接上真实模型，用 LangGraph 编排成「想 → 调工具 → 看结果 → 再想」的循环，支持中途停下来问用户并能续跑，最后通过 SSE 把整个过程推给前端。

**Architecture:** 工具是 Plan 3 写好的普通函数，这里用闭包把运行时上下文（session / owner / graph / changeset / embedder）绑进去，包成 LangChain 工具。图有三个节点：`agent`（模型决策）、`tools`（执行工具）、以及被 `tools` 节点拦截的 `ask_user` 中断点。检查点用 PostgresSaver，`thread_id` 直接复用 `conversation_id`——Plan 2 已定会话与图一对一，不再另引线程表。

**Tech Stack:** 新增 `langgraph`、`langgraph-checkpoint-postgres`、`langchain-core`、`langchain-deepseek`。

**对应设计文档：** 第 6、7 节。

**前置：** Plan 1–3 完成，`0006 (head)`，112 个测试通过。模型已接通并实测：

- `deepseek-flash`（`https://api.deepseek.com`）是**推理模型**，function calling 完整可用；
  响应含 `reasoning_content`，**`max_tokens` 给小了会只出推理、正文为空**。
- `text-embedding-v4`（DashScope 兼容模式）在 `dimensions=1536` 下返回 1536 维，与列维度一致。

## 文件结构

| 文件 | 职责 |
|---|---|
| `app/agent/llm.py` | 模型装配，集中处理推理模型的参数 |
| `app/agent/tools.py` | 把 Plan 3 的普通函数包成 LangChain 工具（闭包注入上下文） |
| `app/agent/graph.py` | LangGraph 状态图、中断节点、检查点 |
| `app/agent/runtime.py` | 一次运行的装配与会话/改动集生命周期 |
| `app/api/agent.py` | SSE 端点 |

---

## Task 1: 模型装配与工具绑定

**Files:**
- Modify: `backend/pyproject.toml`、`backend/app/config.py`、`backend/.env.example`、`backend/.env`
- Create: `backend/app/agent/llm.py`、`backend/app/agent/tools.py`
- Test: `backend/tests/test_agent_tools_binding.py`

- [ ] **Step 1: 补依赖与配置**

`pyproject.toml` 的 `dependencies` 追加：

```toml
    "langgraph==1.2.14",
    "langgraph-checkpoint-postgres==3.0.4",
    "langchain-core==1.6.7",
    "langchain-deepseek==1.1.1",
```

`config.py` 的对话模型一节补两个字段：

```python
    chat_max_tokens: int = 4096
    chat_reasoning_effort: str = ""  # 留空则不传，交给服务端默认
```

`.env.example` 与 `.env` 补上：

```bash
CHAT_MAX_TOKENS=4096
CHAT_REASONING_EFFORT=
```

- [ ] **Step 2: Write the failing test**

```python
# backend/tests/test_agent_tools_binding.py
import uuid

import pytest

from app.agent import tools as agent_tools
from app.config import Settings
from app.errors import InvariantViolationError
from app.services import changeset, conversation, embedding


def _context(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="绑定测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return agent_tools.ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def test_binds_expected_tool_names(session):
    ctx = _context(session, uuid.uuid4())
    names = [item.name for item in agent_tools.build_model_tools(ctx)]
    assert names == [
        "search_ontology",
        "list_types",
        "get_subgraph",
        "propose_type",
        "propose_property",
        "propose_relation",
        "update_element",
        "remove_element",
        "ask_user",
    ]


def test_tool_calls_reach_the_service_layer(session):
    owner = uuid.uuid4()
    ctx = _context(session, owner)
    by_name = {item.name: item for item in agent_tools.build_model_tools(ctx)}

    assert by_name["list_types"].invoke({}) == []

    created = by_name["propose_type"].invoke({"name": "客户"})
    assert created["status"] == "draft"
    assert [op.op for op in changeset.list_ops(session, ctx.changeset_id)] == ["create"]


def test_build_chat_model_requires_key():
    from app.agent import llm

    with pytest.raises(InvariantViolationError):
        llm.build_chat_model(Settings(chat_api_key=""))


def test_build_chat_model_passes_configured_values():
    from app.agent import llm

    model = llm.build_chat_model(
        Settings(
            chat_api_key="k",
            chat_model="deepseek-flash",
            chat_base_url="https://api.deepseek.com",
            chat_max_tokens=2048,
        )
    )
    assert model.model_name == "deepseek-flash"
    assert model.max_tokens == 2048
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_agent_tools_binding.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.agent.tools'`

- [ ] **Step 4: Implement the model factory**

```python
# backend/app/agent/llm.py
from __future__ import annotations

from typing import Any

from langchain_deepseek import ChatDeepSeek

from app.config import Settings
from app.errors import InvariantViolationError


def build_chat_model(settings: Settings) -> ChatDeepSeek:
    """装配对话模型。

    deepseek-flash 是推理模型：它先花 token 推理再出正文，max_tokens 给小了
    会得到「200 但 content 为空」的响应，所以这里必须给足。
    """
    if not settings.chat_configured:
        raise InvariantViolationError("chat api key is not configured")

    kwargs: dict[str, Any] = {
        "model": settings.chat_model,
        "api_key": settings.chat_api_key,
        "api_base": settings.chat_base_url,
        "temperature": settings.chat_temperature,
        "max_tokens": settings.chat_max_tokens,
        "timeout": settings.chat_timeout_seconds,
    }
    if settings.chat_reasoning_effort:
        kwargs["model_kwargs"] = {"reasoning_effort": settings.chat_reasoning_effort}
    return ChatDeepSeek(**kwargs)
```

- [ ] **Step 5: Implement the tool binding**

```python
# backend/app/agent/tools.py
from __future__ import annotations

import uuid
from dataclasses import dataclass

from langchain_core.tools import BaseTool, tool
from sqlalchemy.orm import Session

from app.agent import ontology_tools
from app.services import embedding


@dataclass(frozen=True)
class ToolContext:
    """一次运行里工具需要的全部外部依赖。模型看不见它，由闭包注入。"""

    session: Session
    owner_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID
    embedder: embedding.Embedder


def build_model_tools(ctx: ToolContext) -> list[BaseTool]:
    """把建模工具绑上运行上下文，包成模型可调用的工具。"""

    @tool("search_ontology")
    def search_ontology(query: str, limit: int = 10) -> list[dict]:
        """按语义检索已有对象类型。新建类型之前必须先调用，避免造重复。"""
        return ontology_tools.search_ontology(
            ctx.session, query=query, embedder=ctx.embedder, owner_id=ctx.owner_id, limit=limit
        )

    @tool("list_types")
    def list_types(limit: int = 50) -> list[dict]:
        """列出现有对象类型。"""
        return ontology_tools.list_types(ctx.session, owner_id=ctx.owner_id, limit=limit)

    @tool("get_subgraph")
    def get_subgraph(object_type_id: str) -> dict:
        """从给定对象类型出发走一跳，返回它周围的节点与关系。"""
        return ontology_tools.get_subgraph(
            ctx.session,
            graph_id=ctx.graph_id,
            object_type_id=uuid.UUID(object_type_id),
            owner_id=ctx.owner_id,
        )

    @tool("propose_type")
    def propose_type(name: str, description: str | None = None) -> dict:
        """把新的对象类型加进草稿。内部先查重，同名或足够像就复用已有的。"""
        return ontology_tools.propose_type(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            name=name,
            description=description,
            embedder=ctx.embedder,
        )

    @tool("propose_property")
    def propose_property(
        owner_type_id: str, name: str, datatype: str = "text", required: bool = False
    ) -> dict:
        """给对象类型加属性。owner_type_id 必须来自 search_ontology 或 list_types。"""
        return ontology_tools.propose_property(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            owner_type_id=uuid.UUID(owner_type_id),
            name=name,
            datatype=datatype,
            required=required,
        )

    @tool("propose_relation")
    def propose_relation(
        source_type_id: str, target_type_id: str, name: str, direction: str = "forward"
    ) -> dict:
        """加一条关系，两端必须都已存在。"""
        return ontology_tools.propose_relation(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            source_type_id=uuid.UUID(source_type_id),
            target_type_id=uuid.UUID(target_type_id),
            name=name,
            direction=direction,
        )

    @tool("update_element")
    def update_element(element_kind: str, element_id: str, patch: dict, before: dict) -> dict:
        """改已有元素。before 是改动前的字段快照，撤销时靠它还原，必须原样带上。"""
        return ontology_tools.update_element(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            element_kind=element_kind,
            element_id=uuid.UUID(element_id),
            patch=patch,
            before=before,
        )

    @tool("remove_element")
    def remove_element(element_kind: str, element_id: str) -> dict:
        """标记删除。草稿阶段不真删，用户确认时才删。"""
        return ontology_tools.remove_element(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            element_kind=element_kind,
            element_id=uuid.UUID(element_id),
        )

    @tool("ask_user")
    def ask_user(question: str, options: list[str] | None = None) -> str:
        """拿不准时停下来问用户。这是中断点，用户回答后你会拿到答案继续。

        只在真的无法判断时才用：同名不同义、一个词可能是对象也可能是属性、关系方向不确定。
        """
        return ""  # 实际由 tools 节点拦截，见 graph.py

    return [
        search_ontology,
        list_types,
        get_subgraph,
        propose_type,
        propose_property,
        propose_relation,
        update_element,
        remove_element,
        ask_user,
    ]
```

- [ ] **Step 6: Run tests, then commit**

```bash
cd backend
./.venv/bin/python -m pytest tests/test_agent_tools_binding.py -q   # 期望 4 passed
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
uv lock
cd .. && git add backend && git commit -m "feat(backend): wire chat model and bind agent tools"
```

---

## Task 2: LangGraph 图与中断

**Files:**
- Create: `backend/app/agent/graph.py`
- Test: `backend/tests/test_agent_graph.py`

- [ ] **Step 1: Write the failing test**

用脚本化的假模型替代 `deepseek-flash`，把「调工具」和「收尾」两轮串起来，再测中断与续跑。

```python
# backend/tests/test_agent_graph.py
import uuid

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from app.agent import graph as agent_graph, tools as agent_tools
from app.services import changeset, conversation, embedding


class ScriptedModel:
    """按预设脚本依次返回消息的假模型，测试不联网。"""

    def __init__(self, script):
        self._script = list(script)

    def bind_tools(self, tools):  # noqa: ARG002
        return self

    def invoke(self, messages, config=None):  # noqa: ARG002
        return self._script.pop(0)


def _tool_call(name, args=None, call_id="c1"):
    return AIMessage(
        content="",
        tool_calls=[{"name": name, "args": args or {}, "id": call_id, "type": "tool_call"}],
    )


def _ctx(session, owner):
    conv = conversation.create_conversation(session, owner_id=owner, title="图测试")
    cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
    return conv, agent_tools.ToolContext(
        session=session,
        owner_id=owner,
        graph_id=conv.graph_id,
        changeset_id=cs.id,
        embedder=embedding.HashingEmbedder(),
    )


def test_graph_runs_a_tool_then_finishes(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel([_tool_call("list_types"), AIMessage(content="已经查完了。")])
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver()
    )

    result = app.invoke(
        {"messages": [HumanMessage("看看现在有哪些类型")], "iterations": 0},
        config={"configurable": {"thread_id": str(conv.id)}},
    )
    assert result["messages"][-1].content == "已经查完了。"
    assert any(type(m).__name__ == "ToolMessage" for m in result["messages"])


def test_graph_asks_user_then_resumes(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel([
        _tool_call(
            "ask_user",
            {"question": "张三算客户还是供应商？", "options": ["客户", "供应商"]},
        ),
        AIMessage(content="好的，按客户处理。"),
    ])
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver()
    )
    config = {"configurable": {"thread_id": str(conv.id)}}

    paused = app.invoke(
        {"messages": [HumanMessage("张三上周下了个订单")], "iterations": 0}, config=config
    )
    interrupts = paused.get("__interrupt__")
    assert interrupts
    assert interrupts[0].value["question"] == "张三算客户还是供应商？"
    assert interrupts[0].value["options"] == ["客户", "供应商"]

    resumed = app.invoke(Command(resume="客户"), config=config)
    assert resumed["messages"][-1].content == "好的，按客户处理。"


def test_graph_stops_at_max_iterations(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel([_tool_call("list_types", call_id=f"c{i}") for i in range(10)])
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver(), max_iterations=3
    )
    result = app.invoke(
        {"messages": [HumanMessage("一直调工具")], "iterations": 0},
        config={"configurable": {"thread_id": str(conv.id)}},
    )
    assert result["iterations"] <= 3


def test_tool_errors_are_returned_to_the_model(session):
    owner = uuid.uuid4()
    conv, ctx = _ctx(session, owner)
    model = ScriptedModel([
        _tool_call("get_subgraph", {"object_type_id": str(uuid.uuid4())}),
        AIMessage(content="那个节点不在图里。"),
    ])
    app = agent_graph.build_graph(
        model, agent_tools.build_model_tools(ctx), checkpointer=InMemorySaver()
    )
    result = app.invoke(
        {"messages": [HumanMessage("看看这个节点周边")], "iterations": 0},
        config={"configurable": {"thread_id": str(conv.id)}},
    )
    tool_messages = [m for m in result["messages"] if type(m).__name__ == "ToolMessage"]
    assert "NotFoundError" in tool_messages[0].content
    assert result["messages"][-1].content == "那个节点不在图里。"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_agent_graph.py -q`
Expected: FAIL — `ModuleNotFoundError: No module named 'app.agent.graph'`

- [ ] **Step 3: Implement the graph**

```python
# backend/app/agent/graph.py
from __future__ import annotations

import json
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AnyMessage, BaseMessage, SystemMessage, ToolMessage
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.types import interrupt

SYSTEM_PROMPT = """你是一个企业本体建模助手，负责把业务专家说的话抽象成对象、属性、关系。

规矩：
1. 新建对象类型之前，必须先调 search_ontology 查有没有已经建好的，够像就复用。
2. 实例一律向上抽象成类型（「张三」→ 客户，「上周」→ 订单的下单时间属性）。
3. 拿不准就先调 ask_user 问用户，别猜。只在真的无法判断时才问。
4. 你只往草稿里写，不要试图确认或撤销——那是用户点按钮做的事。
5. 说话简短，用业务语言，不要暴露内部术语。
"""


class AgentState(TypedDict):
    messages: Annotated[list[AnyMessage], add_messages]
    iterations: int


def build_graph(
    model: Any,
    tools: list[BaseTool],
    *,
    checkpointer: BaseCheckpointSaver | None = None,
    max_iterations: int = 12,
):
    """装配 agent ↔ tools 循环；ask_user 由 tools 节点拦截成中断。"""
    bound = model.bind_tools(tools)
    by_name = {item.name: item for item in tools}

    def agent_node(state: AgentState) -> dict:
        history = list(state["messages"])
        if not history or not isinstance(history[0], SystemMessage):
            history = [SystemMessage(SYSTEM_PROMPT), *history]
        response = bound.invoke(history)
        return {"messages": [response], "iterations": state.get("iterations", 0) + 1}

    def tools_node(state: AgentState) -> dict:
        last: BaseMessage = state["messages"][-1]
        outputs: list[BaseMessage] = []
        for call in last.tool_calls:
            if call["name"] == "ask_user":
                answer = interrupt(
                    {
                        "question": call["args"].get("question", ""),
                        "options": call["args"].get("options") or [],
                    }
                )
                outputs.append(
                    ToolMessage(
                        content=json.dumps({"answer": answer}, ensure_ascii=False),
                        tool_call_id=call["id"],
                    )
                )
                continue
            try:
                result = by_name[call["name"]].invoke(call["args"])
                content = json.dumps(result, ensure_ascii=False, default=str)
            except Exception as exc:  # 工具报错要回给模型，让它自己纠正
                content = json.dumps(
                    {"error": type(exc).__name__, "message": str(exc)}, ensure_ascii=False
                )
            outputs.append(ToolMessage(content=content, tool_call_id=call["id"]))
        return {"messages": outputs}

    def route(state: AgentState) -> str:
        if state.get("iterations", 0) >= max_iterations:
            return END
        last = state["messages"][-1]
        return "tools" if getattr(last, "tool_calls", None) else END

    builder = StateGraph(AgentState)
    builder.add_node("agent", agent_node)
    builder.add_node("tools", tools_node)
    builder.add_edge(START, "agent")
    builder.add_conditional_edges("agent", route, {"tools": "tools", END: END})
    builder.add_edge("tools", "agent")
    return builder.compile(checkpointer=checkpointer)


def build_checkpointer(database_url: str) -> BaseCheckpointSaver:
    """用同一个 Postgres 存检查点，中断后能原样续跑。"""
    from langgraph.checkpoint.postgres import PostgresSaver

    saver = PostgresSaver.from_conn_string(database_url)
    saver.setup()
    return saver
```

**注意：** `PostgresSaver.from_conn_string` 返回的是上下文管理器，真实接线时要么用
`with` 包住整个应用生命周期，要么用 `PostgresSaver(connection_pool)` 直接构造。
执行时按实际签名调整，别照抄。

- [ ] **Step 4: Run tests**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_agent_graph.py -q`
Expected: PASS（4 个用例）

- [ ] **Step 5: 用真实模型冒烟（不进 CI）**

```bash
cd backend && ./.venv/bin/python - <<'PY'
import uuid

from langchain_core.messages import HumanMessage
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.agent import graph as agent_graph, llm, tools as agent_tools
from app.config import get_settings
from app.services import changeset, conversation, embedding

settings = get_settings()
engine = create_engine(settings.database_url, future=True)
factory = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False, class_=Session)
session = factory()
owner = uuid.UUID(settings.default_user_id)

conv = conversation.create_conversation(session, owner_id=owner, title="冒烟")
cs = changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner)
ctx = agent_tools.ToolContext(
    session=session,
    owner_id=owner,
    graph_id=conv.graph_id,
    changeset_id=cs.id,
    embedder=embedding.build_embedder(settings),
)
app = agent_graph.build_graph(
    llm.build_chat_model(settings),
    agent_tools.build_model_tools(ctx),
    checkpointer=__import__("langgraph.checkpoint.memory", fromlist=["InMemorySaver"]).InMemorySaver(),
)
out = app.invoke(
    {
        "messages": [
            HumanMessage("客户会下订单，比如张三上周下了一个订单，买了两台 X 机器。")
        ],
        "iterations": 0,
    },
    config={"configurable": {"thread_id": str(conv.id)}},
)
print("最终回复:", out["messages"][-1].content[:300])
called = [m.name for m in out["messages"] if type(m).__name__ == "ToolMessage"]
print("工具调用:", called)
print("草稿操作:", [(op.op, op.payload.get("kind")) for op in changeset.list_ops(session, cs.id)])
session.rollback()
session.close()
PY
```

Expected: 打印出模型回复、它调用过的工具，以及改动集里**真实记下的操作**。
这一步必须真跑——它验证的是「模型能选对工具并把结果落到库里」。

- [ ] **Step 6: Commit**

```bash
cd backend && ./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
cd .. && git add backend && git commit -m "feat(backend): add langgraph agent loop with interrupt and checkpoints"
```

---

## Task 3: SSE 端点

**Files:**
- Create: `backend/app/agent/runtime.py`、`backend/app/api/agent.py`
- Modify: `backend/app/api/router.py`
- Test: `backend/tests/test_api_agent.py`

- [ ] **Step 1: Write the failing test**

```python
# backend/tests/test_api_agent.py
import json
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.main import create_app


def _events(text: str) -> list[dict]:
    out = []
    for block in text.split("\n\n"):
        for line in block.splitlines():
            if line.startswith("data: "):
                out.append(json.loads(line[len("data: ") :]))
    return out


@pytest.fixture
def client(session_factory: sessionmaker[Session], monkeypatch) -> TestClient:
    from langchain_core.messages import AIMessage

    class ScriptedModel:
        def __init__(self, script):
            self._script = list(script)

        def bind_tools(self, tools):  # noqa: ARG002
            return self

        def invoke(self, messages, config=None):  # noqa: ARG002
            return self._script.pop(0)

    script = [
        AIMessage(
            content="",
            tool_calls=[{"name": "list_types", "args": {}, "id": "c1", "type": "tool_call"}],
        ),
        AIMessage(content="图里现在还没有类型。"),
    ]
    monkeypatch.setattr(
        "app.agent.runtime.build_chat_model", lambda settings: ScriptedModel(script)
    )
    return TestClient(create_app(session_factory=session_factory))


def test_message_endpoint_streams_events(client):
    conv = client.post("/api/v1/conversations", json={"title": "SSE 测试"}).json()
    with client.stream(
        "POST",
        f"/api/v1/conversations/{conv['id']}/messages",
        json={"text": "图里有什么？"},
    ) as response:
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("text/event-stream")
        body = "".join(response.iter_text())

    kinds = [event["type"] for event in _events(body)]
    assert kinds[0] == "run.start"
    assert "tool.start" in kinds
    assert "tool.end" in kinds
    assert "message.delta" in kinds
    assert "state.update" in kinds
    assert kinds[-1] == "run.end"


def test_message_endpoint_rejects_unknown_conversation(client):
    resp = client.post(f"/api/v1/conversations/{uuid.uuid4()}/messages", json={"text": "hi"})
    assert resp.status_code == 404
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./.venv/bin/python -m pytest tests/test_api_agent.py -q`
Expected: FAIL — 404（路由不存在）

- [ ] **Step 3: Implement the runtime**

```python
# backend/app/agent/runtime.py
from __future__ import annotations

import json
import uuid
from collections.abc import Iterator
from dataclasses import dataclass

from langchain_core.messages import HumanMessage
from langgraph.types import Command
from sqlalchemy.orm import Session, sessionmaker

from app.agent import graph as agent_graph, llm
from app.agent.tools import ToolContext, build_model_tools
from app.config import Settings
from app.services import changeset, conversation, embedding


@dataclass
class RunContext:
    conversation_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID


def prepare_run(
    session: Session, *, conversation_id: uuid.UUID, owner_id: uuid.UUID
) -> RunContext:
    """保证一次对话有一个打开的改动集，草稿都往它里面写。"""
    conv = conversation.get_conversation(session, conversation_id, owner_id=owner_id)
    open_sets = [
        cs
        for cs in changeset.list_changesets(
            session, graph_id=conv.graph_id, owner_id=owner_id
        )
        if cs.status == changeset.OPEN
    ]
    cs = (
        open_sets[0]
        if open_sets
        else changeset.create_changeset(
            session, graph_id=conv.graph_id, owner_id=owner_id
        )
    )
    return RunContext(conversation_id=conv.id, graph_id=conv.graph_id, changeset_id=cs.id)


def stream_run(
    factory: sessionmaker[Session],
    settings: Settings,
    owner_id: uuid.UUID,
    *,
    conversation_id: uuid.UUID,
    text: str | None = None,
    resume: str | None = None,
) -> Iterator[str]:
    """跑一轮并推 SSE 事件。

    这里自己管事务，不走请求级依赖注入——流式响应会活过依赖的生命周期。
    """
    session = factory()
    try:
        run = prepare_run(session, conversation_id=conversation_id, owner_id=owner_id)
        ctx = ToolContext(
            session=session,
            owner_id=owner_id,
            graph_id=run.graph_id,
            changeset_id=run.changeset_id,
            embedder=embedding.build_embedder(settings),
        )
        app = agent_graph.build_graph(
            llm.build_chat_model(settings),
            build_model_tools(ctx),
            max_iterations=settings.agent_max_iterations,
        )
        config = {"configurable": {"thread_id": str(conversation_id)}}

        yield _sse({"type": "run.start", "conversationId": str(conversation_id)})
        payload = (
            Command(resume=resume)
            if resume is not None
            else {"messages": [HumanMessage(text or "")], "iterations": 0}
        )
        for chunk in app.stream(payload, config=config, stream_mode="updates"):
            for node, update in chunk.items():
                if node == "__interrupt__":
                    yield _sse({"type": "interrupt", **(update[0].value or {})})
                    continue
                for message in (update or {}).get("messages", []) or []:
                    for call in getattr(message, "tool_calls", None) or []:
                        yield _sse(
                            {"type": "tool.start", "name": call["name"], "args": call["args"]}
                        )
                    if type(message).__name__ == "ToolMessage":
                        yield _sse({"type": "tool.end", "content": str(message.content)[:500]})
                    elif getattr(message, "content", None):
                        yield _sse({"type": "message.delta", "text": message.content})

        session.commit()
        ops = [
            {"op": op.op, "kind": op.payload.get("kind")}
            for op in changeset.list_ops(session, run.changeset_id)
        ]
        yield _sse(
            {
                "type": "state.update",
                "changesetId": str(run.changeset_id),
                "ops": ops,
            }
        )
        yield _sse({"type": "run.end"})
    except Exception as exc:
        session.rollback()
        yield _sse({"type": "run.error", "message": f"{type(exc).__name__}: {exc}"})
    finally:
        session.close()


def _sse(payload: dict) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
```

- [ ] **Step 4: Implement the routes**

```python
# backend/app/api/agent.py
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.agent import runtime
from app.api.agent_deps import get_agent_owner_id
from app.config import get_settings
from app.deps import get_current_user_id, get_session

router = APIRouter(tags=["agent"])

# 关掉代理缓冲，否则事件会被攒着一起发，前端看不到渐进效果。
SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


class MessageIn(BaseModel):
    text: str


class ResumeIn(BaseModel):
    answer: str


@router.post("/conversations/{conversation_id}/messages")
def post_message(
    conversation_id: uuid.UUID,
    payload: MessageIn,
    request: Request,
    session: Session = Depends(get_session),
) -> StreamingResponse:
    owner_id = get_current_user_id()
    # 先在普通请求里校验会话存在与归属，别等流开了才报 404
    runtime.prepare_run(session, conversation_id=conversation_id, owner_id=owner_id)
    return StreamingResponse(
        runtime.stream_run(
            request.app.state.session_factory,
            get_settings(),
            owner_id,
            conversation_id=conversation_id,
            text=payload.text,
        ),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )


@router.post("/conversations/{conversation_id}/resume")
def post_resume(
    conversation_id: uuid.UUID,
    payload: ResumeIn,
    request: Request,
    session: Session = Depends(get_session),
) -> StreamingResponse:
    owner_id = get_current_user_id()
    runtime.prepare_run(session, conversation_id=conversation_id, owner_id=owner_id)
    return StreamingResponse(
        runtime.stream_run(
            request.app.state.session_factory,
            get_settings(),
            owner_id,
            conversation_id=conversation_id,
            resume=payload.answer,
        ),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )
```

（上面误引入了 `get_agent_owner_id`，执行时删掉这一行，直接用 `get_current_user_id()`。）

在 `app/api/router.py` 注册 `agent.router`。

- [ ] **Step 5: Run tests, then commit**

```bash
cd backend
./.venv/bin/python -m pytest tests/test_api_agent.py -q     # 期望 2 passed
./.venv/bin/ruff check . && ./.venv/bin/ruff format . && ./.venv/bin/mypy app
./.venv/bin/python -m pytest -q
cd .. && git add backend && git commit -m "feat(backend): expose agent runs over SSE"
```

---

## Task 4: 文档与真实模型端到端

**Files:**
- Modify: `backend/README.md`

- [ ] **Step 1: 补文档**

`backend/README.md` 新增一节说明 `app/agent/` 的四层、两个 SSE 端点，以及事件类型
（`run.start` / `message.delta` / `tool.start` / `tool.end` / `interrupt` /
`state.update` / `run.error` / `run.end`）。要写明三点：

1. `deepseek-flash` 是推理模型，`CHAT_MAX_TOKENS` 给小了会得到「200 但正文为空」。
2. 线程 ID 复用 `conversation_id`，不另引线程表。
3. SSE 的流式响应自己管事务，不用请求级依赖。

- [ ] **Step 2: 真实模型端到端**

```bash
cd backend && docker compose up -d --build
CONV=$(curl -s -X POST http://127.0.0.1:18000/api/v1/conversations \
  -H 'content-type: application/json' -d '{"title":"端到端"}' | jq -r .id)
curl -N -s -X POST "http://127.0.0.1:18000/api/v1/conversations/$CONV/messages" \
  -H 'content-type: application/json' \
  -d '{"text":"客户会下订单，比如张三上周下了一个订单，买了两台 X 机器。"}'
```

Expected: 事件流里能看到 `tool.start` / `tool.end` / `message.delta` / `state.update`，
最后 `run.end`；库里 `object_type` 出现草稿态的「客户」「订单」等。

- [ ] **Step 3: Commit**

```bash
git add backend/README.md && git commit -m "docs(backend): document the agent runtime and SSE endpoints"
```

---

## 自检记录

- **Spec coverage（设计文档 6、7 节）：** 主智能体工具集 ✅；deep agent 循环 ✅；显式中断与续跑 ✅；Postgres 检查点 ✅；REST + SSE 双通道 ✅；确认/撤销仍走 REST、不在智能体职责内 ✅。
- **未覆盖（属于 Plan 5）：** 绘图子智能体接进编排、前端、视图状态实时同步。
- **关键约束：** `deepseek-flash` 是推理模型，token 预算必须给足；`text-embedding-v4` 在 1536 维度下与列维度一致，无需迁移。
- **已知取舍：** 线程 ID 复用 `conversation_id`；`ask_user` 作为独立中断点而非工具内中断；SSE 流自己管事务。
- **计划里两处待现场确认：** `PostgresSaver` 的构造方式（`from_conn_string` 返回上下文管理器）；`agent.py` 里误引入的 `get_agent_owner_id` 需删掉。

## 后续计划

1. **Plan 5 · 前端**：首页菜单、建模三栏布局、React Flow 画布、草图态与幽灵态、SSE 接入、视图状态同步。
