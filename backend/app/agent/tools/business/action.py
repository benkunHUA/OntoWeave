"""业务主 Agent 的行动工具。"""

from __future__ import annotations

import uuid
from typing import Annotated, Any

from langchain_core.tools import BaseTool, InjectedToolCallId, tool
from langgraph.types import interrupt

from app.agent.tools.business.context import BusinessToolContext
from app.services.action import execution, presentation


def _interrupt(payload: dict[str, Any]) -> Any:
    # 经模块级函数转一次，测试可以替换 interrupt，执行器本身不直接依赖 LangGraph。
    return interrupt(payload)


def build_action_tools(ctx: BusinessToolContext) -> list[BaseTool]:
    @tool("get_action_detail")
    def get_action_detail(graph_id: str, action_type_id: str) -> dict:
        """读取当前图内一个行动的完整定义：参数、规则、赋值与提交校验。"""
        return ctx.invoke(
            lambda: presentation.action_detail_to_dict(
                presentation.load_action_detail_in_graph(
                    ctx.session,
                    uuid.UUID(action_type_id),
                    owner_id=ctx.owner_id,
                    graph_id=uuid.UUID(graph_id),
                ),
                ctx.session,
            )
        )

    @tool("execute_action")
    def execute_action(
        graph_id: str,
        action_type_id: str,
        parameters: dict[str, Any] | None = None,
        tool_call_id: Annotated[str, InjectedToolCallId] = "",
    ) -> dict:
        """执行当前图内一个已发布的行动：先预览变更并等待用户确认，再写入并回读。

        parameters 以参数 ID 为键：对象参数传 {object_type_id, primary_key}，值参数传标量。
        不要自行拼接确认信息；确认卡片由本工具在 preview 后触发。
        """
        return ctx.invoke(
            lambda: execution.execute_action(
                ctx.session,
                ctx.client,
                owner_id=ctx.owner_id,
                conversation_id=ctx.conversation_id,
                graph_id=uuid.UUID(graph_id),
                action_type_id=uuid.UUID(action_type_id),
                parameters=parameters,
                tool_call_id=tool_call_id,
                interrupt_fn=_interrupt,
            )
        )

    return [get_action_detail, execute_action]
