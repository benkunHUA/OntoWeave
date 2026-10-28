"""查询工具调用的一行可读摘要：让人知道这一步正在查什么。"""

from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy.orm import Session

from app.models import ObjectType, Relation
from app.services.relation import relation_label

_OUTGOING = "outgoing"
_INCOMING = "incoming"


def summarize_query_tool_call(
    session: Session,
    *,
    owner_id: uuid.UUID,
    name: str,
    args: object,
) -> str | None:
    """只处理两个实例查询工具；其它工具返回 None，继续走前端固定标签。"""
    payload = args if isinstance(args, dict) else {}
    if name == "query_objects":
        return _query_objects_summary(session, owner_id=owner_id, args=payload)
    if name == "get_related_objects":
        return _related_objects_summary(session, owner_id=owner_id, args=payload)
    return None


def _query_objects_summary(session: Session, *, owner_id: uuid.UUID, args: dict[str, Any]) -> str:
    object_type = _owned_object_type(session, owner_id=owner_id, raw_id=args.get("object_type_id"))
    label = object_type.name if object_type is not None else "对象"
    primary_keys = args.get("primary_keys")
    if isinstance(primary_keys, list) and primary_keys:
        if len(primary_keys) == 1:
            return f"{label} · 按主键「{_text(primary_keys[0]) or ''}」"
        return f"{label} · 按 {len(primary_keys)} 个主键"

    keyword = _text(args.get("q"))
    if keyword:
        detail = f"搜索「{keyword}」"
    else:
        filters = args.get("filters")
        filter_count = len(filters) if isinstance(filters, list) else 0
        detail = f"按 {filter_count} 个条件筛选" if filter_count else "读取列表"
    limit = _limit(args.get("limit"), default=20)
    return f"{label} · {detail}，最多 {limit} 条"


def _related_objects_summary(session: Session, *, owner_id: uuid.UUID, args: dict[str, Any]) -> str:
    relation = _owned_relation(session, owner_id=owner_id, raw_id=args.get("relation_id"))
    direction = _text(args.get("direction")) or _OUTGOING
    limit = _limit(args.get("limit"), default=50)
    inputs = args.get("inputs")
    starts = len(inputs) if isinstance(inputs, list) else 0
    if relation is None:
        turn = "向外" if direction == _OUTGOING else "向内"
        return f"沿关系{turn}取 {starts} 个起点，最多 {limit} 条"

    source = session.get(ObjectType, relation.source_type_id)
    target = session.get(ObjectType, relation.target_type_id)
    source_name = source.name if source is not None else "源对象"
    target_name = target.name if target is not None else "目标对象"
    if direction == _INCOMING:
        from_name, to_name = target_name, source_name
    else:
        from_name, to_name = source_name, target_name
    edge = relation_label(
        relation,
        direction,
        source_name=source_name,
        target_name=target_name,
    )
    return f"从「{from_name}」沿「{edge}」查「{to_name}」，{starts} 个起点，最多 {limit} 条"


def _owned_object_type(
    session: Session, *, owner_id: uuid.UUID, raw_id: object
) -> ObjectType | None:
    parsed = _uuid(raw_id)
    if parsed is None:
        return None
    item = session.get(ObjectType, parsed)
    return item if item is not None and item.owner_id == owner_id else None


def _owned_relation(session: Session, *, owner_id: uuid.UUID, raw_id: object) -> Relation | None:
    parsed = _uuid(raw_id)
    if parsed is None:
        return None
    item = session.get(Relation, parsed)
    return item if item is not None and item.owner_id == owner_id else None


def _uuid(value: object) -> uuid.UUID | None:
    try:
        return uuid.UUID(str(value))
    except (TypeError, ValueError):
        return None


def _text(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    stripped = value.strip()
    return stripped or None


def _limit(value: object, *, default: int) -> int:
    if isinstance(value, int) and value > 0:
        return value
    return default
