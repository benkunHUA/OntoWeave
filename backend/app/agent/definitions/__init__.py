"""会话类型到 Agent 实现的注册层。"""

from __future__ import annotations

from app.agent.definitions.base import AgentDefinition, AgentRunContext
from app.agent.definitions.data_connection import (
    DataConnectionAgentDefinition,
    DataConnectionRunContext,
)
from app.agent.definitions.ontology import OntologyAgentDefinition, OntologyRunContext
from app.agent.definitions.registry import get_definition

__all__ = [
    "AgentDefinition",
    "AgentRunContext",
    "DataConnectionAgentDefinition",
    "DataConnectionRunContext",
    "OntologyAgentDefinition",
    "OntologyRunContext",
    "get_definition",
]
