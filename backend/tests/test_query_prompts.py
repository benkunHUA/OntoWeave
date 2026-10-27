from __future__ import annotations

from app.agent.prompts_query import (
    MAIN_QUERY_AGENT_PROMPT,
    QUERY_AGENT_DESCRIPTION,
    QUERY_AGENT_PROMPT,
)


def test_query_agent_prompt_encodes_one_step_and_evidence_rules() -> None:
    prompt = QUERY_AGENT_PROMPT
    for phrase in (
        "一次只规划并执行一步",
        "先 query_objects 解析锚点",
        "direction 只能 outgoing",
        "expected_cardinality 只能 one 或 many",
        "NEEDS_USER",
        "业务时间属性",
        "TRUNCATED",
        "不跨图、不编造数据、不拼 SQL",
        "只写用户问到的结果",
    ):
        assert phrase in prompt
    assert "json" in prompt
    assert '"status":"OK"' in prompt


def test_main_query_prompt_delegates_instance_queries() -> None:
    prompt = MAIN_QUERY_AGENT_PROMPT
    assert 'task(subagent_type="query_agent")' in prompt
    assert '"graph_id":"...","rewritten_query":"..."' in prompt
    assert "不直接调用 query_objects 或 execute_query_step" in prompt
    assert "NEEDS_USER" in prompt
    assert "最终回答固定为两部分" in prompt
    assert "**结果**" in prompt
    assert "**证据链**" in prompt
    assert "不要输出「说明与限制」" in prompt
    assert "多步只读查询" in QUERY_AGENT_DESCRIPTION
