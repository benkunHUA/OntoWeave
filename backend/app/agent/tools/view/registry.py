"""视图工具的声明式注册表。

一种视图操作在这里登记一次：工具名、给模型看的描述、参数模型，以及「怎么改视图规格」
这个纯函数；工具本身由 `view.binding` 按表生成。新增能力 = 加一条登记，不用再抄一遍
包装代码，也不用在别处同步第二份工具清单。
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import Annotated

from pydantic import BaseModel, Field

import app.agent.tools.view.ops as ops
from app.vocabulary import VIEW_LAYOUTS

NodeIds = Annotated[
    list[str], Field(description="对象类型 ID；取自 get_ontology_slice，不要自己编")
]
GroupIds = Annotated[list[str], Field(description="分组 ID；取自 get_view_state 里的 groups")]


class NoArgs(BaseModel):
    """无参数操作。"""


class SetLayoutArgs(BaseModel):
    kind: str = Field(description="布局：" + " / ".join(VIEW_LAYOUTS))
    options: dict | None = Field(default=None, description="布局参数，键由前端布局算法解释")


class NodeIdsArgs(BaseModel):
    node_ids: NodeIds


class OptionalNodeIdsArgs(BaseModel):
    node_ids: list[str] | None = Field(default=None, description="不传表示整张图")


class GroupArgs(BaseModel):
    group_id: str = Field(description="分组标识；与已有分组同 id 表示把它整个换掉")
    label: str = Field(description="分组名，给用户看")
    node_ids: NodeIds


class GroupIdsArgs(BaseModel):
    group_ids: GroupIds


class FilterArgs(BaseModel):
    predicate: dict | None = Field(default=None, description="声明式过滤条件；不传表示清空过滤")


class AnnotateArgs(BaseModel):
    node_id: str = Field(description="对象类型 ID；取自 get_ontology_slice")
    text: str = Field(description="标注文字，给用户看")


@dataclass(frozen=True)
class ViewOp:
    """一条视图操作：模型的入口名、描述、参数，以及落到规格上的纯变换。"""

    name: str
    description: str
    args: type[BaseModel]
    apply: Callable[..., dict]


VIEW_OPS: tuple[ViewOp, ...] = (
    ViewOp(
        name="set_layout",
        description="设置布局：换整体排布方式时用它。",
        args=SetLayoutArgs,
        apply=ops.set_layout,
    ),
    ViewOp(
        name="focus",
        description="把视野移到指定节点。",
        args=NodeIdsArgs,
        apply=ops.focus,
    ),
    ViewOp(
        name="fit_view",
        description="适应画面；可指定只适应这些节点。",
        args=OptionalNodeIdsArgs,
        apply=ops.fit_view,
    ),
    ViewOp(
        name="reset_view",
        description="清空视图设置，回到默认呈现。",
        args=NoArgs,
        apply=ops.reset_view,
    ),
    ViewOp(
        name="highlight",
        description="高亮指定节点，其余保持原样。",
        args=NodeIdsArgs,
        apply=ops.highlight,
    ),
    ViewOp(
        name="dim",
        description="弱化指定节点，把注意力让给别的节点。",
        args=NodeIdsArgs,
        apply=ops.dim,
    ),
    ViewOp(
        name="group",
        description="把节点归进一个命名分组；这些节点会从原来的分组里移出来。",
        args=GroupArgs,
        apply=ops.group,
    ),
    ViewOp(
        name="collapse",
        description="折叠分组，收起组内细节。",
        args=GroupIdsArgs,
        apply=ops.collapse,
    ),
    ViewOp(
        name="expand",
        description="展开分组。",
        args=GroupIdsArgs,
        apply=ops.expand,
    ),
    ViewOp(
        name="filter_view",
        description="按声明式条件过滤显示。",
        args=FilterArgs,
        apply=ops.filter_view,
    ),
    ViewOp(
        name="annotate",
        description="给节点加一条标注，同一节点覆盖旧标注。",
        args=AnnotateArgs,
        apply=ops.annotate,
    ),
)
