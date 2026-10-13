# 动作层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在本体里加入「动作层」——动作类型 / 参数 / 规则 / 字段赋值，作为一等共享池元素，复用既有的草案/确认/撤销与改动集管线；并给关系补上外键绑定。全部是定义层，不执行、不碰实例。

**Architecture:** 动作类型与 object_type / relation 同构（共享池元素）。参数与规则是它的子元素，各自作为改动集元素种类登记。规则的校验策略单独成模块。先做两处行为不变的重构（拆 `ontology.py`、把按 kind 的分派收敛成注册表），再叠加动作能力，避免在旧结构上继续堆分支。

**Tech Stack:** FastAPI + SQLAlchemy + Alembic + pgvector；前端 React + TS + React Flow；agent 层 deepagents。

**对应设计文档：** `docs/superpowers/specs/2026-10-13-action-layer-design.md`

**前置：** 关系基数与属性类型已完成并提交（`0014`，230 测试通过）。

## 文件结构

| 文件 | 职责 |
|---|---|
| `backend/app/vocabulary.py` | 动作枚举（规则种类 / 参数种类 / 值来源） |
| `backend/app/models.py` | 5 张新表 + `relation.foreign_key_property_id` |
| `backend/app/services/element_kinds.py` | 元素种类注册表 |
| `backend/app/services/object_type.py` `property.py` `relation.py` | 从 `ontology.py` 拆出 |
| `backend/app/services/ontology.py` | 门面（行为不变） |
| `backend/app/services/action.py` | 动作类型 + 参数 + 规则读写与不变量 |
| `backend/app/services/action_rules.py` | 规则 / 赋值 / 外键校验策略 |
| `backend/app/api/actions.py` | REST 边界 |
| `backend/app/schemas/action.py` | 请求/响应模型 |
| `backend/app/agent/action_tools.py` | 智能体动作抽取工具 |
| `frontend/src/features/modeling/components/ActionNode.tsx` `ActionDrawer.tsx` `ActionRuleRow.tsx` | 前端 |
| `frontend/src/features/modeling/lib/actionConfig.ts` | 动作配置外置 |

---

## Phase 0 · 关系外键绑定

**Files:** `models.py`、`services/relation.py`（拆分后）、`schemas/`、`alembic/versions/0015_*.py`、前端 `RelationDrawer.tsx`、`lib/relationDetails.ts`；测试 `tests/test_relation_service.py`。

- [x] 迁移 0015：`relation` 加 `foreign_key_property_id`。
- [x] 校验：属性属于关系两端之一、单值、类型与对端主键一致；`many_to_many` 禁止设置。
- [x] API / schema 暴露该字段；前端关系抽屉可选择一个外键属性。
- [x] 测试覆盖合法与非法组合。
- [x] `cd backend && .venv/bin/python -m pytest -q && ruff check . && ruff format --check . && mypy app`；前端 `npx tsc -b && npm run lint && npm test`。

## Phase 1 · 行为不变重构

**Files:** `services/element_kinds.py`（新）、`services/object_type.py` `property.py` `relation.py`（新）、`services/ontology.py`（改门面）、`services/changeset.py`、`services/element_edit.py`。

- [ ] 把 `ontology.py` 的对象 / 属性 / 关系三块拆成三个模块，`ontology.py` 只转发。
- [ ] 建 `element_kinds.py`：kind → 模型 / 可还原字段 / owner 解析 / 校验器 / 删除策略。
- [ ] `changeset.py` 与 `element_edit.py` 改为查注册表，删掉按 kind 的散落分支。
- [ ] 全量测试通过，行为不变。

## Phase 2 · 动作定义层（后端）

**Files:** `vocabulary.py`、`models.py`、`services/action.py`、`services/action_rules.py`、`api/actions.py`、`schemas/action.py`、`api/router.py`、`alembic/versions/0016_*.py`；测试 `tests/test_action_service.py`、`tests/test_action_rules.py`、`tests/test_actions_api.py`。

- [ ] 迁移 0016：`action_type` / `action_parameter` / `action_rule` / `action_rule_assignment` / `graph_action`。
- [ ] 动作类型 CRUD + 参数 / 规则读写的不变量。
- [ ] `action_rules.py`：对象规则 vs 链接规则 vs 外键的校验策略。
- [ ] `changeset.py` / `element_edit.py` 注册 `action_type` / `action_parameter` / `action_rule`。
- [ ] 删除保护：被动作引用的对象类型 / 关系拒绝删除。
- [ ] REST：`/api/v1/actions` 系列。
- [ ] 测试：服务层不变量、规则校验、API 冒烟、改动集确认/撤销。

## Phase 3 · 智能体工具

**Files:** `agent/action_tools.py`、`agent/tools.py`、`agent/schemas.py`、`agent/graph.py`；测试 `tests/test_action_agent_tools.py`。

- [ ] `propose_action` / `update_action` / `remove_action` / `list_actions` 工具，闭包注入上下文，错误结构化。
- [ ] 注册进工具目录与子图 prompt。
- [ ] 测试工具可被调用并落进改动集。

## Phase 4 · 前端

**Files:** `components/ActionNode.tsx` `ActionDrawer.tsx` `ActionRuleRow.tsx`、`lib/actionConfig.ts`、`lib/flow.ts`、`OntologyCanvas.tsx`、`views/ModelingPage.tsx`、`shared/types/*`、`shared/api/client.ts`、`styles/*`。

- [ ] 动作节点渲染 + `involves` 边。
- [ ] 动作抽屉：参数与规则的增删改。
- [ ] 配置外置到 `lib/actionConfig.ts`。
- [ ] `shared/types` 与 `client.ts` 按域拆分（行为不变）。
- [ ] 测试：`lib` 纯函数单测；`npx tsc -b && npm run lint && npm test && npm run build`。

## Phase 5 · 收尾

- [ ] `graph_cleanup.py` 覆盖 `graph_action`。
- [ ] 更新 `backend/README.md`、根 `README.md` 路线图与接口一览。
- [ ] 全量校验命令跑通。
