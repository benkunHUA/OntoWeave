"""查询子 Agent 的单步执行约束。"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langchain.agents.middleware import AgentMiddleware, ModelRequest


class SequentialToolCallsMiddleware(AgentMiddleware):
    """优先请求模型按单步调用工具。

    不是所有 provider 都支持 ``parallel_tool_calls``；查询状态本身也用 reducer 和
    唯一 step_id 兜住并发写入，因此这里只做成本更低的调用侧约束。
    """

    def wrap_model_call(
        self,
        request: ModelRequest[Any],
        handler: Callable[[ModelRequest[Any]], Any],
    ) -> Any:
        settings = {**request.model_settings, "parallel_tool_calls": False}
        return handler(request.override(model_settings=settings))
