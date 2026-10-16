"""枚举值这一小片数据的共同规则。

属性的 `enum` 类型和动作的值参数共用同一份枚举语义，校验只写这一处：非空字符串、
不重复、至少两项。规则变了两个域一起变，不会一个严一个松。
"""

from __future__ import annotations

from collections.abc import Iterable

from app.errors import InvariantViolationError

#: 一个枚举至少要给出两个候选值，只有一个值的「枚举」等同于常量。
MIN_ENUM_VALUES = 2


def ensure_valid(values: Iterable[str], *, where: str) -> list[str]:
    """校验并归一（去掉首尾空白），返回入库用的列表。不合法直接抛领域错误。"""
    cleaned = [value.strip() if isinstance(value, str) else value for value in values]
    if len(cleaned) < MIN_ENUM_VALUES:
        raise InvariantViolationError(
            f"{where}: enum needs at least {MIN_ENUM_VALUES} values, got {len(cleaned)}"
        )
    for value in cleaned:
        if not isinstance(value, str) or not value:
            raise InvariantViolationError(f"{where}: enum values must be non-empty strings")
    if len(set(cleaned)) != len(cleaned):
        raise InvariantViolationError(f"{where}: enum values must be unique")
    return cleaned
