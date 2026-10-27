"""数据集候选的确定性复核与打分。"""

from __future__ import annotations

from typing import Any

from app.models import DatasetSearchIndex
from app.services.dataset_search.models import Requirement, SubjectQuery
from app.services.ontology.matching import name_similarity, type_compatible


def evaluate(
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


def blocked_reason(item: dict[str, Any]) -> str:
    if not item.get("primary_key_columns") and item.get("required_total"):
        return "缺少类型兼容的主键列"
    if item.get("missing_required"):
        return "缺少必填字段：" + "、".join(item["missing_required"])
    return "类型或字段结构不兼容"
