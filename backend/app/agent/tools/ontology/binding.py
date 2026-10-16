"""建模工具的 LangChain 绑定。

模型只看得到这里的工具名、描述与参数：能力本身在 `ops.py`，绑定时统一套上运行上下文
与错误兜底（`ToolContext.invoke`）。
"""

from __future__ import annotations

import uuid

from langchain_core.tools import BaseTool, tool

from app.agent.tools.context import ToolContext
from app.agent.tools.ontology import ops
from app.agent.tools.search import build_search_tool
from app.vocabulary import DEFAULT_CARDINALITY, DEFAULT_DATATYPE


def build_model_tools(ctx: ToolContext) -> list[BaseTool]:
    """把本体操作绑上运行上下文，包成模型可调用的工具。"""

    @tool("get_subgraph")
    def get_subgraph(object_type_id: str) -> dict:
        """从给定对象类型出发走一跳，返回它周围的节点与关系。"""
        return ctx.invoke(
            lambda: ops.get_subgraph(
                ctx.session,
                graph_id=ctx.graph_id,
                object_type_id=uuid.UUID(object_type_id),
                owner_id=ctx.owner_id,
            ),
        )

    @tool("propose_type")
    def propose_type(name: str, description: str | None = None) -> dict:
        """新建对象类型并加进草稿。只负责建：先 search 查重，确认没有可复用的再用它。"""
        return ctx.invoke(
            lambda: ops.propose_type(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                name=name,
                description=description,
            ),
        )

    @tool("reuse_type")
    def reuse_type(object_type_id: str) -> dict:
        """把已有对象类型并进当前图，不新建。用户确认复用后用它，id 取自 search。"""
        return ctx.invoke(
            lambda: ops.reuse_type(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                object_type_id=uuid.UUID(object_type_id),
            ),
        )

    @tool("propose_property")
    def propose_property(
        owner_type_id: str,
        name: str,
        datatype: str = DEFAULT_DATATYPE,
        required: bool = False,
        is_array: bool = False,
        is_primary_key: bool = False,
        is_title_key: bool = False,
        enum_values: list[str] | None = None,
    ) -> dict:
        """给对象类型加属性。datatype 取 text/integer/decimal/boolean/date/timestamp/enum；
        is_array 表示多值，is_primary_key/is_title_key 各类型至多一个。
        datatype=enum 时必填 enum_values：至少两个候选值，如 ["待处理", "已完成"]。
        用户明确列出的属性和字段逐项用它，不要改成关系。"""
        return ctx.invoke(
            lambda: ops.propose_property(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                owner_type_id=uuid.UUID(owner_type_id),
                name=name,
                datatype=datatype,
                required=required,
                is_array=is_array,
                is_primary_key=is_primary_key,
                is_title_key=is_title_key,
                enum_values=enum_values,
            ),
        )

    @tool("propose_relation")
    def propose_relation(
        source_type_id: str,
        target_type_id: str,
        name: str,
        cardinality: str = DEFAULT_CARDINALITY,
        forward_label: str | None = None,
        reverse_label: str | None = None,
        foreign_key_property_id: str | None = None,
        direction: str = "forward",
    ) -> dict:
        """加一条关系，只在用户明确要求关系或确认后才能使用；两端必须都已存在。
        cardinality 取 one_to_one/one_to_many/many_to_many；
        forward_label / reverse_label 是源端与目标端各自的可读名（如 客户—下订单→订单）。
        一对一/一对多可选填 foreign_key_property_id：承载这条链接的外键属性（存对端主键）。"""
        return ctx.invoke(
            lambda: ops.propose_relation(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                source_type_id=uuid.UUID(source_type_id),
                target_type_id=uuid.UUID(target_type_id),
                name=name,
                direction=direction,
                cardinality=cardinality,
                forward_label=forward_label,
                reverse_label=reverse_label,
                foreign_key_property_id=(
                    uuid.UUID(foreign_key_property_id) if foreign_key_property_id else None
                ),
            ),
        )

    @tool("update_element")
    def update_element(element_kind: str, element_id: str, patch: dict) -> dict:
        """改已有元素。只提交 patch；改对象类型的名称/描述会自动重算检索用的向量。"""
        return ctx.invoke(
            lambda: ops.update_element(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                element_kind=element_kind,
                element_id=uuid.UUID(element_id),
                patch=patch,
                embedder=ctx.embedder,
            ),
        )

    @tool("remove_element")
    def remove_element(element_kind: str, element_id: str) -> dict:
        """标记删除。草稿阶段不真删，用户确认时才删。"""
        return ctx.invoke(
            lambda: ops.remove_element(
                ctx.session,
                changeset_id=ctx.changeset_id,
                owner_id=ctx.owner_id,
                element_kind=element_kind,
                element_id=uuid.UUID(element_id),
            ),
        )

    @tool("ask_user")
    def ask_user(
        question: str,
        options: list[str] | None = None,
        details: list[dict] | None = None,
    ) -> str:
        """拿不准时停下来问用户。这是中断点，用户回答后你会拿到答案继续。

        只在真的无法判断时才用：同名不同义、一个词可能是对象也可能是属性、关系方向不确定。
        details 是给用户看的补充信息，形如 [{"label": "描述", "value": "..."}]；
        让用户确认复用某个类型时，把它的名称、描述、状态都放进来。
        """
        # 由 LangGraph 的 interrupt 在工具内部暂停。
        from langgraph.types import interrupt

        answer = interrupt(
            {"question": question, "options": options or [], "details": details or []}
        )
        return str(answer)

    return [
        build_search_tool(ctx),
        get_subgraph,
        propose_type,
        reuse_type,
        propose_property,
        propose_relation,
        update_element,
        remove_element,
        ask_user,
    ]
