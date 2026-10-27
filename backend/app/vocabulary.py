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
    #: 受控取值：候选值存在使用方自己的 enum_values 里（属性、动作参数各存一份）。
    ENUM = "enum"


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


class ConversationKind(StrEnum):
    """会话资源类型。建模会话的 resource_id 指向 graph；数据连接会话指向连接资源。"""

    MODELING = "modeling"
    DATA_CONNECTION = "data_connection"
    DATA_PIPELINE = "data_pipeline"
    ONTOLOGY_WORKBENCH = "ontology_workbench"
    QUERY = "query"


CONVERSATION_KINDS: tuple[str, ...] = tuple(kind.value for kind in ConversationKind)


class ActionParameterKind(StrEnum):
    """动作参数的两种形态：值参数（复用属性数据类型）与对象引用参数。"""

    VALUE = "value"
    OBJECT_REF = "object_ref"


class ActionRuleKind(StrEnum):
    """规则种类：对象增删改 + 链接增删。"""

    CREATE_OBJECT = "create_object"
    MODIFY_OBJECT = "modify_object"
    DELETE_OBJECT = "delete_object"
    CREATE_LINK = "create_link"
    DELETE_LINK = "delete_link"


class ActionValueSource(StrEnum):
    """赋值 / 条件操作数里的值从哪来。"""

    PARAMETER = "parameter"
    OBJECT_PROPERTY = "object_property"
    STATIC = "static"


class ActionCriteriaNodeKind(StrEnum):
    """提交校验的节点：分组（and / or）或叶子条件。"""

    GROUP = "group"
    CONDITION = "condition"


class ActionCriteriaLogic(StrEnum):
    """分组逻辑。v1 只放行 and；or 的结构已就位，留到下一版。"""

    AND = "and"


class ActionCriteriaOperator(StrEnum):
    """条件运算符。matches / contains 留到下一版。"""

    IS = "is"
    IS_NOT = "is not"
    IS_NULL = "is null"
    IS_NOT_NULL = "is not null"
    LT = "<"
    LTE = "<="
    GT = ">"
    GTE = ">="


class ActionCriteriaSide(StrEnum):
    """条件的两侧。左右共用一张操作数表，靠这一列区分。"""

    LEFT = "left"
    RIGHT = "right"


class ActionOperandKind(StrEnum):
    """操作数的取值形态。`null` 表示字面空值，v1 用不到（判空走「无右操作数」）。"""

    PARAMETER = "parameter"
    OBJECT_PROPERTY = "object_property"
    STATIC = "static"
    NULL = "null"


ACTION_PARAMETER_KINDS: tuple[str, ...] = tuple(kind.value for kind in ActionParameterKind)
ACTION_RULE_KINDS: tuple[str, ...] = tuple(kind.value for kind in ActionRuleKind)
ACTION_VALUE_SOURCES: tuple[str, ...] = tuple(source.value for source in ActionValueSource)
ACTION_CRITERIA_NODE_KINDS: tuple[str, ...] = tuple(kind.value for kind in ActionCriteriaNodeKind)
ACTION_CRITERIA_LOGICS: tuple[str, ...] = tuple(logic.value for logic in ActionCriteriaLogic)
ACTION_CRITERIA_OPERATORS: tuple[str, ...] = tuple(op.value for op in ActionCriteriaOperator)
ACTION_CRITERIA_SIDES: tuple[str, ...] = tuple(side.value for side in ActionCriteriaSide)
ACTION_OPERAND_KINDS: tuple[str, ...] = tuple(kind.value for kind in ActionOperandKind)

#: 能比大小的数据类型；其余类型只能用等值 / 判空。
ORDERED_DATATYPES: tuple[str, ...] = (
    PropertyDatatype.INTEGER.value,
    PropertyDatatype.DECIMAL.value,
    PropertyDatatype.DATE.value,
    PropertyDatatype.TIMESTAMP.value,
)

_EQUALITY_OPERATORS: tuple[str, ...] = (
    ActionCriteriaOperator.IS.value,
    ActionCriteriaOperator.IS_NOT.value,
    ActionCriteriaOperator.IS_NULL.value,
    ActionCriteriaOperator.IS_NOT_NULL.value,
)
_ORDERING_OPERATORS: tuple[str, ...] = (
    ActionCriteriaOperator.LT.value,
    ActionCriteriaOperator.LTE.value,
    ActionCriteriaOperator.GT.value,
    ActionCriteriaOperator.GTE.value,
)

#: 只需要左操作数的运算符。
UNARY_OPERATORS: tuple[str, ...] = (
    ActionCriteriaOperator.IS_NULL.value,
    ActionCriteriaOperator.IS_NOT_NULL.value,
)


def criteria_operators_for(datatype: str) -> tuple[str, ...]:
    """某种数据类型能用的运算符：等值与判空全类型可用，比大小只给有序类型。"""
    if datatype in ORDERED_DATATYPES:
        return _EQUALITY_OPERATORS + _ORDERING_OPERATORS
    return _EQUALITY_OPERATORS


class ViewLayout(StrEnum):
    """声明式视图的布局标识。取值即前端算布局用的 kind，顺序即界面下拉顺序。"""

    HIERARCHICAL = "hierarchical"
    FORCE = "force"
    DOMAIN_GROUPED = "domain-grouped"
    GRID = "grid"


VIEW_LAYOUTS: tuple[str, ...] = tuple(layout.value for layout in ViewLayout)


def sql_in_list(column: str, values: tuple[str, ...]) -> str:
    """把枚举渲染成 CHECK 约束里的 `col IN ('a', 'b')` 片段。"""
    rendered = ", ".join(f"'{value}'" for value in values)
    return f"{column} IN ({rendered})"
