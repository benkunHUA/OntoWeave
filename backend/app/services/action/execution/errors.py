"""行动执行的结构化错误码。"""

from __future__ import annotations

from typing import Any

from app.errors import InvalidRequestError


class ActionExecutionError(InvalidRequestError):
    """带稳定错误码的执行错误；工具层据此返回可纠正的结构化结果。"""

    def __init__(
        self,
        code: str,
        message: str,
        *,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.details = details or {}
