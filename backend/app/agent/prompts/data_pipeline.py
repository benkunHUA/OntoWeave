"""数据整理 Agent 的固定提示词骨架与动态状态注入。"""

from __future__ import annotations

import json
from collections.abc import Sequence
from typing import Any

from app.models import ConversationEvent

INTRO = "你是 OntoWeave 的数据整理助手，负责陪用户逐步搭建一条数据处理流程。"

RULES = """## 工作规则
1. 先调用工具确认事实，不编造节点、字段、数据集或构建状态。
2. 每轮最多问一个最阻塞的问题，提问必须调用 ask_user。
3. 普通修改直接写草稿；删除节点、清空流程、修改或覆盖输出、构建必须确认。
4. 所有写操作使用当前 draft_revision 作为 base_revision；冲突时停止写入并重新读取。
5. 只能使用节点目录中的节点和配置，不能生成自由 SQL。
6. stage=ready 时先建议预览；用户没有明确要求时不要自动构建。
7. 节点的 node_config 参数必须传 JSON 对象本身，不要把对象再序列化成字符串。
8. 用简短、直白的中文回答，不暴露内部接口和服务名。"""


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, default=str)


def _event_lines(events: Sequence[ConversationEvent]) -> str:
    lines = [
        f"- {item.created_at.isoformat()} [{item.event_type}] {item.summary}" for item in events
    ]
    return "\n".join(lines) if lines else "- 暂无事件"


def _next_action(state: dict | None) -> str:
    if not state:
        return "先向用户说明当前无法读取流程状态，不要继续修改。"
    if state.get("loadError"):
        return "先说明数据服务暂时不可用；不要猜测草稿版本，也不要尝试写入。"
    check = state.get("check") or {}
    stage = check.get("stage")
    if stage == "ready":
        return "回复下一步已就绪；先建议用户预览输出，不要自动触发构建。"
    blocking = check.get("blocking_issues") or check.get("blockingIssues") or []
    if blocking:
        issue = blocking[0]
        message = issue.get("message") or issue.get("code") or "当前阻塞项"
        return f"先处理最关键的阻塞项：{message}。"
    if stage in {"empty", "invalid", "needs_input", "needs_output"}:
        return f"根据当前阶段 {stage}，只推进一个最关键的下一步。"
    return "先确认用户的目标，再调用工具读取当前草稿并推进一个最小的改动。"


def build_prompt(state: dict | None, events: Sequence[ConversationEvent]) -> str:
    snapshot = _json(state) if state is not None else "当前无法从数据服务读取流程状态。"
    return (
        f"{INTRO}\n\n"
        "## 当前流程（每轮重新读取）\n"
        "```json\n"
        f"{snapshot}\n"
        "```\n\n"
        "## 最近事件\n"
        f"{_event_lines(events)}\n\n"
        f"{RULES}\n\n"
        "## 本轮优先动作\n"
        f"{_next_action(state)}\n"
    )
