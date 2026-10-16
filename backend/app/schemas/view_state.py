"""视图状态的请求 / 响应模型，以及声明式视图规格的形状。

规格由两边写：绘图子智能体的视图工具，以及前端画布。形状必须一致，所以在这里定义
一份，写入前校验——坏形状落进 JSONB，前端算布局时才会炸，那时候离出错点已经很远。

字段名沿用线格式的 camelCase（JSONB 里存的、接口返回的、前端读的都是这个形状），
不为了 Python 侧好看改名字，那会多出一层 alias 映射。
"""

from __future__ import annotations

import uuid

from pydantic import BaseModel, ConfigDict


class LayoutSpec(BaseModel):
    """布局：kind 决定用哪种算法，options 交给前端布局算法自己解释。"""

    model_config = ConfigDict(extra="allow")

    kind: str | None = None
    options: dict | None = None


class GroupSpec(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str
    label: str
    nodeIds: list[str]


class HighlightSpec(BaseModel):
    """高亮一组节点。style 目前只有 focus，其余分组留着给后续样式。"""

    model_config = ConfigDict(extra="allow")

    nodeIds: list[str]
    style: str = "focus"


class AnnotationSpec(BaseModel):
    model_config = ConfigDict(extra="allow")

    nodeId: str
    text: str


class ViewSpec(BaseModel):
    """声明式视图规格：布局、分组、折叠、高亮、弱化、过滤、视野、标注与坐标。

    未知键一律保留（`extra="allow"`）：前端可能先于后端加字段，规格是两端共写的。
    """

    model_config = ConfigDict(extra="allow")

    # 前端算出来的画布坐标；绘图子智能体只产出声明式部分，不碰它。
    nodes: dict[str, dict] | None = None
    layout: LayoutSpec | None = None
    groups: list[GroupSpec] | None = None
    collapsed: list[str] | None = None
    highlight: list[HighlightSpec] | None = None
    dim: list[str] | None = None
    filters: dict | None = None
    camera: dict | None = None
    annotations: list[AnnotationSpec] | None = None

    @classmethod
    def normalize(cls, raw: dict | ViewSpec) -> dict:
        """校验并转回线格式。

        没写的键不补 null：读出来的形状与写入时一致，前端不必处理空壳字段。
        """
        model = raw if isinstance(raw, ViewSpec) else cls.model_validate(raw)
        return model.model_dump(exclude_none=True)


class ViewStateWrite(BaseModel):
    spec: ViewSpec


class ViewStateRead(BaseModel):
    graph_id: uuid.UUID
    name: str | None
    # 直接回写入时的形状（已归一），不再套一层模型，免得给前端补出 null 字段。
    spec: dict
