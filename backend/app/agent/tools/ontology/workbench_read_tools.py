"""映射工作台的只读 Agent 工具。"""

from __future__ import annotations

import uuid
from typing import Any

from langchain_core.tools import BaseTool, tool

from app.agent.tools.ontology import workbench_constants
from app.agent.tools.ontology.workbench_common import binding_mapping, parse_mapping
from app.agent.tools.ontology.workbench_context import OntologyWorkbenchToolContext
from app.services import dataset_search, ontology


def build_read_tools(ctx: OntologyWorkbenchToolContext) -> list[BaseTool]:
    @tool("find_bindable_datasets")
    def find_bindable_datasets(limit: int = 10) -> dict:
        """按当前对象/关系定义召回语义相近且字段结构可绑定的数据集候选。"""
        blocked = ctx.check_subject(ctx.subject_kind, str(ctx.subject_id))
        if blocked is not None:
            return blocked
        size = max(1, min(limit, workbench_constants.DATASET_LIMIT_MAX))
        return ctx.invoke(
            lambda: dataset_search.find_bindable_datasets(
                ctx.session,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                embedder=ctx.embedder,
                limit=size,
            )
        )

    @tool("get_dataset_schema")
    def get_dataset_schema(dataset_id: str, dataset_kind: str) -> dict:
        """读取数据集字段名、类型、可空与样例值。"""

        def call() -> dict:
            columns = ctx.client.list_dataset_columns(
                owner_id=ctx.owner_id,
                dataset_kind=dataset_kind.upper(),
                dataset_id=uuid.UUID(dataset_id),
            )
            return {
                "dataset_id": dataset_id,
                "dataset_kind": dataset_kind.upper(),
                "columns": columns,
            }

        return ctx.invoke(call)

    @tool("get_subject_definition")
    def get_subject_definition(subject_kind: str, subject_id: str) -> dict:
        """读取对象类型属性，或关系两端定义、主键与外键属性。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ontology.get_subject_definition(
                ctx.session,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
            )
        )

    @tool("get_mapping")
    def get_mapping(subject_kind: str, subject_id: str) -> dict:
        """读取草稿 mapping、revision、生效状态和最近一次运行。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked

        def call() -> dict:
            binding = (
                ctx.client.get_object_binding(ctx.subject_id, owner_id=ctx.owner_id)
                if ctx.subject_kind == ontology.OBJECT_TYPE
                else ctx.client.get_relation_binding(ctx.subject_id, owner_id=ctx.owner_id)
            )
            runs = ontology.list_runs(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                limit=1,
            )
            revision = binding.get("revision")
            if isinstance(revision, int):
                ctx.advance_revision(revision)
            return {
                "binding": binding,
                "mapping": binding_mapping(binding),
                "revision": revision,
                "latest_run": runs[0] if runs else None,
            }

        return ctx.invoke(call)

    @tool("suggest_mapping")
    def suggest_mapping(
        subject_kind: str,
        subject_id: str,
        dataset_id: str | None = None,
        dataset_kind: str | None = None,
    ) -> dict:
        """按名称相似度和类型兼容度为当前资源给出建议连线与理由。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ontology.suggest_mapping(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                dataset_kind=dataset_kind,
                dataset_id=uuid.UUID(dataset_id) if dataset_id else None,
            )
        )

    @tool("validate_mapping")
    def validate_mapping(
        subject_kind: str,
        subject_id: str,
        mapping: dict[str, Any] | str | None = None,
    ) -> dict:
        """校验 mapping，返回阻断与告警；不保存草稿。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        try:
            payload = {"mapping": parse_mapping(mapping)} if mapping is not None else None
        except ValueError as exc:
            return {"error": workbench_constants.INVALID_ARGUMENT, "message": str(exc)}
        if ctx.subject_kind == ontology.RELATION:

            def validate_relation() -> dict:
                preview = ontology.preview_binding(
                    ctx.session,
                    ctx.client,
                    owner_id=ctx.owner_id,
                    subject_kind=ctx.subject_kind,
                    subject_id=ctx.subject_id,
                    payload={**(payload or {}), "limit": 1},
                )
                validation = preview.get("validation")
                if isinstance(validation, dict):
                    return validation
                blocking = preview.get("blocking") or []
                return {
                    "blocking": blocking,
                    "warnings": preview.get("warnings") or [],
                    "valid": not blocking,
                }

            return ctx.invoke(validate_relation)
        return ctx.invoke(
            lambda: ontology.validate_binding(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                payload=payload,
            )
        )

    @tool("preview_mapping")
    def preview_mapping(
        subject_kind: str,
        subject_id: str,
        mapping: dict[str, Any] | str | None = None,
        limit: int = workbench_constants.PREVIEW_LIMIT_DEFAULT,
    ) -> dict:
        """只读预览样例行或关系对，不生成实例、不写运行记录。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        try:
            payload: dict[str, Any] = {"limit": max(1, min(limit, 100))}
            if mapping is not None:
                payload["mapping"] = parse_mapping(mapping)
        except ValueError as exc:
            return {"error": workbench_constants.INVALID_ARGUMENT, "message": str(exc)}
        return ctx.invoke(
            lambda: ontology.preview_binding(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                payload=payload,
            )
        )

    @tool("list_index_runs")
    def list_index_runs(
        subject_kind: str,
        subject_id: str,
        limit: int = workbench_constants.RUN_LIMIT_DEFAULT,
    ) -> list[dict] | dict:
        """列出当前资源的索引运行记录。"""
        blocked = ctx.check_subject(subject_kind, subject_id)
        if blocked is not None:
            return blocked
        return ctx.invoke(
            lambda: ontology.list_runs(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                subject_kind=ctx.subject_kind,
                subject_id=ctx.subject_id,
                limit=max(1, min(limit, 200)),
            )
        )

    @tool("get_index_run")
    def get_index_run(run_id: str) -> dict:
        """读取一次运行的阶段、错误和统计；失败先看这里。"""
        return ctx.invoke(lambda: ctx.client.get_run(uuid.UUID(run_id), owner_id=ctx.owner_id))

    return [
        find_bindable_datasets,
        get_dataset_schema,
        get_subject_definition,
        get_mapping,
        suggest_mapping,
        validate_mapping,
        preview_mapping,
        list_index_runs,
        get_index_run,
    ]


__all__ = ["build_read_tools"]
