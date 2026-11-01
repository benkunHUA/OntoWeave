"""执行时才能确定的值：当前时间与自动生成的主键。"""

from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Any

from app.models import Property
from app.vocabulary import PropertyDatatype


def current_time_value(datatype: str, *, now: datetime | None = None) -> str:
    """按目标属性类型返回当前时间；日期只取日期部分。"""
    current = now or datetime.now(UTC)
    if datatype == PropertyDatatype.DATE.value:
        return current.date().isoformat()
    if datatype == PropertyDatatype.TIMESTAMP.value:
        return current.isoformat().replace("+00:00", "Z")
    raise ValueError(f"current_time cannot fill datatype {datatype!r}")


def auto_primary_key_value(
    primary: Property,
    *,
    execution_key: str,
    rule_id: uuid.UUID,
) -> Any:
    """按执行身份确定性地生成主键：同一次工具调用重试拿到同一个值。"""
    seed = f"ontoweave:action-auto-key:{execution_key}:{rule_id}:{primary.id}"
    generated = uuid.uuid5(uuid.NAMESPACE_URL, seed)
    if primary.datatype == PropertyDatatype.TEXT.value:
        return str(generated)
    if primary.datatype in (
        PropertyDatatype.INTEGER.value,
        PropertyDatatype.DECIMAL.value,
    ):
        return generated.int % 10**18 or 1
    raise ValueError(f"auto_generate cannot fill datatype {primary.datatype!r}")


__all__ = ["auto_primary_key_value", "current_time_value"]
