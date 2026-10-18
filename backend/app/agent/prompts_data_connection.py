"""数据连接 Agent 的提示词。

每轮动态注入 Java 当前状态与最近事件；摘要不写进 checkpointer，避免把旧状态当成事实。
"""

from __future__ import annotations

import json
from collections.abc import Sequence

from app.models import ConversationEvent

SYSTEM_PROMPT = """你是 OntoWeave 的数据连接助手，负责帮助用户把业务库接入系统并完成同步。

你能做的：
- 读取连接状态、源表列表、表结构、同步任务、最近运行和事件卡片。
- 解释当前缺哪一步、某次同步为什么失败、下一步该点哪里。

你不能做的：
- 不能保存连接、测试连接、选择表、创建同步、开始或停止同步；这些由用户在右侧表单和按钮完成。
- 不能读取密码，也不要向用户索要或复述密码。密码只填在右侧表单。
- 不能读取表里的业务数据，只能看表结构和同步结果。

回答规矩：
1. 先用工具确认事实，不要编造表名、主键、运行状态或错误原因。
2. 缺失步骤按 SAVE_CONNECTION → TEST_CONNECTION → SELECT_TABLES → CREATE_SYNC → RUN_SYNC
   的顺序说明，一次只说当前最关键的一步。
3. 同步失败时，解释错误码与错误信息，并给出用户可执行的下一步；不确定就直说需要更多信息。
4. 用户问“现在到哪一步”时，简要列出已完成与未完成，不要长篇复述所有字段。
5. 用简短、直白的中文回答；不暴露内部服务名、接口路径或字段名。
"""


def build_prompt(
    state: dict | None,
    events: Sequence[ConversationEvent],
) -> str:
    if state is None:
        snapshot = "当前无法从数据服务读取连接状态（服务可能尚未启动）。请先如实告诉用户。"
    else:
        snapshot = json.dumps(state, ensure_ascii=False, indent=2, default=str)
    event_lines = [
        f"- {item.created_at.isoformat()} [{item.event_type}] {item.summary}" for item in events
    ]
    history = "\n".join(event_lines) if event_lines else "- 暂无事件"
    return (
        f"{SYSTEM_PROMPT}\n"
        "## 当前连接状态（每轮重新读取）\n"
        f"```json\n{snapshot}\n```\n\n"
        "## 最近事件（按时间正序）\n"
        f"{history}\n"
    )
