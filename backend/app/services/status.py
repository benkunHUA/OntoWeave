"""本体元素的共享状态机：草案 → 已确认 → 已批准。

对象类型、属性、关系（以及后续的动作类型）共用同一套状态与转移规则，
集中在这里，各领域模块只引用，不再各写一份。
"""

from __future__ import annotations

from app.errors import InvalidTransitionError

DRAFT = "draft"
CONFIRMED = "confirmed"
APPROVED = "approved"

ALLOWED_TRANSITIONS: dict[str, set[str]] = {
    DRAFT: {CONFIRMED},
    CONFIRMED: {APPROVED},
    APPROVED: set(),
}


def ensure_transition(current: str, target: str, subject: str) -> None:
    """校验一次状态流转是否合法；非法就抛，subject 用于报错定位。"""
    if target not in ALLOWED_TRANSITIONS.get(current, set()):
        raise InvalidTransitionError(f"{subject} cannot move {current} -> {target}")
