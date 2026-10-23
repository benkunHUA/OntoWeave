"""对象与关系映射的确定性匹配算法。"""

from __future__ import annotations

import uuid
from difflib import SequenceMatcher
from typing import Any

from sqlalchemy.orm import Session

from app.errors import InvalidRequestError
from app.services.ontology.common import (
    camel_key,
    mapping_value,
    primary_key_property,
    properties,
)
from app.services.relation import get_relation


def suggest_object_mapping(
    session: Session,
    *,
    object_type_id: uuid.UUID,
    columns: list[dict[str, Any]],
    current: dict[str, Any] | None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    rows = properties(session, object_type_id)
    by_id = {str(item["id"]): item for item in rows}
    columns_by_name = {column_name(item): item for item in columns}
    current = current or {}
    current_rows = current.get("attributes") or []
    mapped: dict[str, str] = {}
    reasons: list[dict[str, Any]] = []
    used: set[str] = set()
    for row in current_rows:
        attribute_id = str(mapping_value(row, "attribute_id", "attributeId"))
        current_column = str(mapping_value(row, "column", "column") or "")
        prop = by_id.get(attribute_id)
        if prop is None or current_column in used or prop["is_array"]:
            continue
        if not type_compatible(prop["datatype"], columns_by_name.get(current_column)):
            continue
        mapped[attribute_id] = current_column
        used.add(current_column)
    for prop in rows:
        attribute_id = str(prop["id"])
        if attribute_id in mapped or prop["is_array"]:
            continue
        candidates = [
            item
            for item in columns
            if column_name(item) not in used and type_compatible(prop["datatype"], item)
        ]
        if not candidates:
            continue
        selected = max(
            candidates,
            key=lambda item: (name_similarity(prop["name"], column_name(item)), column_name(item)),
        )
        selected_column = column_name(selected)
        score = name_similarity(prop["name"], selected_column)
        mapped[attribute_id] = selected_column
        used.add(selected_column)
        reasons.append(
            {
                "attribute_id": prop["id"],
                "column": selected_column,
                "reason": reason(prop["name"], selected_column, score),
                "score": round(score, 3),
            }
        )
    primary = pick_key(rows, mapped, current, "primary_key_attribute_id", ("id", "key", "pk"))
    if primary is None:
        raise InvalidRequestError("没有可用于主键的类型兼容列")
    title = pick_key(
        rows, mapped, current, "title_key_attribute_id", ("name", "title", "label", "email")
    )
    attributes = [
        attribute_mapping(by_id[attribute_id], column)
        for attribute_id, column in mapped.items()
        if attribute_id in by_id
    ]
    mapping: dict[str, Any] = {
        "primary_key_attribute_id": uuid.UUID(primary),
        "title_key_attribute_id": uuid.UUID(title) if title else None,
        "attributes": attributes,
    }
    return mapping, reasons


def suggest_relation_mapping(
    session: Session,
    *,
    relation_id: uuid.UUID,
    columns: list[dict[str, Any]],
    current: dict[str, Any] | None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    relation = get_relation(session, relation_id)
    source_pk = primary_key_property(session, relation.source_type_id)
    target_pk = primary_key_property(session, relation.target_type_id)
    if source_pk is None or target_pk is None:
        raise InvalidRequestError("关系两端都必须先设置主键")
    current = current or {}
    from_column = str(mapping_value(current, "from_key_column", "fromKeyColumn") or "")
    to_column = str(mapping_value(current, "to_key_column", "toKeyColumn") or "")
    names = {column_name(item): item for item in columns}
    if not valid_relation_column(from_column, source_pk.datatype, names, set()):
        from_column = ""
    if not valid_relation_column(to_column, target_pk.datatype, names, {from_column}):
        to_column = ""
    if from_column:
        from_score = name_similarity(source_pk.name, from_column)
    else:
        from_column, from_score = pick_relation_column(
            columns, source_pk.name, source_pk.datatype, used={to_column} if to_column else set()
        )
    if to_column:
        to_score = name_similarity(target_pk.name, to_column)
    else:
        to_column, to_score = pick_relation_column(
            columns,
            target_pk.name,
            target_pk.datatype,
            used={from_column} if from_column else set(),
        )
    reasons = [
        {
            "attribute_id": source_pk.id,
            "column": from_column or "",
            "reason": reason(source_pk.name, from_column or "", from_score),
            "score": round(from_score, 3),
        },
        {
            "attribute_id": target_pk.id,
            "column": to_column or "",
            "reason": reason(target_pk.name, to_column or "", to_score),
            "score": round(to_score, 3),
        },
    ]
    mapping = {
        "from_key_column": from_column,
        "from_key_datatype": source_pk.datatype,
        "to_key_column": to_column,
        "to_key_datatype": target_pk.datatype,
    }
    return mapping, reasons


def attribute_mapping(prop: dict[str, Any], column: str) -> dict[str, Any]:
    return {
        "attribute_id": prop["id"],
        "column": column,
        "datatype": prop["datatype"],
        "required": prop["required"],
        "is_array": prop["is_array"],
        "enum_values": prop["enum_values"],
    }


def pick_key(
    rows: list[dict[str, Any]],
    mapped: dict[str, str],
    current: dict[str, Any],
    field: str,
    hints: tuple[str, ...],
) -> str | None:
    current_id = mapping_value(current, field, camel_key(field))
    if current_id is not None and str(current_id) in mapped:
        return str(current_id)
    flag = "is_primary_key" if field == "primary_key_attribute_id" else "is_title_key"
    flagged = [item for item in rows if item.get(flag) and str(item["id"]) in mapped]
    if flagged:
        return str(flagged[0]["id"])
    hinted = [item for item in rows if str(item["id"]) in mapped and has_hint(item["name"], hints)]
    if hinted:
        return str(hinted[0]["id"])
    return None


def has_hint(name: str, hints: tuple[str, ...]) -> bool:
    tokens = set(tokenize(name))
    return bool(tokens & set(hints)) or any(hint in normalize_name(name) for hint in hints)


def pick_relation_column(
    columns: list[dict[str, Any]], name: str, datatype: str, *, used: set[str]
) -> tuple[str, float]:
    candidates = [
        item
        for item in columns
        if column_name(item) not in used and type_compatible(datatype, item)
    ]
    if not candidates:
        return "", 0.0
    selected = max(
        candidates,
        key=lambda item: (name_similarity(name, column_name(item)), column_name(item)),
    )
    return column_name(selected), name_similarity(name, column_name(selected))


def valid_relation_column(
    column: str, datatype: str, columns: dict[str, dict[str, Any]], used: set[str]
) -> bool:
    return bool(column) and column not in used and type_compatible(datatype, columns.get(column))


def column_name(column: dict[str, Any]) -> str:
    return str(column.get("name") or "")


def column_type(column: dict[str, Any] | None) -> str:
    if column is None:
        return ""
    return str(column.get("type") or "").split("(", 1)[0].strip().lower()


def type_compatible(datatype: str, column: dict[str, Any] | None) -> bool:
    expected = str(datatype).lower()
    actual = column_type(column)
    allowed = {
        "text": {"string", "uuid", "varchar", "char", "text"},
        "integer": {"int", "integer", "long", "bigint", "smallint"},
        "decimal": {"decimal", "numeric", "int", "integer", "long", "bigint"},
        "boolean": {"boolean", "bool"},
        "date": {"date"},
        "timestamp": {"timestamp", "timestamptz", "datetime"},
        "enum": {"string", "varchar", "char", "text"},
    }
    return actual in allowed.get(expected, set())


def name_similarity(left: str, right: str) -> float:
    first = normalize_name(left)
    second = normalize_name(right)
    if not first or not second:
        return 0.0
    if first == second:
        return 1.0
    if first in second or second in first:
        return 0.8
    shared = set(tokenize(left)) & set(tokenize(right))
    if shared:
        return min(0.9, 0.6 + 0.1 * len(shared))
    return round(SequenceMatcher(None, first, second).ratio() * 0.65, 3)


def reason(left: str, right: str, score: float) -> str:
    if not right:
        return "没有类型兼容的剩余列"
    if score >= 0.99:
        return "名称完全一致且类型兼容"
    if score >= 0.6:
        return "名称相近且类型兼容"
    return "类型兼容"


def normalize_name(value: str) -> str:
    return "".join(char.lower() for char in value if char.isalnum())


def tokenize(value: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    for index, char in enumerate(value):
        if not char.isalnum():
            if current:
                parts.append("".join(current).lower())
                current = []
            continue
        if char.isupper() and current and value[index - 1].islower():
            parts.append("".join(current).lower())
            current = []
        current.append(char)
    if current:
        parts.append("".join(current).lower())
    return parts


__all__ = ["suggest_object_mapping", "suggest_relation_mapping"]
