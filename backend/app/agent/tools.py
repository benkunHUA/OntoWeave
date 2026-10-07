from __future__ import annotations

import uuid
from dataclasses import dataclass

from langchain_core.tools import BaseTool, tool
from sqlalchemy.orm import Session

from app.agent import ontology_tools
from app.services import embedding


@dataclass(frozen=True)
class ToolContext:
    """一次运行里工具需要的全部外部依赖。模型看不见它，由闭包注入。"""

    session: Session
    owner_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID
    embedder: embedding.Embedder


def build_model_tools(ctx: ToolContext) -> list[BaseTool]:
    """把建模工具绑上运行上下文，包成模型可调用的工具。"""

    @tool("search_ontology")
    def search_ontology(query: str, limit: int = 10) -> list[dict]:
        """按语义检索已有对象类型。新建类型之前必须先调用，避免造重复。"""
        return ontology_tools.search_ontology(
            ctx.session,
            query=query,
            embedder=ctx.embedder,
            owner_id=ctx.owner_id,
            limit=limit,
        )

    @tool("list_types")
    def list_types(limit: int = 50) -> list[dict]:
        """列出现有对象类型。"""
        return ontology_tools.list_types(ctx.session, owner_id=ctx.owner_id, limit=limit)

    @tool("get_subgraph")
    def get_subgraph(object_type_id: str) -> dict:
        """从给定对象类型出发走一跳，返回它周围的节点与关系。"""
        return ontology_tools.get_subgraph(
            ctx.session,
            graph_id=ctx.graph_id,
            object_type_id=uuid.UUID(object_type_id),
            owner_id=ctx.owner_id,
        )

    @tool("propose_type")
    def propose_type(name: str, description: str | None = None) -> dict:
        """把新的对象类型加进草稿。内部先查重，同名或足够像就复用已有的。"""
        return ontology_tools.propose_type(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            name=name,
            description=description,
            embedder=ctx.embedder,
        )

    @tool("propose_property")
    def propose_property(
        owner_type_id: str, name: str, datatype: str = "text", required: bool = False
    ) -> dict:
        """给对象类型加属性。owner_type_id 必须来自 search_ontology 或 list_types。"""
        return ontology_tools.propose_property(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            owner_type_id=uuid.UUID(owner_type_id),
            name=name,
            datatype=datatype,
            required=required,
        )

    @tool("propose_relation")
    def propose_relation(
        source_type_id: str, target_type_id: str, name: str, direction: str = "forward"
    ) -> dict:
        """加一条关系，两端必须都已存在。"""
        return ontology_tools.propose_relation(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            source_type_id=uuid.UUID(source_type_id),
            target_type_id=uuid.UUID(target_type_id),
            name=name,
            direction=direction,
        )

    @tool("update_element")
    def update_element(element_kind: str, element_id: str, patch: dict, before: dict) -> dict:
        """改已有元素。before 是改动前的字段快照，撤销时靠它还原，必须原样带上。"""
        return ontology_tools.update_element(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            element_kind=element_kind,
            element_id=uuid.UUID(element_id),
            patch=patch,
            before=before,
        )

    @tool("remove_element")
    def remove_element(element_kind: str, element_id: str) -> dict:
        """标记删除。草稿阶段不真删，用户确认时才删。"""
        return ontology_tools.remove_element(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            element_kind=element_kind,
            element_id=uuid.UUID(element_id),
        )

    @tool("ask_user")
    def ask_user(question: str, options: list[str] | None = None) -> str:
        """拿不准时停下来问用户。这是中断点，用户回答后你会拿到答案继续。

        只在真的无法判断时才用：同名不同义、一个词可能是对象也可能是属性、关系方向不确定。
        """
        return ""  # 实际由 tools 节点拦截成中断，见 graph.py

    return [
        search_ontology,
        list_types,
        get_subgraph,
        propose_type,
        propose_property,
        propose_relation,
        update_element,
        remove_element,
        ask_user,
    ]
