"""一次运行的生命周期。

一条会话一个模型线程、一个「打开的改动集」——模型线程的键约定和草稿的去向都从这里取，
别在别处各写各的。
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.services import changeset, conversation


@dataclass
class RunContext:
    conversation_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID


def thread_config(conversation_id: uuid.UUID) -> dict:
    """模型线程的键约定：一条会话一个线程，所有读写都从这里取，别各写各的。"""
    return {"configurable": {"thread_id": str(conversation_id)}}


def prepare_run(session: Session, *, conversation_id: uuid.UUID, owner_id: uuid.UUID) -> RunContext:
    """保证一次对话有一个打开的改动集，草稿都往它里面写。"""
    conv = conversation.get_conversation(session, conversation_id, owner_id=owner_id)
    open_sets = [
        cs
        for cs in changeset.list_changesets(session, graph_id=conv.graph_id, owner_id=owner_id)
        if cs.status == changeset.OPEN
    ]
    cs = (
        open_sets[0]
        if open_sets
        else changeset.create_changeset(session, graph_id=conv.graph_id, owner_id=owner_id)
    )
    return RunContext(conversation_id=conv.id, graph_id=conv.graph_id, changeset_id=cs.id)
