from __future__ import annotations

import threading
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

from deepagents import SubAgent
from langchain_core.tools import BaseTool, tool
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.agent import ontology_tools, view_tools
from app.errors import NotFoundError
from app.models import ObjectType, Relation
from app.services import changeset, embedding, graph, view_state
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE

VIEW_AGENT_PROMPT = """你是 OntoWeave 的绘图子智能体，只决定本体图怎么呈现。

规矩：
1. 你不改本体。不要创建、修改、删除对象类型、属性或关系。
2. 你只产出声明式视图规格：布局、高亮、弱化、分组、折叠、过滤、视野、标注。
3. 改视图前先调 get_view_state；需要节点 ID 时先调 get_ontology_slice，不要编 ID。
4. 不要产出像素坐标，不负责真实画布布局；前端会根据声明式规格计算。
5. 说话简短，告诉用户你看图的方式发生了什么变化。
"""


def _capture(ctx: ToolContext, call: Callable[[], Any]) -> Any:
    """执行工具并把异常转成结构化错误。

    deepagents 的 ToolNode 默认会把工具异常直接抛出去，整轮 run 就死了。
    而我们的领域错误（找不到、状态非法）在设计上是预期内的——模型拿过期 id
    来查是常事。所以这里统一兜住，让模型看到错误后自己纠正。

    参数是一个无参可调用而不是 (fn, *args)：这样连**构造参数**时的异常
    （比如 uuid.UUID("不是 uuid")）也一并兜住，不然模型编个坏 id 就能掀翻整轮。

    执行时加一把**每次运行一把**的锁：deepagents 的 ToolNode 会在线程池里并发跑
    同一轮的多个工具调用，而它们共用同一个 SQLAlchemy Session——Session 不是
    线程安全的，并发的 add/flush 会互相踩。串行化保住了「一次运行一个事务边界」，
    而不是给每个工具另开 Session 把事务边界打散。
    """
    try:
        with ctx.lock:
            return call()
    except Exception as exc:
        return {"error": type(exc).__name__, "message": str(exc)}


@dataclass(frozen=True)
class ToolContext:
    """一次运行里工具需要的全部外部依赖。模型看不见它，由闭包注入。"""

    session: Session
    owner_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID
    embedder: embedding.Embedder
    # 同一轮里模型可能并发调多个工具，它们共用一个 Session，必须串行。
    lock: threading.Lock = field(default_factory=threading.Lock, compare=False)


