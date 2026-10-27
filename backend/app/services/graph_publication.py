"""把业务图投影成可召回的图卡与图向量。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy.orm import Session

from app.errors import InvalidRequestError
from app.models import Property, Relation
from app.services import graph, status
from app.services.embedding import Embedder, embedder_model_name

PUBLISHABLE_STATUSES = frozenset({status.CONFIRMED, status.APPROVED})


@dataclass(frozen=True)
class GraphPublication:
    graph_id: uuid.UUID
    graph_card: str
    embedding_model: str | None
    published_at: datetime


def publish_graph(
    session: Session,
    *,
    graph_id: uuid.UUID,
    owner_id: uuid.UUID,
    embedder: Embedder,
) -> GraphPublication:
    snapshot = graph.get_graph_snapshot(session, graph_id, owner_id=owner_id)
    _validate_publishable(snapshot)
    card = render_graph_card(snapshot)
    vector = embedder.embed([card])[0]
    model = embedder_model_name(embedder)
    published_at = datetime.now(UTC)
    snapshot.graph.graph_card = card
    snapshot.graph.graph_embedding = vector
    snapshot.graph.graph_embedding_model = model
    snapshot.graph.published_at = published_at
    session.flush()
    return GraphPublication(
        graph_id=graph_id,
        graph_card=card,
        embedding_model=snapshot.graph.graph_embedding_model,
        published_at=published_at,
    )


def get_publication(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID
) -> GraphPublication:
    row = graph.get_graph(session, graph_id, owner_id=owner_id)
    if row.graph_card is None or row.published_at is None:
        raise InvalidRequestError("graph has not been published")
    return GraphPublication(
        graph_id=row.id,
        graph_card=row.graph_card,
        embedding_model=row.graph_embedding_model,
        published_at=row.published_at,
    )


def render_graph_card(snapshot: graph.GraphSnapshot) -> str:
    graph_name = snapshot.graph.name or "未命名业务图"
    lines = [f"图：{graph_name}", "", "对象类型："]
    for node in snapshot.nodes:
        properties = snapshot.properties.get(node.id, [])
        primary = _first(properties, "is_primary_key")
        title = _first(properties, "is_title_key")
        key_parts = []
        if primary is not None:
            key_parts.append(f"主键={primary.name}")
        if title is not None:
            key_parts.append(f"标题={title.name}")
        key_text = f"；{'，'.join(key_parts)}" if key_parts else ""
        property_text = "、".join(_property_label(item) for item in properties) or "无属性"
        lines.append(
            f"- {node.name}（{node.name_en or '—'}，id={node.id}）：{property_text}{key_text}"
        )

    lines.extend(["", "关系："])
    object_names = {node.id: node.name for node in snapshot.nodes}
    if not snapshot.relations:
        lines.append("- 无")
    for relation in snapshot.relations:
        source = object_names.get(relation.source_type_id, str(relation.source_type_id))
        target = object_names.get(relation.target_type_id, str(relation.target_type_id))
        label = relation.forward_label or relation.name
        lines.append(
            f"- {relation.name}（{relation.id}）：{source} -> {target}，"
            f"{relation.cardinality}，{label}"
        )
    return "\n".join(lines)


def _validate_publishable(snapshot: graph.GraphSnapshot) -> None:
    if not snapshot.nodes:
        raise InvalidRequestError("graph must contain at least one object type")
    invalid_nodes = [
        item.name for item in snapshot.nodes if item.status not in PUBLISHABLE_STATUSES
    ]
    invalid_properties = [
        prop.name
        for props in snapshot.properties.values()
        for prop in props
        if prop.status not in PUBLISHABLE_STATUSES
    ]
    invalid_relations = [
        item.name for item in snapshot.relations if item.status not in PUBLISHABLE_STATUSES
    ]
    if invalid_nodes or invalid_properties or invalid_relations:
        labels = [*invalid_nodes, *invalid_properties, *invalid_relations]
        raise InvalidRequestError(
            "graph contains unpublished elements: " + "、".join(sorted(set(labels)))
        )


def _first(properties: list[Property], flag: str) -> Property | None:
    return next((item for item in properties if getattr(item, flag)), None)


def _property_label(prop: Property) -> str:
    return f"{prop.name}({prop.datatype})"


def relation_endpoints(relation: Relation) -> tuple[uuid.UUID, uuid.UUID]:
    return relation.source_type_id, relation.target_type_id
