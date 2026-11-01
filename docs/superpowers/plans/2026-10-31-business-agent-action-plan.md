# 业务 Agent 行动执行 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有业务问答 Agent 内交付行动发现、参数装配、提交校验、preview、人工确认、apply、审计与 effective readback；不新增独立执行面。

**Architecture:** 现有 Query Agent 改名 Business Agent；图卡承载行动召回，图详情给行动摘要，完整定义按需读取。`execute_action` 工具在同一个 Agent 内完成“校验 → preview → interrupt 确认 → apply → readback”。数据服务编辑覆盖层负责存储与事务，Python 只负责行动语义与编排。

**Tech Stack:** Python 3.12、FastAPI、SQLAlchemy、Alembic、LangGraph/deepagents、pytest、ruff、mypy；React + TypeScript、Vitest；Java 数据服务编辑覆盖层已实现。

**设计文档：** `docs/superpowers/specs/2026-10-31-business-agent-action-design.md`

**范围：** Agent 改名、行动展示、执行服务、数据服务客户端、确认卡片、测试与验收。

**不在范围：** 独立 Action Agent、独立执行页、`actionTask` 表、`search_graph_actions`、数组属性修改、物化快照。

## 实施状态

- [ ] Phase 0：Agent 改名与兼容
- [ ] Phase 1：行动展示与图发布
- [ ] Phase 2：执行服务原语
- [ ] Phase 3：preview / 确认 / apply 工具
- [ ] Phase 4：前端确认卡片与业务工作台
- [ ] Phase 5：端到端验收

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/alembic/versions/0025_business_conversation.py` | `conversation.kind` 从 `query` 迁移到 `business`。 |
| `backend/app/vocabulary.py` | `ConversationKind.BUSINESS` 与兼容别名。 |
| `backend/app/agent/definitions/business.py` | `BusinessAgentDefinition`、`BusinessRunContext`。 |
| `backend/app/agent/prompts/business.py` | 业务 Agent 提示词，覆盖查询与行动。 |
| `backend/app/agent/tools/business/` | 选图、查询、行动工具与运行上下文。 |
| `backend/app/api/business.py` | 新建业务会话；保留 `/query` 兼容入口。 |
| `backend/app/services/action/presentation.py` | 行动摘要、完整详情、条件文本的单一投影。 |
| `backend/app/services/graph/publication.py` | 图卡增加行动区段；发布校验行动。 |
| `backend/app/services/graph/search.py` | `get_graph_detail` 增加 `actions`。 |
| `backend/app/services/action/execution/context.py` | 行动、图、归属、快照与哈希。 |
| `backend/app/services/action/execution/parameters.py` | 默认值、必填、类型、对象引用。 |
| `backend/app/services/action/execution/criteria.py` | 运行时提交校验求值。 |
| `backend/app/services/action/execution/planner.py` | 五种规则 → edit set 草稿。 |
| `backend/app/services/action/execution/preview.py` | preview 请求、确认载荷、preview hash。 |
| `backend/app/services/action/execution/executor.py` | interrupt、apply、幂等、状态封装。 |
| `backend/app/services/action/execution/readback.py` | apply 结果与前后值投影。 |
| `backend/app/clients/data_service/ontology_client/edits.py` | edit-set preview/apply 客户端。 |
| `backend/app/schemas/message_blocks.py` | `action_confirmation` 中断载荷。 |
| `backend/app/agent/runtime/interrupts.py` | 识别行动确认并转成前端形状。 |
| `backend/app/agent/runtime/events.py` | interrupt 事件携带确认预览。 |
| `frontend/src/features/business/` | 业务工作台、行动确认卡片、文案。 |
| `frontend/src/shared/types/agent.ts` | 行动确认预览类型。 |
| `frontend/src/shared/chat/components/InterruptCard.tsx` | 结构化确认渲染。 |

## Phase 0 · Agent 改名与兼容

### Task 1: 迁移会话类型

**Files:**
- Create: `backend/alembic/versions/0025_business_conversation.py`
- Modify: `backend/app/vocabulary.py`
- Modify: `backend/app/services/conversation/lifecycle.py`
- Test: `backend/tests/test_business_conversation.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- 新会话写入 `kind='business'`。
- `GET /conversations?kind=business` 返回业务会话。
- `GET /conversations?kind=query` 作为兼容别名仍返回同一批会话。
- 旧库升级时 `query` 行迁移为 `business`，会话 ID 和消息不变。