def build_model_tools(ctx: ToolContext) -> list[BaseTool]:
    """把建模工具绑上运行上下文，包成模型可调用的工具。"""

    @tool("search_ontology")
    def search_ontology(query: str, limit: int = 10) -> list[dict] | dict:
        """按语义检索已有对象类型。只返回已确认的类型（草稿没有向量、不在结果里）；
        新建类型之前必须先调用，避免造重复。"""
        return _capture(
            ctx,
            lambda: ontology_tools.search_ontology(
                ctx.session,
                query=query,
                embedder=ctx.embedder,
                owner_id=ctx.owner_id,
                limit=limit,
            ),
        )

    @tool("list_types")
    def list_types(limit: int = 50) -> list[dict] | dict:
        """列出现有对象类型。"""
        return _capture(
            ctx, lambda: ontology_tools.list_types(ctx.session, owner_id=ctx.owner_id, limit=limit)
        )

    @tool("get_subgraph")
    def get_subgraph(object_type_id: str) -> dict:
        """从给定对象类型出发走一跳，返回它周围的节点与关系。"""
        return _capture(
            ctx,
            lambda: ontology_tools.get_subgraph(
                ctx.session,
                graph_id=ctx.graph_id,
                object_type_id=uuid.UUID(object_type_id),
                owner_id=ctx.owner_id,
            ),
        )

    @tool("propose_type")
    def propose_type(name: str, description: str | None = None) -> dict:
        """新建对象类型并加进草稿。只负责建：先 search_ontology 查重，确认没有可复用的再用它。"""
        return _capture(
            ctx,
            lambda: ontology_tools.propose_type(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                name=name,
                description=description,
            ),
        )

    @tool("reuse_type")
    def reuse_type(object_type_id: str) -> dict:
        """把已有对象类型并进当前图，不新建。用户确认复用后用它，id 取自 search_ontology。"""
        return _capture(
            ctx,
            lambda: ontology_tools.reuse_type(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                object_type_id=uuid.UUID(object_type_id),
            ),
        )

    @tool("propose_property")
    def propose_property(
        owner_type_id: str,
        name: str,
        datatype: str = DEFAULT_DATATYPE,
        required: bool = False,
        is_array: bool = False,
        is_primary_key: bool = False,
        is_title_key: bool = False,
    ) -> dict:
        """给对象类型加属性。datatype 取 text/integer/decimal/boolean/date/timestamp；
        is_array 表示多值，is_primary_key/is_title_key 各类型至多一个。
        用户明确列出的属性和字段逐项用它，不要改成关系。"""
        return _capture(
            ctx,
            lambda: ontology_tools.propose_property(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                owner_type_id=uuid.UUID(owner_type_id),
                name=name,
                datatype=datatype,
                required=required,
                is_array=is_array,
                is_primary_key=is_primary_key,
                is_title_key=is_title_key,
            ),
        )

    @tool("propose_relation")
    def propose_relation(
        source_type_id: str,
        target_type_id: str,
        name: str,
        cardinality: str = DEFAULT_CARDINALITY,
        forward_label: str | None = None,
        reverse_label: str | None = None,
        direction: str = "forward",
    ) -> dict:
        """加一条关系，只在用户明确要求关系或确认后才能使用；两端必须都已存在。
        cardinality 取 one_to_one/one_to_many/many_to_many；
        forward_label / reverse_label 是源端与目标端各自的可读名（如 客户—下订单→订单）。"""
        return _capture(
            ctx,
            lambda: ontology_tools.propose_relation(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                source_type_id=uuid.UUID(source_type_id),
                target_type_id=uuid.UUID(target_type_id),
                name=name,
                direction=direction,
                cardinality=cardinality,
                forward_label=forward_label,
                reverse_label=reverse_label,
            ),
        )

    @tool("update_element")
    def update_element(element_kind: str, element_id: str, patch: dict) -> dict:
        """改已有元素。只提交 patch；改对象类型的名称/描述会自动重算检索用的向量。"""
        return _capture(
            ctx,
            lambda: ontology_tools.update_element(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                element_kind=element_kind,
                element_id=uuid.UUID(element_id),
                patch=patch,
                embedder=ctx.embedder,
            ),
        )

    @tool("remove_element")
    def remove_element(element_kind: str, element_id: str) -> dict:
        """标记删除。草稿阶段不真删，用户确认时才删。"""
        return _capture(
            ctx,
            lambda: ontology_tools.remove_element(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                element_kind=element_kind,
                element_id=uuid.UUID(element_id),
            ),
        )

    @tool("ask_user")
    def ask_user(
        question: str,
        options: list[str] | None = None,
        details: list[dict] | None = None,
    ) -> str:
        """拿不准时停下来问用户。这是中断点，用户回答后你会拿到答案继续。

        只在真的无法判断时才用：同名不同义、一个词可能是对象也可能是属性、关系方向不确定。
        details 是给用户看的补充信息，形如 [{"label": "描述", "value": "..."}]；
        让用户确认复用某个类型时，把它的名称、描述、状态都放进来。
        """
        # 由 LangGraph 的 interrupt 在工具内部暂停，见下方 build_ask_user 的说明
        from langgraph.types import interrupt

        answer = interrupt(
            {"question": question, "options": options or [], "details": details or []}
        )
        return str(answer)

    return [
        search_ontology,
        list_types,
        get_subgraph,
        propose_type,
        reuse_type,
        propose_property,
        propose_relation,
        update_element,
        remove_element,
        ask_user,
    ]


