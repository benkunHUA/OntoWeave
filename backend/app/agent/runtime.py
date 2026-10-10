from __future__ import annotations

import json
import time
import uuid
from collections.abc import Iterator
from dataclasses import dataclass
from typing import Any

from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    BaseMessage,
    HumanMessage,
    ToolMessage,
)
from langgraph.types import Command
from sqlalchemy.orm import Session, sessionmaker

from app.agent import graph as agent_graph
from app.agent import llm
from app.agent.tools import ToolContext, build_model_tools, build_view_subagent
from app.config import Settings
from app.services import changeset, conversation, embedding, view_state

# 前后端约定的输出协议版本，加字段可以不动它，改语义时必须抬。
PROTOCOL_VERSION = 1
# 工具结果进 SSE 和入库前都截断，避免长 JSON 撑爆消息。
TOOL_RESULT_LIMIT = 2000
# langgraph 嵌套子图用 "|" 拼命名空间；顶层节点没有它。
_SUBGRAPH_SEP = "|"
_STREAM_MODES = ("updates", "messages", "values", "custom")


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
        subagents=[build_view_subagent(ctx)],
        checkpointer=checkpointer,
    )


def _sse(payload: dict) -> str:
    return f"data: {json.dumps({'v': PROTOCOL_VERSION, **payload}, ensure_ascii=False)}\n\n"


def _jsonable(value: Any) -> Any:
    """把 LLM 给的参数压成一定能落库、能序列化的形状。"""
    try:
        return json.loads(json.dumps(value, default=str, ensure_ascii=False))
    except (TypeError, ValueError):
        return str(value)


def _content_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and isinstance(item.get("text"), str):
                parts.append(item["text"])
        return "".join(parts)
    return "" if content is None else str(content)


def _block_fragments(message: BaseMessage) -> list[tuple[str, str]]:
    """按出现顺序取出消息里的思考 / 正文片段。

    用 langchain 统一后的 content block，provider 差异（DeepSeek 的
    reasoning_content 等）已经被它归一成 reasoning 块，这里不用再分辨。
    工具调用不放这儿，交给 updates 统一处理，免得两条流各算一遍。
    """
    fragments: list[tuple[str, str]] = []
    for block in getattr(message, "content_blocks", None) or []:
        if not isinstance(block, dict):
            continue
        kind = block.get("type")
        if kind == "reasoning":
            piece = block.get("reasoning") or block.get("text")
        elif kind == "text":
            piece = block.get("text")
        else:
            continue
        if isinstance(piece, str) and piece:
            fragments.append((kind, piece))
    return fragments


def _split_chunk(chunk: Any) -> tuple[str, Any]:
    """多 stream_mode 下 langgraph 吐 (mode, payload)；兼容只给 updates 的旧实现。"""
    if (
        isinstance(chunk, tuple)
        and len(chunk) == 2
        and isinstance(chunk[0], str)
        and chunk[0] in _STREAM_MODES
    ):
        return chunk[0], chunk[1]
    return "updates", chunk


def _is_streamable(metadata: dict | None) -> bool:
    """只收主智能体的模型 token：子智能体和隐藏调用不该混进正文流。"""
    metadata = metadata or {}
    node = metadata.get("langgraph_node")
    if node is not None and node != "model":
        return False
    namespace = str(metadata.get("langgraph_checkpoint_ns") or "")
    return _SUBGRAPH_SEP not in namespace


def _normalize_interrupt(value: dict) -> tuple[str, list[dict], dict | None]:
    """把两种中断折成同一份给前端的形状。

    一是 `ask_user` 的提问（question / options），二是工具审批（HumanInTheLoop
    的 action_requests / review_configs）。审批的 resume 必须回填 decisions，
    提问回填一个字符串，所以每个选项直接带上自己的 resume 载荷。

    返回 (问题, 选项, 自由文本回填方式)；自由文本为 None 表示只能点选项。
    """
    question = value.get("question")
    if isinstance(question, str) and question.strip():
        asked_options: list[dict[str, Any]] = [
            {"label": str(item), "answer": str(item)}
            for item in value.get("options") or []
            if str(item).strip()
        ]
        return question, asked_options, {"wrap": "raw"}

    requests = value.get("action_requests")
    if isinstance(requests, list) and requests:
        lines = [
            f"{item.get('name')} {json.dumps(item.get('args') or {}, ensure_ascii=False)}"
            for item in requests
            if isinstance(item, dict)
        ]
        allowed: list[str] = []
        for config in value.get("review_configs") or []:
            if isinstance(config, dict):
                allowed.extend(config.get("allowed_decisions") or [])
        options: list[dict[str, Any]] = []
        if "approve" in allowed:
            options.append(
                {"label": "允许", "answer": {"decisions": [{"type": "approve"}] * len(requests)}}
            )
        if "reject" in allowed:
            options.append(
                {"label": "拒绝", "answer": {"decisions": [{"type": "reject"}] * len(requests)}}
            )
        free_text = {"wrap": "respond"} if "respond" in allowed else None
        return "要执行以下操作，需要你确认：\n" + "\n".join(lines), options, free_text

    # 认不出来的中断也别吞掉，原样带回给前端，至少人能看一眼。
    return f"需要你确认：{json.dumps(value, ensure_ascii=False)}", [], {"wrap": "raw"}


