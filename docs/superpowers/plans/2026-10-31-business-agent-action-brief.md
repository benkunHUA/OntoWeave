# 业务 Agent 行动层实施简报（压缩稿）

- 日期：2026-10-31
- 用途：正式编码时的唯一上下文入口
- 详细设计：`2026-10-31-business-agent-action-design.md`
- 详细计划：`2026-10-31-business-agent-action-plan.md`

## 1. 目标

在现有业务问答 Agent 内加入行动能力，不新增执行面：

```text
业务请求
  → search_graphs（图卡已含行动）
  → get_graph_detail（对象、关系、行动摘要）
  → get_action_detail（按需取完整定义）
  → query_objects / get_related_objects（解析对象参数）
  → execute_action
       preview → interrupt 确认 → apply → effective readback
```

## 2. 不可变决策

- Query Agent 改名 Business Agent；会话 kind 从 `query` 迁移到 `business`。
- 不新增 Action Agent、`actionTask`、独立执行页。
- 图卡增加行动区段，图向量继续由图卡生成；不新增 `search_graph_actions`。
- 行动只能来自已发布图，且参数、规则、赋值、条件均已确认。
- 五种规则全支持：`create_object`、`modify_object`、`delete_object`、`create_link`、`delete_link`。
- 主键不可改；标题可改，清空回退主键；v1 不支持数组属性。
- 未确认不写数据；apply 单事务；失败不半写。
- 变更只写数据服务编辑覆盖层；基础索引、源库、图发布产物不变。
- 审计必须落 `ontology_edit_execution` 与 `ontology_edit_change`。
- Agent 回答必须引用 readback 实际值，不能复述 preview。

## 3. 精确契约

### 3.1 `get_graph_detail.actions`

```json
{
  "id": "uuid",
  "name": "关闭工单",
  "description": "关闭处理中的工单并记录解决说明",
  "status": "confirmed",
  "parameters": [
    {
      "id": "uuid",
      "name": "工单",
      "description": "要关闭的工单",
      "kind": "object_ref",
      "datatype": null,
      "target_object_type_id": "uuid",
      "target_object_type_name": "工单",
      "is_array": false,
      "is_required": true,
      "default_value": null,
      "enum_values": null
    }
  ],
  "effects": {
    "objects": [{"object_type_id": "uuid", "object_type_name": "工单", "rule_kinds": ["modify_object"]}],
    "relations": []
  },
  "criteria": [{"condition_id": "uuid", "text": "工单.状态 是 处理中", "failure_message": "只有处理中的工单才能关闭"}]
}
```

不返回：赋值明细、完整条件操作数、内部规则 ID、时间戳。

### 3.2 `execute_action` 入参

```json
{
  "graph_id": "uuid",
  "action_type_id": "uuid",
  "parameters": {
    "<parameter_id>": {"object_type_id": "uuid", "primary_key": "T-1001"},
    "<parameter_id>": "已更换硒鼓"
  }
}
```

工具生成 `execution_id`、`idempotency_key`、`expected`，模型不能传。

### 3.3 确认载荷

```json
{
  "kind": "action_confirmation",
  "title": "执行「关闭工单」？",
  "summary": "将修改 1 个对象",
  "preview_hash": "sha256",
  "execution_id": "uuid",
  "object_changes": [{
    "kind": "OBJECT_MODIFY",
    "object_type_name": "工单",
    "primary_key": "T-1001",
    "title": "打印机故障",
    "fields": [{"property_id": "uuid", "name": "状态", "before": "处理中", "after": "已关闭"}]
  }],
  "relation_changes": [],
  "warnings": [],
  "options": [
    {"label": "确认执行", "answer": {"decision": "approve"}},
    {"label": "取消", "answer": {"decision": "reject"}}
  ]
}
```

确认后重新 preview；`preview_hash` 变化返回 `PREVIEW_STALE`，不 apply。

### 3.4 edit set

```json
{
  "execution_id": "uuid",
  "owner_id": "uuid",
  "conversation_id": "uuid",
  "graph_id": "uuid",
  "action_type_id": "uuid",
  "action_definition_hash": "sha256",
  "action_snapshot": {},
  "parameters": {},
  "idempotency_key": "conversation_id:tool_call_id",
  "expected": {"objects": [], "relations": []},
  "object_creates": [],
  "object_updates": [],
  "object_deletes": [],
  "relation_creates": [],
  "relation_deletes": []
}
```

