"""把中断折成前端统一认识的一份形状。

中断有两种来源：`ask_user` 的提问（question / options）和工具审批（HumanInTheLoop
的 action_requests / review_configs）。审批的 resume 要回填 decisions、提问回填字符串，
所以每个选项直接把 resume 载荷带在身上，前端不用知道这是哪一类中断。
"""

from __future__ import annotations

import json

from app.schemas.message_blocks import FreeTextMode, InterruptDetail, InterruptOption


def _normalize_details(raw: object) -> list[InterruptDetail]:
    """给用户看的补充信息：只收 {label, value} 都非空的条目，其余丢掉。"""
    if not isinstance(raw, list):
        return []
    details: list[InterruptDetail] = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        label = str(item.get("label") or "").strip()
        value = str(item.get("value") or "").strip()
        if label and value:
            details.append(InterruptDetail(label=label, value=value))
    return details


def normalize_interrupt(
    value: dict,
) -> tuple[str, list[InterruptOption], FreeTextMode | None, list[InterruptDetail]]:
    """把两种中断折成同一份给前端的形状。

    一是 `ask_user` 的提问（question / options），二是工具审批（HumanInTheLoop
    的 action_requests / review_configs）。审批的 resume 必须回填 decisions，
    提问回填一个字符串，所以每个选项直接带上自己的 resume 载荷。

    返回 (问题, 选项, 自由文本回填方式, 详情)；自由文本为 None 表示只能点选项。
    """
    question = value.get("question")
    if isinstance(question, str) and question.strip():
        asked_options = [
            InterruptOption(label=str(item), answer=str(item))
            for item in value.get("options") or []
            if str(item).strip()
        ]
        return (
            question,
            asked_options,
            FreeTextMode(wrap="raw"),
            _normalize_details(value.get("details")),
        )

    requests = value.get("action_requests")
    if isinstance(requests, list) and requests:
        lines = [
            f"{item.get('name')} {json.dumps(item.get('args') or {}, ensure_ascii=False)}"
            for item in requests
            if isinstance(item, dict)
        ]
        allowed: list[str] = []
        for config in value.get("review_configs") or []:
            if isinstance(config, dict):
                allowed.extend(config.get("allowed_decisions") or [])
        options: list[InterruptOption] = []
        if "approve" in allowed:
            options.append(
                InterruptOption(
                    label="允许", answer={"decisions": [{"type": "approve"}] * len(requests)}
                )
            )
        if "reject" in allowed:
            options.append(
                InterruptOption(
                    label="拒绝", answer={"decisions": [{"type": "reject"}] * len(requests)}
                )
            )
        free_text = FreeTextMode(wrap="respond") if "respond" in allowed else None
        return "要执行以下操作，需要你确认：\n" + "\n".join(lines), options, free_text, []

    # 认不出来的中断也别吞掉，原样带回给前端，至少人能看一眼。
    return f"需要你确认：{json.dumps(value, ensure_ascii=False)}", [], FreeTextMode(wrap="raw"), []
