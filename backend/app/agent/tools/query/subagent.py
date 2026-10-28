"""带独立 checkpoint 线程的查询子 Agent。"""

from __future__ import annotations

import json
import logging
import uuid
from collections.abc import Callable
from typing import Any, cast

from deepagents import CompiledSubAgent
from langchain.agents import create_agent
from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.runnables import RunnableConfig, RunnableLambda
from langgraph.checkpoint.base import BaseCheckpointSaver
from sqlalchemy.orm import Session

from app.agent import llm
from app.agent.prompts.query import QUERY_AGENT_DESCRIPTION, QUERY_AGENT_PROMPT
from app.agent.tools.query.binding import build_query_instance_tools
from app.agent.tools.query.context import QueryToolContext
from app.agent.tools.query.sequential import SequentialToolCallsMiddleware
from app.agent.tools.query.state import QueryAgentState
from app.clients.data_service.ontology_client import OntologyDataServiceClient
from app.config import Settings
from app.errors import InvalidRequestError, InvariantViolationError
from app.schemas.query import QueryAgentResult, QueryAgentStatus, QueryEvidence
from app.services import embedding
from app.services.graph import search as graph_search

QUERY_AGENT_NAME = "query_agent"
logger = logging.getLogger(__name__)


def build_query_subagent(
    session: Session,
    settings: Settings,
    *,
    conversation_id: uuid.UUID,
    owner_id: uuid.UUID,
    checkpointer: BaseCheckpointSaver | None,
    model: Any | None = None,
) -> CompiledSubAgent:
    if checkpointer is None:
        raise InvariantViolationError("persistent query agent requires a checkpointer")
    client = OntologyDataServiceClient(settings)
    context = QueryToolContext(
        session=session,
        owner_id=owner_id,
        client=client,
        embedder=embedding.build_embedder(settings),
    )
    child = create_agent(
        model or llm.build_chat_model(settings),
        build_query_instance_tools(context),
        system_prompt=QUERY_AGENT_PROMPT,
        middleware=[SequentialToolCallsMiddleware()],
        state_schema=QueryAgentState,
        checkpointer=checkpointer,
        response_format=QueryAgentResult,
        name=QUERY_AGENT_NAME,
    )

    def load_graph_detail(graph_id: uuid.UUID) -> dict[str, Any]:
        return graph_search.get_graph_detail(session, graph_id=graph_id, owner_id=owner_id)

    runnable = RunnableLambda(
        _QueryRunner(
            child,
            conversation_id,
            checkpointer,
            graph_detail_loader=load_graph_detail,
        )
    )
    return {
        "name": QUERY_AGENT_NAME,
        "description": QUERY_AGENT_DESCRIPTION,
        "runnable": runnable,
    }


class _QueryRunner:
    """把 task 的自由文本信封翻译成独立线程，并只回传主 Agent 需要的字段。"""

    def __init__(
        self,
        child: Any,
        conversation_id: uuid.UUID,
        checkpointer: BaseCheckpointSaver | None = None,
        graph_detail_loader: Callable[[uuid.UUID], dict[str, Any]] | None = None,
    ) -> None:
        self.child = child
        self.conversation_id = conversation_id
        self.checkpointer = checkpointer
        self.graph_detail_loader = graph_detail_loader

    def __call__(
        self, state: dict[str, Any], config: dict[str, Any] | None = None
    ) -> dict[str, Any]:
        try:
            payload = _payload(state)
            graph_id = _graph_id(payload)
            graph_detail = self._graph_detail(graph_id, payload)
            child_payload = {
                "graph_id": str(graph_id),
                "graph_detail": graph_detail,
                "rewritten_query": payload["rewritten_query"].strip(),
            }
            turn_id = uuid.uuid4().hex
            child_state = {
                "messages": [HumanMessage(content=json.dumps(child_payload, ensure_ascii=False))],
                "graph_id": str(graph_id),
                "graph_detail": graph_detail,
                "rewritten_query": child_payload["rewritten_query"],
                "turn_id": turn_id,
            }
            child_config = _child_config(config, self.conversation_id, graph_id)
            self._reset_broken_history(child_config)
            try:
                result = self.child.invoke(child_state, child_config)
            except Exception as exc:
                if not self._reset_after_history_error(exc, child_config):
                    raise
                result = self.child.invoke(child_state, child_config)
            return _project(result, turn_id, graph_detail)
        except Exception as exc:
            message = AIMessage(content=f"查询输入无效：{exc}")
            return {
                "messages": [message],
                "structured_response": QueryAgentResult(
                    status=QueryAgentStatus.BLOCKED,
                    limitations=[str(exc)],
                ),
            }

    def _graph_detail(self, graph_id: uuid.UUID, payload: dict[str, Any]) -> dict[str, Any]:
        detail = (
            self.graph_detail_loader(graph_id)
            if self.graph_detail_loader is not None
            else payload.get("graph_detail")
        )
        if not isinstance(detail, dict):
            raise InvalidRequestError("graph_detail must be an object")
        detail_graph = detail.get("graph_id")
        if detail_graph and str(detail_graph) != str(graph_id):
            raise InvalidRequestError("graph_id does not match graph_detail.graph_id")
        return detail

    def _reset_broken_history(self, config: dict[str, Any]) -> None:
        """checkpoint 里残留未响应的 tool_calls 时，清掉该子线程再重跑。"""
        if self.checkpointer is None:
            return
        checkpoint = self.checkpointer.get_tuple(cast("RunnableConfig", config))
        if checkpoint is None:
            return
        messages = (checkpoint.checkpoint.get("channel_values") or {}).get("messages") or []
        if _has_dangling_tool_calls(messages):
            thread_id = _thread_id(config)
            logger.warning(
                "resetting query subagent thread with dangling tool calls: %s", thread_id
            )
            self.checkpointer.delete_thread(thread_id)

    def _reset_after_history_error(self, exc: Exception, config: dict[str, Any]) -> bool:
        if self.checkpointer is None or not _is_dangling_history_error(exc):
            return False
        thread_id = _thread_id(config)
        logger.warning("resetting query subagent thread after history error: %s", thread_id)
        self.checkpointer.delete_thread(thread_id)
        return True


