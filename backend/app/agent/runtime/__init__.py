"""智能体运行时。

对外只有四个入口：`RunContext` / `prepare_run` / `thread_config`（运行的生命周期）、
`build_agent_for_run`（装配）、`stream_run`（流式编排）。
"""

from __future__ import annotations

from app.agent.runtime.assembly import build_agent_for_run
from app.agent.runtime.runs import RunContext, prepare_run, thread_config
from app.agent.runtime.stream import stream_run

__all__ = ["RunContext", "build_agent_for_run", "prepare_run", "stream_run", "thread_config"]
