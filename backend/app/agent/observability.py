from __future__ import annotations

import logging
from typing import Any

from app.config import Settings

logger = logging.getLogger("app.agent.tracing")


def build_trace_callback(settings: Settings) -> Any | None:
    """按配置构造 Langfuse 追踪回调；没配 key 就返回 None，链路零负担。

    Langfuse 的 LangChain handler 内部走 `get_client()` 取全局客户端，所以这里
    先用带凭证的参数建一个 `Langfuse` 客户端，把它注册成默认实例，handler 再取到
    同一个。`Langfuse(...)` 以 public_key 做单例，重复构造是安全的。

    这个回调挂到主图的 config 上即可——langgraph 会把父 run 的 callbacks 传给
    子智能体，所以 `view_designer` 内部的模型与工具调用也会被一并记录。
    """
    if not settings.langfuse_configured:
        return None
    try:
        from langfuse import Langfuse
        from langfuse.langchain import CallbackHandler
    except ImportError:  # pragma: no cover - 只在依赖缺失时走到
        logger.warning("已配置 Langfuse 但未安装 langfuse，跳过追踪：pip install langfuse")
        return None

    Langfuse(
        public_key=settings.langfuse_public_key,
        secret_key=settings.langfuse_secret_key,
        base_url=settings.langfuse_base_url,
        environment=settings.langfuse_environment or None,
        release=settings.langfuse_release or None,
    )
    return CallbackHandler()


def trace_config(settings: Settings, *, conversation_id: Any, owner_id: Any) -> dict:
    """给一轮 run 的 langgraph config 补上追踪回调与 trace 属性。

    会话 id 当 session、用户 id 当 user，前端「一次对话」和 Langfuse 的 session
    一一对应，审计时好回看。
    """
    handler = build_trace_callback(settings)
    if handler is None:
        return {}
    return {
        "callbacks": [handler],
        "metadata": {
            "langfuse_session_id": str(conversation_id),
            "langfuse_user_id": str(owner_id),
            "langfuse_trace_name": "ontoweave.turn",
            "langfuse_tags": ["ontoweave", "agent"],
        },
    }