def _thread_id(config: dict[str, Any]) -> str:
    configurable = config.get("configurable") if isinstance(config, dict) else None
    return str((configurable or {}).get("thread_id") or "")


def _has_dangling_tool_calls(messages: list[Any]) -> bool:
    pending: set[str] = set()
    for message in messages:
        if pending and not isinstance(message, ToolMessage):
            return True
        if isinstance(message, ToolMessage):
            pending.discard(str(getattr(message, "tool_call_id", "") or ""))
            continue
        if isinstance(message, AIMessage):
            pending.update(str(call["id"]) for call in message.tool_calls or [] if call.get("id"))
    return bool(pending)


def _is_dangling_history_error(exc: Exception) -> bool:
    message = str(exc)
    return "insufficient tool messages" in message or (
        "tool_calls" in message and "tool messages" in message
    )


def _payload(state: dict[str, Any]) -> dict[str, Any]:
    from app.agent.runtime.events import content_text

    messages = state.get("messages") if isinstance(state, dict) else None
    if not isinstance(messages, list) or not messages:
        raise InvalidRequestError("query task is missing its payload")
    raw = content_text(getattr(messages[-1], "content", "")).strip()
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise InvalidRequestError("query task payload must be a JSON object") from exc
    if not isinstance(payload, dict):
        raise InvalidRequestError("query task payload must be a JSON object")
    if (
        not isinstance(payload.get("rewritten_query"), str)
        or not payload["rewritten_query"].strip()
    ):
        raise InvalidRequestError("rewritten_query must be a non-empty string")
    return payload


def _graph_id(payload: dict[str, Any]) -> uuid.UUID:
    raw = payload.get("graph_id")
    try:
        graph_id = uuid.UUID(str(raw))
    except (TypeError, ValueError) as exc:
        raise InvalidRequestError("graph_id must be a UUID") from exc
    return graph_id


def _child_config(
    config: dict[str, Any] | None, conversation_id: uuid.UUID, graph_id: uuid.UUID
) -> dict[str, Any]:
    child = dict(config or {})
    configurable = dict(child.get("configurable") or {})
    configurable.pop("checkpoint_ns", None)
    configurable.pop("checkpoint_id", None)
    configurable["thread_id"] = f"{conversation_id}:query:{graph_id}"
    child["configurable"] = configurable
    return child


def _project(result: dict[str, Any], turn_id: str, graph_detail: dict[str, Any]) -> dict[str, Any]:
    messages = result.get("messages") if isinstance(result, dict) else None
    last = messages[-1] if isinstance(messages, list) and messages else AIMessage(content="")
    structured = result.get("structured_response") if isinstance(result, dict) else None
    evidence = _evidence_from_state(
        result.get("evidence") if isinstance(result, dict) else None,
        turn_id,
        graph_detail,
    )
    if isinstance(structured, QueryAgentResult):
        update: dict[str, Any] = {"evidence": evidence}
        if structured.status is QueryAgentStatus.OK:
            # 正常结果只保留答案与证据；限制信息只在无法正常回答时转述。
            update["limitations"] = []
        structured = structured.model_copy(update=update)
    return {
        "messages": [last],
        "structured_response": structured,
    }


def _evidence_from_state(
    raw: Any, turn_id: str, graph_detail: dict[str, Any]
) -> list[QueryEvidence]:
    if not isinstance(raw, list):
        return []
    object_names, property_names = _evidence_labels(graph_detail)
    evidence: list[QueryEvidence] = []
    for item in raw:
        if (
            not isinstance(item, dict)
            or item.get("turn_id") != turn_id
            or not item.get("object_type_id")
            or not item.get("primary_key")
        ):
            continue
        object_type_id = str(item["object_type_id"])
        properties = item.get("properties")
        property_ids = properties if isinstance(properties, dict) else {}
        payload: dict[str, Any] = {
            **item,
            "object_type_name": object_names.get(object_type_id),
            "property_names": {
                key: property_names[object_type_id][key]
                for key in property_ids
                if key in property_names.get(object_type_id, {})
            },
        }
        evidence.append(QueryEvidence.model_validate(payload))
    return evidence


def _evidence_labels(
    graph_detail: dict[str, Any],
) -> tuple[dict[str, str], dict[str, dict[str, str]]]:
    """从图详情建立对象类型、属性 ID 到展示名的映射。"""
    object_names: dict[str, str] = {}
    property_names: dict[str, dict[str, str]] = {}
    rows = graph_detail.get("object_types")
    if not isinstance(rows, list):
        return object_names, property_names
    for row in rows:
        if not isinstance(row, dict):
            continue
        object_type_id = _text(row.get("id"))
        if object_type_id is None:
            continue
        object_name = _text(row.get("name"))
        if object_name is not None:
            object_names[object_type_id] = object_name
        labels: dict[str, str] = {}
        properties = row.get("properties")
        if isinstance(properties, list):
            for prop in properties:
                if not isinstance(prop, dict):
                    continue
                property_id = _text(prop.get("id"))
                property_name = _text(prop.get("name"))
                if property_id is not None and property_name is not None:
                    labels[property_id] = property_name
        property_names[object_type_id] = labels
    return object_names, property_names


def _text(value: object) -> str | None:
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text or None
