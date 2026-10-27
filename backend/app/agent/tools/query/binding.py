"""查询子 Agent 的两个实例工具。"""

from __future__ import annotations

import json
import uuid
from typing import Any, Literal

from langchain.tools import ToolRuntime
from langchain_core.messages import AIMessage, ToolMessage
from langchain_core.tools import BaseTool, tool
from langgraph.types import Command

from app.agent.tools.query.context import QueryToolContext
from app.agent.tools.query.state import next_step_id, step_update
from app.errors import InvalidRequestError
from app.models import GraphObject
from app.schemas.query import QueryDirection, QueryObjectsInput, QueryStepInput
from app.services.query import execute_query_step, query_objects


def build_query_instance_tools(ctx: QueryToolContext) -> list[BaseTool]:
    @tool("query_objects")
    def query_objects_tool(
        runtime: ToolRuntime,
        object_type_id: str,
        primary_key: str | None = None,
        q: str | None = None,
        filters: list[dict[str, Any]] | None = None,
        sort: list[dict[str, Any]] | None = None,
        limit: int = 20,
        project: list[str] | None = None,
    ) -> Command:
        """查询对象实例：按主键读取、按名称搜索、条件列表或排序。一次调用只触碰一个对象类型。"""
        try:
            graph_id = _graph_id(runtime)
            payload = QueryObjectsInput.model_validate(
                {
                    "object_type_id": object_type_id,
                    "primary_key": primary_key,
                    "q": q,
                    "filters": filters or [],
                    "sort": sort or [],
                    "limit": limit,
                    "project": project or [],
                }
            )
            object_type_uuid = uuid.UUID(payload.object_type_id)
            _ensure_object_in_graph(ctx, graph_id, object_type_uuid)
            step_id, counter = next_step_id(
                runtime.state,
                tool_call_id=runtime.tool_call_id,
                parallel=_has_parallel_tool_calls(runtime),
            )
            result = ctx.invoke(
                lambda: query_objects(
                    ctx.session,
                    ctx.client,
                    owner_id=ctx.owner_id,
                    payload=payload,
                    step_id=step_id,
                )
            )
            if not hasattr(result, "model_dump"):
                return _error_command(runtime, result)
            data = result.model_dump(mode="json")
            update = step_update(
                runtime.state,
                step_id=step_id,
                counter=counter,
                items=data["items"],
                result_status=str(data["status"]),
                index_version_id=data.get("index_version_id"),
                is_anchor=not bool(runtime.state.get("steps")),
            )
            return _command(runtime, data, update)
        except Exception as exc:
            return _error_command(runtime, ctx.error_result(exc))

    @tool("execute_query_step")
    def execute_query_step_tool(
        runtime: ToolRuntime,
        input_step_id: str,
        relation_id: str,
        direction: QueryDirection,
        filters: list[dict[str, Any]] | None = None,
        sort: list[dict[str, Any]] | None = None,
        limit: int = 20,
        project: list[str] | None = None,
        expected_cardinality: Literal["one", "many"] | None = None,
    ) -> Command:
        """沿一条关系走一步；输入是前一步的 step_id，一次只走一条关系。

        direction：outgoing=source→target，incoming=target→source。
        expected_cardinality：预期命中的目标条数，只能 one 或 many；
        不要填关系自身的 cardinality（如 one_to_many）。
        """
        try:
            graph_id = _graph_id(runtime)
            payload = QueryStepInput.model_validate(
                {
                    "input_step_id": input_step_id,
                    "relation_id": relation_id,
                    "direction": direction,
                    "filters": filters or [],
                    "sort": sort or [],
                    "limit": limit,
                    "project": project or [],
                    "expected_cardinality": expected_cardinality,
                }
            )
            steps = runtime.state.get("steps") if isinstance(runtime.state, dict) else {}
            input_items = steps.get(payload.input_step_id) if isinstance(steps, dict) else None
            if not isinstance(input_items, list):
                raise InvalidRequestError(f"unknown input step {payload.input_step_id!r}")
            step_id, counter = next_step_id(
                runtime.state,
                tool_call_id=runtime.tool_call_id,
                parallel=_has_parallel_tool_calls(runtime),
            )
            result = ctx.invoke(
                lambda: execute_query_step(
                    ctx.session,
                    ctx.client,
                    owner_id=ctx.owner_id,
                    graph_id=graph_id,
                    payload=payload,
                    input_items=input_items,
                    step_id=step_id,
                )
            )
            if not hasattr(result, "model_dump"):
                return _error_command(runtime, result)
            data = result.model_dump(mode="json")
            update = step_update(
                runtime.state,
                step_id=step_id,
                counter=counter,
                items=data["items"],
                result_status=str(data["status"]),
                index_version_id=data.get("index_version_id"),
            )
            return _command(runtime, data, update)
        except Exception as exc:
            return _error_command(runtime, ctx.error_result(exc))

    return [query_objects_tool, execute_query_step_tool]


def _graph_id(runtime: ToolRuntime) -> uuid.UUID:
    value = runtime.state.get("graph_id") if isinstance(runtime.state, dict) else None
    if not value:
        raise InvalidRequestError("query graph_id is missing")
    return uuid.UUID(str(value))


def _has_parallel_tool_calls(runtime: ToolRuntime) -> bool:
    messages = runtime.state.get("messages") if isinstance(runtime.state, dict) else None
    if not isinstance(messages, list):
        return False
    for message in reversed(messages):
        if isinstance(message, AIMessage):
            return len(message.tool_calls or []) > 1
    return False


def _ensure_object_in_graph(
    ctx: QueryToolContext, graph_id: uuid.UUID, object_type_id: uuid.UUID
) -> None:
    if ctx.session.get(GraphObject, (graph_id, object_type_id)) is None:
        raise InvalidRequestError("object type is not in the selected graph")


def _command(runtime: ToolRuntime, data: dict[str, Any], update: dict[str, Any]) -> Command:
    return Command(
        update={
            **update,
            "messages": [
                ToolMessage(
                    content=json.dumps(data, ensure_ascii=False, default=str),
                    tool_call_id=runtime.tool_call_id or "",
                )
            ],
        }
    )


def _error_command(runtime: ToolRuntime, error: Any) -> Command:
    return Command(
        update={
            "messages": [
                ToolMessage(
                    content=json.dumps(error, ensure_ascii=False, default=str),
                    tool_call_id=runtime.tool_call_id or "",
                    status="error",
                )
            ]
        }
    )
