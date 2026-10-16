"""值本身与数据类型配不配。

属性默认值、动作参数的默认值、赋值与条件里的静态值都走这一份判断——类型学只有一套，
分散开就会出现「接口让过、仓库不让过」这种半截校验。
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date, datetime
from typing import Any

from app.errors import InvariantViolationError
from app.vocabulary import PropertyDatatype


def ensure_value_matches(
    datatype: str,
    value: Any,
    *,
    where: str,
    is_array: bool = False,
    enum_values: Sequence[str] | None = None,
) -> None:
    """值必须能落进这个数据类型；多值属性要求给的是列表。"""
    if is_array:
        if not isinstance(value, list):
            raise InvariantViolationError(f"{where}: expected a list of {datatype}")
        for item in value:
            _ensure_scalar(datatype, item, where=where, enum_values=enum_values)
        return
    _ensure_scalar(datatype, value, where=where, enum_values=enum_values)


def _ensure_scalar(
    datatype: str,
    value: Any,
    *,
    where: str,
    enum_values: Sequence[str] | None = None,
) -> None:
    if datatype == PropertyDatatype.TEXT:
        ok = isinstance(value, str)
    elif datatype == PropertyDatatype.BOOLEAN:
        # bool 是 int 的子类，所以这里不用 type(value) is bool 的说法，直接 isinstance。
        ok = isinstance(value, bool)
    elif datatype == PropertyDatatype.INTEGER:
        ok = isinstance(value, int) and not isinstance(value, bool)
    elif datatype == PropertyDatatype.DECIMAL:
        ok = isinstance(value, (int, float)) and not isinstance(value, bool)
    elif datatype == PropertyDatatype.DATE:
        ok = _is_iso_date(value)
    elif datatype == PropertyDatatype.TIMESTAMP:
        ok = _is_iso_datetime(value)
    elif datatype == PropertyDatatype.ENUM:
        ok = isinstance(value, str) and value in tuple(enum_values or ())
    else:
        raise InvariantViolationError(f"{where}: unsupported datatype {datatype!r}")

    if not ok:
        raise InvariantViolationError(
            f"{where}: value {value!r} does not fit datatype {datatype!r}"
        )


def _is_iso_date(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    try:
        date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _is_iso_datetime(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return False
    return True


def same_enum_values(left: Sequence[str] | None, right: Sequence[str] | None) -> bool:
    """两个枚举的候选值是不是同一套：顺序也要一致，界面按顺序展示。"""
    return list(left or ()) == list(right or ())


__all__ = ["ensure_value_matches", "same_enum_values"]
