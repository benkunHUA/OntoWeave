from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ObjectType, Relation
from app.services import embedding, graph, ontology


def _type_brief(obj: ObjectType) -> dict:
    return {
        "id": str(obj.id),
        "name": obj.name,
        "description": obj.description,
        "status": obj.status,
    }


def search_ontology(
    session: Session,
    *,
    query: str,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int = 10,
) -> list[dict]:
    """按语义找已有对象类型。新建之前应该先调这个，避免造重复。"""
    hits = embedding.search_object_types(
        session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
    )
    return [{**_type_brief(obj), "similarity": round(score, 4)} for obj, score in hits]


def list_types(session: Session, *, owner_id: uuid.UUID, limit: int = 50) -> list[dict]:
    return [
        _type_brief(obj)
        for obj in ontology.list_object_types(session, owner_id=owner_id, limit=limit)
    ]


def get_subgraph(
    session: Session,
    *,
    graph_id: uuid.UUID,
    object_type_id: uuid.UUID,
    owner_id: uuid.UUID,
    max_nodes: int = 50,
) -> dict:
    """从给定节点出发，沿图内的关系走一跳，返回周围的节点与关系。"""
    graph.get_graph(session, graph_id, owner_id=owner_id)
    present = set(graph.list_graph_objects(session, graph_id))
    if object_type_id not in present:
        raise NotFoundError(f"object type {object_type_id} is not in graph {graph_id}")

    relation_ids = set(graph.list_graph_relations(session, graph_id))
    relations = (
        list(session.scalars(select(Relation).where(Relation.id.in_(relation_ids))))
        if relation_ids
        else []
    )
    neighbours = {
        rel.target_type_id if rel.source_type_id == object_type_id else rel.source_type_id
        for rel in relations
        if object_type_id in (rel.source_type_id, rel.target_type_id)
    }
    node_ids = sorted({object_type_id, *neighbours}, key=str)[:max_nodes]
    nodes = list(session.scalars(select(ObjectType).where(ObjectType.id.in_(node_ids))))
    connected = [
        rel
        for rel in relations
        if rel.source_type_id in node_ids and rel.target_type_id in node_ids
    ]
    return {
        "nodes": [_type_brief(node) for node in nodes],
        "relations": [
            {
                "id": str(rel.id),
                "name": rel.name,
                "source_type_id": str(rel.source_type_id),
                "target_type_id": str(rel.target_type_id),
                "direction": rel.direction,
            }
            for rel in connected
        ],
    }
