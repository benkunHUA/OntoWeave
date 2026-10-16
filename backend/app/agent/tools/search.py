"""共享的只读检索：一个入口查本体里的对象类型与动作。

工具名就叫 `search`：给 query 走语义检索（只有已确认的元素有向量），不给 query 就按名称
列出全部。两类元素各有自己的检索与列举实现，这里只做分派——主智能体与子智能体都认这一个
入口，不再各自挂一个「列全部」的工具。
"""

from __future__ import annotations

import uuid

from langchain_core.tools import BaseTool, tool
from sqlalchemy.orm import Session

from app.agent.tools.action.ops import action_type_brief
from app.agent.tools.context import ToolContext
from app.agent.tools.graph_read import object_type_brief
from app.errors import InvariantViolationError
from app.services import action, element_kinds, embedding, ontology

#: 检索能覆盖的元素种类；顺序即结果顺序。
SEARCH_KINDS: tuple[str, ...] = (element_kinds.OBJECT_TYPE, element_kinds.ACTION_TYPE)


def search(
    session: Session,
    *,
    owner_id: uuid.UUID,
    embedder: embedding.Embedder,
    query: str | None = None,
    kind: str | None = None,
    limit: int = 10,
) -> list[dict]:
    """查本体元素；`kind` 限定只查一类，`limit` 按每类各算一次。"""
    kinds = (kind,) if kind is not None else SEARCH_KINDS
    found: list[dict] = []
    for element_kind in kinds:
        found.extend(
            _search_kind(
                session,
                kind=element_kind,
                query=query,
                embedder=embedder,
                owner_id=owner_id,
                limit=limit,
            )
        )
    return found


def _search_kind(
    session: Session,
    *,
    kind: str,
    query: str | None,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int,
) -> list[dict]:
    if kind == element_kinds.OBJECT_TYPE:
        return _search_object_types(
            session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
        )
    if kind == element_kinds.ACTION_TYPE:
        return _search_actions(
            session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
        )
    raise InvariantViolationError(
        f"unsupported search kind {kind!r}; expected one of {list(SEARCH_KINDS)}"
    )


def _search_object_types(
    session: Session,
    *,
    query: str | None,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int,
) -> list[dict]:
    if query is None:
        return [
            _entry(element_kinds.OBJECT_TYPE, object_type_brief(item))
            for item in ontology.list_object_types(session, owner_id=owner_id, limit=limit)
        ]
    hits = embedding.search_object_types(
        session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
    )
    return [
        _entry(element_kinds.OBJECT_TYPE, object_type_brief(item), score) for item, score in hits
    ]


def _search_actions(
    session: Session,
    *,
    query: str | None,
    embedder: embedding.Embedder,
    owner_id: uuid.UUID,
    limit: int,
) -> list[dict]:
    if query is None:
        return [
            _entry(element_kinds.ACTION_TYPE, action_type_brief(item))
            for item in action.list_action_types(session, owner_id=owner_id, limit=limit)
        ]
    hits = embedding.search_action_types(
        session, query=query, embedder=embedder, owner_id=owner_id, limit=limit
    )
    return [
        _entry(element_kinds.ACTION_TYPE, action_type_brief(item), score) for item, score in hits
    ]


def _entry(kind: str, brief: dict, score: float | None = None) -> dict:
    entry = {"kind": kind, **brief}
    if score is not None:
        entry["similarity"] = round(score, 4)
    return entry


def build_search_tool(ctx: ToolContext) -> BaseTool:
    """检索本体元素：主智能体与动作子智能体共用同一个只读工具。"""

    @tool("search")
    def search_elements(
        query: str | None = None, kind: str | None = None, limit: int = 10
    ) -> list[dict] | dict:
        """查本体里已有的元素：对象类型与动作。

        给 query 走语义检索，只命中已确认的（草稿没有向量）；不给 query 就按名称列出全部，
        含草稿——看 status 区分。新建任何东西之前先用它查重。
        kind 可选 object_type / action_type；不传则两类都查。limit 是每类各自的条数上限。
        """
        return ctx.invoke(
            lambda: search(
                ctx.session,
                owner_id=ctx.owner_id,
                embedder=ctx.embedder,
                query=query,
                kind=kind,
                limit=limit,
            )
        )

    return search_elements