def _view_spec(ctx: ToolContext) -> dict:
    current = view_state.get_view_state(
        ctx.session,
        graph_id=ctx.graph_id,
        user_id=ctx.owner_id,
        owner_id=ctx.owner_id,
    )
    return dict(current.spec) if current is not None else {}


def _save_view_spec(ctx: ToolContext, *, spec: dict) -> dict:
    saved = view_state.upsert_view_state(
        ctx.session,
        graph_id=ctx.graph_id,
        user_id=ctx.owner_id,
        spec=spec,
        owner_id=ctx.owner_id,
    )
    return {"name": saved.name, "spec": saved.spec}


def _apply_view_change(ctx: ToolContext, change: Callable[[dict], dict]) -> dict:
    current = view_state.get_view_state(
        ctx.session,
        graph_id=ctx.graph_id,
        user_id=ctx.owner_id,
        owner_id=ctx.owner_id,
    )
    spec = dict(current.spec) if current is not None else {}
    updated = change(spec)
    # 先挂进改动集再落库：改动集若已关闭，这里直接报错，视图也不动。
    # 反过来先落库后记录，一旦记录失败就会留下「图变了、撤销记录里却没有」的漏网改动。
    changeset.record_view_change(
        ctx.session,
        changeset_id=ctx.changeset_id,
        user_id=ctx.owner_id,
        before=spec,
        after=dict(updated),
        owner_id=ctx.owner_id,
    )
    return _save_view_spec(ctx, spec=updated)


