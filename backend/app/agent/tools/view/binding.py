"""绘图子智能体的工具装配。

视图操作本身是纯变换（`ops.py`），这里只负责三件事：读工具怎么查、按注册表把纯变换
包成模型可调用的工具、以及写之前先记进改动集再落库。
"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool, StructuredTool, tool

from app.agent.tools.graph_read import load_membership, render_slice
from app.agent.tools.ontology.context import OntologyToolContext
from app.agent.tools.view.registry import VIEW_OPS, ViewOp
from app.services import changeset, view_state


def _read_view_state(ctx: OntologyToolContext) -> dict:
    current = view_state.get_view_state(
        ctx.session,
        graph_id=ctx.graph_id,
        user_id=ctx.owner_id,
        owner_id=ctx.owner_id,
    )
    return {
        "name": current.name if current is not None else None,
        "spec": dict(current.spec) if current is not None else {},
    }


def _read_ontology_slice(ctx: OntologyToolContext, node_ids: list[str] | None) -> dict:
    membership = load_membership(ctx.session, graph_id=ctx.graph_id, owner_id=ctx.owner_id)
    selected = {uuid.UUID(node_id) for node_id in node_ids or []}
    return render_slice(ctx.session, membership=membership, node_ids=selected or None)


def _save_spec(ctx: OntologyToolContext, *, spec: dict) -> dict:
    saved = view_state.upsert_view_state(
        ctx.session,
        graph_id=ctx.graph_id,
        user_id=ctx.owner_id,
        spec=spec,
        owner_id=ctx.owner_id,
    )
    return {"name": saved.name, "spec": saved.spec}


def _apply_change(ctx: OntologyToolContext, change: Callable[[dict], dict]) -> dict:
    """改一次视图：先记进改动集再落库。

    顺序不能反：改动集若已关闭，这里直接报错，视图也不动；反过来先落库后记录，
    一旦记录失败就会留下「图变了、撤销记录里却没有」的漏网改动。
    """
    spec = _read_view_state(ctx)["spec"]
    updated = change(spec)
    changeset.record_view_change(
        ctx.session,
        changeset_id=ctx.changeset_id,
        user_id=ctx.owner_id,
        before=spec,
        after=dict(updated),
        owner_id=ctx.owner_id,
    )
    return _save_spec(ctx, spec=updated)


def _op_tool(ctx: OntologyToolContext, op: ViewOp) -> BaseTool:
    def run(**kwargs: Any) -> dict:
        return ctx.invoke(lambda: _apply_change(ctx, lambda spec: op.apply(spec, **kwargs)))

    return StructuredTool.from_function(
        func=run,
        name=op.name,
        description=op.description,
        args_schema=op.args,
    )


def build_view_tools(ctx: OntologyToolContext) -> list[BaseTool]:
    """把视图能力绑上运行上下文，包成模型可调用的工具。"""

    @tool("get_view_state")
    def get_view_state() -> dict:
        """读取当前用户对这张图的视图状态。"""
        return ctx.invoke(lambda: _read_view_state(ctx))

    @tool("get_ontology_slice")
    def get_ontology_slice(node_ids: list[str] | None = None) -> dict:
        """读取图里的节点和关系；传 node_ids 时只返回这些节点及它们之间的边。"""
        return ctx.invoke(lambda: _read_ontology_slice(ctx, node_ids))

    @tool("save_view")
    def save_view() -> dict:
        """保存当前视图的布局与呈现设置。"""
        return ctx.invoke(lambda: _save_spec(ctx, spec=_read_view_state(ctx)["spec"]))

    @tool("load_view")
    def load_view() -> dict:
        """读取当前保存的视图。"""
        return ctx.invoke(lambda: _read_view_state(ctx))

    return [
        get_view_state,
        get_ontology_slice,
        *(_op_tool(ctx, op) for op in VIEW_OPS),
        save_view,
        load_view,
    ]
