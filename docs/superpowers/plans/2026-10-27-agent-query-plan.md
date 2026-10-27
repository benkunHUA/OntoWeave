# P4 只读查询 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付只读查询闭环：改写问题、召回并选择图、读取图详情，由持久化查询子 Agent 解析锚点、逐步执行关系查询并返回带证据的答案。

**Architecture:** 主 Agent 只负责改写、选图和委派；查询子 Agent 使用 `CompiledSubAgent` 和独立 checkpoint 线程，自己规划并执行步骤。图详情只提供类型层 schema，实例数据只通过 `query_objects` 和 `execute_query_step` 读取。不建 `queryTask` 表，不恢复 `match_question_to_graph` 或 `plan_next_query_step`。

**Tech Stack:** Python 3.12、FastAPI、SQLAlchemy、Alembic、LangGraph/deepagents、pgvector、pytest、ruff、mypy；Java 21、Spring Boot、JdbcTemplate、Testcontainers。

**设计文档：** `docs/superpowers/specs/2026-10-27-agent-query-design.md`

**范围：** P4 只读查询链路与前端查询工作台。动作执行、审计和写回不在本计划内。

**前端设计：** `docs/superpowers/specs/2026-10-27-query-workbench-ui.md`

## 实施状态

- Task 1–12、Task 14–15 已完成；Java 查询原语 Task 6 已完成。
- Task 13 的固定夹具场景由现有查询服务、工具、子 Agent 与前后端测试覆盖，未单独创建 `test_query_acceptance.py`。
- 后端 649 个测试通过，ruff/mypy 通过；前端 284 个测试与生产构建通过；Java 查询定向测试通过。
- 建模画布顶部新增发布入口，接入 `POST /graphs/{id}/publish` 与 `GET /graphs/{id}/publication`。
- Java 全量仍有 11 个既有失败，集中在 Iceberg/Spark 与调度时间戳用例；查询相关用例不受影响。

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/app/schemas/query.py` | 查询工具入参/出参、查询步骤、子 Agent 结果与状态枚举。 |
| `backend/app/vocabulary.py` | 查询状态、方向、排序方向等共享枚举。 |
| `backend/alembic/versions/0023_graph_publication.py` | `graph` 增加发布图卡、图向量、发布时间，并建向量索引。 |
| `backend/app/models.py` | `Graph` 发布字段。 |
| `backend/app/services/graph_publication.py` | 生成图卡、发布整图、覆盖旧发布产物。 |
| `backend/app/services/graph_search.py` | 已发布图向量召回与图详情读取。 |
| `backend/app/services/query/objects.py` | `query_objects` 的 domain service：参数归一、结果形状、状态。 |
| `backend/app/services/query/steps.py` | `execute_query_step` 的 step 校验、输入步骤依赖与结果形状。 |
| `backend/app/agent/tools/query/state.py` | 子 Agent 持久化步骤状态、步骤 ID 与证据累积。 |
| `backend/app/agent/tools/query/selection.py` | `search_graphs`、`get_graph_detail` 工具。 |
| `backend/app/agent/tools/query/binding.py` | `query_objects`、`execute_query_step` 工具绑定。 |
| `backend/app/agent/tools/query/context.py` | 查询工具上下文：session、owner、graph、data-service client。 |
| `backend/app/agent/tools/query/state.py` | 查询子 Agent 的持久化步骤状态：steps、anchor、frontier、evidence。 |
| `backend/app/agent/tools/query/subagent.py` | 持久化 `CompiledSubAgent`、独立线程包装、工具与提示词装配。 |
| `backend/app/agent/prompts_query.py` | 主 Agent 与查询子 Agent 的提示词。 |
| `backend/app/agent/definitions/query.py` | `query` 会话的 AgentDefinition。 |
| `backend/alembic/versions/0024_query_conversation.py` | `conversation.kind` 增加 `query` 并更新 CHECK 约束。 |
| `backend/app/services/conversation.py` | 创建不绑定固定图的查询会话。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/query/` | 对象排序、关系遍历过滤/排序/投影、批量主键读取。 |
| `backend/tests/test_query_schemas.py` | 查询 schema 与状态不变量。 |
| `backend/tests/test_graph_publication.py` | 图卡、发布覆盖、发布校验。 |
| `backend/tests/test_graph_search.py` | 图向量召回、发布状态过滤、图详情。 |
| `backend/tests/test_query_services.py` | `query_objects` 与 `execute_query_step` 的领域校验、去重与截断。 |
| `backend/tests/test_query_tools.py` | 两个实例查询工具的绑定、步骤状态与结构化错误。 |
| `backend/tests/test_query_agent.py` | 查询会话、独立线程、checkpoint 续跑与结构化输出。 |
| `backend/tests/test_query_prompts.py` | 主 Agent 与查询子 Agent 的提示词约束。 |

