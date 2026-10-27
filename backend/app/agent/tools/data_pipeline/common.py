"""数据整理工具共享的守卫、确认与写入流程。"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

import app.agent.tools.data_pipeline.constants as constants
import app.agent.tools.data_pipeline.ops as ops
from app.agent.tools.data_pipeline.context import DataPipelineToolContext
from app.clients.data_service import pipeline_mapping as mapping


def state_guard(ctx: DataPipelineToolContext) -> dict | None:
    if ctx.load_error:
        return constants.state_unavailable(ctx.load_error)
    return None


def write_revision(ctx: DataPipelineToolContext) -> int | None:
    return ctx.draft_revision


def write_guard(ctx: DataPipelineToolContext) -> dict | None:
    blocked = state_guard(ctx)
    if blocked is not None:
        return blocked
    if write_revision(ctx) is None:
        return constants.state_unavailable("当前草稿版本尚不可用")
    return None


def operation_id() -> str:
    return str(uuid.uuid4())


def payload(ctx: DataPipelineToolContext, command_type: str, **fields: Any) -> dict:
    return {
        "type": command_type,
        "baseRevision": write_revision(ctx),
        "operationId": operation_id(),
        "turnId": str(ctx.turn_id),
        **fields,
    }


def write(ctx: DataPipelineToolContext, command: dict) -> dict:
    def call() -> dict:
        response = ops.apply_command(
            ctx.client,
            owner_id=ctx.owner_id,
            pipeline_id=ctx.pipeline_id,
            payload=command,
        )

        revision = response.get("revision")
        if not isinstance(revision, int):
            return constants.state_unavailable("草稿写入后未返回有效版本")
        ctx.advance_revision(revision)
        check_result = ops.check(
            ctx.client,
            owner_id=ctx.owner_id,
            pipeline_id=ctx.pipeline_id,
            revision=revision,
        )
        ctx.set_check_result(check_result)
        return {
            "revision": revision,
            "draft": mapping.draft(response),
            "check": check_result,
        }

    return ctx.invoke(call)


def _is_confirmed(answer: Any) -> bool:
    if isinstance(answer, bool):
        return answer
    if isinstance(answer, dict):
        if answer.get("confirmed") is not None:
            return bool(answer["confirmed"])
        decision = str(answer.get("decision") or "").strip().lower()
        return decision in constants.CONFIRM_ANSWERS
    return str(answer or "").strip().lower() in constants.CONFIRM_ANSWERS


def confirm(
    interrupt_fn: Callable[[dict], Any],
    action: str,
    question: str,
) -> dict | None:
    answer = interrupt_fn(
        {
            "question": question,
            "options": ["确认", "取消"],
            "details": [],
            "confirmation": True,
            "action": action,
        }
    )
    if _is_confirmed(answer):
        return None
    return {"interrupted": True, "action": action, "message": "用户未确认，未执行操作"}
