"""可用 Agent 定义的注册表。"""

from __future__ import annotations

from app.agent.definitions.base import AgentDefinition
from app.agent.definitions.data_connection import DataConnectionAgentDefinition
from app.agent.definitions.data_pipeline import DataPipelineAgentDefinition
from app.agent.definitions.ontology import OntologyAgentDefinition
from app.agent.definitions.ontology_workbench import OntologyWorkbenchAgentDefinition
from app.errors import InvariantViolationError

_DEFINITIONS: dict[str, AgentDefinition] = {
    OntologyAgentDefinition.kind: OntologyAgentDefinition(),
    DataConnectionAgentDefinition.kind: DataConnectionAgentDefinition(),
    DataPipelineAgentDefinition.kind: DataPipelineAgentDefinition(),
    OntologyWorkbenchAgentDefinition.kind: OntologyWorkbenchAgentDefinition(),
}


def get_definition(kind: str) -> AgentDefinition:
    try:
        return _DEFINITIONS[kind]
    except KeyError as exc:
        raise InvariantViolationError(f"unsupported conversation kind: {kind}") from exc
