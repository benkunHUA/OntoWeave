from __future__ import annotations

import pytest

from app.agent.prompts.base import ACTION_AGENT_PROMPT, SYSTEM_PROMPT, VIEW_AGENT_PROMPT
from app.agent.prompts.business import MAIN_BUSINESS_AGENT_PROMPT
from app.agent.prompts.data_connection import SYSTEM_PROMPT as DATA_CONNECTION_PROMPT
from app.agent.prompts.data_pipeline import RULES as DATA_PIPELINE_RULES
from app.agent.prompts.language import CHINESE_ONLY_RULE
from app.agent.prompts.ontology_workbench import RULES as ONTOLOGY_WORKBENCH_RULES


@pytest.mark.parametrize(
    "prompt",
    [
        SYSTEM_PROMPT,
        VIEW_AGENT_PROMPT,
        ACTION_AGENT_PROMPT,
        MAIN_BUSINESS_AGENT_PROMPT,
        DATA_CONNECTION_PROMPT,
        DATA_PIPELINE_RULES,
        ONTOLOGY_WORKBENCH_RULES,
    ],
)
def test_agent_prompts_require_simplified_chinese(prompt: str) -> None:
    assert CHINESE_ONLY_RULE in prompt
