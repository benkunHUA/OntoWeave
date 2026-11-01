from __future__ import annotations

from app.agent.prompts.business import MAIN_BUSINESS_AGENT_PROMPT


def test_main_query_prompt_owns_instance_queries() -> None:
    prompt = MAIN_BUSINESS_AGENT_PROMPT
    for phrase in (
        "query_objects",
        "get_related_objects",
        "primary_keys",
        "outgoing",
        "incoming",
        "先按名称/条件找到锚点",
        "TRUNCATED",
        "不拼 SQL",
        "get_graph_detail.actions",
        "get_action_detail",
        "先判断用户是要查询还是要执行行动",
        "execute_action",
        "只通过 execute_action 执行",
        "execute_action 会先 preview 并触发确认卡片",
        "readback",
        "多个行动都匹配时先 ask_user",
    ):
        assert phrase in prompt
    assert "task(subagent_type" not in prompt
    assert "最终回答固定为两部分" in prompt
    assert "**结果**" in prompt
    assert "**证据链**" in prompt
    assert "业务助手" in prompt