- [ ] **Step 2: 写迁移**

迁移步骤：

1. 删除 `ck_conversation_kind`。
2. `UPDATE conversation SET kind='business' WHERE kind='query'`。
3. 重建 CHECK，包含 `business`，不再包含 `query`。
4. downgrade 反向迁移，并在存在 `business` 会话时拒绝降级。

- [ ] **Step 3: 更新枚举与兼容解析**

`ConversationKind.BUSINESS = "business"`；读取入口把 `query` 归一为 `business`，写入只落 `business`。

- [ ] **Step 4: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_business_conversation.py tests/test_migrations.py`

Expected: PASS。

### Task 2: 重命名 Agent 定义、提示词与工具目录

**Files:**
- Rename: `backend/app/agent/definitions/query.py` → `business.py`
- Rename: `backend/app/agent/prompts/query.py` → `business.py`
- Rename: `backend/app/agent/tools/query/` → `business/`
- Modify: `backend/app/agent/definitions/registry.py`
- Modify: `backend/app/agent/definitions/__init__.py`
- Modify: `backend/app/agent/tools/business/summary.py`
- Test: `backend/tests/test_business_agent_definition.py`

- [ ] **Step 1: 写失败测试**

断言：

- 注册表用 `kind='business'` 找到 `BusinessAgentDefinition`。
- 定义构建的工具集包含 `search_graphs`、`get_graph_detail`、`query_objects`、`get_related_objects`、`ask_user`。
- 提示词角色是“业务助手”，同时描述查询与行动。
- 工具摘要仍能正确描述 `query_objects` / `get_related_objects`。

- [ ] **Step 2: 重命名并更新导入**

保留 `query_objects`、`get_related_objects`、`rewrite_query` 工具名；只改 Agent、目录、提示词与摘要模块命名。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_business_agent_definition.py tests/test_agent_definitions.py`

Expected: PASS。

### Task 3: API 与前端路由兼容

**Files:**
- Create: `backend/app/api/business.py`
- Modify: `backend/app/api/query.py`
- Modify: `frontend/src/app/App.tsx`
- Test: `backend/tests/test_business_conversation.py`
- Test: `frontend/src/features/business/views/BusinessWorkbench.test.tsx`

- [ ] **Step 1: 后端入口**

新增 `POST /business/conversations`；旧 `POST /query/conversations` 保留，内部创建 `business` 会话。

- [ ] **Step 2: 前端路由**

新增 `/business`、`/business/new`、`/business/:conversationId`；`/query` 重定向到 `/business`。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_business_conversation.py`

Run: `cd frontend && npm test -- --run BusinessWorkbench`

Expected: PASS。

## Phase 1 · 行动展示与图发布

### Task 4: 建立行动展示层

**Files:**
- Create: `backend/app/services/action/presentation.py`
- Test: `backend/tests/test_action_presentation.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- 摘要字段严格为设计文档 §4.3 定义的集合。
- `parameters` 按 `order_index` 排序，对象参数带 `target_object_type_name`。
- `effects.objects` / `effects.relations` 从规则推导，带 `rule_kinds`。
- `criteria` 用代码渲染一行文本，带 `failure_message`；不返回完整操作数。
- 完整详情包含赋值、条件树、属性名称。
- 同一行动多次读取输出稳定。

- [ ] **Step 2: 实现投影**

提供：

```text
load_action_summary(session, action_type_id, owner_id, graph_id) -> ActionSummary
load_action_summaries(session, graph_id, owner_id) -> list[ActionSummary]
summary_to_dict(summary) -> dict
action_detail_to_dict(detail) -> dict
render_action_card_section(summaries) -> list[str]
```