---

## Phase 0 · 查询契约

### Task 1: 定义查询 schema 与状态

**Files:**
- Create: `backend/app/schemas/query.py`
- Modify: `backend/app/vocabulary.py`
- Test: `backend/tests/test_query_schemas.py`

- [ ] **Step 1: 写失败测试**

覆盖以下不变量：

- `QueryStatus` 只能取 `OK / EMPTY / TRUNCATED / NOT_INDEXED / FAILED`。
- `QueryAgentStatus` 只能取 `OK / EMPTY / NOT_INDEXED / NEEDS_USER / BLOCKED`。
- `query_objects` 的 `primary_key` 与 `q`、`filters`、`sort` 互斥。
- `execute_query_step` 的 `direction` 只能是 `outgoing / incoming`。
- `expected_cardinality` 只能是 `one / many`。
- `limit` 有正数上限。
- `NEEDS_USER` 必须带 `question`；`OK` 必须带 `answer`；`BLOCKED` 必须带 `limitations`。

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_schemas.py`

Expected: FAIL，模块不存在或断言失败。

- [ ] **Step 2: 实现 schema**

建立以下模型：

```text
QueryFilter {property_id, op, value?}
QuerySort {property_id, direction}
QueryObjectsInput {object_type_id, primary_key?, q?, filters?, sort?, limit, project?}
QueryObjectsResult {step_id, object_type_id, index_version_id, items, total, status, truncated}
QueryStep {input_step_id, relation_id, direction, filters?, sort?, limit, project?, expected_cardinality}
QueryStepResult {step_id, status, items, total, truncated, index_version_id}
Evidence {step_id, object_type_id, primary_key, title, properties, index_version_id}
QueryAgentResult {status, answer?, evidence, question?, options, limitations}
```

状态值和方向值放进 `vocabulary.py`，不要在 schema 里另写一套字符串。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_schemas.py`

Expected: PASS。

### Task 2: 固定提示词与结果格式

**Files:**
- Create: `backend/app/agent/prompts_query.py`
- Create: `backend/app/agent/prompts_rewrite_query.py`
- Test: `backend/tests/test_query_prompts.py`

- [ ] **Step 1: 写提示词断言**

测试提示词必须包含：

- 一次只执行一步
- 只能使用图详情中的类型、属性、关系 ID
- 多个同名对象必须返回 `NEEDS_USER`
- “最新/最近”必须使用业务时间字段
- 结果截断时不能假设全集
- 不跨图、不编造、不拼 SQL
- 不直接回答未经工具验证的数据

- [ ] **Step 2: 写提示词**

`prompts_rewrite_query.py` 复用已确认的 `2026-10-27-rewrite-query-prompt.md`，不要再写第二套规则。

`prompts_query.py` 分成：

- `MAIN_QUERY_AGENT_PROMPT`：主 Agent 负责改写、召回、选图、校验和委派。
- `QUERY_AGENT_PROMPT`：查询子 Agent 负责锚点、路径、逐步执行和证据回答。
- `QUERY_AGENT_DESCRIPTION`：给 `task` 工具使用的委派说明。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_prompts.py`

Expected: PASS。

---

## Phase 1 · 图发布与选择

### Task 3: 增加图发布产物

**Files:**
- Create: `backend/alembic/versions/0023_graph_publication.py`
- Modify: `backend/app/models.py`
- Test: `backend/tests/test_migrations.py`

- [ ] **Step 1: 写迁移测试**

断言 `graph` 有：

```text
graph_card TEXT NULL
graph_embedding VECTOR(1536) NULL
published_at TIMESTAMPTZ NULL
```

并断言 `ix_graph_embedding_hnsw` 存在。

- [ ] **Step 2: 写迁移与模型**

在 `Graph` 上增加对应字段。`graph_embedding` 维度复用 `EMBEDDING_DIM`。重新发布允许覆盖，不建历史表。

- [ ] **Step 3: 跑迁移测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py`

