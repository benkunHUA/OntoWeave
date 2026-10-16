"""视图规格的纯变换。

每个函数只做一件事：拿当前声明式规格，返回改过的规格；不碰数据库、不碰本体。
模型调用它们之前/之后怎么落库、怎么记进改动集，属于工具层的事（见 binding.py）。
"""

from __future__ import annotations

import uuid
from collections.abc import Sequence

from app.errors import InvariantViolationError
from app.vocabulary import VIEW_LAYOUTS


def _copy(spec: dict) -> dict:
    """浅拷贝够用：每个操作都是整块替换某个顶层键，不做嵌套改写。"""
    return dict(spec)


def set_layout(spec: dict, *, kind: str, options: dict | None = None) -> dict:
    if kind not in VIEW_LAYOUTS:
        raise InvariantViolationError(f"unsupported layout kind: {kind!r}")
    result = _copy(spec)
    result["layout"] = {"kind": kind, "options": options or {}}
    return result


def highlight(spec: dict, *, node_ids: Sequence[uuid.UUID | str]) -> dict:
    unique = sorted({str(node_id) for node_id in node_ids})
    others = [entry for entry in spec.get("highlight", []) if entry["style"] != "focus"]
    result = _copy(spec)
    result["highlight"] = [{"nodeIds": unique, "style": "focus"}, *others]
    return result


def dim(spec: dict, *, node_ids: Sequence[uuid.UUID | str]) -> dict:
    result = _copy(spec)
    result["dim"] = sorted({str(node_id) for node_id in node_ids})
    return result


def group(spec: dict, *, group_id: str, label: str, node_ids: Sequence[uuid.UUID | str]) -> dict:
    members = {str(node_id) for node_id in node_ids}
    groups = []
    for existing in spec.get("groups", []):
        if existing["id"] == group_id:
            continue
        remaining = [node for node in existing["nodeIds"] if node not in members]
        if remaining:
            groups.append({**existing, "nodeIds": remaining})
    groups.append({"id": group_id, "label": label, "nodeIds": sorted(members)})
    result = _copy(spec)
    result["groups"] = groups
    return result


def collapse(spec: dict, *, group_ids: Sequence[str]) -> dict:
    result = _copy(spec)
    result["collapsed"] = sorted({*spec.get("collapsed", []), *(str(g) for g in group_ids)})
    return result


def expand(spec: dict, *, group_ids: Sequence[str]) -> dict:
    removing = {str(group_id) for group_id in group_ids}
    result = _copy(spec)
    result["collapsed"] = [group for group in spec.get("collapsed", []) if group not in removing]
    return result


def filter_view(spec: dict, *, predicate: dict | None) -> dict:
    result = _copy(spec)
    result["filters"] = predicate or {}
    return result


def focus(spec: dict, *, node_ids: Sequence[uuid.UUID | str]) -> dict:
    result = _copy(spec)
    result["camera"] = {"focus": [str(node_id) for node_id in node_ids]}
    return result


def fit_view(spec: dict, *, node_ids: Sequence[uuid.UUID | str] = ()) -> dict:
    result = _copy(spec)
    result["camera"] = {"fit": [str(node_id) for node_id in node_ids]}
    return result


def reset_view(spec: dict) -> dict:
    return {}


def annotate(spec: dict, *, node_id: uuid.UUID | str, text: str) -> dict:
    if not text.strip():
        raise InvariantViolationError("annotation text must not be empty")
    target = str(node_id)
    others = [item for item in spec.get("annotations", []) if item["nodeId"] != target]
    result = _copy(spec)
    result["annotations"] = [*others, {"nodeId": target, "text": text.strip()}]
    return result
