"""SQLAlchemy 模型包。

所有模型在这里统一导出，保证 Alembic 只需导入 `app.models` 就能加载完整 metadata；
业务模块继续使用 `from app.models import Xxx`，无需关心具体文件。
"""

from app.models.action import (
    ActionParameter,
    ActionRule,
    ActionRuleAssignment,
    ActionType,
    GraphAction,
)
from app.models.action_criteria import ActionCriteriaNode, ActionCriteriaOperand
from app.models.base import Base
from app.models.changeset import Changeset, ChangesetOp
from app.models.conversation import Conversation, ConversationEvent, ConversationMessage
from app.models.dataset_search import DatasetSearchIndex
from app.models.graph import Graph, GraphObject, GraphRelation, ViewState
from app.models.ontology import ObjectType, Property, Relation

__all__ = [
    "ActionCriteriaNode",
    "ActionCriteriaOperand",
    "ActionParameter",
    "ActionRule",
    "ActionRuleAssignment",
    "ActionType",
    "Base",
    "Changeset",
    "ChangesetOp",
    "Conversation",
    "ConversationEvent",
    "ConversationMessage",
    "DatasetSearchIndex",
    "Graph",
    "GraphAction",
    "GraphObject",
    "GraphRelation",
    "ObjectType",
    "Property",
    "Relation",
    "ViewState",
]
