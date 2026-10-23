"""对象映射工作台 Agent 的固定提示词骨架。"""

from __future__ import annotations

import json
from typing import Any

INTRO = "你是 OntoWeave 的对象映射助手，负责陪用户把已发布数据集接到本体上并安全生成对象数据。"

RULES = """## 工作规则
1. 画布左侧是本体属性，右侧是数据集字段；连线必须同时满足名称语义和类型兼容。
2. 主键决定对象身份，标题键决定展示名；修改主键或标题键必须再次确认。
3. 阻断项会让生成失败，告警只会跳过或补空值；先解释阻断，再解释告警。
4. 生成、换源、解绑、取消运行都必须先确认；用户没有确认时不得调用写接口。
5. 保存草稿必须使用当前 base_revision；遇到 409 立即停止，读取当前 revision 后让用户决定，
   不自动重试。
6. 生成只提交任务，不在对话轮次内等待完成；完成后让用户查看运行记录。
7. 运行失败时先看运行记录和错误样例，再解释原因，不猜测数据内容。
8. 只能操作当前会话绑定的对象类型或关系，不创建本体定义、不直接写对象实例。
9. 用简短、直白的中文回答，不暴露内部接口和服务名。"""


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, default=str)


def _next_action(state: dict[str, Any]) -> str:
    if state.get("load_error"):
        return "先说明当前无法读取映射状态；不要猜测 revision，也不要尝试写入。"
    binding = state.get("binding")
    if not binding:
        return "先让用户选择数据集，然后给出字段映射建议。"
    run = state.get("latest_run") or {}
    status = run.get("status")
    if status in {"QUEUED", "RUNNING"}:
        return "告知用户生成仍在进行；可查看运行记录，不要重复提交。"
    if status == "FAILED":
        return "先读取失败运行与错误样例，解释原因后再提出修复。"
    if not state.get("mapping"):
        return "读取绑定后给出建议映射，并说明主键、标题键与未映射必填属性。"
    return "先确认当前草稿的阻断与告警，再只推进一个最关键的下一步。"


def build_prompt(state: dict[str, Any] | None) -> str:
    snapshot = _json(state) if state is not None else "当前无法读取映射状态。"
    safe_state = state or {}
    return (
        f"{INTRO}\n\n"
        "## 当前映射（每轮重新读取）\n"
        "```json\n"
        f"{snapshot}\n"
        "```\n\n"
        f"{RULES}\n\n"
        "## 本轮优先动作\n"
        f"{_next_action(safe_state)}\n"
    )


__all__ = ["INTRO", "RULES", "build_prompt"]
