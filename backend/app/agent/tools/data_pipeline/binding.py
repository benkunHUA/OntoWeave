"""把数据整理读写能力按固定顺序装配成模型工具。"""

from __future__ import annotations

from typing import Any

from langchain_core.tools import BaseTool
from langgraph.types import interrupt

from app.agent.tools.data_pipeline.action_binding import build_ask_user_tool
from app.agent.tools.data_pipeline.context import DataPipelineToolContext
from app.agent.tools.data_pipeline.read_binding import build_read_tools
from app.agent.tools.data_pipeline.write_binding import build_write_tools


def _interrupt(payload: dict) -> Any:
    # 经这个函数转一次，测试和自定义运行环境可以稳定替换模块级 interrupt。
    return interrupt(payload)


def build_data_pipeline_tools(ctx: DataPipelineToolContext) -> list[BaseTool]:
    return [
        *build_read_tools(ctx),
        *build_write_tools(ctx, _interrupt),
        build_ask_user_tool(ctx, _interrupt),
    ]
