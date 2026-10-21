"""数据整理 Agent 的结构化写入与动作工具。"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool, tool

from app.agent.tools.data_pipeline import common, constants, ops
from app.agent.tools.data_pipeline.context import DataPipelineToolContext


def build_write_tools(
    ctx: DataPipelineToolContext,
    interrupt_fn: Callable[[dict], Any],
) -> list[BaseTool]:
    @tool("add_pipeline_node")
    def add_pipeline_node(
        type: str,
        name: str,
        config: dict | None = None,
        position: dict | None = None,
        node_id: str | None = None,
    ) -> dict:
        """新增一个节点；type 必须来自节点目录，配置必须是结构化 JSON。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        fields = {
            "nodeId": node_id or str(uuid.uuid4()),
            "nodeType": type,
            "name": name,
            "config": config or {},
        }
        if position is not None:
            fields["position"] = position
        command = common.payload(
            ctx,
            "add_node",
            **fields,
        )
        return common.write(ctx, command)

    @tool("update_pipeline_node")
    def update_pipeline_node(
        node_id: str,
        name: str | None = None,
        config: dict | None = None,
    ) -> dict:
        """更新节点名称或配置；至少提供 name 与 config 之一。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        return common.write(
            ctx,
            common.payload(ctx, "update_node", nodeId=node_id, name=name, config=config),
        )

    @tool("delete_pipeline_node")
    def delete_pipeline_node(node_id: str) -> dict:
        """删除节点及其连线；这是破坏性操作，必须在工具内部确认。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        rejected = common.confirm(interrupt_fn, "delete_node", "删除这个节点及其连线？")
        if rejected is not None:
            return rejected
        return common.write(ctx, common.payload(ctx, "delete_node", nodeId=node_id))

    @tool("connect_pipeline_nodes")
    def connect_pipeline_nodes(
        source_node_id: str,
        source_port: str,
        target_node_id: str,
        target_port: str,
        edge_id: str | None = None,
    ) -> dict:
        """连接两个合法端口；节点与端口必须来自当前草稿和节点目录。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        return common.write(
            ctx,
            common.payload(
                ctx,
                "connect",
                edgeId=edge_id or str(uuid.uuid4()),
                sourceNodeId=source_node_id,
                sourcePort=source_port,
                targetNodeId=target_node_id,
                targetPort=target_port,
            ),
        )

    @tool("disconnect_pipeline_nodes")
    def disconnect_pipeline_nodes(edge_id: str) -> dict:
        """断开一条连线；撤销仍由会话轮次撤销能力提供。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        return common.write(ctx, common.payload(ctx, "disconnect", edgeId=edge_id))

    @tool("set_pipeline_output")
    def set_pipeline_output(
        node_id: str,
        name: str,
        write_mode: str = constants.DEFAULT_WRITE_MODE,
        primary_key_columns: list[str] | None = None,
        expectations: list[dict] | None = None,
    ) -> dict:
        """设置输出节点；修改或覆盖输出必须在工具内部确认。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        rejected = common.confirm(interrupt_fn, "set_output", f"把输出设置为「{name}」？")
        if rejected is not None:
            return rejected
        return common.write(
            ctx,
            common.payload(
                ctx,
                "set_output",
                nodeId=node_id,
                name=name,
                writeMode=write_mode,
                primaryKeyColumns=primary_key_columns or [],
                expectations=expectations or [],
            ),
        )

    @tool("preview_pipeline_node")
    def preview_pipeline_node(node_id: str) -> dict:
        """以当前草稿 revision 预览节点，最多返回有限样例行。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ops.preview(
                ctx.client,
                owner_id=ctx.owner_id,
                pipeline_id=ctx.pipeline_id,
                payload={
                    "draftRevision": common.write_revision(ctx),
                    "nodeId": node_id,
                    "requestId": str(uuid.uuid4()),
                },
            )
        )

    @tool("build_pipeline")
    def build_pipeline() -> dict:
        """触发正式构建；必须由用户确认，Agent 不自动构建。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        rejected = common.confirm(interrupt_fn, "build", "确认开始构建这条流程？")
        if rejected is not None:
            return rejected
        return ctx.invoke(
            lambda: ops.build(
                ctx.client,
                owner_id=ctx.owner_id,
                pipeline_id=ctx.pipeline_id,
                payload={
                    "draftRevision": common.write_revision(ctx),
                    "operationId": common.operation_id(),
                    "turnId": str(ctx.turn_id),
                },
            )
        )

    return [
        add_pipeline_node,
        update_pipeline_node,
        delete_pipeline_node,
        connect_pipeline_nodes,
        disconnect_pipeline_nodes,
        set_pipeline_output,
        preview_pipeline_node,
        build_pipeline,
    ]
