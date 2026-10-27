"""数据整理 Agent 的结构化写入与动作工具。"""

from __future__ import annotations

import json
import uuid
from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool, tool

import app.agent.tools.data_pipeline.common as common
import app.agent.tools.data_pipeline.constants as constants
import app.agent.tools.data_pipeline.ops as ops
from app.agent.tools.data_pipeline.context import DataPipelineToolContext


class ToolArgumentError(ValueError):
    """模型传入了工具 schema 之外的参数形状。"""


def _object_argument(value: Any, field: str) -> dict[str, Any] | None:
    """兼容模型把 JSON 对象序列化成字符串再调用工具的情况。"""
    if value is None:
        return None
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        if not value.strip():
            return None
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError as exc:
            raise ToolArgumentError(f"{field} 必须是 JSON 对象，不能是无效 JSON 字符串") from exc
        if isinstance(parsed, dict):
            return parsed
    raise ToolArgumentError(f"{field} 必须是 JSON 对象")


def build_write_tools(
    ctx: DataPipelineToolContext,
    interrupt_fn: Callable[[dict], Any],
) -> list[BaseTool]:
    @tool("add_pipeline_node")
    def add_pipeline_node(
        type: str,
        name: str,
        node_config: dict[str, Any] | str | None = None,
        position: dict | None = None,
        node_id: str | None = None,
    ) -> dict:
        """新增节点；type 必须来自目录，node_config 传 JSON 对象而非 JSON 字符串。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        try:
            normalized_config = _object_argument(node_config, "node_config") or {}
        except ToolArgumentError as exc:
            return {"error": "INVALID_ARGUMENT", "message": str(exc)}
        fields = {
            "nodeId": node_id or str(uuid.uuid4()),
            "nodeType": type,
            "name": name,
            "config": normalized_config,
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
        node_config: dict[str, Any] | str | None = None,
    ) -> dict:
        """更新节点名称或配置；node_config 传 JSON 对象而非 JSON 字符串。"""
        blocked = common.write_guard(ctx)
        if blocked is not None:
            return blocked
        try:
            normalized_config = _object_argument(node_config, "node_config")
        except ToolArgumentError as exc:
            return {"error": "INVALID_ARGUMENT", "message": str(exc)}
        return common.write(
            ctx,
            common.payload(
                ctx,
                "update_node",
                nodeId=node_id,
                name=name,
                config=normalized_config,
            ),
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
        """以当前草稿 revision 预览节点，最多返回 5 条样例行。"""
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
                    "rowLimit": constants.PREVIEW_ROW_LIMIT,
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
