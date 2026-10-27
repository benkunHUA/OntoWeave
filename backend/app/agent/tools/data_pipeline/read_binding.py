"""数据整理 Agent 的只读工具。"""

from __future__ import annotations

import uuid
from typing import Any

from langchain_core.tools import BaseTool, tool

import app.agent.tools.data_pipeline.common as common
import app.agent.tools.data_pipeline.constants as constants
import app.agent.tools.data_pipeline.ops as ops
from app.agent.tools.data_pipeline.context import DataPipelineToolContext


def _read_draft(ctx: DataPipelineToolContext) -> dict:
    def call() -> dict:
        draft = ops.get_draft(
            ctx.client,
            owner_id=ctx.owner_id,
            pipeline_id=ctx.pipeline_id,
        )
        revision = draft.get("revision")
        if isinstance(revision, int):
            ctx.advance_revision(revision)
        return draft

    return ctx.invoke(call)


def build_read_tools(ctx: DataPipelineToolContext) -> list[BaseTool]:
    @tool("get_pipeline_state")
    def get_pipeline_state() -> dict:
        """读取流程名称、状态、当前版本和草稿版本。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ops.get_pipeline_state(
                ctx.client, owner_id=ctx.owner_id, pipeline_id=ctx.pipeline_id
            )
        )

    @tool("get_pipeline_draft")
    def get_pipeline_draft() -> dict:
        """读取完整草稿定义；这是节点、连线与配置的事实来源。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return _read_draft(ctx)

    @tool("get_pipeline_check_result")
    def get_pipeline_check_result() -> dict:
        """重新检查当前草稿，返回阻塞项、警告和下一步动作。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked

        def call() -> dict:
            result = ops.check(
                ctx.client,
                owner_id=ctx.owner_id,
                pipeline_id=ctx.pipeline_id,
                revision=common.write_revision(ctx) or 0,
            )
            ctx.set_check_result(result)
            return result

        return ctx.invoke(call)

    @tool("get_pipeline_events")
    def get_pipeline_events(
        limit: int = constants.EVENT_LIMIT_DEFAULT,
        after_event_id: str | None = None,
    ) -> Any:
        """读取最近的流程事件；传 after_event_id 时从该事件之后按时间正序取。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ops.get_events(
                ctx.session,
                owner_id=ctx.owner_id,
                conversation_id=ctx.conversation_id,
                after_event_id=uuid.UUID(after_event_id) if after_event_id else None,
                limit=max(1, min(limit, constants.EVENT_LIMIT_MAX)),
            )
        )

    @tool("get_node_catalog")
    def get_node_catalog() -> dict:
        """读取唯一可用的节点目录及其配置 schema。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(lambda: ops.get_node_catalog(ctx.client))

    @tool("list_pipeline_input_datasets")
    def list_pipeline_input_datasets(
        limit: int = constants.DATASET_LIMIT_DEFAULT,
        offset: int = 0,
    ) -> dict:
        """列出可选的输入数据集；必须从结果中选择 dataset_id。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ops.list_input_datasets(
                ctx.client,
                owner_id=ctx.owner_id,
                limit=max(1, min(limit, constants.DATASET_LIMIT_MAX)),
                offset=max(0, offset),
            )
        )

    @tool("get_dataset_schema")
    def get_dataset_schema(dataset_id: str) -> dict:
        """读取数据集当前已发布版本的字段结构。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ops.get_dataset_schema(
                ctx.client,
                owner_id=ctx.owner_id,
                dataset_id=uuid.UUID(dataset_id),
            )
        )

    @tool("get_output_sources")
    def get_output_sources(dataset_id: str, version_id: str | None = None) -> dict:
        """读取一个已发布输出的来源、构建和输入版本信息。"""
        blocked = common.state_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ops.get_output_sources(
                ctx.client,
                owner_id=ctx.owner_id,
                pipeline_id=ctx.pipeline_id,
                dataset_id=uuid.UUID(dataset_id),
                version_id=uuid.UUID(version_id) if version_id else None,
            )
        )

    return [
        get_pipeline_state,
        get_pipeline_draft,
        get_pipeline_check_result,
        get_pipeline_events,
        get_node_catalog,
        list_pipeline_input_datasets,
        get_dataset_schema,
        get_output_sources,
    ]
