from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.errors import InvariantViolationError, NotFoundError
from app.models import Graph, GraphObject, GraphRelation, ObjectType, Property, Relation


@dataclass(frozen=True)
class GraphSnapshot:
    graph: Graph
    nodes: list[ObjectType]
    relations: list[Relation]
    properties: dict[uuid.UUID, list[Property]]


def create_graph(session: Session, *, owner_id: uuid.UUID, name: str | None = None) -> Graph:
    g = Graph(owner_id=owner_id, name=name)
    session.add(g)
    session.flush()
    return g


def get_graph(session: Session, graph_id: uuid.UUID, *, owner_id: uuid.UUID | None = None) -> Graph:
    """owner_id 给了就顺带校验归属；不匹配时同样报 not found，不泄露资源是否存在。"""
    g = session.get(Graph, graph_id)
    if g is None or (owner_id is not None and g.owner_id != owner_id):
        raise NotFoundError(f"graph {graph_id} not found")
    return g


def add_object_to_graph(
    session: Session,
    graph_id: uuid.UUID,
    object_type_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    get_graph(session, graph_id, owner_id=owner_id)
    if session.get(ObjectType, object_type_id) is None:
        raise NotFoundError(f"object type {object_type_id} not found")
    if session.get(GraphObject, (graph_id, object_type_id)) is not None:
        return
    session.add(GraphObject(graph_id=graph_id, object_type_id=object_type_id))
    session.flush()


def list_graph_objects(session: Session, graph_id: uuid.UUID) -> list[uuid.UUID]:
    rows = session.scalars(
        select(GraphObject.object_type_id)
        .where(GraphObject.graph_id == graph_id)
        .order_by(GraphObject.added_at, GraphObject.object_type_id)
    )
    return list(rows)


def add_relation_to_graph(
    session: Session,
    graph_id: uuid.UUID,
    relation_id: uuid.UUID,
    *,
    owner_id: uuid.UUID | None = None,
) -> None:
    get_graph(session, graph_id, owner_id=owner_id)
    rel = session.get(Relation, relation_id)
    if rel is None:
        raise NotFoundError(f"relation {relation_id} not found")

    present = set(list_graph_objects(session, graph_id))
    missing = {rel.source_type_id, rel.target_type_id} - present
    if missing:
        raise InvariantViolationError(
            "relation endpoints must be in the graph before the relation: "
            + ", ".join(sorted(str(m) for m in missing))
        )

    if session.get(GraphRelation, (graph_id, relation_id)) is not None:
        return
    session.add(GraphRelation(graph_id=graph_id, relation_id=relation_id))
    session.flush()


def list_graph_relations(session: Session, graph_id: uuid.UUID) -> list[uuid.UUID]:
    rows = session.scalars(
        select(GraphRelation.relation_id)
        .where(GraphRelation.graph_id == graph_id)
        .order_by(GraphRelation.added_at, GraphRelation.relation_id)
    )
    return list(rows)


def get_graph_snapshot(
    session: Session, graph_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> GraphSnapshot:
    """一次取齐画布需要的本体快照，包含草稿和待删除元素。"""
    graph = get_graph(session, graph_id, owner_id=owner_id)
    object_ids = list_graph_objects(session, graph_id)
    relation_ids = list_graph_relations(session, graph_id)

    nodes = (
        list(
            session.scalars(
                select(ObjectType)
                .where(ObjectType.id.in_(object_ids))
                .order_by(ObjectType.name, ObjectType.id)
            )
        )
        if object_ids
        else []
    )
    relations = (
        list(
            session.scalars(
                select(Relation)
                .where(Relation.id.in_(relation_ids))
                .order_by(Relation.name, Relation.id)
            )
        )
        if relation_ids
        else []
    )
    properties: dict[uuid.UUID, list[Property]] = {object_id: [] for object_id in object_ids}
    if object_ids:
        rows = session.scalars(
            select(Property)
            .where(Property.owner_type_id.in_(object_ids))
            .order_by(Property.owner_type_id, Property.name, Property.id)
        )
        for prop in rows:
            properties.setdefault(prop.owner_type_id, []).append(prop)

    return GraphSnapshot(
        graph=graph,
        nodes=nodes,
        relations=relations,
        properties=properties,
    )


def remove_object_from_graph(
    session: Session, graph_id: uuid.UUID, object_type_id: uuid.UUID
) -> None:
    """把对象移出图时，必须同时清掉引用它的图关系，否则会留下悬空边。"""
    get_graph(session, graph_id)
    relations = session.scalars(
        select(Relation).where(
            (Relation.source_type_id == object_type_id)
            | (Relation.target_type_id == object_type_id)
        )
    ).all()
    for rel in relations:
        session.execute(
            delete(GraphRelation).where(
                GraphRelation.graph_id == graph_id,
                GraphRelation.relation_id == rel.id,
            )
        )
    session.execute(
        delete(GraphObject).where(
            GraphObject.graph_id == graph_id,
            GraphObject.object_type_id == object_type_id,
        )
    )
    session.flush()
