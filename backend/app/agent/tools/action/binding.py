"""动作工具的 LangChain 绑定。

模型只看得到这里的工具名、描述与参数模型：能力本身在 `ops.py`，入参形状在 `args.py`，
绑定时统一套上运行上下文与错误兜底（`OntologyToolContext.invoke`）。

描述与参数说明分散在 `args.py` 与这里的 `bind(...)`：名字 + 一句话描述放在一处，
字段级的说明放在模型定义里。两处合起来就是模型看到的全部契约。
"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from typing import Any

from langchain_core.tools import BaseTool, StructuredTool
from pydantic import BaseModel

from app.agent.tools.action import args as action_args
from app.agent.tools.action import ops
from app.agent.tools.ontology.context import OntologyToolContext

_ARGS = type[BaseModel]
_Run = Callable[[Any], Any]


def _uuid(value: str | None) -> uuid.UUID | None:
    """模型给的是字符串；空串按「没填」处理，坏 id 由 `ctx.invoke` 兜成结构化错误。"""
    return uuid.UUID(value) if value else None


def build_action_tools(ctx: OntologyToolContext) -> list[BaseTool]:
    """把动作能力绑上运行上下文，包成模型可调用的工具。"""

    def bind(name: str, description: str, args_schema: _ARGS, run: _Run) -> BaseTool:
        def call(**kwargs: Any) -> dict:
            parsed = args_schema(**kwargs)
            return ctx.invoke(lambda: run(parsed))

        return StructuredTool.from_function(
            func=call, name=name, description=description, args_schema=args_schema
        )

    def search_actions(parsed: action_args.SearchActionsArgs) -> list[dict]:
        return ops.search_actions(
            ctx.session,
            query=parsed.query,
            embedder=ctx.embedder,
            owner_id=ctx.owner_id,
            limit=parsed.limit,
        )

    def get_action(parsed: action_args.GetActionArgs) -> dict:
        return ops.get_action(
            ctx.session,
            action_type_id=uuid.UUID(parsed.action_type_id),
            owner_id=ctx.owner_id,
        )

    def get_object_type(parsed: action_args.GetObjectTypeArgs) -> dict:
        return ops.get_object_type(
            ctx.session,
            graph_id=ctx.graph_id,
            object_type_id=uuid.UUID(parsed.object_type_id),
            owner_id=ctx.owner_id,
        )

    def propose_action(parsed: action_args.ProposeActionArgs) -> dict:
        return ops.propose_action(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            name=parsed.name,
            description=parsed.description,
        )

    def reuse_action(parsed: action_args.ReuseActionArgs) -> dict:
        return ops.reuse_action(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            action_type_id=uuid.UUID(parsed.action_type_id),
        )

    def propose_action_parameter(parsed: action_args.ProposeActionParameterArgs) -> dict:
        return ops.propose_action_parameter(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            action_type_id=uuid.UUID(parsed.action_type_id),
            name=parsed.name,
            kind=parsed.kind,
            description=parsed.description,
            datatype=parsed.datatype,
            target_object_type_id=_uuid(parsed.target_object_type_id),
            is_array=parsed.is_array,
            is_required=parsed.is_required,
            default_value=parsed.default_value,
            enum_values=parsed.enum_values,
        )

    def propose_action_rule(parsed: action_args.ProposeActionRuleArgs) -> dict:
        return ops.propose_action_rule(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            action_type_id=uuid.UUID(parsed.action_type_id),
            rule_kind=parsed.rule_kind,
            target_object_type_id=_uuid(parsed.target_object_type_id),
            target_relation_id=_uuid(parsed.target_relation_id),
            source_parameter_id=_uuid(parsed.source_parameter_id),
            target_parameter_id=_uuid(parsed.target_parameter_id),
            assignments=tuple(assignment.model_dump() for assignment in parsed.assignments),
        )

    def set_action_criteria(parsed: action_args.SetActionCriteriaArgs) -> dict:
        return ops.set_action_criteria(
            ctx.session,
            changeset_id=ctx.changeset_id,
            owner_id=ctx.owner_id,
            action_type_id=uuid.UUID(parsed.action_type_id),
            conditions=tuple(condition.model_dump() for condition in parsed.conditions),
        )

    return [
        bind(
            "search_actions",
            "查已有动作：先搜有没有能复用的，别急着新建。只返回已确认的动作（草稿不在结果里）；"
            "不传 query 则列出全部动作（含草稿，看 status）。",
            action_args.SearchActionsArgs,
            search_actions,
        ),
        bind(
            "get_action",
            "看一个动作的完整定义：参数、规则（含字段赋值）与提交校验。id 取自 search_actions。",
            action_args.GetActionArgs,
            get_action,
        ),
        bind(
            "get_object_type",
            "看一个对象类型的属性与它在这张图里的关系。写规则、字段赋值、校验条件前都要先拿到"
            "这些 id；id 取自 get_subgraph。",
            action_args.GetObjectTypeArgs,
            get_object_type,
        ),
        bind(
            "propose_action",
            "新建一个动作（草稿）。只有一个动作名也能先建；参数、规则之后用另外的工具补。",
            action_args.ProposeActionArgs,
            propose_action,
        ),
        bind(
            "reuse_action",
            "把已有的动作并进当前图，不新建。用户确认复用后用它，id 取自 search_actions。",
            action_args.ReuseActionArgs,
            reuse_action,
        ),
        bind(
            "propose_action_parameter",
            "给动作加一个参数（用户要填的输入）。对象引用参数让用户选一个对象，值参数让用户填一个值。",
            action_args.ProposeActionParameterArgs,
            propose_action_parameter,
        ),
        bind(
            "propose_action_rule",
            "给动作加一条规则：这个动作到底改了什么。字段赋值内联在同一次调用里。",
            action_args.ProposeActionRuleArgs,
            propose_action_rule,
        ),
        bind(
            "set_action_criteria",
            "整棵替换这个动作的提交校验：交一份完整条件列表，不满足就拦住这次动作。"
            "传空列表表示不设校验。",
            action_args.SetActionCriteriaArgs,
            set_action_criteria,
        ),
    ]
