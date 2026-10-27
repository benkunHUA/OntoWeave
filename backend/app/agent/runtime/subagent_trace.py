"""子智能体的过程可见性。

`subgraphs=True` 时，langgraph 给「工具内部调起来的图」打上 `("tools:<task_id>",)` 命名空间，
但那个 id 是 langgraph 自己的任务 id，认不出是哪个子智能体。这里记下主智能体发出的 `task`
调用，按「先起的先认」把命名空间绑回那次调用；绑定只做一次并缓存。

可见范围由会话的 AgentDefinition 提供；默认不暴露任何子智能体的内部过程。
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

# 子图命名空间的形状：`("<父节点>:<langgraph 任务 id>", ...)`。
_TOOL_NAMESPACE_PREFIX = "tools:"


@dataclass(frozen=True)
class SubagentCall:
    """一次 `task` 委派：哪个子智能体、露不露给前端、发起它的工具调用 id。"""

    call_id: str
    name: str
    visible: bool


class SubagentTrace:
    """把子图命名空间对回发起它的那次 `task` 调用。"""

    def __init__(self, visible: frozenset[str] = frozenset()) -> None:
        self._visible = visible
        self._pending: list[SubagentCall] = []
        self._active: list[SubagentCall] = []
        self._bound: dict[str, SubagentCall | None] = {}

    def note_task_call(self, *, call_id: str, subagent_type: str) -> None:
        """主智能体委派了一次 `task`：等它的子图命名空间露头，好把两边对起来。"""
        if not subagent_type:
            return
        call = SubagentCall(
            call_id=call_id,
            name=subagent_type,
            visible=subagent_type in self._visible,
        )
        self._pending.append(call)
        self._active.append(call)

    def note_tool_end(self, call_id: str) -> None:
        self._pending = [call for call in self._pending if call.call_id != call_id]
        self._active = [call for call in self._active if call.call_id != call_id]

    def call_for(self, namespace: Sequence[str]) -> SubagentCall | None:
        """这段事件属于哪个子智能体；顶层命名空间（主智能体）返回 None。"""
        if not namespace:
            # CompiledSubAgent 里的 create_agent 有时不会给流事件附上子图命名空间。
            # 此时 task 还没返回，活跃委派内部的事件都属于该子智能体。
            return self._active[0] if self._active else None
        head = str(namespace[0])
        if not head.startswith(_TOOL_NAMESPACE_PREFIX):
            return None
        if head in self._bound:
            return self._bound[head]
        # 并行委派时命名空间里没有子智能体名，只能按「先起的先认」依次绑定。
        call = self._pending.pop(0) if self._pending else None
        self._bound[head] = call
        return call
