"""映射工作台的写 Agent 工具；每个动作都经过确认。"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool, tool
from langgraph.types import interrupt

import app.agent.tools.ontology.workbench_constants as workbench_constants
from app.agent.tools.ontology.workbench_common import (
    binding_dataset,
    binding_mapping,
    confirm,
    keys_changed,
    parse_mapping,
)
from app.agent.tools.ontology.workbench_context import OntologyWorkbenchToolContext
from app.errors import NotFoundError
from app.services import ontology


def build_write_tools(
    ctx: OntologyWorkbenchToolContext,
    interrupt_fn: Callable[[dict], Any] | None = None,
) -> list[BaseTool]:
    resume: Callable[[dict], Any] = interrupt_fn or interrupt

    @tool("update_mapping")
    def update_mapping(
        subject_kind: str,
        subject_id: str,
        base_revision: int,
        mapping: dict[str, Any] | str,
    ) -> dict:
        """保存草稿；修改主键或标题键会在此工具内部再次确认。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        try:
            parsed = parse_mapping(mapping)
        except ValueError as exc:
            return {"error": workbench_constants.INVALID_ARGUMENT, "message": str(exc)}

        def call() -> dict:
            try:
                previous = ontology.get_binding(
                    ctx.session,
                    ctx.client,
                    owner_id=ctx.owner_id,
                    subject_kind=ctx.subject_kind,
                    subject_id=ctx.subject_id,
                )
            except NotFoundError:
                previous = None
            previous_mapping = binding_mapping(previous)
            key_changed, key_details = keys_changed(previous_mapping, parsed)
            changed = previous_mapping is not None and key_changed
            if changed:
                rejected = confirm(
                    resume,
                    "update_mapping_keys",
                    "这会修改主键或标题键，确认保存吗？",
                )
                if rejected is not None:
                    return rejected
            dataset_kind, dataset_id = binding_dataset(previous)
            if dataset_kind is None or dataset_id is None:
                return {
                    "error": workbench_constants.INVALID_ARGUMENT,
                    "message": "当前资源还没有绑定数据集",
                    "key_changes": key_details,
                }
            result = ontology.save_binding(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                payload={
                    "base_revision": base_revision,
                    "dataset_kind": dataset_kind,
                    "dataset_id": dataset_id,
                    "mapping": parsed,
                },
                operation_id=uuid.uuid4(),
            )
            revision = result.get("revision")
            if isinstance(revision, int):
                ctx.advance_revision(revision)
            return {
                **result,
                "previous_mapping": previous_mapping,
                "previousMapping": previous_mapping,
            }

        return ctx.invoke(call)

    @tool("start_generation")
    def start_generation(subject_kind: str, subject_id: str, mapping_revision: int) -> dict:
        """提交全量生成；必须确认，且本工具不等待生成完成。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        rejected = confirm(resume, "start_generation", "确认按当前草稿全量重新生成吗？")
        if rejected is not None:
            return rejected
        return ctx.invoke(
            lambda: ontology.generate_binding(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                payload={"mapping_revision": mapping_revision},
                operation_id=uuid.uuid4(),
            )
        )

    @tool("rebind_dataset")
    def rebind_dataset(
        subject_kind: str,
        subject_id: str,
        dataset_kind: str,
        dataset_id: str,
        base_revision: int,
    ) -> dict:
        """更换数据源；旧对象保留到新版本成功切换，必须确认。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        rejected = confirm(resume, "rebind_dataset", "确认更换数据源吗？")
        if rejected is not None:
            return rejected

        def call() -> dict:
            binding = ontology.get_binding(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
            )
            mapping = binding_mapping(binding)
            if mapping is None:
                return {
                    "error": workbench_constants.INVALID_ARGUMENT,
                    "message": "当前绑定没有可复用的映射草稿",
                }
            result = ontology.save_binding(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                payload={
                    "base_revision": base_revision,
                    "dataset_kind": dataset_kind.upper(),
                    "dataset_id": dataset_id,
                    "mapping": mapping,
                },
                operation_id=uuid.uuid4(),
            )
            revision = result.get("revision")
            if isinstance(revision, int):
                ctx.advance_revision(revision)
            return result

        return ctx.invoke(call)

    @tool("unbind")
    def unbind(subject_kind: str, subject_id: str) -> dict:
        """解绑并删除由该绑定产生的实例；必须确认。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        rejected = confirm(resume, "unbind", "确认解绑并删除已生成的对象数据吗？")
        if rejected is not None:
            return rejected
        return ctx.invoke(
            lambda: _unbind(ctx),
        )

    @tool("cancel_index_run")
    def cancel_index_run(run_id: str) -> dict:
        """取消排队或运行中的索引任务；必须确认。"""
        rejected = confirm(resume, "cancel_index_run", "确认取消这次索引运行吗？")
        if rejected is not None:
            return rejected
        return ctx.invoke(
            lambda: ontology.cancel_run(
                ctx.client,
                owner_id=ctx.owner_id,
                run_id=uuid.UUID(run_id),
                operation_id=uuid.uuid4(),
            )
        )

    return [update_mapping, start_generation, rebind_dataset, unbind, cancel_index_run]


def _unbind(ctx: OntologyWorkbenchToolContext) -> dict:
    ontology.delete_binding(
        ctx.session,
        ctx.client,
        owner_id=ctx.owner_id,
        subject_kind=ctx.subject_kind,
        subject_id=ctx.subject_id,
        operation_id=uuid.uuid4(),
    )
    return {"status": "unbound", "subject_id": str(ctx.subject_id)}


__all__ = ["build_write_tools"]
