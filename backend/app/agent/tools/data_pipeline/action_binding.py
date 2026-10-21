"""数据整理 Agent 的提问中断工具。"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool, tool

from app.agent.tools.data_pipeline import constants
from app.agent.tools.data_pipeline.context import DataPipelineToolContext


def build_ask_user_tool(
    ctx: DataPipelineToolContext,
    interrupt_fn: Callable[[dict], Any],
) -> BaseTool:
    @tool("ask_user")
    def ask_user(
        question: str,
        options: list[str] | None = None,
        details: list[dict] | None = None,
    ) -> Any:
        """每轮最多问一个澄清问题；确认中断不占用这个额度。"""
        if ctx.questions_asked:
            return {
                "error": constants.QUESTION_LIMIT_REACHED,
                "message": constants.QUESTION_LIMIT_MESSAGE,
            }
        answer = interrupt_fn(
            {"question": question, "options": options or [], "details": details or []}
        )
        ctx.questions_asked.append(question)
        return str(answer)

    return ask_user
