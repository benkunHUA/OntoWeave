"""查询证据采集：工具执行后把完整结果写入图状态。"""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from langchain.agents.middleware import AgentMiddleware
from langchain_core.messages import ToolMessage
from langgraph.types import Command

from app.agent.evidence.capture import evidence_delta_from_tool
from app.agent.state import BusinessAgentState


class QueryEvidenceMiddleware(AgentMiddleware):
    state_schema = BusinessAgentState

    def wrap_tool_call(
        self,
        request: Any,
        handler: Callable[[Any], ToolMessage | Command[Any]],
    ) -> ToolMessage | Command[Any]:
        result = handler(request)
        if not isinstance(result, ToolMessage) or result.status == "error":
            return result
        delta = evidence_delta_from_tool(
            str(request.tool_call.get("name") or ""),
            request.tool_call.get("args") or {},
            result.content,
            turn_id=_turn_id(request),
        )
        if delta is None:
            return result
        return Command(update={"messages": [result], "evidence": [delta]})


def _turn_id(request: Any) -> str | None:
    config = getattr(request.runtime, "config", None) or {}
    configurable = config.get("configurable") or {}
    value = configurable.get("turn_id")
    return str(value) if value else None
