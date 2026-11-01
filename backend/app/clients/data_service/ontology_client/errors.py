"""本体内部接口的专用错误。"""

from __future__ import annotations

from typing import Any

from app.errors import InvariantViolationError


class EditConflict(InvariantViolationError):
    """apply 时版本或对象状态冲突；保留数据服务给出的冲突明细。"""

    def __init__(
        self,
        message: str,
        *,
        code: str = "EDIT_CONFLICT",
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.details = details or {}
        conflicts = self.details.get("conflicts")
        self.conflicts = conflicts if isinstance(conflicts, list) else []


__all__ = ["EditConflict"]
