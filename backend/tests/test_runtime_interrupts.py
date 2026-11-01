from app.agent.runtime.interrupts import normalize_interrupt
from app.schemas.message_blocks import FreeTextMode, InterruptDetail, InterruptOption


def test_tool_approval_interrupt_maps_to_decisions():
    question, options, free_text, details, confirmation = normalize_interrupt(
        {
            "action_requests": [
                {"name": "remove_element", "args": {"element_kind": "property", "element_id": "p1"}}
            ],
            "review_configs": [
                {
                    "action_name": "remove_element",
                    "allowed_decisions": ["approve", "reject", "respond"],
                }
            ],
        }
    )
    assert question == "以下操作需要你确认："
    assert options == [
        InterruptOption(label="允许", answer={"decisions": [{"type": "approve"}]}),
        InterruptOption(label="拒绝", answer={"decisions": [{"type": "reject"}]}),
    ]
    assert free_text == FreeTextMode(wrap="respond")
    assert details == [InterruptDetail(label="标记删除属性", value="确认改动后才会真正删除")]
    assert confirmation is None


def test_tool_approval_interrupt_summarizes_the_patch_instead_of_json():
    question, _, _, details, _ = normalize_interrupt(
        {
            "action_requests": [
                {
                    "name": "update_element",
                    "args": {
                        "element_kind": "object_type",
                        "element_id": "type-1",
                        "patch": {"name": "企业客户", "description": "新的描述"},
                    },
                }
            ],
            "review_configs": [
                {"action_name": "update_element", "allowed_decisions": ["approve", "reject"]}
            ],
        }
    )

    assert question == "以下操作需要你确认："
    assert details == [
        InterruptDetail(label="修改对象类型", value="名称改为「企业客户」、更新描述")
    ]
    assert "{" not in question


def test_ask_user_interrupt_carries_details():
    question, options, free_text, details, confirmation = normalize_interrupt(
        {
            "question": "要复用已有的「客户」吗？",
            "options": ["复用这个", "新建单独的"],
            "details": [
                {"label": "名称", "value": "客户"},
                {"label": "描述", "value": "下订单、购买产品的一方"},
                {"label": "空值", "value": "   "},
                "不是字典",
            ],
        }
    )
    assert question == "要复用已有的「客户」吗？"
    assert options == [
        InterruptOption(label="复用这个", answer="复用这个"),
        InterruptOption(label="新建单独的", answer="新建单独的"),
    ]
    assert free_text == FreeTextMode(wrap="raw")
    assert details == [
        InterruptDetail(label="名称", value="客户"),
        InterruptDetail(label="描述", value="下订单、购买产品的一方"),
    ]
    assert confirmation is None


def test_action_confirmation_is_carried_with_the_interrupt():
    question, _, free_text, _, confirmation = normalize_interrupt(
        {
            "question": "执行「关闭工单」？",
            "details": [{"label": "变更摘要", "value": "修改 1 个对象"}],
            "confirmation": {
                "kind": "action_confirmation",
                "title": "执行「关闭工单」？",
                "summary": "修改 1 个对象",
                "action": {"id": "a1", "name": "关闭工单", "description": "记录解决说明"},
                "preview_hash": "hash",
                "execution_id": "e1",
                "object_changes": [
                    {
                        "kind": "OBJECT_MODIFY",
                        "object_type_id": "t1",
                        "object_type_name": "工单",
                        "primary_key": "T-1",
                        "title": "打印机故障",
                        "fields": [
                            {
                                "property_id": "p1",
                                "name": "解决说明",
                                "before": None,
                                "after": "已修复",
                            }
                        ],
                    }
                ],
                "relation_changes": [],
                "warnings": [],
                "options": [{"label": "确认执行", "answer": {"decision": "approve"}}],
            },
        }
    )

    assert question == "执行「关闭工单」？"
    assert free_text is None
    assert confirmation is not None
    assert confirmation.title == "执行「关闭工单」？"
    assert confirmation.object_changes[0].fields[0].after == "已修复"
    assert confirmation.options[0].answer == {"decision": "approve"}
