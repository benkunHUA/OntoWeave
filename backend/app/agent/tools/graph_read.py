"""图切片的读取与对外形状。

主智能体的 `get_subgraph` 与绘图子智能体的 `get_ontology_slice` 都要把「图里的节点
+ 它们之间的关系」交给模型，字段必须一致，所以形状只在这里定义一份。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ObjectType, Property, Relation
from app.services import graph


def object_type_brief(obj: ObjectType) -> dict:
    """模型看到的对象类型摘要：够它判断「这是什么」，不带内部字段。"""
    return {
        "id": str(obj.id),
        "name": obj.name,
        "name_en": obj.name_en,
        "description": obj.description,
        "status": obj.status,
    }


def property_brief(prop: Property) -> dict:
    """模型看到的属性摘要：写规则赋值 / 校验条件时要知道属性 id、类型与候选值。"""
    return {
        "id": str(prop.id),
        "name": prop.name,
        "name_en": prop.name_en,
        "datatype": prop.datatype,
        "is_array": prop.is_array,
        "is_primary_key": prop.is_primary_key,
        "is_title_key": prop.is_title_key,
        "enum_values": prop.enum_values,
        "status": prop.status,
    }


def relation_brief(relation: Relation) -> dict:
    return {
        "id": str(relation.id),
        "name": relation.name,
        "source_type_id": str(relation.source_type_id),
        "target_type_id": str(relation.target_type_id),
        "direction": relation.direction,
        "cardinality": relation.cardinality,
        "forward_label": relation.forward_label,
        "reverse_label": relation.reverse_label,
        "status": relation.status,
    }


@dataclass(frozen=True)
class GraphMembership:
    """一张图的成员关系，一次性读出来供切片渲染复用，避免每个工具各查一遍。"""

    node_ids: set[uuid.UUID]
    relations: list[Relation]


def load_membership(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID
) -> GraphMembership:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    relation_ids = graph.list_graph_relations(session, graph_id)
    relations = (
        list(session.scalars(select(Relation).where(Relation.id.in_(relation_ids))))
        if relation_ids
        else []
    )
    return GraphMembership(
        node_ids=set(graph.list_graph_objects(session, graph_id)),
        relations=relations,
    )


def render_slice(
    session: Session,
    *,
    membership: GraphMembership,
    node_ids: set[uuid.UUID] | None = None,
) -> dict:
    """渲染选定节点及其内部关系；node_ids 为 None 表示全图。"""
    if node_ids is None:
        selected = membership.node_ids
    else:
        missing = node_ids - membership.node_ids
        if missing:
            raise NotFoundError(
                "object types are not in this graph: "
                + ", ".join(sorted(str(item) for item in missing))
            )
        selected = node_ids

    nodes = list(
        session.scalars(
            select(ObjectType)
            .where(ObjectType.id.in_(selected))
            .order_by(ObjectType.name, ObjectType.id)
        )
    )
    connected = [
        relation
        for relation in membership.relations
        if relation.source_type_id in selected and relation.target_type_id in selected
    ]
    return {
        "nodes": [object_type_brief(node) for node in nodes],
        "relations": [relation_brief(relation) for relation in connected],
    }
