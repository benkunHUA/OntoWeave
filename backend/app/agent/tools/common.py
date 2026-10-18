"""跨领域共享的模型工具。"""

from __future__ import annotations

from langchain_core.tools import BaseTool, tool


def build_ask_user_tool() -> BaseTool:
    """停下来问用户；由 LangGraph 的 interrupt 在工具内部挂起。"""

    @tool("ask_user")
    def ask_user(
        question: str,
        options: list[str] | None = None,
        details: list[dict] | None = None,
    ) -> str:
        """拿不准时停下来问用户。这是中断点，用户回答后你会拿到答案继续。

        只在真的无法判断时才用：同名不同义、一个词可能有多种解释、方向或选择不确定。
        details 是给用户看的补充信息，形如 [{"label": "描述", "value": "..."}]。
        """
        from langgraph.types import interrupt

        answer = interrupt(
            {"question": question, "options": options or [], "details": details or []}
        )
        return str(answer)

    return ask_user
