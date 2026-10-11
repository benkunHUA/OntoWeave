from __future__ import annotations

from pydantic import BaseModel, Field


class ToolSpec(BaseModel):
    """工具的元数据。用它导出 LangChain 的 tool schema。"""

    name: str
    description: str
    parameters: dict = Field(default_factory=dict)


ONTOLOGY_TOOLS = [
    ToolSpec(
        name="search_ontology",
        description="按语义检索已有对象类型，只返回已确认的；新建之前必须先调用。",
        parameters={"query": "str", "limit": "int"},
    ),
    ToolSpec(
        name="list_types",
        description="列出现有对象类型。",
        parameters={"limit": "int"},
    ),
    ToolSpec(
        name="get_subgraph",
        description="从给定节点出发走一跳，返回周围的节点与关系。",
        parameters={"graph_id": "uuid", "object_type_id": "uuid"},
    ),
    ToolSpec(
        name="propose_type",
        description="新建对象类型并加进草稿；先 search_ontology 查重，确认没有可复用的再用它。",
        parameters={"name": "str", "description": "str | None"},
    ),
    ToolSpec(
        name="reuse_type",
        description="把已有对象类型并进当前图；用户确认复用后调用，id 取自 search_ontology。",
        parameters={"object_type_id": "uuid"},
    ),
    ToolSpec(
        name="propose_property",
        description="给对象类型加属性。",
        parameters={"owner_type_id": "uuid", "name": "str", "datatype": "str"},
    ),
    ToolSpec(
        name="propose_relation",
        description="加一条关系，两端必须都已存在。",
        parameters={"source_type_id": "uuid", "target_type_id": "uuid", "name": "str"},
    ),
    ToolSpec(
        name="update_element",
        description="改已有元素，必须带上 before 快照供撤销使用。",
        parameters={"element_kind": "str", "element_id": "uuid", "patch": "dict"},
    ),
    ToolSpec(
        name="remove_element",
        description="标记删除；草稿阶段不真删，确认时才删。",
        parameters={"element_kind": "str", "element_id": "uuid"},
    ),
]

VIEW_TOOLS = [
    ToolSpec(
        name="get_view_state",
        description="读取当前视图状态。",
        parameters={},
    ),
    ToolSpec(
        name="get_ontology_slice",
        description="读取图里的节点和关系；可指定节点 ID 缩小范围。",
        parameters={"node_ids": "list[str] | None"},
    ),
    ToolSpec(
        name="set_layout",
        description="换布局：层级 / 力导向 / 按域分组 / 网格。",
        parameters={"kind": "str", "options": "dict | None"},
    ),
    ToolSpec(
        name="focus",
        description="把视野移到若干节点。",
        parameters={"node_ids": "list[str]"},
    ),
    ToolSpec(
        name="fit_view",
        description="适应画面；可指定只适应若干节点。",
        parameters={"node_ids": "list[str] | None"},
    ),
    ToolSpec(name="reset_view", description="清空视图设置。", parameters={}),
    ToolSpec(
        name="highlight",
        description="高亮若干节点。",
        parameters={"node_ids": "list[str]"},
    ),
    ToolSpec(
        name="dim",
        description="弱化若干节点。",
        parameters={"node_ids": "list[str]"},
    ),
    ToolSpec(
        name="group",
        description="把节点归进一个命名分组。",
        parameters={"group_id": "str", "label": "str", "node_ids": "list[str]"},
    ),
    ToolSpec(
        name="collapse",
        description="折叠分组。",
        parameters={"group_ids": "list[str]"},
    ),
    ToolSpec(
        name="expand",
        description="展开分组。",
        parameters={"group_ids": "list[str]"},
    ),
    ToolSpec(
        name="filter_view",
        description="按条件过滤显示。",
        parameters={"predicate": "dict | None"},
    ),
    ToolSpec(
        name="annotate",
        description="给节点加一条标注。",
        parameters={"node_id": "str", "text": "str"},
    ),
    ToolSpec(
        name="save_view",
        description="保存当前视图的布局与呈现设置。",
        parameters={},
    ),
    ToolSpec(name="load_view", description="读取当前保存的视图。", parameters={}),
]
