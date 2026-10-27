"""绘图子智能体：只管「图怎么呈现」，拿不到任何改本体的工具。"""

from __future__ import annotations

from deepagents import SubAgent

from app.agent.prompts.base import VIEW_AGENT_DESCRIPTION, VIEW_AGENT_PROMPT
from app.agent.tools.ontology.context import OntologyToolContext
from app.agent.tools.view.binding import build_view_tools

VIEW_AGENT_NAME = "view_designer"


def build_view_subagent(ctx: OntologyToolContext) -> SubAgent:
    return {
        "name": VIEW_AGENT_NAME,
        "description": VIEW_AGENT_DESCRIPTION,
        "system_prompt": VIEW_AGENT_PROMPT,
        "tools": build_view_tools(ctx),
    }
