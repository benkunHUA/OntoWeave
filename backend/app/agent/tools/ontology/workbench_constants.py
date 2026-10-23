"""映射工作台 Agent 工具的固定文案、默认值与错误码。"""

from __future__ import annotations

DATASET_LIMIT_DEFAULT = 20
DATASET_LIMIT_MAX = 50
RUN_LIMIT_DEFAULT = 20
PREVIEW_LIMIT_DEFAULT = 20

REVISION_CONFLICT = "REVISION_CONFLICT"
SUBJECT_MISMATCH = "SUBJECT_MISMATCH"
MAPPING_INVALID = "MAPPING_INVALID"
NOT_FOUND = "NOT_FOUND"
INVALID_ARGUMENT = "INVALID_ARGUMENT"
DATA_SERVICE_UNAVAILABLE = "DATA_SERVICE_UNAVAILABLE"

CONFIRM_ANSWERS = frozenset({"确认", "是", "同意", "继续", "yes", "true", "approve", "approved"})


def subject_mismatch() -> dict[str, str]:
    return {
        "error": SUBJECT_MISMATCH,
        "message": "只能操作当前会话绑定的对象类型或关系",
    }


def interrupted(action: str) -> dict[str, object]:
    return {
        "interrupted": True,
        "action": action,
        "message": "用户未确认，未执行操作",
    }