名称解析在本模块一次完成，图卡、图详情、工具都复用。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_action_presentation.py`

Expected: PASS。

### Task 5: 图卡与图详情加入行动

**Files:**
- Modify: `backend/app/services/graph/publication.py`
- Modify: `backend/app/services/graph/search.py`
- Test: `backend/tests/test_graph_publication_actions.py`
- Test: `backend/tests/test_graph_detail_actions.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- 图卡包含“可执行动作”区段，包含名称、描述、参数、影响和校验。
- 图向量对包含行动的整张图卡生成。
- 行动或其子元素为 draft 时阻断发布。
- 行动引用的对象/关系不在图内时阻断发布。
- `get_graph_detail` 顶层有 `actions`，字段与 §4.3 一致。
- 未发布图不返回行动摘要。

- [ ] **Step 2: 实现**

`render_graph_card(snapshot, action_summaries)` 保持纯函数；`publish_graph` 先加载摘要、校验行动、再生成图卡和向量。`get_graph_detail` 复用 `load_action_summaries`。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_graph_publication_actions.py tests/test_graph_detail_actions.py tests/test_graph_publication.py tests/test_graph_search.py`

Expected: PASS。

### Task 6: 增加 `get_action_detail` 工具

**Files:**
- Modify: `backend/app/agent/tools/business/action.py`
- Modify: `backend/app/agent/tools/business/selection.py`
- Modify: `backend/app/agent/prompts/business.py`
- Test: `backend/tests/test_business_action_tools.py`

- [ ] **Step 1: 写工具测试**

覆盖：

- `get_action_detail(graph_id, action_type_id)` 只返回当前图内、当前 owner 的行动。
- 返回完整参数、规则、赋值、条件树和名称。
- 行动不在图内返回结构化 `ACTION_NOT_IN_GRAPH`。
- 提示词要求先摘要、后详情，禁止猜测参数或赋值。

- [ ] **Step 2: 实现绑定**

工具只做 UUID 转换与 `ctx.invoke`；业务校验在 `services/action/presentation.py`。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_business_action_tools.py`

Expected: PASS。

## Phase 2 · 执行服务原语

### Task 7: 行动上下文与参数解析

**Files:**
- Create: `backend/app/services/action/execution/context.py`
- Create: `backend/app/services/action/execution/parameters.py`
- Create: `backend/app/services/action/execution/errors.py`
- Test: `backend/tests/test_action_execution_parameters.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- 行动必须属于当前 owner、当前图且状态可执行。
- 行动快照包含参数、规则、赋值、条件树，哈希稳定。
- 默认值只在参数缺失时生效。
- 必填、未知参数、类型、枚举、对象类型不匹配分别返回明确错误。
- 对象参数必须 effective 可见。
- v1 `is_array=true` 返回 `UNSUPPORTED_ARRAY`。

- [ ] **Step 2: 实现**

`context.py` 负责行动/图/名称/哈希；`parameters.py` 只负责输入归一与校验；`errors.py` 定义稳定错误码。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_action_execution_parameters.py`

Expected: PASS。

### Task 8: 提交校验求值

**Files:**
- Create: `backend/app/services/action/execution/criteria.py`
- Test: `backend/tests/test_action_execution_criteria.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- `parameter`、`object_property`、`static` 三种操作数。
- `is`、`is not`、判空、数值/日期/时间比较。
- 失败返回条件 ID、失败提示、实际值、期望值。
- 条件树不合法时返回结构化错误，不执行。
- 无条件的行动直接通过。

- [ ] **Step 2: 实现**

求值使用 effective 对象值；不把条件树交给 LLM。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_action_execution_criteria.py`

Expected: PASS。

### Task 9: 规则装配 edit set

**Files:**
- Create: `backend/app/services/action/execution/planner.py`
- Test: `backend/tests/test_action_execution_planner.py`

- [ ] **Step 1: 写失败测试**

覆盖五种规则：

- `create_object`：赋值转 values；文本主键自动生成；非文本主键缺失报错。
- `modify_object`：赋值转 set，null 转 clear；主键修改被拒绝。
- `delete_object`：生成 objectDeletes。
- `create_link` / `delete_link`：只允许 many-to-many，生成关系变更。
- 外键关系不生成 link 操作。
- 同一对象/关系重复冲突返回 `DUPLICATE_EFFECT`。
- 数组属性返回 `UNSUPPORTED_ARRAY`。

- [ ] **Step 2: 实现**

