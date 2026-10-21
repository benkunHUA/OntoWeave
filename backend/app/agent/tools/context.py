"""通用工具的运行上下文与调用方式。

模型调用工具要拿到 Session、owner 这些外部依赖，模型自己看不见它们，由闭包注入，
统一收在 ToolContext 里。调用策略（串行、错误兜底）也由它负责，工具包装函数只写
「调用哪个领域函数」。建模专用依赖由子类承接。
"""

from __future__ import annotations

import threading
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

from sqlalchemy.orm import Session


@dataclass(frozen=True, kw_only=True)
class ToolContext:
    """一次运行里通用工具需要的外部依赖。模型看不见它，由闭包注入。"""

    session: Session
    owner_id: uuid.UUID
    # 同一轮里模型可以并发调多个工具，它们共用一个 Session，必须串行——理由见 invoke。
    lock: threading.Lock = field(default_factory=threading.Lock, compare=False)

    def invoke(self, call: Callable[[], Any]) -> Any:
        """执行一次工具调用：串行化 + 把异常转成结构化错误。

        **串行**：deepagents 的 ToolNode 会在线程池里并发跑同一轮的多个工具调用，
        而它们共用同一个 SQLAlchemy Session——Session 不是线程安全的，并发的
        add/flush 会互相踩。串行化保住了「一次运行一个事务边界」，而不是给每个
        工具另开 Session 把事务边界打散。

        **兜底**：deepagents 的 ToolNode 默认会把工具异常直接抛出去，整轮 run 就
        死了；而我们的领域错误（找不到、状态非法）在设计上是预期内的——模型拿过期
        id 来查是常事。所以这里统一转成错误对象，让模型看到后自己纠正。

        参数是无参可调用而不是 (fn, *args)：这样连**构造参数**时的异常（比如
        uuid.UUID("不是 uuid")）也一并兜住，不然模型编个坏 id 就能掀翻整轮。
        """
        try:
            with self.lock:
                return call()
        except Exception as exc:
            return self.error_result(exc)

    def error_result(self, exc: Exception) -> dict[str, str]:
        """领域子类可以覆写错误形状；通用默认行为保持不变。"""
        return {"error": type(exc).__name__, "message": str(exc)}
