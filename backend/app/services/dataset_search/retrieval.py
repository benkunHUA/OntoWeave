"""按当前本体定义召回可绑定数据集，并用确定性规则复核主键/必填/类型。"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import DatasetSearchIndex, ObjectType, Property, Relation
from app.services.dataset_search.documents import TEMPLATE_VERSION, embedding_model_name
from app.services.ontology.common import primary_key_property, properties, require_subject
from app.services.ontology.matching import (
    compatible_column_types,
    name_similarity,
    type_compatible,
)

RETRIEVAL_LIMIT = 50
NEAR_MISS_LIMIT = 5


@dataclass(frozen=True)
class Requirement:
    label: str
    name: str
    name_en: str | None
    datatype: str
    required: bool
    is_array: bool
    is_primary_key: bool
    is_title_key: bool


@dataclass(frozen=True)
class SubjectQuery:
    subject_kind: str
    subject_id: uuid.UUID
    name: str
    name_en: str | None
    query_text: str
    requirements: tuple[Requirement, ...]


def find_bindable_datasets(
    session: Session,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
    embedder: Any,
    limit: int = 10,
) -> dict[str, Any]:
    subject = load_subject_query(
        session,
        owner_id=owner_id,
        subject_kind=subject_kind,
        subject_id=subject_id,
    )
    vector = embedder.embed([subject.query_text])[0]
    safe_limit = max(1, min(limit, 20))
    allowed_types = sorted(
        {
            column_type
            for item in subject.requirements
            for column_type in compatible_column_types(item.datatype)
        }
    )
    rows = _vector_rows(
        session,
        owner_id=owner_id,
        embedder=embedder,
        vector=vector,
        allowed_types=allowed_types,
        limit=RETRIEVAL_LIMIT,
    )
    candidates = [
        _evaluate(row, vector_similarity=similarity, subject=subject) for row, similarity in rows
    ]
    items = sorted(
        [item for item in candidates if item["status"] != "BLOCKED"],
        key=lambda item: (-item["score"], item["name"]),
    )[:safe_limit]

    near_misses: list[dict[str, Any]] = []
    if not items:
        seen = {(item["dataset_kind"], item["version_id"]) for item in candidates}
        near_misses.extend(
            {**item, "reason": _blocked_reason(item)}
            for item in candidates
            if item["status"] == "BLOCKED"
        )
        for row, similarity in _vector_rows(
            session,
            owner_id=owner_id,
            embedder=embedder,
            vector=vector,
            allowed_types=[],
            limit=NEAR_MISS_LIMIT * 2,
        ):
            key = (row.dataset_kind, row.version_id)
            if key in seen or len(near_misses) >= NEAR_MISS_LIMIT:
                continue
            item = _evaluate(row, vector_similarity=similarity, subject=subject)
            if item["status"] == "BLOCKED":
                near_misses.append({**item, "reason": _blocked_reason(item)})
    result: dict[str, Any] = {
        "subject": {
            "kind": subject.subject_kind,
            "id": str(subject.subject_id),
            "name": subject.name,
            "name_en": subject.name_en,
        },
        "items": items,
        "near_misses": near_misses[:NEAR_MISS_LIMIT],
    }
    if not rows:
        result["message"] = "当前没有已建立向量索引的数据集；请等待发布事件完成或执行回填。"
    return result


def load_subject_query(
    session: Session,
    *,
    owner_id: uuid.UUID,
    subject_kind: str,
    subject_id: uuid.UUID,
) -> SubjectQuery:
    kind = require_subject(session, owner_id, subject_kind, subject_id)
    if kind == "object_type":
        obj = session.get(ObjectType, subject_id)
        if obj is None:
            raise ValueError(f"object type {subject_id} not found")
        requirements = tuple(_property_requirements(properties(session, obj.id)))
        return SubjectQuery(
            subject_kind=kind,
            subject_id=obj.id,
            name=obj.name,
            name_en=obj.name_en,
            query_text=_object_text(obj, requirements),
            requirements=requirements,
        )
    relation = session.get(Relation, subject_id)
    if relation is None:
        raise ValueError(f"relation {subject_id} not found")
    source = session.get(ObjectType, relation.source_type_id)
    target = session.get(ObjectType, relation.target_type_id)
    if source is None or target is None:
        raise ValueError("relation endpoints are missing")
    source_key = primary_key_property(session, source.id)
    target_key = primary_key_property(session, target.id)
    requirements = tuple(
        item
        for item in (
            _relation_key_requirement("源主键", source, source_key),
            _relation_key_requirement("目标主键", target, target_key),
        )
        if item is not None
    )
    return SubjectQuery(
        subject_kind=kind,
        subject_id=relation.id,
        name=relation.name,
        name_en=None,
        query_text=_relation_text(relation, source, target, requirements),
        requirements=requirements,
    )


def _vector_rows(
    session: Session,
    *,
    owner_id: uuid.UUID,
    embedder: Any,
    vector: list[float],
    allowed_types: list[str],
    limit: int,
) -> list[tuple[DatasetSearchIndex, float]]:
    distance = DatasetSearchIndex.embedding.cosine_distance(vector)
    stmt = select(DatasetSearchIndex, distance.label("distance")).where(
        DatasetSearchIndex.owner_id == owner_id,
        DatasetSearchIndex.is_current.is_(True),
        DatasetSearchIndex.status == "ready",
        DatasetSearchIndex.embedding.isnot(None),
        DatasetSearchIndex.embedding_model == embedding_model_name(embedder),
        DatasetSearchIndex.template_version == TEMPLATE_VERSION,
    )
    if allowed_types:
        stmt = stmt.where(DatasetSearchIndex.column_types.overlap(allowed_types))
    stmt = stmt.order_by(distance).limit(limit)
    return [(row[0], max(0.0, min(1.0, 1.0 - float(row[1])))) for row in session.execute(stmt)]


def _evaluate(
    row: DatasetSearchIndex, *, vector_similarity: float, subject: SubjectQuery
) -> dict[str, Any]:
    columns = [item for item in row.columns if isinstance(item, dict) and item.get("name")]
    used: set[str] = set()
    matched_columns: list[dict[str, str]] = []
    missing_required: list[str] = []
    warnings: list[str] = []
    primary_key_column: str | None = None
    primary_key_columns: dict[str, str] = {}
    type_only = False
    similarity_total = 0.0

    ordered = sorted(
        subject.requirements,
        key=lambda item: (not item.is_primary_key, not item.required, item.label),
    )
    for requirement in ordered:
        if requirement.is_array:
            warning = f"{requirement.label} 是多值属性，当前数据集绑定不支持"
            warnings.append(warning)
            if requirement.required:
                missing_required.append(requirement.label)
            continue
        matched = _best_column(requirement, columns, used)
        if matched is None:
            similarity_total += 0.0
            if requirement.required or requirement.is_primary_key:
                missing_required.append(requirement.label)
            continue
        column, similarity, only_type = matched
        similarity_total += similarity
        used.add(str(column["name"]))
        matched_columns.append({"attribute": requirement.label, "column": str(column["name"])})
        if requirement.is_primary_key:
            primary_key_columns[requirement.label] = str(column["name"])
            if primary_key_column is None:
                primary_key_column = str(column["name"])
        if only_type:
            type_only = True
            warnings.append(f"{requirement.label} 仅按类型匹配到 {column['name']}，请人工确认")
        if requirement.is_title_key and similarity < 0.6:
            warnings.append(f"标题键 {requirement.label} 的候选列语义不够明确")

    required = [item for item in subject.requirements if item.required or item.is_primary_key]
    matched_required = max(0, len(required) - len(missing_required))
    coverage = matched_required / len(required) if required else 1.0
    name_score = similarity_total / len(subject.requirements) if subject.requirements else 1.0
    score = round(0.55 * vector_similarity + 0.30 * coverage + 0.15 * name_score, 4)
    has_primary = any(item.is_primary_key for item in subject.requirements)
    if has_primary and not primary_key_columns:
        status = "BLOCKED"
    elif missing_required or type_only:
        status = "PARTIAL"
    else:
        status = "READY"
    return {
        "dataset_kind": row.dataset_kind,
        "dataset_id": str(row.dataset_id),
        "version_id": str(row.version_id),
        "name": row.name,
        "version_number": row.version_number,
        "row_count": row.row_count,
        "status": status,
        "score": score,
        "vector_similarity": round(vector_similarity, 4),
        "primary_key_column": primary_key_column,
        "primary_key_columns": primary_key_columns,
        "matched_required": matched_required,
        "required_total": len(required),
        "missing_required": missing_required,
        "matched_columns": matched_columns,
        "warnings": warnings,
    }


def _best_column(
    requirement: Requirement, columns: list[dict[str, Any]], used: set[str]
) -> tuple[dict[str, Any], float, bool] | None:
    candidates = [
        column
        for column in columns
        if str(column["name"]) not in used and type_compatible(requirement.datatype, column)
    ]
    if not candidates:
        return None
    scored = [
        (_requirement_similarity(requirement, str(column["name"])), column) for column in candidates
    ]
    scored.sort(key=lambda item: (-item[0], str(item[1]["name"])))
    similarity, column = scored[0]
    if similarity >= 0.3:
        return column, similarity, False
    if len(candidates) == 1:
        return column, similarity, True
    return None


def _requirement_similarity(requirement: Requirement, column_name: str) -> float:
    values = [name_similarity(requirement.name, column_name)]
    if requirement.name_en:
        values.append(name_similarity(requirement.name_en, column_name))
    return max(values)


def _property_requirements(rows: list[dict[str, Any]]) -> list[Requirement]:
    return [
        Requirement(
            label=str(item["name"]),
            name=str(item["name"]),
            name_en=item.get("name_en"),
            datatype=str(item["datatype"]),
            required=bool(item["required"]),
            is_array=bool(item["is_array"]),
            is_primary_key=bool(item["is_primary_key"]),
            is_title_key=bool(item["is_title_key"]),
        )
        for item in rows
    ]


def _relation_key_requirement(
    label: str, obj: ObjectType, prop: Property | None
) -> Requirement | None:
    if prop is None:
        return None
    return Requirement(
        label=label,
        name=prop.name,
        name_en=prop.name_en or (f"{obj.name_en}_id" if obj.name_en else None),
        datatype=prop.datatype,
        required=True,
        is_array=prop.is_array,
        is_primary_key=True,
        is_title_key=False,
    )


def _object_text(obj: ObjectType, requirements: tuple[Requirement, ...]) -> str:
    lines = [f"对象：{obj.name}"]
    if obj.name_en:
        lines.append(f"英文名：{obj.name_en}")
    if obj.description:
        lines.append(f"描述：{obj.description}")
    lines.append("字段：")
    for item in requirements:
        flags = [item.datatype]
        if item.is_primary_key:
            flags.append("主键")
        if item.is_title_key:
            flags.append("标题键")
        if item.required:
            flags.append("必填")
        english = f" ({item.name_en})" if item.name_en else ""
        lines.append(f"- {item.label}{english}: {'、'.join(flags)}")
    return "\n".join(lines)


def _relation_text(
    relation: Relation,
    source: ObjectType,
    target: ObjectType,
    requirements: tuple[Requirement, ...],
) -> str:
    lines = [
        f"关系：{source.name} {relation.name} {target.name}",
        f"源对象：{source.name}" + (f" ({source.name_en})" if source.name_en else ""),
        f"目标对象：{target.name}" + (f" ({target.name_en})" if target.name_en else ""),
        "需要的键：",
    ]
    for item in requirements:
        english = f" ({item.name_en})" if item.name_en else ""
        lines.append(f"- {item.label}{english}: {item.datatype}")
    return "\n".join(lines)


def _blocked_reason(item: dict[str, Any]) -> str:
    if not item.get("primary_key_columns") and item.get("required_total"):
        return "缺少类型兼容的主键列"
    if item.get("missing_required"):
        return "缺少必填字段：" + "、".join(item["missing_required"])
    return "类型或字段结构不兼容"


__all__ = ["find_bindable_datasets", "load_subject_query"]
