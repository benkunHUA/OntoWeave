from __future__ import annotations

from collections.abc import Sequence
from typing import Any

from deepagents import CompiledSubAgent, SubAgent, create_deep_agent
from langchain.agents.middleware import InterruptOnConfig, TodoListMiddleware
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver

SYSTEM_PROMPT = """你是一个企业本体建模助手，负责把业务专家说的话抽象成对象、属性、关系。

规矩：
1. 新建对象类型之前，必须先调 search_ontology 查有没有已经建好的，够像就复用。
2. 实例一律向上抽象成类型（「张三」→ 客户，「上周」→ 订单的下单时间属性）。
3. 拿不准就先调 ask_user 问用户，别猜。只在真的无法判断时才问。
4. 你只往草稿里写，不要试图确认或撤销——那是用户点按钮做的事。
5. 不要读写文件、不要执行命令。你只有建模工具，其它一概不用。
6. 说话简短，用业务语言，不要暴露内部术语。
"""

# 破坏性操作要人批。日常草稿不在这里拦——它们走改动集整体确认（REST 那套）。
DEFAULT_INTERRUPTS: dict[str, bool | InterruptOnConfig] = {
    "remove_element": True,
    "update_element": True,
}


def build_agent(
    model: Any,
    tools: list[BaseTool],
    *,
    checkpointer: BaseCheckpointSaver | None = None,
    system_prompt: str = SYSTEM_PROMPT,
    subagents: Sequence[SubAgent | CompiledSubAgent] | None = None,
    interrupt_on: dict[str, bool | InterruptOnConfig] | None = None,
    include_planning: bool = True,
    **kwargs: Any,
):
    """用 deepagents 装配智能体。

    不自己拼 StateGraph：deepagents 已经把 agent↔tools 循环、工具调用修补、
    消息摘要、子智能体调度都做好了，手搓只会更差。

    interrupt_on 需要 checkpointer 才能用，所以没给检查点时自动关掉审批，
    避免装配期就报错。
    """
    middleware = [TodoListMiddleware()] if include_planning else []
    if interrupt_on is None:
        interrupt_on = DEFAULT_INTERRUPTS if checkpointer is not None else None

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