输出 `EditSetDraft`，不直接发 HTTP。对象/关系名称、属性名称在 `context.py` 中解析。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_action_execution_planner.py`

Expected: PASS。

## Phase 3 · Preview、确认与 Apply

### Task 10: 数据服务 edit-set 客户端

**Files:**
- Create: `backend/app/clients/data_service/ontology_client/edits.py`
- Modify: `backend/app/clients/data_service/ontology_client/client.py`
- Modify: `backend/app/clients/data_service/ontology_client/base.py`
- Test: `backend/tests/test_ontology_edit_client.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- `preview_edit_set(payload)` 调 `POST /internal/v1/ontology/edit-sets/preview`。
- `apply_edit_set(payload)` 调 `POST /internal/v1/ontology/edit-sets/apply`。
- 请求 snake_case 自动转 camelCase，响应转回 snake_case。
- `409 EDIT_CONFLICT` 映射为专用异常，保留 `conflicts`。
- 404/400/422/5xx 映射沿用现有客户端错误。

- [ ] **Step 2: 实现**

新增 `EditClientMixin`，在 `OntologyDataServiceClient` 组合；不改数据服务现有接口。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_ontology_edit_client.py`

Expected: PASS。

### Task 11: Preview 与确认载荷

**Files:**
- Create: `backend/app/services/action/execution/preview.py`
- Test: `backend/tests/test_action_execution_preview.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- preview 请求包含 execution、owner、conversation、graph、action、hash、snapshot、parameters、expected、五类 edit set。
- expected 从 preview 的 before 版本构造；对象创建不设 expected。
- 冲突时返回 `PREVIEW_CONFLICT`，不构造确认选项。
- 确认载荷包含对象/字段前后值、关系变更、warnings、execution_id、preview_hash。
- preview_hash 在相同输入下稳定，变更版本或参数后变化。

- [ ] **Step 2: 实现**

`preview.py` 只编排；对象/属性名称映射复用 `presentation.py`。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_action_execution_preview.py`

Expected: PASS。

### Task 12: `execute_action` 与确认中断

**Files:**
- Create: `backend/app/services/action/execution/executor.py`
- Create: `backend/app/services/action/execution/readback.py`
- Modify: `backend/app/agent/tools/business/action.py`
- Modify: `backend/app/schemas/message_blocks.py`
- Modify: `backend/app/agent/runtime/interrupts.py`
- Modify: `backend/app/agent/runtime/events.py`
- Modify: `backend/app/agent/runtime/stream.py`
- Test: `backend/tests/test_action_execution_tool.py`
- Test: `backend/tests/test_action_confirmation_interrupt.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- 工具名 `execute_action`，参数为 `graph_id`、`action_type_id`、`parameters`。
- 第一次调用执行 preview 并 interrupt，不 apply。
- 确认后重新 preview；hash 一致才 apply。
- 取消返回 `CANCELLED`，不写数据。
- preview 变化返回 `PREVIEW_STALE`，不 apply。
- apply 使用相同 idempotency key；网络重试返回已有结果。
- apply 成功返回 effective readback。
- `action_confirmation` 事件含结构化对象/字段/关系预览，前端事件形状稳定。

- [ ] **Step 2: 实现**

`executor.py` 负责 interrupt/apply/幂等；`readback.py` 负责把数据服务结果与 preview 转成用户可读变更。`stream.py` 透传确认预览。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_action_execution_tool.py tests/test_action_confirmation_interrupt.py`

Expected: PASS。

### Task 13: 更新业务 Agent 提示词

**Files:**
- Modify: `backend/app/agent/prompts/business.py`
- Test: `backend/tests/test_business_prompts.py`

- [ ] **Step 1: 写提示词断言**

必须包含：

- 先判断查询还是行动。
- 行动先看 `get_graph_detail.actions`，再按需 `get_action_detail`。
- 缺参数只问最阻塞的一个，不猜。
- 只通过 `execute_action` 执行，不直接写数据。
- preview 确认由工具触发，模型不能跳过。
- 最终回答引用 readback 实际值。
- 多行动匹配时先 `ask_user`。

- [ ] **Step 2: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_business_prompts.py`

Expected: PASS。

## Phase 4 · 前端