def build_view_tools(ctx: ToolContext) -> list[BaseTool]:
    """把声明式视图函数包成绘图子智能体可调用的工具。"""

    @tool("get_view_state")
    def get_view_state_tool() -> dict:
        """读取当前用户对这张图的视图状态。"""
        return _capture(
            ctx,
            lambda: {
                "name": (
                    current.name
                    if (
                        current := view_state.get_view_state(
                            ctx.session,
                            graph_id=ctx.graph_id,
                            user_id=ctx.owner_id,
                            owner_id=ctx.owner_id,
                        )
                    )
                    else None
                ),
                "spec": _view_spec(ctx),
            },
        )

    @tool("get_ontology_slice")
    def get_ontology_slice(node_ids: list[str] | None = None) -> dict:
        """读取图里的节点和关系；传 node_ids 时只返回这些节点及它们之间的边。"""

        def read_slice() -> dict:
            graph.get_graph(ctx.session, ctx.graph_id, owner_id=ctx.owner_id)
            present = set(graph.list_graph_objects(ctx.session, ctx.graph_id))
            requested = {uuid.UUID(node_id) for node_id in (node_ids or [])}
            missing = requested - present
            if missing:
                raise NotFoundError(
                    "object types are not in this graph: "
                    + ", ".join(sorted(str(item) for item in missing))
                )
            selected = requested or present

            relation_ids = graph.list_graph_relations(ctx.session, ctx.graph_id)
            relations = (
                list(ctx.session.scalars(select(Relation).where(Relation.id.in_(relation_ids))))
                if relation_ids
                else []
            )
            nodes = list(
                ctx.session.scalars(
                    select(ObjectType)
                    .where(ObjectType.id.in_(selected))
                    .order_by(ObjectType.name, ObjectType.id)
                )
            )
            connected = [
                relation
                for relation in relations
                if relation.source_type_id in selected and relation.target_type_id in selected
            ]
            return {
                "nodes": [
                    {
                        "id": str(node.id),
                        "name": node.name,
                        "description": node.description,
                        "status": node.status,
                    }
                    for node in nodes
                ],
                "relations": [
                    {
                        "id": str(relation.id),
                        "name": relation.name,
                        "source_type_id": str(relation.source_type_id),
                        "target_type_id": str(relation.target_type_id),
                        "direction": relation.direction,
                        "cardinality": relation.cardinality,
                        "forward_label": relation.forward_label,
                        "reverse_label": relation.reverse_label,
                        "status": relation.status,
                    }
                    for relation in connected
                ],
            }

        return _capture(ctx, read_slice)

    @tool("set_layout")
    def set_layout(kind: str, options: dict | None = None) -> dict:
        """设置布局：hierarchical / force / domain-grouped / grid。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx, lambda spec: view_tools.set_layout(spec, kind=kind, options=options)
            ),
        )

    @tool("focus")
    def focus(node_ids: list[str]) -> dict:
        """把视野移到指定节点。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(ctx, lambda spec: view_tools.focus(spec, node_ids=node_ids)),
        )

    @tool("fit_view")
    def fit_view(node_ids: list[str] | None = None) -> dict:
        """适应画面；可指定只适应这些节点。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx, lambda spec: view_tools.fit_view(spec, node_ids=node_ids or [])
            ),
        )

    @tool("reset_view")
    def reset_view() -> dict:
        """清空视图设置，回到默认呈现。"""
        return _capture(ctx, lambda: _apply_view_change(ctx, view_tools.reset_view))

    @tool("highlight")
    def highlight(node_ids: list[str]) -> dict:
        """高亮指定节点。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx, lambda spec: view_tools.highlight(spec, node_ids=node_ids)
            ),
        )

    @tool("dim")
    def dim(node_ids: list[str]) -> dict:
        """弱化指定节点。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(ctx, lambda spec: view_tools.dim(spec, node_ids=node_ids)),
        )

    @tool("group")
    def group(group_id: str, label: str, node_ids: list[str]) -> dict:
        """把节点归进一个命名分组。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx,
                lambda spec: view_tools.group(
                    spec, group_id=group_id, label=label, node_ids=node_ids
                ),
            ),
        )

    @tool("collapse")
    def collapse(group_ids: list[str]) -> dict:
        """折叠分组。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx, lambda spec: view_tools.collapse(spec, group_ids=group_ids)
            ),
        )

    @tool("expand")
    def expand(group_ids: list[str]) -> dict:
        """展开分组。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx, lambda spec: view_tools.expand(spec, group_ids=group_ids)
            ),
        )

    @tool("filter_view")
    def filter_view(predicate: dict | None = None) -> dict:
        """按声明式条件过滤显示。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx, lambda spec: view_tools.filter_view(spec, predicate=predicate)
            ),
        )

    @tool("annotate")
    def annotate(node_id: str, text: str) -> dict:
        """给节点加一条标注。"""
        return _capture(
            ctx,
            lambda: _apply_view_change(
                ctx,
                lambda spec: view_tools.annotate(spec, node_id=node_id, text=text),
            ),
        )

    @tool("save_view")
    def save_view() -> dict:
        """保存当前视图的布局与呈现设置。"""
        return _capture(ctx, lambda: _save_view_spec(ctx, spec=_view_spec(ctx)))

    @tool("load_view")
    def load_view() -> dict:
        """读取当前保存的视图。"""
        return get_view_state_tool.invoke({})

    return [
        get_view_state_tool,
        get_ontology_slice,
        set_layout,
        focus,
        fit_view,
        reset_view,
        highlight,
        dim,
        group,
        collapse,
        expand,
        filter_view,
        annotate,
        save_view,
        load_view,
    ]


def build_view_subagent(ctx: ToolContext) -> SubAgent:
    """构造负责「图怎么呈现」的隔离子智能体。"""
    return {
        "name": "view_designer",
        "description": (
            "负责图的呈现方式：布局、高亮、分组、折叠、过滤、视野、标注和视图命名。"
            "用户要求「怎么画/怎么看」时交给它；它不会修改本体。"
            "它自己会调用 get_ontology_slice 读取当前图，无需你先提供节点/关系清单。"
        ),
        "system_prompt": VIEW_AGENT_PROMPT,
        "tools": build_view_tools(ctx),
    }
