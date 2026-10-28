from __future__ import annotations

from app.agent.prompts.query import MAIN_QUERY_AGENT_PROMPT


def test_main_query_prompt_owns_instance_queries() -> None:
    prompt = MAIN_QUERY_AGENT_PROMPT
    for phrase in (
        "query_objects",
        "get_related_objects",
        "primary_keys",
        "outgoing",
        "incoming",
        "先按名称/条件找到锚点",
        "TRUNCATED",
        "不拼 SQL",
    ):
        assert phrase in prompt
    assert "task(subagent_type" not in prompt
    assert "最终回答固定为两部分" in prompt
    assert "**结果**" in prompt
    assert "**证据链**" in prompt
