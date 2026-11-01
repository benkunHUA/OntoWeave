"""把同一轮的证据增量折叠成对象实例子图。

关系定义只来自业务图；实例对能拿到就精确连线，拿不到时按关系端点上的
同类型实例补边，不依赖关系实例索引一定可用。
"""

from __future__ import annotations

from typing import Any

from app.schemas.evidence import (
    EvidenceEdge,
    EvidenceGraph,
    EvidenceNode,
    EvidenceObjectType,
    EvidenceRelation,
)

WARNING_TRUNCATED = "truncated"
WARNING_NOT_INDEXED = "relation_not_indexed"
WARNING_RELATION_MISSING = "relation_schema_missing"


def build_evidence_graph(
    deltas: list[dict[str, Any]],
    *,
    turn_id: str | None = None,
) -> EvidenceGraph | None:
    """把同一轮的增量折叠成一张完整实例图。"""
    relevant = deltas_for_turn(deltas, turn_id)
    graph_delta = latest_graph_delta(relevant)
    object_types = object_types_from(graph_delta)
    relations = relations_from(graph_delta)
    relation_by_id = {item.id: item for item in relations}
    nodes: dict[str, EvidenceNode] = {}
    edges: dict[str, EvidenceEdge] = {}
    warnings: set[str] = set()
    inferred_relations: set[str] = set()
    unavailable_relations: set[str] = set()

    for delta in relevant:
        if delta.get("kind") == "objects":
            fold_objects(delta, nodes, warnings)
        elif delta.get("kind") == "traversal":
            fold_traversal(
                delta,
                nodes,
                edges,
                relation_by_id,
                warnings,
                inferred_relations,
                unavailable_relations,
            )

    fold_schema_relations(inferred_relations, relations, nodes, edges)
    if unavailable_relations and any(
        not has_relation_edges(edges, relation_id) for relation_id in unavailable_relations
    ):
        warnings.add(WARNING_NOT_INDEXED)

    if not nodes:
        return None
    return EvidenceGraph(
        graphId=_text(graph_delta.get("graphId")) if graph_delta else None,
        graphName=_text(graph_delta.get("graphName")) if graph_delta else None,
        objectTypes=object_types,
        relations=relations,
        nodes=list(nodes.values()),
        edges=list(edges.values()),
        warnings=sorted(warnings),
    )


def fold_objects(
    delta: dict[str, Any],
    nodes_by_id: dict[str, EvidenceNode],
    warnings: set[str],
) -> None:
    for item in delta.get("items") or []:
        if isinstance(item, dict):
            upsert_node(nodes_by_id, EvidenceNode.model_validate(item))
    if delta.get("truncated"):
        warnings.add(WARNING_TRUNCATED)


def fold_traversal(
    delta: dict[str, Any],
    nodes_by_id: dict[str, EvidenceNode],
    edges: dict[str, EvidenceEdge],
    relations: dict[str, EvidenceRelation],
    warnings: set[str],
    inferred_relations: set[str],
    unavailable_relations: set[str],
) -> None:
    relation_id = _text(delta.get("relation_id"))
    if not relation_id:
        return
    inputs = [node_from_ref(item) for item in delta.get("inputs") or [] if isinstance(item, dict)]
    targets = [
        EvidenceNode.model_validate(item)
        for item in delta.get("items") or []
        if isinstance(item, dict)
    ]
    for item in [*inputs, *targets]:
        upsert_node(nodes_by_id, item)

    relation = relations.get(relation_id)
    if relation is None:
        warnings.add(WARNING_RELATION_MISSING)
    if _text(delta.get("status")) == "NOT_INDEXED":
        unavailable_relations.add(relation_id)
    if delta.get("truncated"):
        warnings.add(WARNING_TRUNCATED)

    rows = [item for item in delta.get("links") or [] if isinstance(item, dict)]
    if rows:
        fold_links(rows, targets, nodes_by_id, edges, relation, relation_id, delta)
        return
    if len(inputs) == 1 and targets:
        for target in targets:
            upsert_edge(edges, relation, relation_id, inputs[0], target)
        return
    # 多起点拿不到精确实例对时，由业务图关系定义补边。
    if (
        relation is not None
        and inputs
        and (relation_id in unavailable_relations or (len(inputs) > 1 and targets))
    ):
        inferred_relations.add(relation_id)


def fold_schema_relations(
    relation_ids: set[str],
    relations: list[EvidenceRelation],
    nodes_by_id: dict[str, EvidenceNode],
    edges: dict[str, EvidenceEdge],
) -> None:
    """按业务图关系端点补边，实例只决定端点节点集合。"""
    relation_by_id = {item.id: item for item in relations}
    nodes_by_type: dict[str, list[EvidenceNode]] = {}
    for item in nodes_by_id.values():
        nodes_by_type.setdefault(item.objectTypeId, []).append(item)

    for relation_id in sorted(relation_ids):
        relation = relation_by_id.get(relation_id)
        if relation is None or has_relation_edges(edges, relation_id):
            continue
        sources = sorted(nodes_by_type.get(relation.sourceTypeId, []), key=lambda item: item.id)
        targets = sorted(nodes_by_type.get(relation.targetTypeId, []), key=lambda item: item.id)
        pairs = [
            (source, target)
            for source in sources
            for target in targets
            if source.id != target.id and shares_primary_key(source, target)
        ]
        # 属性里没有可配对的键时，才退回到关系端点上的类型级补边。
        if not pairs:
            pairs = [
                (source, target)
                for source in sources
                for target in targets
                if source.id != target.id
            ]
        for source, target in pairs:
            upsert_edge(edges, relation, relation_id, source, target)


