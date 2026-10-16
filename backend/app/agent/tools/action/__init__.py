"""动作子智能体。

`ops.py` 是模型视角的读/写操作（纯 Python，可单测），`args.py` 是工具的入参模型，
`binding.py` 把它们绑成模型可调用的工具，`subagent.py` 包成 isolated 子智能体。
"""

from __future__ import annotations

from app.agent.tools.action.binding import build_action_tools
from app.agent.tools.action.subagent import build_action_subagent

__all__ = ["build_action_subagent", "build_action_tools"]
