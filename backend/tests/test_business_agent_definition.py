from __future__ import annotations

from app.agent.definitions.business import BusinessAgentDefinition, BusinessRunContext
from app.agent.definitions.registry import get_definition
from app.agent.prompts.business import MAIN_BUSINESS_AGENT_PROMPT
from app.vocabulary import ConversationKind


def test_registry_resolves_business_definition() -> None:
    definition = get_definition(ConversationKind.BUSINESS)

    assert isinstance(definition, BusinessAgentDefinition)
    assert definition.kind == "business"
    assert get_definition("business") is definition


def test_business_run_context_is_available() -> None:
    assert BusinessRunContext.__name__ == "BusinessRunContext"


def test_business_prompt_mentions_query_capability() -> None:
    assert "业务助手" in MAIN_BUSINESS_AGENT_PROMPT
    assert "query_objects" in MAIN_BUSINESS_AGENT_PROMPT
    assert "get_action_detail" in MAIN_BUSINESS_AGENT_PROMPT
    assert "execute_action" in MAIN_BUSINESS_AGENT_PROMPT