Expected: PASS。

### Task 4: 生成图卡并发布整图

**Files:**
- Create: `backend/app/services/graph_publication.py`
- Create: `backend/app/schemas/graph_publication.py`
- Modify: `backend/app/api/graphs.py`
- Test: `backend/tests/test_graph_publication.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- 只包含 `confirmed / approved` 的对象类型、属性和关系。
- 草稿元素或指向草稿的关系阻断发布。
- 图卡包含对象类型、主键/标题键、属性和关系方向/基数。
- 再次发布覆盖 `graph_card`、`graph_embedding`、`published_at`。
- 发布失败不写入半成品。
- 未发布图不能出现在查询召回中。

- [ ] **Step 2: 实现服务**

`graph_publication.py` 拆成：

- `render_graph_card(snapshot) -> str`：纯投影，不访问外部服务。
- `publish_graph(session, graph_id, owner_id, embedder) -> Publication`：校验、生成图卡、生成向量、覆盖写入。

接口：

```text
POST /api/v1/graphs/{graph_id}/publish
GET  /api/v1/graphs/{graph_id}/publication
```

发布接口只做边界校验和转发；发布规则放在 service。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_graph_publication.py`

Expected: PASS。

### Task 5: 图召回与图详情工具

**Files:**
- Create: `backend/app/services/graph_search.py`
- Create: `backend/app/agent/tools/query/selection.py`
- Test: `backend/tests/test_graph_search.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- `search_graphs(query, limit=5)` 只返回当前用户已发布图。
- 返回 `graph_id`、`similarity`、`graph_card`，不返回对象实例。
- 相似度按 `1 - cosine_distance` 计算。
- `get_graph_detail(graph_id)` 只返回对象类型、属性、关系类型、方向、基数。
- 不返回对象实例、数据源、映射草稿。
- 未发布或他人图返回 not found。

- [ ] **Step 2: 实现服务与工具**

`graph_search.py` 负责检索与详情投影；`selection.py` 只绑定模型工具。两个工具都通过 `ToolContext.invoke` 把领域错误转成结构化 payload。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_graph_search.py`

Expected: PASS。

---

## Phase 2 · 实例查询能力

### Task 6: 补齐 Java 查询原语

**Files:**
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/query/`

- [x] **Step 1: 先写 Java 失败测试**

覆盖：

- 对象列表按任意已映射属性排序；同值按主键稳定排序。
- 关系遍历按对端属性过滤、排序、投影。
- 批量按主键读取对象。
- 未映射属性、跨类型操作符返回 422。
- 结果超过上限返回 `TRUNCATED` 且带 `total`。
- 空索引返回 `EMPTY`，不是 500。
- 多对多关系结果去重。

- [x] **Step 2: 实现查询能力**

新增一个受控 step 查询入口，避免 BFF 拼接多个分页结果：

```text
POST /internal/v1/ontology/query-steps
```

请求包含图 ID、owner、输入对象引用、关系 ID、方向、过滤、排序、limit、project。响应包含 step 结果、total、status、index_version_id。对象列表接口补排序和批量主键读取；不在 Java 暴露自由 SQL。

- [x] **Step 3: 跑 Java 测试**

Run: `cd data-service && ./mvnw -q test -Dtest='Ontology*Query*,FilterParser*'`

Expected: PASS。

### Task 7: Python 客户端与查询服务

**Files:**
- Modify: `backend/app/services/data_service/ontology_client/queries.py`
- Create: `backend/app/services/query/objects.py`
- Create: `backend/app/services/query/steps.py`
- Test: `backend/tests/test_ontology_client.py`
- Test: `backend/tests/test_query_services.py`
- Test: `backend/tests/test_query_tools.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- Java camelCase 转到 snake_case 后形状稳定。
- `query_objects` 的锚点搜索、按主键读取、筛选、排序、投影和截断。
- `execute_query_step` 的输入类型校验、关系方向校验、目标类型推导、多对多去重。
- 未知属性、未知关系、跨图对象被拒绝。
- 空索引、截断、失败状态原样上抛，不吞掉。
- `step_id` 由 service 分配，模型不能指定。

