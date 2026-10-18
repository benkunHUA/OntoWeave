"""动作子智能体：只管「一次能做哪些改动」的定义，不碰实例、不碰图怎么画。"""

from __future__ import annotations

from deepagents import SubAgent

from app.agent.prompts import ACTION_AGENT_DESCRIPTION, ACTION_AGENT_PROMPT
from app.agent.tools.action.binding import build_action_tools
from app.agent.tools.ontology.binding import build_search_tool
from app.agent.tools.ontology.context import OntologyToolContext

ACTION_AGENT_NAME = "action_modeler"


def build_action_subagent(ctx: OntologyToolContext) -> SubAgent:
    return {
        "name": ACTION_AGENT_NAME,
        "description": ACTION_AGENT_DESCRIPTION,
        "system_prompt": ACTION_AGENT_PROMPT,
        # 检索与图切片是只读的，动作要靠它们先把对象类型 / 关系 / 属性的 id 找出来。
        "tools": [build_search_tool(ctx), *build_action_tools(ctx)],
    }
