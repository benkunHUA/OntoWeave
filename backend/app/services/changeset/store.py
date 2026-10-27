"""改动集的读取、加锁与开放状态管理。"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import Changeset, ChangesetOp
from app.services import graph
from app.services.changeset.common import OP_DELETE, OPEN


def create_changeset(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID | None = None
) -> Changeset:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    cs = Changeset(graph_id=graph_id, status=OPEN)
    session.add(cs)
    session.flush()
    return cs


def get_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    cs = session.get(Changeset, changeset_id)
    if cs is None:
        raise NotFoundError(f"changeset {changeset_id} not found")
    if owner_id is not None:
        graph.get_graph(session, cs.graph_id, owner_id=owner_id)
    return cs


def lock_changeset(
    session: Session, changeset_id: uuid.UUID, *, owner_id: uuid.UUID | None = None
) -> Changeset:
    """拿改动集行锁。

    record / confirm / reject 都从这里进入，保证「检查状态再写」是原子的。
    第二个并发请求会等锁，拿到锁后再看到最新状态，而不是基于旧快照继续写。
    """
    cs = session.scalar(
        select(Changeset)
        .where(Changeset.id == changeset_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    if cs is None:
        raise NotFoundError(f"changeset {changeset_id} not found")
    if owner_id is not None:
        graph.get_graph(session, cs.graph_id, owner_id=owner_id)
    return cs


def list_changesets(
    session: Session,
    *,
    graph_id: uuid.UUID,
    owner_id: uuid.UUID | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[Changeset]:
    graph.get_graph(session, graph_id, owner_id=owner_id)
    return list(
        session.scalars(
            select(Changeset)
            .where(Changeset.graph_id == graph_id)
            .order_by(Changeset.created_at.desc(), Changeset.id)
            .limit(limit)
            .offset(offset)
        )
    )


def get_open_changeset(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID | None = None
) -> Changeset | None:
    """图上当前未定稿的改动集。同一时刻最多只该有一个，取最新的那个。"""
    graph.get_graph(session, graph_id, owner_id=owner_id)
    return session.scalar(
        select(Changeset)
        .where(Changeset.graph_id == graph_id, Changeset.status == OPEN)
        .order_by(Changeset.created_at.desc(), Changeset.id)
    )


def ensure_open_changeset(
    session: Session, *, graph_id: uuid.UUID, owner_id: uuid.UUID | None = None
) -> Changeset:
    """取未定稿的改动集，没有就新建一个。界面编辑要有地方落，不必先手动建。"""
    cs = get_open_changeset(session, graph_id=graph_id, owner_id=owner_id)
    if cs is not None:
        return cs
    return create_changeset(session, graph_id=graph_id, owner_id=owner_id)


def list_ops(session: Session, changeset_id: uuid.UUID) -> list[ChangesetOp]:
    return list(
        session.scalars(
            select(ChangesetOp)
            .where(ChangesetOp.changeset_id == changeset_id)
            .order_by(ChangesetOp.created_at, ChangesetOp.id)
        )
    )


def pending_delete_ids(
    session: Session, changeset_id: uuid.UUID, element_kind: str
) -> set[uuid.UUID]:
    """这个改动集里已经标记删除、但还没真删的元素 id。

    草稿阶段的删除是延后的，行还在库里；读树、算替换计划时要把它们排除，否则第二次
    保存会把上一轮标删的东西又当成现有元素留下。
    """
    return {
        op.target_id
        for op in list_ops(session, changeset_id)
        if op.op == OP_DELETE
        and op.target_id is not None
        and op.payload.get("kind") == element_kind
    }
