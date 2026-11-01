"""把查询工具的完整输出转成可持久化的证据增量。"""

from __future__ import annotations

import json
from typing import Any

from app.schemas.evidence import EvidenceNode, EvidenceObjectType, EvidenceRelation

QUERY_EVIDENCE_TOOLS = frozenset({"get_graph_detail", "query_objects", "get_related_objects"})


def evidence_delta_from_tool(
    name: str,
    args: dict[str, Any],
    content: Any,
    *,
    turn_id: str | None,
) -> dict[str, Any] | None:
    """从一个查询工具的完整输出生成证据增量。

    中间件拿到的是截断前的 ToolMessage，所以这里不会丢图详情或大批实例。
    """
    if name not in QUERY_EVIDENCE_TOOLS:
        return None
    payload = json_payload(content)
    if payload is None or payload.get("error"):
        return None
    delta: dict[str, Any] | None
    if name == "get_graph_detail":
        delta = graph_delta(payload)
    elif name == "query_objects":
        delta = objects_delta(args, payload)
    else:
        delta = related_delta(args, payload)
    if delta is None:
        return None
    if turn_id:
        delta["turn_id"] = turn_id
    return delta


def json_payload(content: Any) -> dict[str, Any] | None:
    value = content
    if isinstance(value, list):
        value = "".join(
            part if isinstance(part, str) else str(part.get("text") or "") for part in value
        )
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except (TypeError, ValueError):
            return None
    return value if isinstance(value, dict) else None


def graph_delta(payload: dict[str, Any]) -> dict[str, Any]:
    object_types = [
        EvidenceObjectType(
            id=str(item["id"]),
            name=str(item.get("name") or item["id"]),
            properties=records(item.get("properties")),
        ).model_dump()
        for item in records(payload.get("object_types"))
        if item.get("id")
    ]
    relations = [
        EvidenceRelation(
            id=str(item["id"]),
            name=str(item.get("name") or item["id"]),
            sourceTypeId=str(item.get("source_type_id") or ""),
            targetTypeId=str(item.get("target_type_id") or ""),
            cardinality=str(item.get("cardinality") or ""),
            forwardLabel=text(item.get("forward_label")),
            reverseLabel=text(item.get("reverse_label")),
        ).model_dump()
        for item in records(payload.get("relations"))
        if item.get("id")
    ]
    return {
        "kind": "graph",
        "graphId": text(payload.get("graph_id")),
        "graphName": text(payload.get("name")),
        "objectTypes": object_types,
        "relations": relations,
    }


def objects_delta(args: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    object_type_id = text(payload.get("object_type_id")) or text(args.get("object_type_id"))
    return {
        "kind": "objects",
        "object_type_id": object_type_id,
        "items": [
            node.model_dump()
            for node in nodes(payload.get("items"), fallback_type_id=object_type_id or "")
        ],
        "status": text(payload.get("status")),
        "truncated": bool(payload.get("truncated")),
    }


def related_delta(args: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any] | None:
    relation_id = text(args.get("relation_id")) or text(payload.get("relation_id"))
    if not relation_id:
        return None
    input_refs = refs(args.get("inputs"))
    target_type_id = text(payload.get("target_type_id")) or (
        input_refs[0]["object_type_id"] if input_refs else ""
    )
    return {
        "kind": "traversal",
        "relation_id": relation_id,
        "direction": text(args.get("direction")) or "outgoing",
        "inputs": input_refs,
        "target_type_id": target_type_id,
        "items": [
            node.model_dump()
            for node in nodes(payload.get("items"), fallback_type_id=target_type_id)
        ],
        "links": links(payload.get("links")),
        "status": text(payload.get("status")),
        "truncated": bool(payload.get("truncated")),
    }


def nodes(value: Any, *, fallback_type_id: str = "") -> list[EvidenceNode]:
    result: list[EvidenceNode] = []
    for item in records(value):
        primary_key = text(item.get("primary_key"))
        object_type_id = text(item.get("object_type_id")) or fallback_type_id
        if not primary_key or not object_type_id:
            continue
        properties = item.get("properties")
        result.append(
            EvidenceNode(
                id=f"{object_type_id}:{primary_key}",
                objectTypeId=object_type_id,
                primaryKey=primary_key,
                title=text(item.get("title")),
                properties=properties if isinstance(properties, dict) else {},
            )
        )
    return result


def refs(value: Any) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for item in records(value):
        object_type_id = text(item.get("object_type_id"))
        primary_key = text(item.get("primary_key"))
        if object_type_id and primary_key:
            result.append({"object_type_id": object_type_id, "primary_key": primary_key})
    return result


def links(value: Any) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for item in records(value):
        source = text(item.get("sourcePrimaryKey"))
        target = text(item.get("targetPrimaryKey"))
        if source and target:
            result.append({"sourcePrimaryKey": source, "targetPrimaryKey": target})
    return result


def records(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def text(value: Any) -> str | None:
    if value is None:
        return None
    stripped = str(value).strip()
    return stripped or None
