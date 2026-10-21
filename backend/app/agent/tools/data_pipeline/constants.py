"""数据整理 Agent 工具的共享常量与提示语。"""

from __future__ import annotations

STATE_UNAVAILABLE = "PIPELINE_STATE_UNAVAILABLE"
STATE_UNAVAILABLE_MESSAGE = "当前无法读取流程状态，请先告知用户稍后重试"
REVISION_CONFLICT = "REVISION_CONFLICT"
QUESTION_LIMIT_REACHED = "QUESTION_LIMIT_REACHED"
QUESTION_LIMIT_MESSAGE = "每轮最多问一个问题"

EVENT_LIMIT_DEFAULT = 10
EVENT_LIMIT_MAX = 50
DATASET_LIMIT_DEFAULT = 20
DATASET_LIMIT_MAX = 200
DEFAULT_NODE_POSITION = {"x": 0, "y": 0}
DEFAULT_WRITE_MODE = "snapshot"

CONFIRM_ANSWERS = frozenset({"确认", "是", "同意", "继续", "yes", "true", "approve", "approved"})


def state_unavailable(message: str | None = None) -> dict:
    return {
        "error": STATE_UNAVAILABLE,
        "message": message or STATE_UNAVAILABLE_MESSAGE,
    }
