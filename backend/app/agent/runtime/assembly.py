"""一轮运行的依赖装配。

把模型、工具、子智能体和检查点拼成一个可运行的图。这里只做装配，不含业务规则：
图怎么写由工具决定，事务边界由调用方决定。
"""

from __future__ import annotations

import uuid

from sqlalchemy.orm import Session

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.runtime.runs import RunContext
from app.agent.tools import (
    ToolContext,
    build_action_subagent,
    build_model_tools,
    build_view_subagent,
)
from app.config import Settings
from app.services import embedding


def build_agent_for_run(
    session: Session,
    settings: Settings,
    run: RunContext,
    owner_id: uuid.UUID,
    *,
    checkpointer=None,
):
    ctx = ToolContext(
        session=session,
        owner_id=owner_id,
        graph_id=run.graph_id,
        changeset_id=run.changeset_id,
        embedder=embedding.build_embedder(settings),
    )
    return agent_graph.build_agent(
        llm.build_chat_model(settings),
        build_model_tools(ctx),
        subagents=[build_view_subagent(ctx), build_action_subagent(ctx)],
        checkpointer=checkpointer,
    )
