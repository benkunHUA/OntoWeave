"""主 Agent 的图召回与图详情工具。"""

from __future__ import annotations

import uuid

from langchain_core.tools import BaseTool, tool

from app.agent.tools.query.context import QueryToolContext
from app.services import graph_search


def build_query_selection_tools(ctx: QueryToolContext) -> list[BaseTool]:
    @tool("search_graphs")
    def search_graphs(query: str, limit: int = 5) -> list[dict] | dict:
        """按业务问题召回已发布业务图。返回图 ID、相似度和图卡，不返回对象实例。"""
        return ctx.invoke(
            lambda: graph_search.search_graphs(
                ctx.session,
                owner_id=ctx.owner_id,
                query=query,
                embedder=ctx.embedder,
                limit=max(1, min(limit, 10)),
            )
        )

    @tool("get_graph_detail")
    def get_graph_detail(graph_id: str) -> dict:
        """读取已发布图的类型层详情：对象类型、属性、关系、方向和基数。"""
        return ctx.invoke(
            lambda: graph_search.get_graph_detail(
                ctx.session,
                graph_id=uuid.UUID(graph_id),
                owner_id=ctx.owner_id,
            )
        )

    return [search_graphs, get_graph_detail]