class _TurnBuilder:
    """把一轮回复攒成有序的内容块，同时吐出对应的 SSE 事件。

    块一旦切换类型就收口，因此思考 / 正文 / 工具调用天然按时间交错，
    前端拿到的事件顺序就是最终的消息结构。
    """

    def __init__(self, message_id: str) -> None:
        self.message_id = message_id
        self.blocks: list[dict] = []
        self._open: dict | None = None
        self._tools: dict[str, dict] = {}

    def _close_open(self) -> list[dict]:
        if self._open is None:
            return []
        block, self._open = self._open, None
        if block["kind"] == "reasoning":
            block["done"] = True
        return [{"type": "block.end", "messageId": self.message_id, "blockId": block["id"]}]

    def _append(self, kind: str, text: str) -> list[dict]:
        events: list[dict] = []
        if self._open is None or self._open["kind"] != kind:
            events += self._close_open()
            block: dict = {"id": uuid.uuid4().hex, "kind": kind, "text": ""}
            self.blocks.append(block)
            self._open = block
            events.append(
                {
                    "type": "block.start",
                    "messageId": self.message_id,
                    "blockId": block["id"],
                    "kind": kind,
                }
            )
        self._open["text"] += text
        events.append(
            {
                "type": "block.delta",
                "messageId": self.message_id,
                "blockId": self._open["id"],
                "text": text,
            }
        )
        return events

    def reasoning(self, text: str) -> list[dict]:
        return self._append("reasoning", text)

    def text(self, text: str) -> list[dict]:
        return self._append("text", text)

    def tool_start(self, *, call_id: str, name: str, args: Any) -> list[dict]:
        events = self._close_open()
        block = {
            "id": uuid.uuid4().hex,
            "kind": "tool",
            "callId": call_id,
            "name": name,
            "args": _jsonable(args),
            "status": "running",
            "result": None,
            "truncated": False,
            "durationMs": None,
        }
        self.blocks.append(block)
        self._tools[call_id] = block
        events.append(
            {
                "type": "tool.start",
                "messageId": self.message_id,
                "callId": call_id,
                "name": name,
                "args": block["args"],
            }
        )
        return events

    def tool_end(self, *, call_id: str, status: str, result: str, duration_ms: int) -> list[dict]:
        truncated = len(result) > TOOL_RESULT_LIMIT
        stored = result[:TOOL_RESULT_LIMIT]
        block = self._tools.get(call_id)
        if block is not None:
            block.update(
                status=status,
                result=stored,
                truncated=truncated,
                durationMs=duration_ms,
            )
        return [
            {
                "type": "tool.end",
                "messageId": self.message_id,
                "callId": call_id,
                "status": status,
                "result": stored,
                "truncated": truncated,
                "durationMs": duration_ms,
            }
        ]

    def finish(self) -> list[dict]:
        return self._close_open()

    def interrupt(
        self, *, question: str, options: list[dict], free_text: dict | None
    ) -> list[dict]:
        """中断也是一块：落库后刷新页面还能原样重建等待回答的状态。"""
        events = self._close_open()
        block: dict = {
            "id": uuid.uuid4().hex,
            "kind": "interrupt",
            "question": question,
            "options": options,
            "freeText": free_text,
        }
        self.blocks.append(block)
        events.append(
            {
                "type": "interrupt",
                "messageId": self.message_id,
                "blockId": block["id"],
                "question": question,
                "options": options,
                "freeText": free_text,
            }
        )
        return events

    @property
    def text_content(self) -> str:
        return "".join(block["text"] for block in self.blocks if block["kind"] == "text")


