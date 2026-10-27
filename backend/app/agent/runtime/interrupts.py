"""把中断折成前端统一认识的一份形状。

中断有两种来源：`ask_user` 的提问（question / options）和工具审批（HumanInTheLoop
的 action_requests / review_configs）。审批的 resume 要回填 decisions、提问回填字符串，
所以每个选项直接把 resume 载荷带在身上，前端不用知道这是哪一类中断。
"""

from __future__ import annotations

from app.schemas.message_blocks import FreeTextMode, InterruptDetail, InterruptOption
from app.services import element_kinds

_FIELD_LABELS = {
    "name": "名称",
    "description": "描述",
    "datatype": "数据类型",
    "required": "必填",
    "is_array": "多值",
    "is_primary_key": "主键",
    "is_title_key": "标题键",
    "enum_values": "枚举值",
    "cardinality": "基数",
    "direction": "方向",
    "forward_label": "正向名称",
    "reverse_label": "反向名称",
    "foreign_key_property_id": "外键属性",
}


def _format_value(value: object) -> str:
    if isinstance(value, bool):
        return "是" if value else "否"
    if isinstance(value, list):
        return "、".join(_format_value(item) for item in value)
    if isinstance(value, dict):
        return "新的设置"
    if value is None:
        return "空"
    return str(value)


def _patch_summary(patch: object) -> str:
    if not isinstance(patch, dict) or not patch:
        return "更新该元素的设置"
    parts: list[str] = []
    for key, label in _FIELD_LABELS.items():
        if key not in patch:
            continue
        value = patch[key]
        if key in {"description", "foreign_key_property_id"}:
            parts.append(f"更新{label}")
        elif key in {"required", "is_array", "is_primary_key", "is_title_key"}:
            parts.append(f"{'设为' if value else '取消'}{label}")
        else:
            parts.append(f"{label}改为「{_format_value(value)}」")
    unknown = len(set(patch) - set(_FIELD_LABELS))
    if unknown:
        parts.append(f"调整其他 {unknown} 项设置")
    return "、".join(parts)


def _approval_detail(item: dict) -> InterruptDetail:
    name = str(item.get("name") or "")
    raw_args = item.get("args")
    args: dict = raw_args if isinstance(raw_args, dict) else {}
    kind = args.get("element_kind")
    kind_label = element_kinds.ELEMENT_KIND_LABELS.get(str(kind or ""), "元素")
    if name == "update_element":
        return InterruptDetail(label=f"修改{kind_label}", value=_patch_summary(args.get("patch")))
    if name == "remove_element":
        return InterruptDetail(
            label=f"标记删除{kind_label}",
            value="确认改动后才会真正删除",
        )
    return InterruptDetail(label="执行一步操作", value="确认后继续执行")


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
        details = [_approval_detail(item) for item in requests if isinstance(item, dict)]
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
        return "以下操作需要你确认：", options, free_text, details

    # 认不出来的中断也别吞掉，给用户一个可回复的出口。
    return "需要你确认一项操作，请直接回复你的决定。", [], FreeTextMode(wrap="raw"), []
