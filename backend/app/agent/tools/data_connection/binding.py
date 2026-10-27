"""把数据连接只读能力绑成模型工具。"""

from __future__ import annotations

import uuid

from langchain_core.tools import BaseTool, tool

import app.agent.tools.data_connection.ops as ops
from app.agent.tools.common import build_ask_user_tool
from app.agent.tools.data_connection.context import DataConnectionToolContext


def build_data_connection_tools(ctx: DataConnectionToolContext) -> list[BaseTool]:
    @tool("get_data_connection_state")
    def get_data_connection_state() -> dict:
        """读取连接、同步任务、最近运行、数据集和缺失步骤的当前状态。"""
        return ctx.invoke(
            lambda: ops.get_state(
                ctx.client,
                owner_id=ctx.owner_id,
                connection_id=ctx.data_connection_id,
            )
        )

    @tool("get_connection_events")
    def get_connection_events(limit: int = 10, after_event_id: str | None = None) -> list[dict]:
        """读取最近的事件卡片；传 after_event_id 时从该事件之后按时间正序取。"""
        return ctx.invoke(
            lambda: ops.get_events(
                ctx.session,
                owner_id=ctx.owner_id,
                conversation_id=ctx.conversation_id,
                after_event_id=uuid.UUID(after_event_id) if after_event_id else None,
                limit=max(1, min(limit, 50)),
            )
        )

    @tool("list_source_tables")
    def list_source_tables() -> dict:
        """列出源库里的表；只返回元数据，不读取表内容。"""
        return ctx.invoke(
            lambda: ops.list_tables(
                ctx.client,
                owner_id=ctx.owner_id,
                connection_id=ctx.data_connection_id,
            )
        )

    @tool("get_source_table_schema")
    def get_source_table_schema(schema_name: str, table: str) -> dict:
        """读取一张源表的字段、类型和主键；schema_name 与 table 必须来自 list_source_tables。"""
        return ctx.invoke(
            lambda: ops.describe_table(
                ctx.client,
                owner_id=ctx.owner_id,
                connection_id=ctx.data_connection_id,
                schema=schema_name,
                table=table,
            )
        )

    return [
        get_data_connection_state,
        get_connection_events,
        list_source_tables,
        get_source_table_schema,
        build_ask_user_tool(),
    ]