def stream_run(
    factory: sessionmaker[Session],
    settings: Settings,
    owner_id: uuid.UUID,
    *,
    conversation_id: uuid.UUID,
    checkpointer=None,
    text: str | None = None,
    resume: Any | None = None,
) -> Iterator[str]:
    """跑一轮并推 SSE 事件。

    这里自己管事务，不走请求级依赖注入——流式响应会活过依赖的生命周期。
    """
    session = factory()
    run: RunContext | None = None
    run_id = uuid.uuid4().hex
    message_id = uuid.uuid4().hex
    turn = _TurnBuilder(message_id)
    try:
        run = prepare_run(session, conversation_id=conversation_id, owner_id=owner_id)
        conversation.add_message(
            session,
            conversation_id=conversation_id,
            role="user",
            content=text or "",
        )
        # 用户输入先独立提交，后面的模型或工具失败也不能把这句话丢掉。
        session.commit()
        agent = build_agent_for_run(session, settings, run, owner_id, checkpointer=checkpointer)
        config = {"configurable": {"thread_id": str(conversation_id)}}

        yield _sse(
            {
                "type": "run.start",
                "runId": run_id,
                "conversationId": str(conversation_id),
                "messageId": message_id,
            }
        )
        payload: dict | Command = (
            Command(resume=resume)
            if resume is not None
            else {"messages": [HumanMessage(text or "")]}
        )
        paused = False
        interrupt_question: str | None = None
        # 模型能流式就只看 messages 的增量；不能流式时用 updates 的整条消息兜底，
        # 两种情况都只出一遍，靠这个标记二选一。
        saw_delta = False
        tool_started_at: dict[str, float] = {}

        for chunk in agent.stream(payload, config=config, stream_mode=["updates", "messages"]):
            mode, data = _split_chunk(chunk)
            if mode == "messages":
                message_chunk, metadata = data
                # 完整的 AIMessage 会再从 updates 走一遍，这里只认增量 chunk。
                if not isinstance(message_chunk, AIMessageChunk) or not _is_streamable(metadata):
                    continue
                fragments = _block_fragments(message_chunk)
                if fragments:
                    saw_delta = True
                for kind, piece in fragments:
                    events = turn.reasoning(piece) if kind == "reasoning" else turn.text(piece)
                    for event in events:
                        yield _sse(event)
                continue

            for node, update in (data or {}).items():
                if node == "__interrupt__":
                    paused = True
                    for item in update if isinstance(update, (list, tuple)) else [update]:
                        value = getattr(item, "value", None) or {}
                        question, options, free_text = _normalize_interrupt(value)
                        interrupt_question = question or interrupt_question
                        for event in turn.interrupt(
                            question=question, options=options, free_text=free_text
                        ):
                            yield _sse(event)
                    continue
                for message in (update or {}).get("messages", []) or []:
                    if isinstance(message, ToolMessage):
                        call_id = getattr(message, "tool_call_id", "") or ""
                        started = tool_started_at.pop(call_id, None)
                        duration = int((time.monotonic() - started) * 1000) if started else 0
                        status = "error" if getattr(message, "status", None) == "error" else "ok"
                        for event in turn.tool_end(
                            call_id=call_id,
                            status=status,
                            result=_content_text(getattr(message, "content", "")),
                            duration_ms=duration,
                        ):
                            yield _sse(event)
                        continue
                    if not isinstance(message, AIMessage):
                        continue
                    # 正文/思考既然已经流式出过，就不再回放整条；工具调用永远走这里。
                    if not saw_delta:
                        for kind, piece in _block_fragments(message):
                            events = (
                                turn.reasoning(piece) if kind == "reasoning" else turn.text(piece)
                            )
                            for event in events:
                                yield _sse(event)
                    for call in getattr(message, "tool_calls", None) or []:
                        call_id = str(call.get("id") or uuid.uuid4().hex)
                        tool_started_at[call_id] = time.monotonic()
                        for event in turn.tool_start(
                            call_id=call_id,
                            name=str(call.get("name") or ""),
                            args=call.get("args") or {},
                        ):
                            yield _sse(event)

        for event in turn.finish():
            yield _sse(event)

        assistant_text = turn.text_content or (interrupt_question or "")
        if assistant_text or turn.blocks:
            conversation.add_message(
                session,
                conversation_id=conversation_id,
                role="assistant",
                content=assistant_text,
                blocks=turn.blocks or None,
            )
        session.commit()
        ops = [
            {"op": op.op, "kind": op.payload.get("kind")}
            for op in changeset.list_ops(session, run.changeset_id)
        ]
        saved_view = view_state.get_view_state(
            session,
            graph_id=run.graph_id,
            user_id=owner_id,
            owner_id=owner_id,
        )
        yield _sse(
            {
                "type": "state.update",
                "changesetId": str(run.changeset_id),
                "ops": ops,
                "view": saved_view.spec if saved_view is not None else {},
            }
        )
        # paused 必须显式给出来：否则前端分不清「本轮做完」和「卡在中途等人回答」，
        # 两个都是 run.end，却要走向完全不同的交互。
        yield _sse({"type": "run.end", "messageId": message_id, "paused": paused})
    except Exception as exc:
        session.rollback()
        if run is not None:
            try:
                conversation.add_message(
                    session,
                    conversation_id=conversation_id,
                    role="assistant",
                    content=f"{type(exc).__name__}: {exc}",
                    kind="error",
                )
                session.commit()
            except Exception:
                session.rollback()
        yield _sse({"type": "run.error", "message": f"{type(exc).__name__}: {exc}"})
    finally:
        session.close()
