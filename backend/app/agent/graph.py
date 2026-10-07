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
        for call in getattr(last, "tool_calls", None) or []:
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
            except Exception as exc:  # 工具报错回给模型，让它自己纠正
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
    """用同一个 Postgres 存检查点，中断后能原样续跑。

    注意 from_conn_string 返回的是上下文管理器，用在长期存活的进程里要自己建连接池；
    而且 conninfo 要 libpq 格式，得把 SQLAlchemy 的驱动后缀去掉。
    """
    from langgraph.checkpoint.postgres import PostgresSaver
    from psycopg.rows import dict_row
    from psycopg_pool import ConnectionPool

    conninfo = database_url.replace("postgresql+psycopg", "postgresql")
    pool = ConnectionPool(
        conninfo=conninfo,
        max_size=10,
        kwargs={"autocommit": True, "prepare_threshold": 0, "row_factory": dict_row},
        open=True,
    )
    # psycopg_pool 没能从 kwargs 里推断出 dict 行工厂，类型标注过不来；
    # 运行时行为已验证正确，这里做一次定向豁免。
    saver = PostgresSaver(pool)  # type: ignore[arg-type]
    saver.setup()
    return saver
