from __future__ import annotations

import json
import uuid
from collections.abc import Iterator
from dataclasses import dataclass

from langchain_core.messages import HumanMessage
from langgraph.types import Command
from sqlalchemy.orm import Session, sessionmaker

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.tools import ToolContext, build_model_tools
from app.config import Settings
from app.services import changeset, conversation, embedding


@dataclass
class RunContext:
    conversation_id: uuid.UUID
    graph_id: uuid.UUID
    changeset_id: uuid.UUID


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


def build_agent_for_run(
    session: Session,
    settings: Settings,
    run: RunContext,
    owner_id: uuid.UUID,
    *,
    checkpointer=None,
):
    ctx = ToolContext(
        session=session,
        owner_id=owner_id,
        graph_id=run.graph_id,
        changeset_id=run.changeset_id,
        embedder=embedding.build_embedder(settings),
    )
    return agent_graph.build_agent(
        llm.build_chat_model(settings),
        build_model_tools(ctx),
        checkpointer=checkpointer,
    )


def _sse(payload: dict) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def stream_run(
    factory: sessionmaker[Session],
    settings: Settings,
    owner_id: uuid.UUID,
    *,
    conversation_id: uuid.UUID,
    checkpointer=None,
    text: str | None = None,
    resume: str | None = None,
) -> Iterator[str]:
    """跑一轮并推 SSE 事件。

    这里自己管事务，不走请求级依赖注入——流式响应会活过依赖的生命周期。
    """
    session = factory()
    try:
        run = prepare_run(session, conversation_id=conversation_id, owner_id=owner_id)
        agent = build_agent_for_run(session, settings, run, owner_id, checkpointer=checkpointer)
        config = {"configurable": {"thread_id": str(conversation_id)}}

        yield _sse({"type": "run.start", "conversationId": str(conversation_id)})
        payload: dict | Command = (
            Command(resume=resume)
            if resume is not None
            else {"messages": [HumanMessage(text or "")]}
        )
        for chunk in agent.stream(payload, config=config, stream_mode="updates"):
            for node, update in chunk.items():
                if node == "__interrupt__":
                    for item in update if isinstance(update, (list, tuple)) else [update]:
                        yield _sse({"type": "interrupt", **(getattr(item, "value", None) or {})})
                    continue
                for message in (update or {}).get("messages", []) or []:
                    for call in getattr(message, "tool_calls", None) or []:
                        yield _sse(
                            {"type": "tool.start", "name": call["name"], "args": call["args"]}
                        )
                    if type(message).__name__ == "ToolMessage":
                        yield _sse({"type": "tool.end", "content": str(message.content)[:500]})
                    elif getattr(message, "content", None):
                        yield _sse({"type": "message.delta", "text": message.content})

        session.commit()
        ops = [
            {"op": op.op, "kind": op.payload.get("kind")}
            for op in changeset.list_ops(session, run.changeset_id)
        ]
        yield _sse({"type": "state.update", "changesetId": str(run.changeset_id), "ops": ops})
        yield _sse({"type": "run.end"})
    except Exception as exc:
        session.rollback()
        yield _sse({"type": "run.error", "message": f"{type(exc).__name__}: {exc}"})
    finally:
        session.close()
