"""rewrite_query 工具。"""

from __future__ import annotations

from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.tools import BaseTool, tool

from app.agent import llm
from app.agent.prompts.query_rewrite import REWRITE_QUERY_PROMPT
from app.config import Settings


def build_rewrite_tool(settings: Settings, *, model: Any | None = None) -> BaseTool:
    """用一次独立模型调用把问题改写成业务查询句。"""

    @tool("rewrite_query")
    def rewrite_query(question: str, context: str | None = None) -> str:
        """把用户原问题改写成一句业务查询；只返回改写句。"""
        chat = model or llm.build_chat_model(settings)
        messages = [
            SystemMessage(content=REWRITE_QUERY_PROMPT),
            HumanMessage(
                content=question + (f"\n上下文：{context}" if context and context.strip() else "")
            ),
        ]
        response = chat.invoke(messages)
        content = getattr(response, "content", response)
        if isinstance(content, list):
            content = "".join(
                str(item.get("text", "")) if isinstance(item, dict) else str(item)
                for item in content
            )
        return str(content).strip().strip('"').strip("'")

    return rewrite_query