- [ ] **Step 2: 实现客户端方法**

至少新增：

```text
query_objects(...)
execute_query_step(...)
batch_get_objects(...)
```

`query/objects.py` 和 `query/steps.py` 只做领域校验和结果归一，不直接解析 HTTP DTO。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_ontology_client.py tests/test_query_services.py tests/test_query_tools.py`

Expected: PASS。

### Task 8: 绑定两个查询工具

**Files:**
- Create: `backend/app/agent/tools/query/context.py`
- Create: `backend/app/agent/tools/query/binding.py`
- Test: `backend/tests/test_query_tools.py`

- [ ] **Step 1: 写工具测试**

覆盖：

- 工具名严格是 `query_objects`、`execute_query_step`。
- 不暴露 `get_object`、`get_object_relations`、`match_question_to_graph`、`plan_next_query_step`。
- 入参 schema 与设计文档一致。
- `query_objects` 第一次结果分配 `s0`，并写入 `steps` 状态；后续详情读取分配新 step ID。
- `execute_query_step` 从 `steps` 状态解析 `input_step_id`，缺失时返回结构化错误。
- 领域错误返回 `{error, message}`，不抛穿工具层。
- 不传 owner/graph ID，全部由上下文注入。

- [ ] **Step 2: 实现绑定**

`binding.py` 只做参数转换和 `ctx.invoke`，业务规则留在 `services/query/`。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_tools.py`

Expected: PASS。

---

## Phase 3 · 持久化查询子 Agent

### Task 9: 独立线程与 CompiledSubAgent

**Files:**
- Create: `backend/app/agent/tools/query/state.py`
- Create: `backend/app/agent/tools/query/subagent.py`
- Test: `backend/tests/test_query_agent.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- `query_agent` 用 `CompiledSubAgent` 暴露给 `task`。
- `task` 的 `description` 是固定 JSON 信封，包含 `graph_id`、`graph_detail`、`rewritten_query`；缺少字段、非法 `graph_id` 或图 ID 不一致时返回 `BLOCKED`，不写线程。
- 线程 ID 固定为 `<conversation_id>:query:<graph_id>`。
- 同一个线程连续两次调用能看到上一次的工具结果。
- `steps`、`anchor_refs`、`frontier`、`evidence` 随 checkpoint 一起持久化。
- `execute_query_step` 能按 `input_step_id` 从状态取到上一步的输入对象引用。
- 子 Agent 只向主 Agent 回传 `messages` 和 `structured_response`，不泄漏 `steps`、`frontier`。
- 换 graph_id 得到新线程。
- 子 Agent 不继承主 Agent 的 messages，只拿到任务输入。
- `response_format` 返回 `QueryAgentResult`。
- 没传 checkpointer 时明确拒绝构建持久化查询子 Agent。

- [ ] **Step 2: 实现持久化 runnable**

用一个 `Runnable` 包装器覆盖 `task` 传入的 config。先从 `description` 的 JSON 信封解析并校验 `graph_id`，再生成子线程 config：

```text
parent thread_id = conversation_id
query thread_id  = f"{conversation_id}:query:{graph_id}"
```

包装器内部调用 `create_agent(..., state_schema=QueryAgentState, checkpointer=checkpointer, response_format=QueryAgentResult)`，并把返回 state 显式投影为 `{"messages": ..., "structured_response": ...}`。不能只依赖 `CompiledSubAgent` 的默认返回逻辑：它会把未列入排除项的自定义字段并回父 Agent。查询工具通过 `ToolRuntime` 读取并更新步骤状态。复用应用现有的 `PostgresSaver`，不新建表，不建 `queryTask`。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_agent.py`