def shares_primary_key(left: EvidenceNode, right: EvidenceNode) -> bool:
    """实例属性里出现对端主键时，用它代替关系实例索引配对。"""
    right_values = {_text(value) for value in right.properties.values()}
    left_values = {_text(value) for value in left.properties.values()}
    return (
        left.primaryKey == right.primaryKey
        or left.primaryKey in right_values
        or right.primaryKey in left_values
    )


def has_relation_edges(edges: dict[str, EvidenceEdge], relation_id: str) -> bool:
    return any(edge.relationId == relation_id for edge in edges.values())


def fold_links(
    rows: list[dict[str, Any]],
    targets: list[EvidenceNode],
    nodes_by_id: dict[str, EvidenceNode],
    edges: dict[str, EvidenceEdge],
    relation: EvidenceRelation | None,
    relation_id: str,
    delta: dict[str, Any],
) -> None:
    target_by_key = {item.primaryKey: item for item in targets}
    for row in rows:
        source_key = _text(row.get("sourcePrimaryKey"))
        target_key = _text(row.get("targetPrimaryKey"))
        if not source_key or not target_key:
            continue
        source_type = relation.sourceTypeId if relation else source_fallback_type(delta)
        target_type = relation.targetTypeId if relation else target_fallback_type(delta)
        source = node(source_type, source_key)
        target = target_by_key.get(target_key) or node(target_type, target_key)
        upsert_node(nodes_by_id, source)
        upsert_node(nodes_by_id, target)
        upsert_edge(edges, relation, relation_id, source, target)


def upsert_edge(
    edges: dict[str, EvidenceEdge],
    relation: EvidenceRelation | None,
    relation_id: str,
    source: EvidenceNode,
    target: EvidenceNode,
) -> None:
    edge_id = f"{relation_id}:{source.id}->{target.id}"
    if edge_id in edges:
        return
    label = (relation.forwardLabel or relation.name) if relation else relation_id
    edges[edge_id] = EvidenceEdge(
        id=edge_id,
        relationId=relation_id,
        source=source.id,
        target=target.id,
        label=label,
    )


def upsert_node(nodes: dict[str, EvidenceNode], incoming: EvidenceNode) -> None:
    current = nodes.get(incoming.id)
    if current is None:
        nodes[incoming.id] = incoming
        return
    nodes[incoming.id] = current.model_copy(
        update={
            "title": current.title or incoming.title,
            "properties": {**current.properties, **incoming.properties},
        }
    )


def node(object_type_id: str, primary_key: str) -> EvidenceNode:
    return EvidenceNode(
        id=f"{object_type_id}:{primary_key}",
        objectTypeId=object_type_id,
        primaryKey=primary_key,
    )


def node_from_ref(item: dict[str, Any]) -> EvidenceNode:
    return node(
        _text(item.get("object_type_id")) or "",
        _text(item.get("primary_key")) or "",
    )


def source_fallback_type(delta: dict[str, Any]) -> str:
    if _text(delta.get("direction")) == "incoming":
        return _text(delta.get("target_type_id")) or ""
    return first_input_type(delta)


def target_fallback_type(delta: dict[str, Any]) -> str:
    if _text(delta.get("direction")) == "incoming":
        return first_input_type(delta)
    return _text(delta.get("target_type_id")) or ""


def first_input_type(delta: dict[str, Any]) -> str:
    inputs = delta.get("inputs") or []
    if not inputs or not isinstance(inputs[0], dict):
        return ""
    return _text(inputs[0].get("object_type_id")) or ""


def object_types_from(graph_delta: dict[str, Any] | None) -> list[EvidenceObjectType]:
    if not graph_delta:
        return []
    return [
        EvidenceObjectType.model_validate(item)
        for item in graph_delta.get("objectTypes") or []
        if isinstance(item, dict) and item.get("id") and item.get("name")
    ]


def relations_from(graph_delta: dict[str, Any] | None) -> list[EvidenceRelation]:
    if not graph_delta:
        return []
    return [
        EvidenceRelation.model_validate(item)
        for item in graph_delta.get("relations") or []
        if isinstance(item, dict) and item.get("id")
    ]


def latest_graph_delta(deltas: list[dict[str, Any]]) -> dict[str, Any] | None:
    result = None
    for delta in deltas:
        if delta.get("kind") == "graph":
            result = delta
    return result


def deltas_for_turn(deltas: list[dict[str, Any]], turn_id: str | None) -> list[dict[str, Any]]:
    if turn_id is None:
        return deltas
    return [item for item in deltas if item.get("turn_id") == turn_id]


def _text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
