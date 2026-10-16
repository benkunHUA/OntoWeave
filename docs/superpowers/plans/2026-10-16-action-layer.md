# 动作层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在本体里加入动作层——动作类型 / 参数 / 规则 / 字段赋值 / 提交校验，作为一等共享池元素，复用既有的草案-确认-撤销与改动集管线；同时给共享词表补 `enum` 数据类型。全部是定义层，不执行、不碰实例。

**Architecture:** 动作类型与 object_type / relation 同构（共享池元素），子元素（参数 / 规则 / 赋值 / 条件节点 / 操作数）各自作为改动集元素种类登记进 `element_kinds.py`。前端先完成 `ModelingPage` 的 hook + 服务端状态重构，再叠动作 UI。

**Tech Stack:** FastAPI + SQLAlchemy + Alembic + pgvector；前端 React + TS + React Flow + TanStack Query；agent 层 deepagents。

**对应设计文档：** `docs/superpowers/specs/2026-10-16-action-layer-design.md`

**设计已定，不再改动：** 规则 5 种 / 值来源 3 种 / 参数 2 类 / `enum` 进共享词表 / 条件树邻接表（v1 只放行一个 `and` 根 + 扁平 condition）/ 动作工具做成 `action_modeler` 子智能体 / 写路径复用 `POST /graphs/{id}/edits`。

## 全局约束

