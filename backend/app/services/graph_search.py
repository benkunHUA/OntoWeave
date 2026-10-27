"""已发布图的向量召回与类型层详情。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import Graph, Property
from app.services import graph
from app.services.embedding import Embedder, embedder_model_name, ensure_embedding_dim


def search_graphs(
    session: Session,
    *,
    owner_id: uuid.UUID,
    query: str,
    embedder: Embedder,
    limit: int = 5,
) -> list[dict[str, Any]]:
    ensure_embedding_dim(embedder)
    vector = embedder.embed([query])[0]
    distance = Graph.graph_embedding.cosine_distance(vector)
    rows = session.execute(
        select(Graph, distance.label("distance"))
        .where(
            Graph.owner_id == owner_id,
            Graph.published_at.isnot(None),
            Graph.graph_embedding.isnot(None),
            Graph.graph_embedding_model == embedder_model_name(embedder),
        )
        .order_by(distance)
        .limit(limit)
    )
    return [
        {
            "graph_id": row.id,
            "name": row.name,
            "similarity": 1.0 - float(value),
            "graph_card": row.graph_card,
        }
        for row, value in rows
    ]


def get_graph_detail(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID
) -> dict[str, Any]:
    row = graph.get_graph(session, graph_id, owner_id=owner_id)
    if row.published_at is None:
        raise NotFoundError(f"published graph {graph_id} not found")
    snapshot = graph.get_graph_snapshot(session, graph_id, owner_id=owner_id)
    object_names = {node.id: node.name for node in snapshot.nodes}
    return {
        "graph_id": str(row.id),
        "name": row.name,
        "published_at": row.published_at.isoformat(),
        "object_types": [
            {
                "id": str(node.id),
                "name": node.name,
                "name_en": node.name_en,
                "description": node.description,
                "properties": [
                    _property_detail(prop) for prop in snapshot.properties.get(node.id, [])
                ],
            }
            for node in snapshot.nodes
        ],
        "relations": [
            {
                "id": str(relation.id),
                "name": relation.name,
                "source_type_id": str(relation.source_type_id),
                "source_type_name": object_names.get(relation.source_type_id),
                "target_type_id": str(relation.target_type_id),
                "target_type_name": object_names.get(relation.target_type_id),
                "cardinality": relation.cardinality,
                "forward_label": relation.forward_label,
                "reverse_label": relation.reverse_label,
            }
            for relation in snapshot.relations
        ],
    }


def _property_detail(prop: Property) -> dict[str, Any]:
    return {
        "id": str(prop.id),
        "name": prop.name,
        "name_en": prop.name_en,
        "datatype": prop.datatype,
        "required": prop.required,
        "is_array": prop.is_array,
        "is_primary_key": prop.is_primary_key,
        "is_title_key": prop.is_title_key,
        "enum_values": prop.enum_values,
    }
