"""本体建模的统一入口（门面）。

对象类型 / 属性 / 关系的读写按元素拆到 `object_type` / `property` / `relation`，
状态机抽到 `status`。这里只做转发，让既有调用方（changeset、element_edit、
agent 工具、API、测试）不必改 import。
"""

from __future__ import annotations

from app.services.object_type import (
    create_object_type,
    get_object_type,
    list_object_types,
    transition_object_type,
)
from app.services.property import (
    create_property,
    get_property,
    list_properties,
    transition_property,
    validate_property_patch,
)
from app.services.relation import (
    create_relation,
    get_relation,
    transition_relation,
    validate_relation_patch,
)
from app.services.status import ALLOWED_TRANSITIONS, APPROVED, CONFIRMED, DRAFT

__all__ = [
    "ALLOWED_TRANSITIONS",
    "APPROVED",
    "CONFIRMED",
    "DRAFT",
    "create_object_type",
    "create_property",
    "create_relation",
    "get_object_type",
    "get_property",
    "get_relation",
    "list_object_types",
    "list_properties",
    "transition_object_type",
    "transition_property",
    "transition_relation",
    "validate_property_patch",
    "validate_relation_patch",
]