- 先规划再落笔；单文件 > ~300 行按职责拆；重复即抽象；配置与文案外置。
- 后端校验：`cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app`
- 前端校验：`cd frontend && npx tsc -b && npm run lint && npm test && npm run build`
- 每个 Phase 结束都要跑通上述命令再进下一个 Phase。

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/app/vocabulary.py` | `PropertyDatatype` 加 `enum`；动作枚举（rule_kind / parameter kind / value_source / criteria operator / node kind / logic） |
| `backend/app/models.py` | 7 张新表（含 `graph_action`）+ `property.enum_values` |
| `backend/app/services/element_kinds.py` | 元素种类注册表扩到 9 种，并接管 create 分派 |
| `backend/app/services/action.py` | 动作类型 / 参数 / 规则 / 赋值 / 条件树的读写与不变量 |
| `backend/app/services/action_rules.py` | 规则-赋值校验策略（按 rule_kind 分派） |
| `backend/app/services/criteria/` | 条件树：`store` 读取与删除顺序 / `shape` 单条条件 / `build` 建树 / `tree` 整棵树不变量 |
| `backend/app/services/action_references.py` | 删除保护：被动作定义引用的共享元素拒绝删除 |
| `backend/app/services/action_brief.py` `action_detail.py` | 画布 brief 与 `involves` 推导 / 动作详情组装 |
| `backend/app/services/element_edit/criteria.py` | 条件树整棵替换：diff 后落草稿并登记改动集 |
| `backend/app/services/property.py` `relation.py` | 补 `enum_values` 校验；引用检查 |
| `backend/app/services/graph_cleanup.py` | 覆盖 `graph_action` |
| `backend/app/api/actions.py` `backend/app/schemas/action.py` | REST 边界与模型 |
| `backend/app/schemas/changeset.py` | `ElementKind` 扩展 + 新 op |
| `backend/app/agent/tools/action/{__init__,ops,args,binding,subagent}.py` | 动作工具包与 `action_modeler` 子智能体 |
| `backend/app/agent/tools/search.py` | 主智能体与子智能体共用的只读检索（对象类型 + 动作） |
| `backend/app/agent/prompts.py` | 子智能体注册与行为规矩 |
| `frontend/src/features/modeling/components/ActionNode.tsx` `ActionDrawer.tsx` `ActionParameterRow.tsx` `ActionRuleRow.tsx` `ActionCriteriaEditor.tsx` | 动作 UI |
| `frontend/src/features/modeling/lib/actionConfig.ts` `actionDetails.ts` | 配置与纯函数 |
| `frontend/src/features/modeling/views/ModelingPage.tsx` + `features/modeling/hooks/*` | 页面重构与编排 |
| `frontend/src/shared/types/*` `shared/api/*` | 按域拆分 |

---

## Phase 0 · 行为不变重构

**Why:** 不先把删除顺序和前端页面结构理顺，后面每个 Phase 都要在错的结构上加东西。

**Files:** `backend/app/services/element_edit/`、`backend/app/services/element_kinds.py`、`backend/tests/test_element_kinds.py`；前端 `frontend/src/features/modeling/hooks/*`、`context/*`、`components/*`、`shared/types/*`、`shared/api/*`、`shared/query/client.ts`。

后端：

- [x] `services/element_edit.py`（371 行）拆成包：`ops.py`（编辑操作形状）、`creates.py`（各元素「建草稿 + 登记改动集」）、`apply.py`（分派与更新/删除）；`__init__.py` 原样 re-export，调用方不改 import。
- [x] create 分派收敛成 `apply.py` 里的一张表 `_CREATE_HANDLERS`（op 类 → 实现），加新元素不再写 elif；并暴露 `create_handlers()` 给「漏登记」兜底测试。
- [x] `delete_phase` 重排：`relation` / `property` = 3、`object_type` = 4；0 / 1 / 2 留给动作元素（赋值 / 操作数、规则 / 条件节点、参数）。阶梯写进 `element_kinds.py` 注释。
- [x] 测试：`tests/test_element_kinds.py`（阶梯关系断言 + 一批里同时删类型/属性/关系）+ `test_element_edit.py` 里的 create op 全覆盖断言。
- [x] 自引用的「按树深度倒序删」挪到 P2：P0 里没有自引用种类，写在这里无法验证；等 `action_criteria_node` 落地时连测试一起加。

前端（先重构，再叠动作 UI）：

- [x] `shared/types` 按域拆成 `common` / `ontology` / `graph` / `view` / `changeset` / `conversation` / `agent`，`index.ts` 只 re-export。
- [x] `shared/api` 拆成 `client`（请求内核）/ `conversations` / `graph` / `view` / `changesets` / `agent`，`index.ts` re-export。
- [x] 引入 TanStack Query：`shared/query/client.ts` 一份缓存，`App.tsx` 挂 `QueryClientProvider`。
- [x] 拆 hook：`useConversations` / `useWorkspace` / `useChatMessages` / `useAgentRun` / `useCanvasSelection` / `useNodeDrawer` / `useRelationDrawer` / `usePaneLayout` / `useErrorToast`；配置外置 `lib/paneLayout.ts`、`lib/drawerSubmit.ts`、`lib/chatMessages.ts`。
- [x] `context/ModelingProvider.tsx` 装配 hook，`context/modelingContext.ts` 按关注点分组提供上下文。
- [x] `views/ModelingPage.tsx` 1006 → 42 行，只摆放三栏；面板拆成 `ConversationPane` / `ChatPane`（+ `ChatHeader` / `MessageList` / `MessageBubble` / `Composer` / `ChangesetPanel` / `ErrorToast`）/ `GraphPane`（+ `PaneResizer`）。
- [x] 会话改名后同步图名走 `useWorkspace.applyConversation`（本地改缓存，不整块重取）。
- [x] 前后端全量校验通过（后端 263 测试；前端 45 测试 + build）。

## Phase 1 · 语义层补 `enum`

**Files:** `backend/app/vocabulary.py`、`backend/app/services/enum_values.py`（新）、`property.py`、`models.py`、`element_kinds.py`、`element_edit/`、`schemas/changeset.py`、`schemas/graph.py`、`api/changesets.py`、`alembic/versions/0016_*.py`；`backend/tests/test_property_enum.py`（新）；前端 `shared/types/{ontology,changeset}.ts`、`lib/nodeDetails.ts`、`components/EnumValuesEditor.tsx`（新）、`PropertyFieldRow.tsx`、`styles/drawer.css`。

- [x] `PropertyDatatype` 加 `ENUM`，`PROPERTY_DATATYPES` / `sql_in_list` 自动带上。
- [x] 迁移 0016：`property.enum_values`（JSONB 可空）+ 重建 `ck_property_datatype`（含 `enum`）+ 新增 `ck_property_enum_values`（enum 必须带候选值）。
- [x] `services/enum_values.py`：候选值共同规则（非空字符串、不重复、至少 2 项），属性与（P2 的）动作参数共用一份。
- [x] `property.py`：`create_property(enum_values=...)` 与 patch 校验都要求「类型与候选值配套」——转成 enum 必须同时给候选值，转出 enum 必须同时清空。
- [x] `element_kinds.py` 的 property `restorable_fields` 加 `enum_values`（撤销要能还原）。
- [x] 改动集 `PropertyCreate` / `CreatePropertyEdit` / API 映射 / agent `propose_property` 都支持 `enum_values`；快照读模型带上 `enum_values`。
- [x] 前端：类型下拉加「枚举」；`nodeDetails` 的草稿带 `enumValues`，diff 规则与后端严格校验对齐（换出 enum 时发 `enum_values: null`），`draftValidationError` 拦「不足两个 / 重复」。
- [x] 前端：`EnumValuesEditor`（一值一行，可增删）+ `PropertyFieldRow` 只在该类型下渲染 + 样式进 `drawer.css`。
- [x] 测试：`test_property_enum.py`（10 条，含库层 CHECK 与撤销还原）、`nodeDetails.test.ts`（5 条）。
- [x] 前后端全量校验通过（后端 273 测试；前端 50 测试 + build）。

## Phase 2 · 动作定义层（后端）

**Files:** `backend/app/vocabulary.py`、`backend/app/models.py`、`backend/app/services/action.py`、`action_rules.py`、`criteria/{store,shape,build,tree}.py`、`action_references.py`、`value_types.py`、`enum_values.py`、`element_kinds.py`、`element_edit/`、`changeset.py`、`changeset_review.py`、`graph_cleanup.py`、`embedding.py`、`backend/alembic/versions/0017_*.py`、`backend/tests/test_action_{service,rules,criteria,references}.py`。

- [x] `vocabulary.py`：`ActionRuleKind` / `ActionParameterKind` / `ActionValueSource` / `ActionCriteriaNodeKind` / `ActionCriteriaLogic` / `ActionCriteriaOperator` + 运算符兼容矩阵（`criteria_operators_for`）。
- [x] 迁移 0017：7 张新表 + 索引（`action_type_id`、`rule_id`、`node_id`、`parent_id`）+ CHECK；`alembic check` 干净。
- [x] `services/action.py`：动作类型 CRUD、参数读写、状态流转、入图（`graph_action` 的 `add_action_to_graph` / `list_graph_actions`）；参数名与顺序位在动作内唯一。
- [x] `services/action_rules.py`：按 `rule_kind` 分派的规则校验策略 + 赋值校验（值来源 / 类型 / 枚举 / 一个属性只赋一次）。
- [x] `services/criteria/`（拆成包）：`store` 读取 + 删除顺序、`shape` 单条条件与操作数的形状、`build` 建树与整棵替换、`tree` 整棵树不变量与补丁校验（单一根、无环、v1 只放行一层 `and`、运算符矩阵、判空无右操作数）。
- [x] `element_kinds.py`：种类名常量 + 登记 6 种新 kind（`restorable_fields` / `delete_phase` / `promote` / `validate_patch`），并给条件节点加 `order_for_delete`（子先父后）。
- [x] `element_kinds.py` 另加 `dependents` 钩子：删动作类型时连子元素各记一条 delete op——赋值 / 操作数对参数是 RESTRICT，只靠数据库级联会在删参数那一步撞外键。
- [x] `element_edit/`：动作元素 create 路径（`ActionTypeCreate` / `ActionParameterCreate` / `ActionRuleCreate`，赋值内联并各记一条 op）+ 注册表驱动的 update / delete。
- [x] 删除保护 `services/action_references.py`：`object_type` / `relation` / `property` / `action_parameter` 被引用即拒绝删除，错误信息带引用方清单（含级联算引用：对象类型 → 它的属性与关系）。
- [x] `changeset.py` 同相位内按 `order_for_delete` 逐条删（自引用表）；`changeset_review.py` 的定稿向量与回执文案覆盖动作类型。
- [x] `graph_cleanup.py` 覆盖 `graph_action`（三种 scope）；`embedding.py` 抽出共用内核并覆盖动作类型向量。
- [x] 测试：`test_action_service.py`（16）、`test_action_rules.py`（16）、`test_action_criteria.py`（19）、`test_action_references.py`（8）+ 删除顺序 / 清理 / 向量扩到既有测试文件；后端全量 338 passed。

## Phase 3 · REST

**Files:** `backend/app/api/actions.py`、`api/router.py`、`backend/app/schemas/action.py`、`schemas/changeset.py`、`schemas/graph.py`、`backend/app/services/action_{brief,detail}.py`、`services/element_edit/criteria.py`、`backend/tests/test_actions_api.py`。

- [x] `schemas/action.py`：读模型（`ActionTypeRead` / `ActionParameterRead` / `ActionRuleRead` + 赋值 / `ActionCriteriaNodeRead` 递归 + `ActionDetailRead`）+ 入参模型 `OperandIn` / `ConditionIn` / `AssignmentIn`。
- [x] `GET /api/v1/actions`（`q` 按名称 / 描述子串匹配、`status` 过滤、分页）与 `GET /api/v1/actions/{id}`（参数 + 规则含赋值 + 条件树）。
- [x] `services/action_detail.py`：详情组装（接口层与动作工具共用）；`services/action_brief.py`：画布 brief 与 `involves` 推导（对象引用参数 / 对象规则 / 链接规则 + 关系两端）。
- [x] `ElementKind` 扩到 9 种（与 `element_kinds.REGISTRY` 一致，有测试兜住）；新增 op `create_action_type` / `add_action_parameter` / `add_action_rule`（assignments 内联）/ `set_action_criteria`。
- [x] `services/element_edit/criteria.py`：整棵替换——`criteria.replacement_plan` 算 diff，新建节点 + 操作数各记 create，消失的条件记 delete；算计划时排除本改动集里已标删的节点（`changeset.pending_delete_ids`）。根分组只建一次、不参与 diff（删了再建会同时出现两个根）。
- [x] `element_edit/apply.py` 的 `_CREATE_HANDLERS` 改名 `_WRITE_HANDLERS` / `create_handlers()` → `write_handlers()`：这张表现在装的是「新建 + 整棵替换」两类写入 op。
- [x] `GraphSnapshotRead` 加 `actions` brief（`GraphSnapshot.actions`，服务层推导 `involves`）。
- [x] 删除保护复查：`action_references` 不区分草稿 / 已确认，被已确认动作引用的共享元素一样删不掉（`test_shared_element_used_by_a_confirmed_action_cannot_be_deleted` 锁定），无需新增代码。
- [x] 测试 `tests/test_actions_api.py`（12）：API 冒烟、判别联合与形状 422、brief 的 `involves`、详情形状、条件整棵替换的留 / 删 / 建、确认落状态、列表过滤、404；后端全量 349 passed。
- [ ] 已知取舍：同一个改动集里「删掉一条条件 + 删掉该条件用到的参数」会被删除保护挡住（保护只看库里现有引用，不看本批次的标删）。留到有真实用法时再给 `ensure_deletable` 加改动集上下文。

## Phase 4 · Agent

**Files:** `backend/app/agent/tools/action/{__init__.py,ops.py,args.py,binding.py,subagent.py}`、`agent/tools/search.py`、`agent/tools/ontology/{ops,binding}.py`、`agent/tools/graph_read.py`、`agent/prompts.py`、`agent/runtime/assembly.py`、`backend/app/services/element_edit/creates.py`、`backend/tests/test_action_agent_tools.py`、`tests/test_agent_search.py`。

- [x] `ops.py`：模型视角的读 / 写（`search_actions` / `get_action` / `get_object_type` / `propose_action` / `reuse_action` / `propose_action_parameter` / `propose_action_rule` / `set_action_criteria`），纯 Python 可单测；`args.py` 是工具入参模型，`Field(description=...)` 写法对齐 `tools/view/registry.py`。
- [x] `binding.py`：按表装配工具，入参与 id 转换在绑定期做，错误统一走 `ctx.invoke` 兜成结构化结果。
- [x] `subagent.py` 注册 `action_modeler`（与 `view_designer` 同先例），工具 = 共享检索 `search` + 动作工具；`runtime/assembly.py` 挂上去。
- [x] `list_types` / `search_ontology` 合并成 `search`（`agent/tools/search.py`）：`kind` 可选 `object_type` / `action_type`，不传 `query` 列出全部（含草稿，看 `status`）；主智能体与动作子智能体共用一个只读入口。
- [x] 超计划的必要补充：`graph_read.property_brief` + `get_object_type`——规则赋值与校验条件要落到 `target_property_id`，光靠 `get_subgraph` 拿不到属性 id。
- [x] `element_edit.reuse_action_type`：复用已确认的动作并进当前图（与 `reuse_object_type` 同规矩，不记改动集 op）；只给工具用，暂不进 REST（设计里的写 op 只有 4 个）。
- [x] `prompts.py`：`ACTION_AGENT_PROMPT` / `ACTION_AGENT_DESCRIPTION` + 主提示词第 3 条改 `search`、新增第 10 条「动作交给 action_modeler」；子智能体提示词写清最小输入（名字即可落草稿 / 确认至少名字 + 一条规则）、规则选择（一对一 / 一对多走 `modify_object` 改外键、多对多才 link）、`create_object` 主键可留空。
- [x] 测试：`tests/test_action_agent_tools.py`（10，工具可调用且落进改动集、子智能体加载、错误结构化）+ `tests/test_agent_search.py`（6，合并后的检索）；后端全量 363 passed。
- [ ] 未做（下一步单独拍板）：把对象建模工具也收进 `modeler` 子智能体。设计 §8 的「工具分类 = 子智能体目录」目前只落了视图 / 动作两档，主智能体仍直接持有建模工具。

## Phase 5 · 前端动作 UI

**Files:** `frontend/src/features/modeling/components/ActionNode.tsx` `ActionDrawer.tsx` `ActionParameterRow.tsx` `ActionRuleRow.tsx` `ActionCriteriaEditor.tsx`、`components/dialogs/*`、`lib/actionConfig.ts`、`lib/actionDetails.ts`、`lib/flow.ts`、`OntologyCanvas.tsx`、`hooks/useActionDetail.ts`、`shared/types/action.ts`、`shared/api/action.ts`、`styles/action.css`。

- [x] `shared/types/action.ts` + `shared/api/action.ts`：详情与 brief 模型、取数。
- [x] `lib/actionConfig.ts`：规则种类、参数种类、值来源、运算符矩阵、尺寸、文案（全部外置）。
- [x] `lib/actionDraft.ts` + `lib/actionEdits.ts` + `lib/actionValidation.ts`：草稿模型 + `buildActionEdits` + 实时校验（含引用失效），配单测。
- [x] `flow.ts`：动作节点 + `involves` 边（自动、不可连删、视觉弱化）+ `selectedActionId`。
- [x] `ActionNode.tsx`：图上的动作节点（视觉与对象节点区分）。
- [x] `ActionDrawer.tsx`：四块（基本信息 / 参数 / 规则 / 提交校验）+ 子组件；新增参数与规则各发一条 create edit，校验整棵替换。
- [x] 切换规则种类清空不相关字段；引用失效行内标红并禁用保存。
- [x] 样式文件 `styles/action.css`，在 `App.tsx` 按顺序 import。
- [x] 前端全量校验 + 手动过一遍对话创建动作的链路。

## Phase 6 · 收尾

- [x] 更新 `backend/README.md`、根 `README.md`（含 `frontend/README.md`）的路线图与接口一览。
- [ ] 复查 `docs/palantir/docs-summary/` 与实现的一致性，更新本规格的「待定」列表。
- [x] 前后端全量校验命令跑通（后端 366 passed + ruff / format / mypy / alembic check；前端 tsc / lint / 68 tests / build）。
