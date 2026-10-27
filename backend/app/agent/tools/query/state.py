"""查询子 Agent 在 checkpoint 中保存的步骤状态。"""

from __future__ import annotations

import hashlib
from operator import add
from typing import Annotated, Any, NotRequired

from langchain.agents import AgentState


def _merge_steps(
    left: dict[str, list[dict[str, Any]]] | None,
    right: dict[str, list[dict[str, Any]]] | None,
) -> dict[str, list[dict[str, Any]]]:
    return {**(left or {}), **(right or {})}


def _max_counter(left: int | None, right: int | None) -> int:
    return max(left or 0, right or 0)


def _merge_evidence(
    left: list[dict[str, Any]] | None,
    right: list[dict[str, Any]] | None,
) -> list[dict[str, Any]]:
    """证据只保留当前轮；上一轮仍留在 messages/steps 里供追问参考。"""
    previous = left or []
    incoming = right or []
    if not incoming:
        return previous
    turn_id = next((item.get("turn_id") for item in incoming if item.get("turn_id")), None)
    if not turn_id:
        return [*previous, *incoming]
    return [item for item in previous if item.get("turn_id") == turn_id] + incoming


class QueryAgentState(AgentState):
    graph_id: NotRequired[str]
    graph_detail: NotRequired[dict[str, Any]]
    rewritten_query: NotRequired[str]
    turn_id: NotRequired[str]
    steps: NotRequired[Annotated[dict[str, list[dict[str, Any]]], _merge_steps]]
    anchor_refs: NotRequired[Annotated[list[dict[str, Any]], add]]
    frontier: NotRequired[Annotated[list[dict[str, Any]], add]]
    evidence: NotRequired[Annotated[list[dict[str, Any]], _merge_evidence]]
    step_counter: NotRequired[Annotated[int, _max_counter]]


def next_step_id(
    state: dict[str, Any],
    *,
    tool_call_id: str | None = None,
    parallel: bool = False,
) -> tuple[str, int]:
    steps = state.get("steps") if isinstance(state.get("steps"), dict) else {}
    if not steps:
        step_id, counter = "s0", 0
    else:
        counter = int(state.get("step_counter") or 0) + 1
        step_id = f"s{counter}"
    if parallel and tool_call_id:
        suffix = hashlib.sha1(tool_call_id.encode()).hexdigest()[:6]
        step_id = f"{step_id}-{suffix}"
    return step_id, counter


def step_update(
    state: dict[str, Any],
    *,
    step_id: str,
    counter: int,
    items: list[dict[str, Any]],
    result_status: str,
    index_version_id: Any = None,
    is_anchor: bool = False,
) -> dict[str, Any]:
    refs = [_ref(item, index_version_id) for item in items]
    turn_id = str(state.get("turn_id") or "")
    evidence = [
        {
            "turn_id": turn_id,
            "step_id": step_id,
            "object_type_id": item.get("object_type_id"),
            "primary_key": item.get("primary_key"),
            "title": item.get("title"),
            "properties": item.get("properties") or {},
            "index_version_id": index_version_id,
            "status": result_status,
        }
        for item in refs
    ]
    update: dict[str, Any] = {
        "steps": {step_id: refs},
        "step_counter": counter,
        "frontier": refs,
        "evidence": evidence,
    }
    if is_anchor:
        update["anchor_refs"] = refs
    return update


def _ref(item: dict[str, Any], index_version_id: Any) -> dict[str, Any]:
    return {
        "object_type_id": str(item.get("object_type_id") or ""),
        "primary_key": str(item.get("primary_key") or ""),
        "title": item.get("title"),
        "properties": item.get("properties") or {},
        "index_version_id": str(index_version_id) if index_version_id else None,
    }