Expected: PASS；用 `InMemorySaver` 验证续跑，不依赖真实 Postgres。

### Task 10: 子 Agent 提示词与工具集

**Files:**
- Modify: `backend/app/agent/tools/query/subagent.py`
- Test: `backend/tests/test_query_agent.py`

- [ ] **Step 1: 写装配测试**

断言子 Agent：

- 工具只有 `query_objects`、`execute_query_step`。
- 没有文件、Shell、`task`、`ask_user` 工具。
- 系统提示词包含一步一规划、锚点优先、同名追问、业务时间字段、截断处理规则。
- 输出 schema 是 `QueryAgentResult`。

- [ ] **Step 2: 装配**

`build_query_subagent(...)` 返回 `CompiledSubAgent`，名称 `query_agent`，描述说明它负责“图内多步只读查询与证据回答”。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_agent.py`

Expected: PASS。

---

## Phase 4 · 主 Agent 接入与验收

### Task 11: 新增查询会话

**Files:**
- Create: `backend/alembic/versions/0024_query_conversation.py`
- Modify: `backend/app/vocabulary.py`
- Modify: `backend/app/services/conversation.py`
- Create: `backend/app/agent/definitions/query.py`
- Modify: `backend/app/agent/definitions/registry.py`
- Test: `backend/tests/test_query_agent.py`
- Test: `backend/tests/test_agent_definitions.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- `ConversationKind.QUERY` 可创建，不绑定固定图。
- `conversation.kind` 的 CHECK 约束接受 `query`。
- 查询会话的 `resource_id` 只用于会话身份，不当作 graph ID。
- `QueryAgentDefinition` 注册成功，工具包含 `rewrite_query`、`search_graphs`、`get_graph_detail`、`ask_user`。
- 子 Agent 列表包含 `query_agent`。

- [ ] **Step 2: 实现迁移与定义**

先写迁移更新 `ck_conversation_kind`，再改模型、枚举和会话服务。迁移必须可降级；降级前检查是否存在 `kind='query'` 的会话。

查询会话资源 ID 使用随机 UUID，不要求对应 `graph` 行。`QueryAgentDefinition.build_graph`：

- 主 Agent 工具：`rewrite_query`、`search_graphs`、`get_graph_detail`、`ask_user`。
- 子 Agent：`build_query_subagent(...)`。
- 使用现有 `checkpointer`；没有 checkpointer 时，持久化查询子 Agent不启用，并返回明确错误。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_agent.py tests/test_agent_definitions.py`

Expected: PASS。

### Task 12: 改写与委派闭环

**Files:**
- Create: `backend/app/agent/tools/query/rewrite.py`
- Modify: `backend/app/agent/definitions/query.py`
- Test: `backend/tests/test_query_agent.py`

- [ ] **Step 1: 写失败测试**

覆盖：

- `rewrite_query` 只返回一句业务查询。
- 主 Agent 看到 Top-5 图卡后选图；代码校验图已发布且属于当前用户。
- 选图后调用 `get_graph_detail`，再委派 `query_agent`。
- 委派 `task` 的 `description` 是含 `graph_id`、`graph_detail`、`rewritten_query` 的固定 JSON 信封。
- 子 Agent 返回 `NEEDS_USER` 时，主 Agent 调 `ask_user`；回答后再次委派到同一 query 线程。
- 没有合适图时报告模型缺口，不硬选。
- 代码不按概念相似度过滤。

- [ ] **Step 2: 实现与提示词**

`rewrite.py` 用一次模型调用完成改写；主 Agent 提示词明确“先改写、再召回、再选图、最后委派”。选图后的代码校验放在 service，不放在提示词里。

- [ ] **Step 3: 跑测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_agent.py`

Expected: PASS。

### Task 13: 集成验收

**Files:**
- Create: `backend/tests/test_query_acceptance.py`
- Create: `backend/tests/fixtures/query_acceptance.py`

- [ ] **Step 1: 准备固定图与假数据服务**

构造两张已发布图：

- 销售运营：Customer → Order → Product → Supplier
- 员工组织：Employee → Department

假数据服务返回固定对象实例、关系实例和索引版本。

