"""用 deepagents 装配智能体。

不自己拼 StateGraph：agent↔tools 循环、工具调用修补、消息摘要、子智能体调度都由
deepagents 提供，手搓只会更差。这里只决定用哪些工具、挂哪些子智能体、拦哪些操作。
"""

from __future__ import annotations

from collections.abc import Sequence
from typing import Any

from deepagents import CompiledSubAgent, SubAgent, create_deep_agent
from langchain.agents.middleware import InterruptOnConfig, TodoListMiddleware
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver


def build_agent(
    model: Any,
    tools: list[BaseTool],
    *,
    system_prompt: str,
    checkpointer: BaseCheckpointSaver | None = None,
    subagents: Sequence[SubAgent | CompiledSubAgent] | None = None,
    interrupt_on: dict[str, bool | InterruptOnConfig] | None = None,
    include_planning: bool = True,
    **kwargs: Any,
):
    """装配一轮可运行的智能体。

    提示词与审批策略都由调用方的 Agent 定义提供；通用装配层不暗含任何领域默认值。
    interrupt_on 没有 checkpointer 时不可用，这里统一关掉，避免装配期就报错。
    """
    middleware = [TodoListMiddleware()] if include_planning else []
    if checkpointer is None:
        interrupt_on = None

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


def build_checkpointer(database_url: str):
    """用同一个 Postgres 存检查点，中断后能原样续跑。

    注意 from_conn_string 返回的是上下文管理器，用在长期存活的进程里要自己建连接池；
    而且 conninfo 要 libpq 格式，得把 SQLAlchemy 的驱动后缀去掉。

    返回 (saver, pool)：连接池必须由调用方在应用关闭时关掉，否则进程退出前
    会漏一串 ResourceWarning。
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
    return saver, pool
