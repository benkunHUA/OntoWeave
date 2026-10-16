"""绘图子智能体。

`ops.py` 是纯变换，`registry.py` 声明有哪些能力，`binding.py` 装配成工具，
`subagent.py` 把它包成 isolated 子智能体。
"""

from __future__ import annotations

from app.agent.tools.view.binding import build_view_tools
from app.agent.tools.view.subagent import build_view_subagent

__all__ = ["build_view_subagent", "build_view_tools"]
