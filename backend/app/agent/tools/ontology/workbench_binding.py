"""映射工作台 Agent 工具的组合入口。"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool

from app.agent.tools.common import build_ask_user_tool
from app.agent.tools.ontology.workbench_context import OntologyWorkbenchToolContext
from app.agent.tools.ontology.workbench_read_tools import build_read_tools
from app.agent.tools.ontology.workbench_write_tools import build_write_tools


def build_workbench_tools(
    ctx: OntologyWorkbenchToolContext,
    interrupt_fn: Callable[[dict], Any] | None = None,
) -> list[BaseTool]:
    return [*build_read_tools(ctx), *build_write_tools(ctx, interrupt_fn), build_ask_user_tool()]


__all__ = ["build_workbench_tools"]