## 4. 模块边界

| 模块 | 只负责 |
|---|---|
| `services/action/presentation.py` | 行动摘要、完整详情、条件文本、名称解析 |
| `services/action/execution/context.py` | 行动/图/归属、快照与哈希 |
| `services/action/execution/parameters.py` | 默认值、必填、类型、对象引用 |
| `services/action/execution/criteria.py` | 运行时提交校验求值 |
| `services/action/execution/planner.py` | 五种规则 → edit set 草稿 |
| `services/action/execution/preview.py` | preview 请求、确认载荷、preview hash |
| `services/action/execution/executor.py` | interrupt、apply、幂等、错误封装 |
| `services/action/execution/readback.py` | apply 结果与前后值投影 |
| `clients/.../edits.py` | 只做 HTTP 与错误映射 |
| `agent/tools/business/action.py` | 只做工具参数转换与 `ctx.invoke` |

单文件超过约 300 行就按职责拆。

## 5. 阶段清单

### Phase 0 · 改名与迁移

- [ ] migration `0025_business_conversation.py`：`query` → `business`，更新 CHECK。
- [ ] `ConversationKind.BUSINESS`；读取时兼容 `query` 别名。
- [ ] `BusinessAgentDefinition` / `BusinessRunContext` / `MAIN_BUSINESS_AGENT_PROMPT`。
- [ ] `agent/tools/query` → `agent/tools/business`。
- [ ] `POST /business/conversations`；旧 `/query` 入口兼容。
- [ ] 前端 `/query` → `/business` 重定向（Phase 4 完成）。

### Phase 1 · 行动展示

- [ ] `action/presentation.py` 统一摘要与详情。
- [ ] 图卡增加行动区段，行动参与图向量。
- [ ] 发布校验行动及子元素状态、涉及元素必须在图内。
- [ ] `get_graph_detail.actions` 使用统一摘要。
- [ ] `get_action_detail` 返回完整参数、规则、赋值、条件树。

### Phase 2 · 执行原语

- [ ] 参数默认值、必填、类型、枚举、对象引用；数组拒绝。
- [ ] 条件树求值；失败返回条件 ID 与提示。
- [ ] 五种规则装配；主键生成；重复影响检测。

### Phase 3 · Preview / 确认 / Apply

- [ ] 数据服务客户端 `preview_edit_set` / `apply_edit_set`。
- [ ] expected 从 preview before 版本构造。
- [ ] preview 冲突不弹确认。
- [ ] `execute_action` 内 interrupt，确认后比对 preview hash 再 apply。
- [ ] 幂等键由 conversation_id + tool_call_id 确定性生成。
- [ ] readback 映射对象/字段/关系名称。

### Phase 4 · 前端

- [ ] 业务工作台改名与路由兼容。
- [ ] `action_confirmation` 结构化确认卡片。
- [ ] 字段前后值、对象、关系、警告、确认/取消。
- [ ] 旧 `ask_user` 中断保持原样。

### Phase 5 · 验收

```bash
cd backend && .venv/bin/python -m pytest -q
cd backend && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app
cd frontend && npx tsc -b && npm run lint && npm test && npm run build
cd data-service && ./mvnw -q -Dtest='EditSet*,OntologyEditApiTest,Effective*' test
```

端到端覆盖：修改、创建、删除、建链、删链、校验失败、取消、preview 变化、版本冲突、网络重试。

## 6. 易错点

- `execution_id` / `idempotency_key` 不能在 interrupt 前后重新随机。
- 用户确认的是 preview；apply 前必须重新 preview 并比对 hash。
- preview 的 before 版本用于 expected；对象创建不设 expected。
- modify 的 null 赋值转 clear，不要写 JSON null。
- 主键不能出现在 update 的 set/clear 中。
- 外键关系不写 relation edit，只改外键属性。
- 数组属性在 preview 前就拒绝。
- 所有名称解析只做一次，避免图卡、详情、readback 三套逻辑漂移。
