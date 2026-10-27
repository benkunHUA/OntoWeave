from app.agent.runtime.interrupts import normalize_interrupt
from app.schemas.message_blocks import FreeTextMode, InterruptDetail, InterruptOption


def test_tool_approval_interrupt_maps_to_decisions():
    question, options, free_text, details = normalize_interrupt(
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


def test_tool_approval_interrupt_summarizes_the_patch_instead_of_json():
    question, _, _, details = normalize_interrupt(
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
    question, options, free_text, details = normalize_interrupt(
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