### Task 14: 业务工作台改名与路由

**Files:**
- Rename: `frontend/src/features/query/` → `frontend/src/features/business/`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/shared/api/query.ts`
- Modify: `frontend/src/shared/api/conversations.ts`
- Test: `frontend/src/features/business/views/BusinessWorkbench.test.tsx`

- [ ] **Step 1: 更新文案与路由**

“查询工作台”改为“业务工作台”；`/query` 重定向 `/business`；API 调用改 `/business/conversations`。

- [ ] **Step 2: 跑前端校验**

Run: `cd frontend && npx tsc -b && npm run lint && npm test -- --run BusinessWorkbench`

Expected: PASS。

### Task 15: 行动确认卡片

**Files:**
- Modify: `frontend/src/shared/types/agent.ts`
- Modify: `frontend/src/shared/chat/lib/messageBlocks.ts`
- Modify: `frontend/src/shared/chat/hooks/useAgentRun.ts`
- Modify: `frontend/src/shared/chat/components/InterruptCard.tsx`
- Create: `frontend/src/features/business/components/ActionConfirmationCard.tsx`
- Create: `frontend/src/features/business/lib/actionCopy.ts`
- Modify: `frontend/src/styles/query.css` 或拆分 `styles/action-confirmation.css`
- Test: `frontend/src/features/business/components/ActionConfirmationCard.test.tsx`
- Test: `frontend/src/shared/chat/components/InterruptCard.test.tsx`

- [ ] **Step 1: 写失败测试**

覆盖：

- 显示行动名称、摘要、对象类型/标题/主键。
- 字段表显示 before → after；新增/删除对象正确标记。
- 关系变更显示两端对象。
- 确认/取消按钮回填正确 answer。
- 提交中禁用按钮；取消后不再提交。
- 旧 `ask_user` 中断仍按原样渲染。

- [ ] **Step 2: 实现类型与渲染**

确认卡片只消费结构化 `confirmation`；文案放 `actionCopy.ts`；样式独立文件，不塞进页面。

- [ ] **Step 3: 跑测试**

Run: `cd frontend && npx tsc -b && npm run lint && npm test -- --run ActionConfirmationCard InterruptCard`

Expected: PASS。

## Phase 5 · 验收

### Task 16: 后端全量校验

- [ ] **Step 1: 跑后端校验**

```bash
cd backend && .venv/bin/python -m pytest -q
cd backend && .venv/bin/ruff check .
cd backend && .venv/bin/ruff format --check .
cd backend && .venv/bin/mypy app
```

Expected: 全部通过；若出现与本计划无关的既有失败，单独列出，不混入结果。

- [ ] **Step 2: 跑数据服务定向校验**

```bash
cd data-service && ./mvnw -q -Dtest='EditSet*,OntologyEditApiTest,Effective*' test
```

Expected: PASS。编辑覆盖层已实现，本计划不应修改其行为。

### Task 17: 前端全量校验

```bash
cd frontend && npx tsc -b
cd frontend && npm run lint
cd frontend && npm test
cd frontend && npm run build
```

Expected: 全部通过。

### Task 18: 端到端验收

- [ ] **Step 1: 查询链路回归**

```text
提问 → 召回图 → 读取图详情 → query_objects / get_related_objects → 证据回答
```

- [ ] **Step 2: 行动链路**

```text
行动请求 → 图卡含行动 → get_graph_detail.actions → get_action_detail
→ 查询对象参数 → execute_action → preview 卡片 → 确认 → apply → readback → 回答
```

- [ ] **Step 3: 失败路径**

覆盖：缺参数、提交校验失败、preview 冲突、用户取消、preview 变化、apply 版本冲突、网络超时幂等重试。

## 完成标准

- 业务 Agent 已改名，旧 `/query` 入口仍可兼容。
- 图卡包含行动并参与图向量召回，不需要独立 action 检索。
- `get_graph_detail.actions` 字段与设计文档一致。
- 五种行动规则均可通过 `execute_action` 执行。
- 未确认不写数据；确认后单事务 apply；失败不半写。
- 变更只落编辑覆盖层，审计可回放。
- Agent 回答使用 readback 实际值。
- 后端、前端与定向 Java 校验通过。
