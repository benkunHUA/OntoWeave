"""删图之后，共享池里的对象类型 / 关系 / 动作类型该怎么处理。

DB 层只负责级联：图一删，图成员（graph_object / graph_relation / graph_action）、
改动集、视图状态跟着走。共享池元素是跨图复用的资源，外键是 ON DELETE SET NULL，
删图只会把它们的归属置空 —— 要不要顺手清掉这些共享元素，取决于调用方选的范围。

把「范围」这件事收在这一个模块里，别让它散落到各个调用点：会话、图、将来的
批量清理都只调 `purge_graph_elements`，策略变化只改这里。
"""

from __future__ import annotations

import uuid
from typing import Literal

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.models import ActionType, GraphAction, GraphObject, GraphRelation, ObjectType, Relation

#: 删图时对共享池的处理范围。
#:
#: - ``graph-only``：只删图，共享池全部保留。
#: - ``graph-and-drafts``：额外清掉本图新建的草稿对象 / 关系 / 动作。
#: - ``graph-and-unreferenced``：额外清掉本图新建、没被任何其它图引用的元素。
DeleteScope = Literal["graph-only", "graph-and-drafts", "graph-and-unreferenced"]

DEFAULT_DELETE_SCOPE: DeleteScope = "graph-only"


def purge_graph_elements(
    session: Session,
    *,
    graph_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
    scope: DeleteScope = DEFAULT_DELETE_SCOPE,
) -> None:
    """按 `scope` 清理本图带来的共享元素。调用方负责在此之前删掉图本身。"""
    if scope == "graph-and-drafts":
        _purge_drafts(session, graph_id=graph_id, owner_id=owner_id)
    elif scope == "graph-and-unreferenced":
        _purge_unreferenced(session, graph_id=graph_id, owner_id=owner_id)


def _purge_drafts(session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID | None) -> None:
    """清掉「本图新建、且仍是草稿」的对象类型、关系与动作类型。

    只认 home_graph_id 指向本图的草稿，避免误删别处建出来、被复用进来的草稿。
    删对象类型会级联清掉它的属性、挂在它上面的关系，以及图成员记录；动作类型
    顺带清掉参数、规则、赋值与条件。所以顺序是「关系 → 对象类型 → 动作类型」。
    """
    rel_filters = [Relation.home_graph_id == graph_id, Relation.status == "draft"]
    object_filters = [ObjectType.home_graph_id == graph_id, ObjectType.status == "draft"]
    action_filters = [ActionType.home_graph_id == graph_id, ActionType.status == "draft"]
    if owner_id is not None:
        rel_filters.append(Relation.owner_id == owner_id)
        object_filters.append(ObjectType.owner_id == owner_id)
        action_filters.append(ActionType.owner_id == owner_id)

    session.execute(delete(Relation).where(*rel_filters))
    session.execute(delete(ObjectType).where(*object_filters))
    session.execute(delete(ActionType).where(*action_filters))


def _purge_unreferenced(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID | None
) -> None:
    """清掉「本图新建、且没被任何其它图引用」的对象类型、关系与动作类型。

    「被引用」的口径：别的图的成员表里收了这个元素（graph_object / graph_relation /
    graph_action）。被别处占用的保留，所以已确认元素在这里也照清 —— 这与只清草稿的
    `_purge_drafts` 不同。
    """
    referenced_objects = select(GraphObject.object_type_id).where(GraphObject.graph_id != graph_id)
    referenced_relations = select(GraphRelation.relation_id).where(
        GraphRelation.graph_id != graph_id
    )
    referenced_actions = select(GraphAction.action_type_id).where(GraphAction.graph_id != graph_id)

    rel_filters = [
        Relation.home_graph_id == graph_id,
        Relation.id.not_in(referenced_relations),
    ]
    object_filters = [
        ObjectType.home_graph_id == graph_id,
        ObjectType.id.not_in(referenced_objects),
    ]
    action_filters = [
        ActionType.home_graph_id == graph_id,
        ActionType.id.not_in(referenced_actions),
    ]
    if owner_id is not None:
        rel_filters.append(Relation.owner_id == owner_id)
        object_filters.append(ObjectType.owner_id == owner_id)
        action_filters.append(ActionType.owner_id == owner_id)

    session.execute(delete(Relation).where(*rel_filters))
    session.execute(delete(ObjectType).where(*object_filters))
    session.execute(delete(ActionType).where(*action_filters))
