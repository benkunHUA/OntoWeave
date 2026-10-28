"""业务问答主 Agent 的实例查询工具。"""

from __future__ import annotations

import uuid
from typing import Any

from langchain_core.tools import BaseTool, tool
from pydantic import BaseModel

from app.agent.tools.query.context import QueryToolContext
from app.errors import InvalidRequestError
from app.models import GraphObject
from app.schemas.query import QueryDirection, QueryObjectsInput, RelatedObjectsInput
from app.services.query import get_related_objects, query_objects


def build_query_instance_tools(ctx: QueryToolContext) -> list[BaseTool]:
    @tool("query_objects")
    def query_objects_tool(
        graph_id: str,
        object_type_id: str,
        primary_keys: list[str] | None = None,
        q: str | None = None,
        filters: list[dict[str, Any]] | None = None,
        sort: list[dict[str, Any]] | None = None,
        limit: int = 20,
        project: list[str] | None = None,
    ) -> dict[str, Any]:
        """查询一个对象类型的实例：按主键批量读取、名称搜索、条件筛选或排序。"""
        result = ctx.invoke(
            lambda: _query_objects(
                ctx,
                graph_id=graph_id,
                object_type_id=object_type_id,
                primary_keys=primary_keys,
                q=q,
                filters=filters,
                sort=sort,
                limit=limit,
                project=project,
            )
        )
        return _result(result)

    @tool("get_related_objects")
    def get_related_objects_tool(
        graph_id: str,
        relation_id: str,
        direction: QueryDirection,
        inputs: list[dict[str, Any]],
        limit: int = 50,
    ) -> dict[str, Any]:
        """沿一条关系批量取对端对象引用；返回主键后可用 query_objects 读取完整对象。

        direction：outgoing=source→target，incoming=target→source。
        inputs 每项为 {object_type_id, primary_key}，必须来自同一关系端点。
        """
        result = ctx.invoke(
            lambda: _related_objects(
                ctx,
                graph_id=graph_id,
                relation_id=relation_id,
                direction=direction,
                inputs=inputs,
                limit=limit,
            )
        )
        return _result(result)

    return [query_objects_tool, get_related_objects_tool]


def _query_objects(
    ctx: QueryToolContext,
    *,
    graph_id: str,
    object_type_id: str,
    primary_keys: list[str] | None,
    q: str | None,
    filters: list[dict[str, Any]] | None,
    sort: list[dict[str, Any]] | None,
    limit: int,
    project: list[str] | None,
) -> BaseModel | dict[str, str]:
    graph_uuid = uuid.UUID(graph_id)
    object_uuid = uuid.UUID(object_type_id)
    _ensure_object_in_graph(ctx, graph_uuid, object_uuid)
    payload = QueryObjectsInput.model_validate(
        {
            "object_type_id": object_type_id,
            "primary_keys": primary_keys or [],
            "q": q,
            "filters": filters or [],
            "sort": sort or [],
            "limit": limit,
            "project": project or [],
        }
    )
    return query_objects(
        ctx.session,
        ctx.client,
        owner_id=ctx.owner_id,
        payload=payload,
    )


def _related_objects(
    ctx: QueryToolContext,
    *,
    graph_id: str,
    relation_id: str,
    direction: QueryDirection,
    inputs: list[dict[str, Any]],
    limit: int,
) -> BaseModel | dict[str, str]:
    graph_uuid = uuid.UUID(graph_id)
    payload = RelatedObjectsInput.model_validate(
        {
            "relation_id": relation_id,
            "direction": direction,
            "inputs": inputs,
            "limit": limit,
        }
    )
    return get_related_objects(
        ctx.session,
        ctx.client,
        owner_id=ctx.owner_id,
        graph_id=graph_uuid,
        payload=payload,
    )


def _ensure_object_in_graph(
    ctx: QueryToolContext, graph_id: uuid.UUID, object_type_id: uuid.UUID
) -> None:
    if ctx.session.get(GraphObject, (graph_id, object_type_id)) is None:
        raise InvalidRequestError("object type is not in the selected graph")


def _result(value: Any) -> dict[str, Any]:
    if isinstance(value, BaseModel):
        return value.model_dump(mode="json")
    return value
