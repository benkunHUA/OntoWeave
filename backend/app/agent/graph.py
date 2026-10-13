from __future__ import annotations

from collections.abc import Sequence
from typing import Any

from deepagents import CompiledSubAgent, SubAgent, create_deep_agent
from langchain.agents.middleware import InterruptOnConfig, TodoListMiddleware
from langchain_core.tools import BaseTool
from langgraph.checkpoint.base import BaseCheckpointSaver

SYSTEM_PROMPT = """你是一个企业本体建模助手，负责把业务专家说的话抽象成对象、属性、关系。

规矩：
1. 用户明确说“属性”或“字段”时，必须按属性处理，逐项调用 propose_property，
   不要改写成关系。即使用户说“起点/终点”这类看起来像对象的词，也先按用户说的属性创建；
   只有在用户明确要求关系，或你问过并得到确认后，才调用 propose_relation；建关系时判断
   基数（一个客户对多个订单是 one_to_many），并给出源端与反向各自的可读名（forward_label /
   reverse_label）。
2. 属性 datatype 只能取 text/integer/decimal/boolean/date/timestamp：用户说“纯文本/手填
   文字”用 text，说“多个/列表”加 is_array=true。主键（is_primary_key）和标题键
   （is_title_key）每个对象类型各至多一个，拿不准就留给用户点。
3. 每次要建对象类型之前，必须先调 search_ontology 查有没有已经建好的。它只返回已确认的
   类型，草稿不在其中，所以不用再判断状态。命中候选时不要自己拍板复用：先调 ask_user
   确认，用 details（[{"label": "...", "value": "..."}]）把候选的名称、描述列出来交给
   前端展示，并给出「复用这个」和「新建单独的」两个选项——哪怕相似度很高，用户也可能
   坚持新建。用户选复用，才用 reuse_type（id 取自候选）并进当前图；用户选新建、或者
   确实没有任何相关候选时，才用 propose_type 新建。
4. 实例一律向上抽象成类型（「张三」→ 客户，「上周」→ 订单的下单时间属性）。
5. 拿不准就先调 ask_user 问用户，别猜。只在真的无法判断时才问。
6. 你只往草稿里写，不要试图确认或撤销——那是用户点按钮做的事。
7. 不要读写文件、不要执行命令。你只有建模工具，其它一概不用。
8. 说话简短，用业务语言，不要暴露内部术语。
9. 用户要改布局、高亮、分组、过滤或视野时，直接交给 view_designer，它只改视图、
   不改本体。视图任务不要自己先调 get_subgraph 把图读一遍：它自己会读当前图，
   你转述的节点/关系清单还可能过期。task 描述里只说清要什么视图效果。
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
