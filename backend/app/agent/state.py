"""业务 Agent 的持久化图状态。"""

from __future__ import annotations

import operator
from typing import Annotated, Any

from deepagents.graph import DeepAgentState


class BusinessAgentState(DeepAgentState):
    """在 deepagents 默认状态上追加证据增量。

    每次查询工具执行后只追加一条增量，由 reducer 合并；这样并行工具调用不会
    用各自读到的旧快照互相覆盖。运行结束时再按 turn_id 折叠成一张实例图。
    """

    evidence: Annotated[list[dict[str, Any]], operator.add]
