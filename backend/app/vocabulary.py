"""本体建模共享词表。

属性数据类型、关系基数这类枚举集中在这里，让 models 的 CHECK 约束、service
的校验、agent schema 与前端下拉引用同一份定义，不再各写各的。改集合要配一次迁移。
"""

from __future__ import annotations

from enum import StrEnum


class PropertyDatatype(StrEnum):
    """属性数据类型。取值即入库值，顺序即界面下拉顺序。"""

    TEXT = "text"
    INTEGER = "integer"
    DECIMAL = "decimal"
    BOOLEAN = "boolean"
    DATE = "date"
    TIMESTAMP = "timestamp"


class RelationCardinality(StrEnum):
    """关系基数：从源端看目标端是一对一、一对多还是多对多。"""

    ONE_TO_ONE = "one_to_one"
    ONE_TO_MANY = "one_to_many"
    MANY_TO_MANY = "many_to_many"


PROPERTY_DATATYPES: tuple[str, ...] = tuple(datatype.value for datatype in PropertyDatatype)
RELATION_CARDINALITIES: tuple[str, ...] = tuple(
    cardinality.value for cardinality in RelationCardinality
)

DEFAULT_DATATYPE: str = PropertyDatatype.TEXT.value
DEFAULT_CARDINALITY: str = RelationCardinality.MANY_TO_MANY.value


def sql_in_list(column: str, values: tuple[str, ...]) -> str:
    """把枚举渲染成 CHECK 约束里的 `col IN ('a', 'b')` 片段。"""
    rendered = ", ".join(f"'{value}'" for value in values)
    return f"{column} IN ({rendered})"