- [ ] **Step 2: 写场景测试**

至少覆盖：

1. “张三最新订单有没有发货”
2. “张三最新订单里买的商品由哪个供应商提供”
3. “查一下张三的订单”（同名/多类型需要追问）
4. “有多少订单”（锚点为空时列对象并返回 total）
5. 无数据、未建索引、结果截断
6. 同一会话追问沿用 query 线程
7. 换图后使用新的 query 线程

- [ ] **Step 3: 跑集成测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_query_acceptance.py`

Expected: PASS。

### Task 14: 全量校验与文档回填

**Files:**
- Modify: `docs/superpowers/phases/p4-agent-loop.md`
- Modify: `docs/superpowers/ROADMAP.md`
- Modify: `README.md`（如有查询入口说明）

- [x] **Step 1: 跑后端校验**

Run:

```bash
cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app
```

Expected: 全绿。

### Task 15: 查询工作台入口与两栏布局

**Files:**
- Create: `frontend/src/features/query/views/QueryWorkbench.tsx`
- Create: `frontend/src/features/query/components/QueryConversationSidebar.tsx`
- Create: `frontend/src/features/query/components/QueryChatPane.tsx`
- Create: `frontend/src/features/query/hooks/useQueryConversations.ts`
- Create: `frontend/src/features/query/hooks/useQueryConversation.ts`
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/features/home/views/HomePage.tsx`
- Modify: `frontend/src/shared/types/conversation.ts`
- Create: `frontend/src/shared/api/query.ts`
- Create: `frontend/src/styles/query.css`
- Create: `frontend/src/features/modeling/components/GraphPublicationControl.tsx`
- Create: `frontend/src/features/modeling/hooks/useGraphPublication.ts`
- Create: `frontend/src/features/modeling/lib/publication.ts`
- Modify: `frontend/src/shared/api/graph.ts`

- [x] **Step 1: 接入查询会话类型**
增加 `CONVERSATION_KIND_QUERY`，并实现创建查询会话的 API 调用。

- [x] **Step 2: 实现两栏工作台**
左侧会话列表、右侧对话；复用共享消息、输入框、中断卡片和错误提示。

- [x] **Step 3: 接入首页入口**
首页新增「业务问答」卡片与路由 `/query`、`/query/:conversationId`。

- [x] **Step 4: 查询证据展示**
从 `task` 工具结果解析 `evidence`，能解析时显示摘要，失败时保留原始结果。

- [x] **Step 5: 前端校验**
Run: `cd frontend && npx tsc -b && npm run lint && npm test && npm run build`

Expected: PASS。

- [x] **Step 2: 跑 Java 查询定向校验**

Run:

```bash
cd data-service && TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -q test -Dtest='Ontology*Query*,FilterParser*'
```

Expected: 全绿。Java 全量另有 3 个失败、8 个错误，均在本轮未改动的 Iceberg/Spark 预览与调度时间戳用例，受 Java 26 与本地环境限制。

- [x] **Step 3: 回填阶段记录**

在 `p4-agent-loop.md` 记录实现结果、测试数量和已知限制；在 `ROADMAP.md` 更新 P4 查询子阶段状态。不要在阶段文件复制设计正文。

## Definition of Done

- 查询链路没有 `match_question_to_graph`、`plan_next_query_step`、`queryTask` 表。
- 查询子 Agent 是 `CompiledSubAgent`，同一会话同一图可跨 `task` 调用续跑上下文。
- 图详情只含类型层信息；实例数据只经 `query_objects`、`execute_query_step` 读取。
- 同名对象、缺少业务时间字段、截断、未建索引都有明确状态，不静默猜。
- 主 Agent 只做改写、选图、校验和委派，不参与单步规划。
- 后端与 Java 查询定向校验通过；Java 全量剩余失败已记录且与本变更无关。

## 交接说明

- 图发布接口是 P4 的前置；未发布图不会进入召回。
- Java 查询接口未就绪时，先用 fake client 锁定 BFF 与工具契约，不要绕过受控工具。
- 查询子 Agent 的线程复用现有 checkpointer；不要另建任务状态表。
