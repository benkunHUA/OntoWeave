# 数据整理（P2）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付「对话 + 画布」的数据处理流程：用户可以选择输入数据集、配置处理节点、预览结果、构建派生数据集版本、配置定时调度，并查看输出版本的直接来源。

**Architecture:** 浏览器只调用 Python BFF；Python 负责会话、事件和 Agent，Java 数据服务负责流程、草稿、节点目录、校验、预览、构建、调度和派生数据集。预览与构建共用一套内部逻辑计划，但预览使用有界读取，构建使用冻结版本和精确输入快照。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring Data JPA、Flyway、PostgreSQL、Flink 1.20 Table API、Iceberg 1.10、Python 3.12、FastAPI、SQLAlchemy、Alembic、LangGraph、React 19、TypeScript、`@xyflow/react`、`@dagrejs/dagre`、Vitest、JUnit 5、Testcontainers。

**Baseline:** `f09fca7`（P1 已完成；本计划只新增 P2 代码）。

**设计来源:** `docs/superpowers/specs/2026-10-19-data-preparation-technical-design.md`。设计已确认 8/8，来源追踪按降级范围执行；本计划不重新打开设计议题。

**Non-goals:** 不做多分支、增量写入、增量调度触发、完整血缘图、下游影响分析、字段级血缘、自由 SQL、用户自定义函数、Python/Java 跨服务分布式事务。

**执行规则:** 按 Task 1 → Task 12 顺序执行；每个 Task 先写失败测试，再写最小实现，测试通过后单独提交。完成一个 Task 立即勾选步骤，并同步更新 `docs/superpowers/phases/p2-data-preparation.md` 的进度；不要在未通过当前 Task 校验前进入下一个 Task。

---

## 1. 执行顺序与交付边界

| 阶段 | 任务 | 交付结果 | 验证 |
|---|---|---|---|
| Java 基础 | Task 1-4 | 流程、草稿、节点目录、校验、REST 可独立运行 | Maven 单测 + MockMvc 集成测试 |
| Java 执行 | Task 5-7 | 节点预览、构建发布、定时调度、来源查询 | 引擎适配器测试 + Testcontainers 集成测试 |
| Python | Task 8-9 | 浏览器统一 API、会话绑定、数据整理 Agent | pytest + ruff + mypy |
| 前端 | Task 10-11 | 三栏工作台、画布、配置、预览、构建、调度、来源 | tsc + lint + Vitest + build |
| 总验收 | Task 12 | 真实环境端到端闭环和验收记录 | 真实 P1 数据集 → P2 构建 → 来源查询 |

每个任务都能单独形成可测试的增量。Task 1 建表后先保证数据库和实体一致；Task 2-4 完成后即使没有执行引擎，也可以通过 REST 创建流程、编辑草稿并获得检查结果；Task 5-7 再补预览、构建和调度。Task 8-9 通过假 Java 客户端先锁定 Python 契约，Task 10-11 再接入真实浏览器界面，Task 12 才做真实 Docker 闭环。

## 2. 文件结构

| 区域 | 文件/目录 | 职责 |
|---|---|---|
| Java 迁移 | `data-service/src/main/resources/db/migration/V8__pipeline_domain.sql` | P2 全部表、约束、索引和 P3 只读视图 |
| Java 领域 | `data-service/src/main/java/com/ontoweave/data/pipeline/domain/` | 流程、草稿、输出、版本、构建、调度实体与枚举 |
| Java 仓储 | `data-service/src/main/java/com/ontoweave/data/pipeline/repository/` | JPA 仓储、条件更新、`SKIP LOCKED` 查询 |
| Java 节点目录 | `data-service/src/main/java/com/ontoweave/data/pipeline/catalog/` | 节点定义、端口、配置 JSON Schema、UI Schema |
| Java 草稿与校验 | `data-service/src/main/java/com/ontoweave/data/pipeline/draft/`、`graph/`、`check/` | 命令、撤销、DAG、schema 推导、统一检查结果 |
| Java 执行 | `data-service/src/main/java/com/ontoweave/data/pipeline/execution/` | 逻辑计划、Flink 适配器、预览、构建 worker、发布 |
| Java 调度 | `data-service/src/main/java/com/ontoweave/data/pipeline/schedule/` | cron 解析、到期扫描、无变化判断、暂停恢复 |
| Java 来源 | `data-service/src/main/java/com/ontoweave/data/pipeline/source/` | 单跳来源投影和 P1 元数据批量读取 |
| Java API | `data-service/src/main/java/com/ontoweave/data/pipeline/api/` | `/internal/v1/pipelines/**` 和节点目录接口 |
| Python 客户端 | `backend/app/services/data_service/client.py`、`mapping.py` | Java 内部接口调用与 camelCase 映射 |
| Python 编排 | `backend/app/services/data_pipeline.py`、`backend/app/api/data_pipelines.py` | 稳定 ID、会话绑定、浏览器 API |
| Python Agent | `backend/app/agent/definitions/data_pipeline.py`、`prompts_data_pipeline.py`、`tools/data_pipeline/` | 流程状态、结构化写入工具、确认中断、提示词 |
| Python 迁移 | `backend/alembic/versions/0020_data_pipeline_conversation.py` | `data_pipeline` 会话、归档字段、`turn_id` |
| Python 事件 | `backend/app/services/conversation_event.py` | 消费 P2 发件箱事件、写入 `turn_id`、按会话投递 |
| 前端 API/类型 | `frontend/src/shared/api/dataPipeline.ts`、`frontend/src/shared/types/dataPipeline.ts` | 浏览器 API 和类型契约 |
| 前端工作台 | `frontend/src/features/data-pipeline/` | 流程列表、对话、画布、配置抽屉、预览、构建、调度、来源 |
| 共享骨架 | `frontend/src/shared/workbench/` | 三栏布局、拖拽分栏、会话面板外壳；抽取后数据连接工作台行为保持不变 |
| 样式 | `frontend/src/styles/data-pipeline.css` | 工作台、画布、节点、抽屉、预览和构建详情样式 |

## 3. 固定契约

### 3.1 草稿 JSON

```json
{
  "schemaVersion": 1,
  "nodes": [
    {
      "id": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11",
      "type": "filter",
      "name": "只保留有效订单",
      "config": {
        "condition": {
          "kind": "compare",
          "field": "status",
          "op": "eq",
          "value": {"kind": "literal", "dataType": "string", "value": "active"}
        }
      },
      "position": {"x": 320, "y": 180}
    }
  ],
  "edges": [
    {
      "id": "f0a0c5b0-6e1a-4f2f-9d6b-7a8c2c9d4e21",
      "sourceNodeId": "30000000-0000-4000-8000-000000000001",
      "sourcePort": "out",
      "targetNodeId": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11",
      "targetPort": "in"
    }
  ]
}
```

### 3.2 草稿命令

```json
{
  "type": "add_node",
  "baseRevision": 4,
  "operationId": "5e2a0b3c-7d4e-4f6a-8b9c-0d1e2f3a4b5c",
  "turnId": "b5f4f1d8-1d8e-4d6e-9c5f-3a2b1c0d9e8f",
  "node": {
    "id": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11",
    "type": "filter",
    "name": "只保留有效订单",
    "config": {},
    "position": {"x": 320, "y": 180}
  }
}
```

允许的 `type`：`add_node`、`update_node`、`delete_node`、`connect`、`disconnect`、`set_output`、`move_node`。`move_node` 不递增 revision、不写编辑历史、不触发检查。

### 3.3 检查结果

`GET /internal/v1/pipelines/{id}/check` 返回：

```json
{
  "pipelineId": "...",
  "revision": 12,
  "stage": "invalid",
  "blockingIssues": [
    {
      "code": "FIELD_NOT_FOUND",
      "phase": "compile",
      "blocking": true,
      "nodeId": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11",
      "portId": "in",
      "field": "amount",
      "message": "上游输出没有 amount 字段",
      "hint": "检查上游的选列或改名配置"
    }
  ],
  "warnings": [],
  "nextActions": [{"action": "configure_node", "nodeId": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11"}]
}
```

派生状态优先级：`building` → `empty` / `needs_input` / `needs_output` / `invalid` → `failed` → `stale` → `succeeded` → `ready`。

### 3.4 来源响应

`GET /internal/v1/pipelines/{id}/datasets/{datasetId}/sources?versionId=...` 返回直接输入，不递归。`versionId` 省略时使用 `pipeline_dataset.current_version_id`。查询链路固定为：

`pipeline_dataset_version.build_output_id → pipeline_build_output → pipeline_build.resolved_inputs → pipeline_version`

## 4. Task 1：Java 数据库与领域模型

**Files:**
- Create: `data-service/src/main/resources/db/migration/V8__pipeline_domain.sql`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/domain/`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/PipelineSchemaIntegrationTest.java`

- [x] **Step 1: 写失败的 schema 测试**

```java
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PipelineSchemaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void createsPipelineTablesAndViews() {
        List<String> tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'data'",
            String.class
        );
        assertThat(tables).contains(
            "pipeline", "pipeline_draft", "pipeline_edit_op", "pipeline_output",
            "pipeline_dataset", "pipeline_version", "pipeline_build",
            "pipeline_build_output", "pipeline_dataset_version",
            "pipeline_schedule", "pipeline_schedule_run", "dataset_catalog",
            "dataset_version_catalog"
        );
    }

    @Test
    void keepsOnlyOneActiveBuildAndExposesPublishedVersions() {
        jdbc.update("""
            INSERT INTO data.pipeline
                (id, owner_id, conversation_id, name, status)
            VALUES (?, ?, ?, 'p', 'active')
            """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        List<String> indexes = jdbc.queryForList("""
            SELECT indexname FROM pg_indexes
            WHERE schemaname = 'data' AND tablename = 'pipeline_build'
            """, String.class);
        assertThat(indexes).contains("uq_pipeline_active_build");

        List<String> catalogColumns = jdbc.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'data' AND table_name = 'dataset_version_catalog'
            """, String.class);
        assertThat(catalogColumns).contains("dataset_kind", "version_id", "published_at");
    }
}
```

除上述元数据断言外，测试必须插入真实流程、版本和构建记录，验证：同一流程的第二个
`queued`/`running` 构建违反 `uq_pipeline_active_build`（SQLState `23505`），终态构建后
可以创建新的活跃构建；`dataset_version_catalog` 只返回 `published_at` 非空的
`pipeline_dataset_version`；两个锁定仓储方法都在事务中可执行。测试结束必须回滚或清理
插入数据，不能污染后续测试。

- [x] **Step 2: 运行测试，确认迁移缺失**

Run: `cd data-service && ./mvnw -Dtest=PipelineSchemaIntegrationTest test`

Expected: FAIL，提示 `pipeline` 表不存在或 Flyway 找不到 `V8`。

- [x] **Step 3: 创建 V8 迁移**

在 `V8__pipeline_domain.sql` 创建以下表和约束；字段名必须与实体一致：

```sql
CREATE TABLE data.pipeline (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft',
    current_version_id UUID,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_conversation UNIQUE (conversation_id),
    CONSTRAINT ck_pipeline_status CHECK (status IN ('draft', 'active', 'archived'))
);
CREATE INDEX ix_pipeline_owner_updated
    ON data.pipeline (owner_id, archived_at, updated_at DESC);

CREATE TABLE data.pipeline_draft (
    pipeline_id UUID PRIMARY KEY REFERENCES data.pipeline (id) ON DELETE CASCADE,
    definition JSONB NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by_type TEXT NOT NULL,
    updated_by_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE data.pipeline_edit_op (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    seq INTEGER NOT NULL,
    op_type TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id UUID,
    before JSONB,
    after JSONB,
    actor_type TEXT NOT NULL,
    actor_id UUID,
    operation_id UUID NOT NULL,
    turn_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_edit_op_revision_seq UNIQUE (pipeline_id, revision, seq)
);
CREATE INDEX ix_pipeline_edit_turn ON data.pipeline_edit_op (pipeline_id, turn_id);

CREATE TABLE data.pipeline_output (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    node_id UUID NOT NULL,
    output_type TEXT NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_output_node UNIQUE (pipeline_id, node_id),
    CONSTRAINT ck_pipeline_output_type CHECK (output_type IN ('dataset'))
);

CREATE TABLE data.pipeline_dataset (
    id UUID PRIMARY KEY,
    output_id UUID NOT NULL UNIQUE REFERENCES data.pipeline_output (id) ON DELETE CASCADE,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    write_mode TEXT NOT NULL DEFAULT 'snapshot',
    primary_key_columns JSONB NOT NULL DEFAULT '[]'::jsonb,
    expectations JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_version_id UUID,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_dataset_name UNIQUE (pipeline_id, name),
    CONSTRAINT ck_pipeline_dataset_write_mode CHECK (write_mode IN ('snapshot'))
);
CREATE INDEX ix_pipeline_dataset_pipeline ON data.pipeline_dataset (pipeline_id);

CREATE TABLE data.pipeline_version (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    definition JSONB NOT NULL,
    outputs JSONB NOT NULL,
    definition_hash TEXT NOT NULL,
    input_refs JSONB NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_version_number UNIQUE (pipeline_id, version_number),
    CONSTRAINT uq_pipeline_version_hash UNIQUE (pipeline_id, definition_hash)
);

CREATE TABLE data.pipeline_build (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    pipeline_version_id UUID NOT NULL REFERENCES data.pipeline_version (id),
    status TEXT NOT NULL,
    stage TEXT,
    trigger_type TEXT NOT NULL,
    operation_id UUID NOT NULL,
    requested_by UUID,
    resolved_inputs JSONB NOT NULL DEFAULT '[]'::jsonb,
    logic_hash TEXT,
    log_uri TEXT,
    error_code TEXT,
    error_message TEXT,
    stats JSONB NOT NULL DEFAULT '{}'::jsonb,
    heartbeat_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_build_operation UNIQUE (pipeline_id, operation_id),
    CONSTRAINT ck_pipeline_build_status
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'canceled')),
    CONSTRAINT ck_pipeline_build_trigger CHECK (trigger_type IN ('manual', 'schedule'))
);
CREATE UNIQUE INDEX uq_pipeline_active_build
    ON data.pipeline_build (pipeline_id) WHERE status IN ('queued', 'running');
CREATE INDEX ix_pipeline_build_created
    ON data.pipeline_build (pipeline_id, created_at DESC);
CREATE INDEX ix_pipeline_build_version_created
    ON data.pipeline_build (pipeline_id, pipeline_version_id, created_at DESC);
CREATE INDEX ix_pipeline_build_queued
    ON data.pipeline_build (created_at, id) WHERE status = 'queued';

CREATE TABLE data.pipeline_build_output (
    id UUID PRIMARY KEY,
    build_id UUID NOT NULL REFERENCES data.pipeline_build (id) ON DELETE CASCADE,
    output_id UUID NOT NULL REFERENCES data.pipeline_output (id) ON DELETE RESTRICT,
    output_type TEXT NOT NULL,
    status TEXT NOT NULL,
    stage TEXT,
    engine_job_id TEXT,
    log_uri TEXT,
    result JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code TEXT,
    error_message TEXT,
    stats JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_build_output UNIQUE (build_id, output_id),
    CONSTRAINT ck_pipeline_build_output_status
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'canceled'))
);

CREATE TABLE data.pipeline_dataset_version (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES data.pipeline_dataset (id) ON DELETE CASCADE,
    build_output_id UUID NOT NULL UNIQUE REFERENCES data.pipeline_build_output (id),
    version_number INTEGER NOT NULL,
    iceberg_catalog TEXT NOT NULL,
    iceberg_namespace TEXT NOT NULL,
    iceberg_table TEXT NOT NULL,
    iceberg_snapshot_id BIGINT NOT NULL,
    schema_json JSONB NOT NULL,
    row_count BIGINT NOT NULL,
    byte_size BIGINT NOT NULL,
    storage_path TEXT,
    committed_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_dataset_version_number UNIQUE (dataset_id, version_number),
    CONSTRAINT uq_pipeline_dataset_version_snapshot
        UNIQUE (dataset_id, iceberg_table, iceberg_snapshot_id)
);
CREATE INDEX ix_pipeline_dataset_version_created
    ON data.pipeline_dataset_version (dataset_id, version_number DESC);

ALTER TABLE data.pipeline_dataset
    ADD CONSTRAINT fk_pipeline_dataset_current_version
    FOREIGN KEY (current_version_id) REFERENCES data.pipeline_dataset_version (id) ON DELETE SET NULL;
ALTER TABLE data.pipeline
    ADD CONSTRAINT fk_pipeline_current_version
    FOREIGN KEY (current_version_id) REFERENCES data.pipeline_version (id) ON DELETE SET NULL;

CREATE TABLE data.pipeline_schedule (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL UNIQUE REFERENCES data.pipeline (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    trigger_type TEXT NOT NULL DEFAULT 'time',
    cron_expression TEXT NOT NULL,
    timezone TEXT NOT NULL,
    next_run_at TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    paused_by UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pipeline_schedule_status CHECK (status IN ('active', 'paused')),
    CONSTRAINT ck_pipeline_schedule_trigger CHECK (trigger_type IN ('time')),
    CONSTRAINT ck_pipeline_schedule_next_run
        CHECK (
            (status = 'active' AND next_run_at IS NOT NULL)
            OR (status = 'paused' AND next_run_at IS NULL)
        )
);
CREATE INDEX ix_pipeline_schedule_due
    ON data.pipeline_schedule (next_run_at) WHERE status = 'active';

CREATE TABLE data.pipeline_schedule_run (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES data.pipeline_schedule (id) ON DELETE CASCADE,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    build_id UUID UNIQUE REFERENCES data.pipeline_build (id) ON DELETE RESTRICT,
    logic_hash TEXT,
    resolved_inputs JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason_code TEXT,
    message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_schedule_run_time UNIQUE (schedule_id, scheduled_for),
    CONSTRAINT ck_pipeline_schedule_run_status
        CHECK (status IN ('pending', 'dispatched', 'ignored', 'failed'))
);
CREATE UNIQUE INDEX uq_pipeline_schedule_pending
    ON data.pipeline_schedule_run (schedule_id) WHERE status = 'pending';

CREATE VIEW data.dataset_catalog AS
SELECT 'sync'::text AS dataset_kind, d.id AS dataset_id, c.owner_id, d.name,
       d.current_version_id, d.created_at, d.updated_at
FROM data.dataset d
JOIN data.data_connection c ON c.id = d.connection_id
UNION ALL
SELECT 'pipeline'::text AS dataset_kind, d.id AS dataset_id, p.owner_id, d.name,
       d.current_version_id, d.created_at, d.updated_at
FROM data.pipeline_dataset d
JOIN data.pipeline p ON p.id = d.pipeline_id;

CREATE VIEW data.dataset_version_catalog AS
SELECT 'sync'::text AS dataset_kind, v.dataset_id, v.id AS version_id,
       v.version_number, v.row_count, v.schema_json, v.published_at
FROM data.dataset_version v
WHERE v.published_at IS NOT NULL
UNION ALL
SELECT 'pipeline'::text AS dataset_kind, v.dataset_id, v.id AS version_id,
       v.version_number, v.row_count, v.schema_json, v.published_at
FROM data.pipeline_dataset_version v
WHERE v.published_at IS NOT NULL;
```

- [x] **Step 4: 创建实体和仓储**

实体字段与迁移一一对应，实体名固定为：

`PipelineEntity`、`PipelineDraftEntity`、`PipelineEditOpEntity`、`PipelineOutputEntity`、`PipelineDatasetEntity`、`PipelineVersionEntity`、`PipelineBuildEntity`、`PipelineBuildOutputEntity`、`PipelineDatasetVersionEntity`、`PipelineScheduleEntity`、`PipelineScheduleRunEntity`。

字段映射固定为：

```text
PipelineEntity: id, ownerId, conversationId, name, status, currentVersionId,
                archivedAt, createdAt, updatedAt
PipelineDraftEntity: pipelineId, definition, revision, updatedByType,
                     updatedById, updatedAt
PipelineEditOpEntity: id, pipelineId, revision, seq, opType, targetType,
                      targetId, before, after, actorType, actorId,
                      operationId, turnId, createdAt
PipelineOutputEntity: id, pipelineId, nodeId, outputType, name, status,
                      archivedAt, createdAt, updatedAt
PipelineDatasetEntity: id, outputId, pipelineId, name, writeMode,
                       primaryKeyColumns, expectations, currentVersionId,
                       archivedAt, createdAt, updatedAt
PipelineVersionEntity: id, pipelineId, versionNumber, definition, outputs,
                       definitionHash, inputRefs, sourceDraftRevision,
                       createdBy, createdAt
PipelineBuildEntity: id, pipelineId, pipelineVersionId, status, stage,
                     triggerType, operationId, requestedBy, resolvedInputs,
                     logicHash, logUri, errorCode, errorMessage, stats,
                     heartbeatAt, cancelRequestedAt, startedAt, finishedAt,
                     createdAt, updatedAt
PipelineBuildOutputEntity: id, buildId, outputId, outputType, status, stage,
                           engineJobId, logUri, result, errorCode, errorMessage,
                           stats, startedAt, finishedAt, createdAt, updatedAt
PipelineDatasetVersionEntity: id, datasetId, buildOutputId, versionNumber,
                              icebergCatalog, icebergNamespace, icebergTable,
                              icebergSnapshotId, schemaJson, rowCount, byteSize,
                              storagePath, committedAt, publishedAt, createdAt
PipelineScheduleEntity: id, pipelineId, name, status, triggerType,
                        cronExpression, timezone, nextRunAt, lastRunAt,
                        pausedAt, pausedBy, createdBy, createdAt, updatedAt
PipelineScheduleRunEntity: id, scheduleId, pipelineId, status, triggerType,
                           scheduledFor, buildId, logicHash, resolvedInputs,
                           reasonCode, message, startedAt, finishedAt, createdAt
```

`pipeline_output` 和 `pipeline_build` 只归档、不物理删除；因此引用它们的
`pipeline_build_output.output_id` 和 `pipeline_schedule_run.build_id` 使用
`ON DELETE RESTRICT`，历史来源和运行记录始终可读。

枚举值固定为：

```text
PipelineStatus: draft | active | archived
PipelineOutputType: dataset
PipelineBuildStatus: queued | running | succeeded | failed | canceled
PipelineBuildTrigger: manual | schedule
PipelineBuildStage: resolving_inputs | submitting | running | checking_expectations | publishing | finished
PipelineOutputStatus: queued | running | succeeded | failed | canceled
PipelineScheduleStatus: active | paused
PipelineScheduleRunStatus: pending | dispatched | ignored | failed
PipelineDatasetWriteMode: snapshot
```

仓储必须提供：

```java
interface PipelineBuildRepository extends JpaRepository<PipelineBuildEntity, UUID> {
    @Query(value = """
        SELECT * FROM data.pipeline_build
        WHERE status = 'queued'
        ORDER BY created_at, id
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
        """, nativeQuery = true)
    List<PipelineBuildEntity> lockQueued(@Param("limit") int limit);
}
```

同时提供 `PipelineRepository.lockById(UUID)`，使用 `SELECT ... FOR UPDATE`；版本号分配必须锁流程行，不能靠 `max(version_number)+1` 的并发查询。

- [x] **Step 5: 运行 schema 测试**

Run: `cd data-service && ./mvnw -Dtest=PipelineSchemaIntegrationTest test`

Expected: PASS；Flyway `V8` 成功执行，实体上下文启动成功。

- [x] **Step 6: 提交**

```bash
git add data-service/src/main/resources/db/migration/V8__pipeline_domain.sql \
  data-service/src/main/java/com/ontoweave/data/pipeline/domain \
  data-service/src/main/java/com/ontoweave/data/pipeline/repository \
  data-service/src/test/java/com/ontoweave/data/pipeline/PipelineSchemaIntegrationTest.java
git commit -m "feat(data-pipeline): add pipeline schema and domain model"
```

完成记录：2026-10-20，提交 `b9c72c2`；`PipelineSchemaIntegrationTest` 4/4 通过；
规格一致性与代码质量评审通过。

## 5. Task 2：节点目录、端口与配置 Schema

**Files:**
- Modify: `data-service/pom.xml`（加入 JSON Schema 校验依赖）
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/catalog/NodeDefinition.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/catalog/NodePort.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/catalog/PipelineNodeCatalog.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/catalog/ConfigSchemas.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/catalog/UiSchemas.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineNodeCatalogController.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/catalog/PipelineNodeCatalogTest.java`

- [x] **Step 1: 写节点目录测试**

```java
@Test
void exposesMvpNodesAndPorts() {
    PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
    assertThat(catalog.types()).containsExactlyInAnyOrder(
        "input", "filter", "select_columns", "rename_columns", "derive_column",
        "deduplicate", "join", "union", "aggregate", "output"
    );
    assertThat(catalog.require("join").ports())
        .extracting(NodePort::id)
        .containsExactly("left", "right", "out");
    assertThat(catalog.require("output").ports())
        .extracting(NodePort::id)
        .containsExactly("in");
    assertThat(catalog.require("filter").configSchema().path("additionalProperties").asBoolean())
        .isFalse();
    assertThat(catalog.require("filter").configSchema().path("required").isArray())
        .isFalse();
}
```

- [x] **Step 2: 运行测试，确认目录不存在**

Run: `cd data-service && ./mvnw -Dtest=PipelineNodeCatalogTest test`

Expected: FAIL，提示 `PipelineNodeCatalog` 不存在。

- [x] **Step 3: 实现节点目录**

目录类型固定为以下 Java `record`，字段名和 JSON 输出保持一致：

```java
public enum NodePortRole { INPUT, OUTPUT }

public record NodePort(
        String id,
        NodePortRole role,
        String dataType,
        boolean required,
        boolean multiple
) {}

public record NodeDefinition(
        String type,
        int version,
        String label,
        String category,
        List<NodePort> ports,
        ObjectNode configSchema,
        ObjectNode uiSchema
) {}

public record NodeCatalogResponse(String catalogVersion, List<NodeDefinition> nodes) {}

public record PipelineNodeCatalog(List<NodeDefinition> nodes) {
    public NodeDefinition require(String type) { /* missing -> ApiException 400 */ }
    public String catalogVersion() { /* SHA-256 of canonical node JSON */ }
    public NodeCatalogResponse response() { return new NodeCatalogResponse(catalogVersion(), nodes); }
    public static PipelineNodeCatalog defaultCatalog() { /* 10 个固定节点 */ }
}
```

配置形状固定为：

```text
input:          {datasetKind, datasetId}
filter:         {condition}
select_columns: {mode: include|exclude, columns: string[]}
rename_columns: {renames: [{from, to}]}
derive_column:  {outputField, expression}
deduplicate:    {mode: all|subset, columns: string[]}
join:           {joinType: left|right|inner|full, keys: [{left, right}]}
union:          {}
aggregate:      {groupBy: string[], aggregations: [{op, field?, outputField}]}
output:         {outputId}
```

筛选条件的 `op` 固定为 `eq`、`ne`、`gt`、`gte`、`lt`、`lte`、`is_null`、
`is_not_null`、`in`。比较条件使用 `value`；`in` 使用 `values`；`is_null` /
`is_not_null` 不带值字段。`condition` 通过 `kind` 区分 `compare` 和 `and` / `or` 组合。

`configSchema` 使用 JSON Schema，开启 `additionalProperties: false`，但顶层业务字段不放入 `required`。`uiSchema` 只声明控件类型：`text`、`number`、`boolean`、`select`、`multi-select`、`field-selector`、`operator-selector`、`condition-tree`、`rename-list`、`join-keys`、`aggregation-list`、`expression-tree`。

在 `data-service/pom.xml` 加入 `com.networknt:json-schema-validator:1.5.6`，由 `ConfigSchemas`
编译并缓存 schema；校验失败映射为 400 / `CONFIG_INVALID`，不要让前端或 Agent 各自实现
JSON Schema 解释器。

- [x] **Step 4: 暴露内部目录接口**

新增 `PipelineNodeCatalogController`：

```java
@RestController
@RequestMapping("/internal/v1/pipeline-node-catalog")
public class PipelineNodeCatalogController {
    private final PipelineNodeCatalog catalog;
    public PipelineNodeCatalogController(PipelineNodeCatalog catalog) { this.catalog = catalog; }

    @GetMapping
    public NodeCatalogResponse get() { return catalog.response(); }
}
```

`GET /internal/v1/pipeline-node-catalog` 的响应体包含 `catalogVersion` 和 `nodes`，并用同一个哈希返回 `ETag`。目录测试必须逐项断言 10 个节点、端口角色、固定端口 ID 和以下配置形状：

```text
input:          {datasetKind, datasetId}
filter:         {condition}
select_columns: {mode: include|exclude, columns: string[]}
rename_columns: {renames: [{from, to}]}
derive_column:  {outputField, expression}
deduplicate:    {mode: all|subset, columns: string[]}
join:           {joinType: left|right|inner|full, keys: [{left, right}]}
union:          {}
aggregate:      {groupBy: string[], aggregations: [{op, field?, outputField}]}
output:         {outputId}
```

端口固定为（`multiple` 只允许 `union.inputs` 为 `true`，其余输入端口均为 `false`）：

```text
input:          out
filter/select_columns/rename_columns/derive_column/deduplicate/aggregate/output:
                in -> out（output 只有 in）
join:           left, right -> out
union:          inputs[] -> out
```

`union.inputs[]` 是唯一允许多条入边的逻辑端口；草稿中的边仍逐条保存，每条边使用同一个
`targetPort="inputs"`。Java 校验时按 `targetNodeId + targetPort` 统计边数，只有 union 允许大于 1。

- [x] **Step 5: 运行目录测试**

Run: `cd data-service && ./mvnw -Dtest=PipelineNodeCatalogTest test`

Expected: PASS。

- [x] **Step 6: 提交**

```bash
git add data-service/pom.xml \
  data-service/src/main/java/com/ontoweave/data/pipeline/catalog \
  data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineNodeCatalogController.java \
  data-service/src/test/java/com/ontoweave/data/pipeline/catalog/PipelineNodeCatalogTest.java
git commit -m "feat(data-pipeline): add pipeline node catalog"
```

完成记录：2026-10-20，提交 `c73323a`；目录相关测试 44/44 通过；规格一致性与
代码质量评审通过。

## 6. Task 3：草稿命令、编辑历史与统一检查

**Files:**
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineDraftRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineOutputRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineDatasetRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineEditOpRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineBuildRepository.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/PipelineDraftCommandService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/DraftCommand.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/AddNode.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/UpdateNode.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/DeleteNode.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/ConnectNodes.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/DisconnectNodes.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/SetOutput.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/MoveNodeCommand.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/DraftResponse.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/PipelineEditRecorder.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/PipelineRevisionConflict.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/graph/PipelineGraphValidator.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/graph/PipelineDraftGraph.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/graph/FieldSchema.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/graph/InputSchema.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/graph/InputSchemaResolver.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/graph/PublishedInputSchemaResolver.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/check/PipelineCheckService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/check/PipelineCheckServiceImpl.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/check/PipelineIssue.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/check/PipelineCheckResult.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/check/PipelineStage.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/check/NextAction.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/draft/PipelineDraftCommandServiceTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/graph/PipelineGraphValidatorTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/PipelineDraftCommandIntegrationTest.java`

- [x] **Step 1: 写 revision 与坐标测试**

```java
@Test
void semanticEditBumpsRevisionButMoveDoesNot() {
    UUID pipelineId = UUID.randomUUID();
    when(repository.updateDefinition(eq(pipelineId), eq(4L), any(), any(), any()))
        .thenReturn(1);

    DraftResponse afterAdd = service.apply(pipelineId, ownerId, addNodeCommand(4L));
    DraftResponse afterMove = service.moveNode(
            pipelineId, ownerId, moveNodeCommand(afterAdd.revision() + 100));

    assertThat(afterAdd.revision()).isEqualTo(5);
    assertThat(afterMove.revision()).isEqualTo(5);
    verify(editRecorder, times(1)).recordSemanticEdit(any());
    verify(editRecorder, never()).recordMove(any());
}
```

- [x] **Step 2: 写 DAG 与字段错误测试**

```java
@Test
void reportsCycleAndMissingFieldOnTheCorrectNode() {
    String filterNode = "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11";
    PipelineCheckResult result = service.check(pipelineId, ownerId, 4L);

    assertThat(result.stage()).isEqualTo(PipelineStage.INVALID);
    assertThat(result.blockingIssues())
        .extracting(PipelineIssue::code)
        .contains("CYCLE_DETECTED", "FIELD_NOT_FOUND");
    assertThat(result.blockingIssues())
        .anySatisfy(issue -> {
            assertThat(issue.nodeId()).isEqualTo(filterNode);
            assertThat(issue.field()).isEqualTo("amount");
            assertThat(issue.blocking()).isTrue();
        });
}
```

**Task 3 固定契约**

- 复用 Task 1 已创建的 `repository/PipelineDraftRepository`，禁止再建 `draft/PipelineDraftRepository`；条件更新、按 revision 查询和只更新坐标的方法加在既有接口中。
- `DraftResponse` 固定为 `(UUID pipelineId, long revision, JsonNode definition)`。
- `DraftCommand` 是 sealed interface，固定方法为 `long baseRevision()`、`UUID operationId()`、`UUID turnId()`；实现只有 `AddNode`、`UpdateNode`、`DeleteNode`、`ConnectNodes`、`DisconnectNodes`、`SetOutput`。`MoveNodeCommand` 不属于语义命令，不递增 revision。
- 命令参数使用 UUID 类型：`AddNode(nodeId, type, name, config, position)`、`UpdateNode(nodeId, name, config)`、`DeleteNode(nodeId)`、`ConnectNodes(edgeId, sourceNodeId, sourcePort, targetNodeId, targetPort)`、`DisconnectNodes(edgeId)`、`SetOutput(nodeId, name, writeMode, primaryKeyColumns, expectations)`。`UpdateNode.name` 或 `config` 允许为 `null`，表示本次不修改该字段。
- `SetOutput.writeMode` 为空时按 `snapshot` 处理；`primaryKeyColumns` 和 `expectations` 为空时按空数组处理。只允许 `dataset` 输出，输出名称在流程内唯一，重名返回 409 / `OUTPUT_NAME_CONFLICT`。
- `PipelineStage` 使用大写 Java 枚举和 `@JsonValue` 输出小写：`EMPTY`、`NEEDS_INPUT`、`NEEDS_OUTPUT`、`INVALID`、`READY`、`BUILDING`、`FAILED`、`SUCCEEDED`、`STALE`。
- `NextAction` 固定为 `(String action, String nodeId)`；`nodeId` 可为空。Task 3 只产出与结构检查直接相关的动作，不虚构构建或预览动作。
- `PipelineGraphValidator` 不依赖 Task 5 的 `ResolvedInput`。输入 schema 通过窄接口解析：`InputSchemaResolver.resolve(UUID ownerId, String datasetKind, UUID datasetId)` 返回 `Optional<InputSchema>`；`InputSchema` 为 `(UUID datasetVersionId, List<FieldSchema> fields)`，`FieldSchema` 为 `(String name, String dataType, boolean nullable)`。Task 5 只需把完整输入解析结果适配到该接口。
- `PublishedInputSchemaResolver` 从 P1 的 `dataset.currentVersionId` 和 `dataset_version.schema_json.fields[]` 读取已发布 schema，并校验数据集连接属于当前 owner；没有已发布版本时返回空，由检查结果映射为 `INPUT_VERSION_UNRESOLVED`。
- 编辑历史每条语义命令写一行：`revision` 使用命令成功后的新 revision，`seq=1`；`target_type/target_id` 保存实际目标；`before/after` 保存命令前后的完整草稿 JSON；`operation_id` 来自命令，`turn_id` 原样保存。坐标移动不写历史。撤销追加一行，不删除历史。
- 发件箱事件固定为：`aggregateType=pipeline`、`aggregateId=pipelineId`、`eventType=pipeline.draft.changed`、payload=`{pipelineId, revision, operationId, turnId, actorType, opType}`。Task 3 的 actor 固定为 `user/ownerId`；后续接入 Agent 时再通过显式 actor 上下文扩展，不把 Agent 操作伪装成用户操作。
- `PipelineRevisionConflict` 继承 `ApiException`，HTTP 409，错误码 `REVISION_CONFLICT`。语义命令更新 0 行时抛出；所有权不匹配仍返回 404。

- [x] **Step 3: 实现条件更新与编辑历史**

`PipelineDraftRepository` 必须使用一条条件更新：

```java
@Modifying
@Query(value = """
    UPDATE data.pipeline_draft
    SET definition = CAST(:definition AS jsonb), revision = revision + 1,
        updated_by_type = :actorType, updated_by_id = :actorId, updated_at = now()
    WHERE pipeline_id = :pipelineId AND revision = :baseRevision
    """, nativeQuery = true)
int updateDefinition(
        @Param("pipelineId") UUID pipelineId,
        @Param("baseRevision") long baseRevision,
        @Param("definition") String definition,
        @Param("actorType") String actorType,
        @Param("actorId") UUID actorId
);
```

更新 0 行抛 `PipelineRevisionConflict`，映射 HTTP 409 / `REVISION_CONFLICT`。成功后在同一个事务写 `pipeline_edit_op` 和 `OutboxWriter` 事件 `pipeline.draft.changed`。先按 `pipelineId + ownerId` 校验流程所有权，再执行条件更新；不能把“无权限”和“revision 过期”混成同一个 409。

`move_node` 只更新 JSON 中的 `position`，使用单独的 `UPDATE ... WHERE pipeline_id = ?`，不递增 revision，不写编辑记录，不发布发件箱事件。`MoveNodeCommand` 固定为 `(UUID nodeId, JsonNode position)`；传入过期 revision 不影响移动结果，最后写入者生效。

`PipelineDraftCommandService` 的公开方法固定为：

```java
@Transactional
public DraftResponse apply(UUID pipelineId, UUID ownerId, DraftCommand command);

@Transactional
public DraftResponse moveNode(UUID pipelineId, UUID ownerId, MoveNodeCommand command);

@Transactional
public DraftResponse undoTurn(UUID pipelineId, UUID ownerId, UUID turnId, long currentRevision);

@Transactional(readOnly = true)
public DraftResponse get(UUID pipelineId, UUID ownerId);
```

`DraftCommand` 是封闭命令类型，必须覆盖 `AddNode`、`UpdateNode`、`DeleteNode`、`ConnectNodes`、`DisconnectNodes`、`SetOutput`，每个命令携带 `baseRevision`、`operationId` 和 `turnId`。结构校验失败返回 400 / `CONFIG_INVALID`；结构合法但配置不完整时仍然保存，由检查结果返回 `NODE_CONFIG_INCOMPLETE`。

节点 ID 和边 ID 都必须是 UUID 字符串。`pipeline_output.node_id` 是 UUID 外键语义字段，
`set_output` 的 `output.nodeId` 必须与草稿中输出节点的 UUID 完全一致；禁止使用
`filter-1`、`output-1` 这类可读别名，否则数据库写入和 Agent 工具都会在运行时失败。

`SetOutput` 的幂等语义固定为：按 `pipelineId + nodeId` 查找 `pipeline_output`；不存在时生成新的 `outputId`，创建 `pipeline_output` 和 `pipeline_dataset`；存在且类型为 `dataset` 时更新名称、写入模式和关联的 `pipeline_dataset`，不得重复插入。同一 `nodeId` 只能绑定一个输出。草稿中的输出节点只写 `config.outputId`，不复制名称、写入模式或数据期望。节点必须存在且类型为 `output`，否则返回 400 / `CONFIG_INVALID`；输出数据集配置在同一个事务内写入，条件更新失败时整体回滚。

- [x] **Step 4: 实现 DAG 校验与 schema 推导**

固定校验顺序：

```text
JSON 结构 → 节点/连线身份和端口 → 环检测与必需输入 → 输出可达性
→ 拓扑序 schema 推导和语义校验
```

必须覆盖问题码：`INPUT_MISSING`、`INPUT_VERSION_UNRESOLVED`、`OUTPUT_MISSING`、`OUTPUT_NOT_CONNECTED`、`NODE_CONFIG_INCOMPLETE`、`CYCLE_DETECTED`、`SCHEMA_MISMATCH`、`FIELD_NOT_FOUND`、`TYPE_MISMATCH`、`PRIMARY_KEY_INVALID`、`EXPECTATION_INVALID`、`EDGE_PORT_INVALID`、`PORT_OCCUPIED`、`DUPLICATE_FIELD`、`AGGREGATION_INVALID`。

问题阶段固定为 `structure`、`graph`、`schema`。JSON 根必须是 `schemaVersion=1` 的对象，`nodes` 和 `edges` 必须是数组；节点必须有 UUID `id`、已知 `type`、非空 `name`、对象 `config` 和数值坐标 `position.x/y`；连线必须有 UUID `id` 和四个非空端口字段。节点 ID、边 ID 重复分别产生阻塞问题，不能静默覆盖。

节点语义按目录中的 `configSchema` 先做结构校验，再按固定规则推导：筛选输出上游字段；选列按 include/exclude 生成字段；改名保持字段顺序并拒绝重名；派生列覆盖或追加一个字段；去重保持字段；关联固定 `left/right` 两个输入并把右侧冲突字段加 `right_` 前缀；合并要求至少两个输入且字段名和类型完全一致；聚合输出 `groupBy + aggregations[].outputField`；输出节点只接收一个输入。所有字段引用都要校验存在性和类型兼容性，所有问题都带能定位的 `nodeId`，字段问题同时带 `field`。

输出配置校验同时读取 `pipeline_output`：没有输出节点返回 `OUTPUT_MISSING`；输出节点没有入边返回 `OUTPUT_NOT_CONNECTED`；`config.outputId` 为空或找不到对应 `dataset` 输出时返回 `OUTPUT_MISSING`。`primaryKeyColumns` 必须是非空字符串组成的唯一数组；`expectations` 必须是以 `type` 判别的数组，MVP 只接受 `rowCount`、`acceptedValues`、`notNull`，并校验字段存在性、数值区间和取值列表。主键或期望字段不在输出 schema 中时分别返回 `PRIMARY_KEY_INVALID` 或 `EXPECTATION_INVALID`。

统一检查入口固定为：

```java
public record PipelineIssue(
        String code,
        String phase,
        boolean blocking,
        String nodeId,
        String portId,
        String field,
        String message,
        String hint
) {}

public record PipelineCheckResult(
        UUID pipelineId,
        long revision,
        PipelineStage stage,
        List<PipelineIssue> blockingIssues,
        List<PipelineIssue> warnings,
        List<NextAction> nextActions
) {}

public interface PipelineCheckService {
    PipelineCheckResult check(UUID pipelineId, UUID ownerId, long revision);
}
```

`PipelineGraphValidator.validate(draft, catalog, inputSchemas, outputIds)` 返回同一组 `PipelineIssue`，其中 `inputSchemas` 是 `Map<UUID, InputSchema>`（以输入节点的 `datasetId` 为键），`outputIds` 是该流程当前有效的 `pipeline_output.id` 集合。校验器保持纯计算、不访问数据库；`PipelineCheckServiceImpl` 负责一次批量读取输入 schema 和输出 ID 后传入。

`PipelineCheckServiceImpl.check` 必须先读取草稿和请求中的 revision；revision 不一致返回 409 / `REVISION_CONFLICT`。派生阶段按固定优先级计算：存在 queued/running 构建为 `BUILDING`；无节点为 `EMPTY`；无输入节点为 `NEEDS_INPUT`；无输出节点为 `NEEDS_OUTPUT`；存在阻塞问题为 `INVALID`；最近一次终态构建失败且 `source_draft_revision` 等于当前 revision 为 `FAILED`；当前已发布版本的 `source_draft_revision` 小于当前 revision 为 `STALE`；当前版本存在且无更新为 `SUCCEEDED`；否则为 `READY`。Task 3 必须实现上述完整优先级，构建查询只使用现有 `pipeline_build`、`pipeline_version` 仓储，不提前实现构建执行。

- [x] **Step 5: 实现按 turn 撤销**

`undoTurn(pipelineId, turnId, currentRevision)` 只允许撤销最近一个带 `turn_id` 的编辑批次，并要求当前 revision 等于该批次结束 revision。先按 revision 倒序找到全局最新编辑；如果它的 `turn_id` 不是请求的 `turnId`，返回 409 / `UNDO_NOT_LATEST`。再读取该 turn 的最小和最大 revision：最小 revision 行的 `before` 是恢复目标，最大 revision 必须等于 `currentRevision`。恢复时保留当前仍存在节点的坐标，避免撤销把用户之后的拖动位置一并回退。撤销写入新的 `pipeline_edit_op`，`revision=currentRevision+1`、`seq=1`、`op_type=undo_turn`、`target_type=turn`、`target_id=turnId`，`before` 为当前完整草稿、`after` 为合并坐标后的恢复草稿；`operationId` 使用新的 UUID，`turnId` 原样保留。随后条件更新 revision 并发布同格式的 `pipeline.draft.changed` 事件，不删除历史。

- [x] **Step 6: 运行草稿和校验测试**

Run: `cd data-service && TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -Dtest=PipelineDraftCommandServiceTest,PipelineGraphValidatorTest,PipelineDraftCommandIntegrationTest test`

Expected: PASS。

集成测试至少验证：条件更新只成功一次、revision 冲突返回 409、坐标移动不递增 revision、编辑记录和发件箱在同一事务落下、`set_output` 重复执行不产生重复输出、按 turn 撤销追加新 revision 且恢复目标正确。

- [x] **Step 7: 提交**

```bash
git add data-service/src/main/java/com/ontoweave/data/pipeline/draft \
  data-service/src/main/java/com/ontoweave/data/pipeline/graph \
  data-service/src/main/java/com/ontoweave/data/pipeline/check \
  data-service/src/main/java/com/ontoweave/data/pipeline/repository \
  data-service/src/test/java/com/ontoweave/data/pipeline/draft \
  data-service/src/test/java/com/ontoweave/data/pipeline/graph \
  data-service/src/test/java/com/ontoweave/data/pipeline/PipelineDraftCommandIntegrationTest.java
git commit -m "feat(data-pipeline): add draft commands and graph checks"
```

**完成记录（2026-10-20）：** 提交 `5399845`、`c8291e3`、`a41e0a8`、`c4adcc9`、`35ca239`。实现草稿命令、悲观锁串行化、编辑历史与发件箱、`set_output` 幂等、统一检查、批量输入 schema 解析和按 turn 撤销。Task 3 定向测试 56/56 通过；规格一致性与代码质量评审通过。

## 7. Task 4：流程 REST、幂等创建与来源查询

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineController.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineDraftController.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineSourceController.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/PipelinePageResponse.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/RenamePipelineRequest.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/CopyPipelineRequest.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/MoveNodeRequest.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/UndoRequest.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/SourceResponse.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/SourceInput.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/SourceOutput.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/SourceBuild.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/dto/SourcePipelineVersion.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/service/PipelineService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/service/PipelineServiceImpl.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/service/PipelineCreateResult.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/service/CreatePipelineRequest.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/service/PipelineResponse.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/source/PipelineSourceService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/source/DatasetMetadataQuery.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/source/DatasetRef.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/source/DatasetMetadata.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/source/MetadataStatus.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/domain/PipelineEntity.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/domain/PipelineStatus.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/domain/PipelineDraftEntity.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/domain/PipelineOutputEntity.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/domain/PipelineDatasetEntity.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/draft/DraftCommand.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineOutputRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineDatasetRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineDatasetVersionRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/repository/PipelineBuildOutputRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/sync/repository/DatasetRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/sync/repository/DatasetVersionRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/sync/repository/SyncTaskRepository.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/connection/repository/DataConnectionRepository.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/PipelineApiIntegrationTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/source/PipelineSourceServiceTest.java`

**Task 4 固定契约**

- 创建接口保留 201/200 语义。`PipelineService.create` 返回 `PipelineCreateResult(PipelineResponse pipeline, boolean created)`，控制器据 `created` 返回 201 或 200；不要用“总是 200”规避幂等测试。
- `PipelinePageResponse(List<PipelineResponse> items, long total, int limit, int offset)`。列表默认 limit=50、offset=0，范围 1..200；只返回未归档流程，按 `updated_at DESC, id DESC` 稳定排序。默认值不得在代码中重复硬编码。
- `RenamePipelineRequest(String name)` 和 `CopyPipelineRequest(UUID pipelineId, UUID conversationId, String name)`。名称 trim 后非空；长度上限 200，超限 400 `VALIDATION_ERROR`。
- 复制必须新建 pipeline（`status=draft`、`current_version_id=null`）、空 revision 之前复制当前草稿、复制所有未归档输出声明和对应 dataset。复制事务开始时必须用 `PipelineDraftRepository.findByIdForUpdate` 锁定源草稿，使草稿、输出和 dataset 来自同一一致快照，再读取后三者的当前状态。复制时生成新的 outputId/datasetId，并同步重写草稿 output 节点 `config.outputId`；保留节点/边 ID。输出缺少 dataset 配置，或草稿引用的 outputId 不在复制集合中时，返回 409 `OUTPUT_CONFIGURATION_INCOMPLETE`。不复制版本、构建、调度、编辑历史或消息。新 pipelineId 已存在返回 409 `PIPELINE_BINDING_CONFLICT`；conversationId 已绑定其他流程返回同一错误码。复制失败必须整体回滚。
- `PipelineEntity` 增加受控领域方法 `rename(String)`、`archive()`；`archive()` 设 `status=archived` 和 `archivedAt`，重复归档保持幂等。`PipelineStatus` 用 `@JsonValue` 输出 `draft/active/archived`。
- 归档是写操作终态。归档后 rename、copy、draft commands、positions、undo 一律返回 409 `PIPELINE_ARCHIVED`；详情、草稿、检查和来源等只读接口仍可读取。所有写路径必须用 `PipelineRepository.lockById` 锁定 `pipeline` 行后再读取状态与 owner，随后才可锁 `pipeline_draft`，固定锁序为 `pipeline → draft`；不得先普通读取状态再写，避免归档与 rename/copy/草稿写操作并发时把已归档流程写回 `draft`。校验在 service 层统一执行，不能只在单个 controller 或某个命令类型上判断。
- `PipelineDraftEntity` 增加只读 `revision` 或复制构造需要的访问能力，不暴露任意 setter；新建草稿 revision 从 0 开始。
- 草稿命令 JSON 使用 Jackson 多态：`DraftCommand` 加 `@JsonTypeInfo(use=NAME, property="type")` 和 `@JsonSubTypes`，类型名固定为 `add_node`、`update_node`、`delete_node`、`connect`、`disconnect`、`set_output`。请求字段使用 camelCase；未知 type 返回 400。`move_node` 只走位置接口，不混入语义命令。
- `DraftCommand.baseRevision()` 和 `UndoRequest.currentRevision` 都使用可空 `Long`，不允许用 primitive `long` 让 Jackson 把缺字段默认成 0。命令缺失 `baseRevision` 返回 400 `CONFIG_INVALID`；undo 缺失 `currentRevision` 返回 400 `VALIDATION_ERROR`。
- `POST /draft/commands` 请求体就是上面的多态命令，返回 `DraftResponse`；`POST /draft/positions` 请求体 `MoveNodeRequest(UUID nodeId, JsonNode position)`；`POST /turns/{turnId}/undo` 请求体 `UndoRequest(Long currentRevision)`；`GET /check` 必须带 `revision` 查询参数并返回 `PipelineCheckResult`。所有接口带 `ownerId` 和内部 token；owner 不匹配统一 404。
- 创建、复制、改名、归档、草稿写命令和撤销都写事务边界：API 只校验 HTTP 边界并转 DTO，业务规则在 service，数据访问在 repository。不要把这些规则塞进 controller。
- 来源响应 DTO 固定为 `SourceResponse(pipelineId, datasetId, versionId, output, build, pipelineVersion, inputs)`；`SourceOutput(outputId, name, versionNumber, rowCount, publishedAt)`；`SourceBuild(buildId, status, trigger, startedAt, finishedAt)`；`SourcePipelineVersion(versionId, versionNumber, createdAt)`；`SourceInput(datasetKind, datasetId, datasetVersionId, datasetVersionNumber, schemaHash, datasetName, connectionName, sourceSchemaName, sourceTableName, metadataStatus)`。
- `MetadataStatus` 用 `@JsonValue` 输出 `available/missing/restricted`。`SourceInput` 在 `restricted` 时名称、连接名、源 schema/表置 null；`missing` 保留已保存的 `displaySnapshot`，空白字符串按缺失处理，没有 snapshot 时对应字段为 null，不得用空字符串伪装。历史输入必须使用冻结的 `datasetVersionNumber`，不得用 dataset 当前版本号补位。
- `DatasetMetadataQuery.batchLoad(UUID ownerId, List<DatasetRef>)` 返回 `Map<DatasetRef, DatasetMetadata>`，必须一次批量读取 dataset、version、sync task、connection，不允许按输入循环查询。owner 是权限过滤的必填参数，禁止提供 owner 为空、语义会退化为 `restricted` 的单参入口。只查询当前 owner 可见的连接；不可见返回 `restricted`。
- 来源读取固定顺序：dataset → version（必须是该 dataset 且 `published_at != null`）→ build_output → build → pipeline_version；`versionId` 省略时使用 `pipeline_dataset.current_version_id`。datasetId 必须属于 pipeline，否则 404。`resolved_inputs` 按数组解析，缺失/格式错误时 inputs 为空且不回退到当前输入版本。

- [x] **Step 1: 写幂等创建与绑定冲突测试**

```java
@Test
void createIsIdempotentByConversationIdAndOwner() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID pipelineId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    String body = createBody(owner, pipelineId, conversationId, UUID.randomUUID());

    mvc.perform(post("/internal/v1/pipelines")
            .header("X-Internal-Token", "test-internal-token")
            .contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mvc.perform(post("/internal/v1/pipelines")
            .header("X-Internal-Token", "test-internal-token")
            .contentType(APPLICATION_JSON).content(body))
        .andExpect(status().isOk());

    assertThat(jdbc.queryForObject("select count(*) from data.pipeline", Integer.class)).isOne();
}
```

- [x] **Step 2: 写来源历史稳定性测试**

```java
@Test
void sourceUsesBuildTimeSnapshotAfterInputDatasetIsRenamed() {
    when(build.resolvedInputs()).thenReturn(List.of(resolvedInput("orders-v1", "orders")));
    when(datasetMetadata.batchLoad(any())).thenReturn(List.of(metadata("orders-current")));

    SourceResponse response = service.get(pipelineId, datasetId, versionId, ownerId);

    assertThat(response.inputs()).extracting(SourceInput::datasetVersionId)
        .containsExactly("orders-v1");
    assertThat(response.inputs()).extracting(SourceInput::datasetName)
        .containsExactly("orders");
}
```

- [x] **Step 3: 实现流程接口**

服务层方法固定为：

```java
public record CreatePipelineRequest(
        UUID ownerId,
        UUID pipelineId,
        UUID conversationId,
        String name
) {}

public record PipelineResponse(
        UUID id,
        UUID conversationId,
        String name,
        String status,
        Long draftRevision,
        UUID currentVersionId,
        Instant createdAt,
        Instant updatedAt
) {}

public interface PipelineService {
    PipelineCreateResult create(CreatePipelineRequest request, UUID operationId);
    PipelinePageResponse list(UUID ownerId, int limit, int offset);
    PipelineResponse get(UUID pipelineId, UUID ownerId);
    PipelineResponse rename(UUID pipelineId, UUID ownerId, String name);
    void archive(UUID pipelineId, UUID ownerId);
    PipelineResponse copy(UUID pipelineId, UUID ownerId, CopyPipelineRequest request);
}
```

创建幂等规则固定为：先按 `pipelineId` 查询；不存在时在同一事务插入流程和空草稿（`revision=0`）；唯一约束冲突时重新读取。已有记录必须同时满足 `ownerId` 和 `conversationId` 相同才返回 200；`conversationId` 已绑定其他流程时返回 409 / `PIPELINE_BINDING_CONFLICT`。`X-Operation-Id` 只用于审计，流程身份以请求体中的三个 UUID 为准。输出声明不预创建，由第一次 `set_output` 命令创建 `pipeline_output` 和 `pipeline_dataset`。空草稿固定为 `{"schemaVersion":1,"nodes":[],"edges":[]}`。

必须提供：

```text
POST   /internal/v1/pipelines
GET    /internal/v1/pipelines?ownerId=...
GET    /internal/v1/pipelines/{pipelineId}?ownerId=...
PATCH  /internal/v1/pipelines/{pipelineId}?ownerId=...
POST   /internal/v1/pipelines/{pipelineId}/archive?ownerId=...
POST   /internal/v1/pipelines/{pipelineId}/copy?ownerId=...
GET    /internal/v1/pipelines/{pipelineId}/draft?ownerId=...
POST   /internal/v1/pipelines/{pipelineId}/draft/commands?ownerId=...
POST   /internal/v1/pipelines/{pipelineId}/draft/positions?ownerId=...
GET    /internal/v1/pipelines/{pipelineId}/check?ownerId=...
POST   /internal/v1/pipelines/{pipelineId}/turns/{turnId}/undo?ownerId=...
GET    /internal/v1/pipelines/{pipelineId}/datasets/{datasetId}/sources?ownerId=...
```

创建时校验 `ownerId`、`pipelineId`、`conversationId`；`conversationId` 已绑定其他 owner 时返回 409 / `PIPELINE_BINDING_CONFLICT`。复制只复制当前草稿和输出声明，不复制消息、事件、构建历史或派生版本。

所有内部接口继续使用 `X-Internal-Token` 和 `ownerId` 查询参数；`ownerId` 不匹配统一返回 404，避免泄露其他用户资源。

- [x] **Step 4: 实现来源投影**

`PipelineSourceService.get` 读取顺序固定为：

```text
pipeline_dataset_version.build_output_id
→ pipeline_build_output(build_id, output_id)
→ pipeline_build.resolved_inputs
→ pipeline_version
```

顶层响应固定为：

```json
{
  "pipelineId": "...",
  "datasetId": "...",
  "versionId": "...",
  "output": {"outputId": "...", "name": "orders_clean", "versionNumber": 3, "rowCount": 1200, "publishedAt": "..."},
  "build": {"buildId": "...", "status": "succeeded", "trigger": "manual", "startedAt": "...", "finishedAt": "..."},
  "pipelineVersion": {"versionId": "...", "versionNumber": 7, "createdAt": "..."},
  "inputs": []
}
```

输入元数据使用 `DatasetMetadataQuery.batchLoad(ownerId, List<DatasetRef>)` 一次批量读取，禁止在循环中逐条查询。`resolved_inputs` 每项固定包含以下字段；`displaySnapshot` 在构建时写入，历史展示优先使用它而不是当前元数据。直接输入去重键固定为 `DatasetRef(datasetKind, datasetId)`。解析 `resolved_inputs` 采用全有或全无：数组缺失、不是数组或任一元素缺少必要字段时，`inputs` 返回空列表，禁止保留部分合法元素。

`PipelineSourceService.get(UUID pipelineId, UUID datasetId, UUID versionId, UUID ownerId)`
返回 `SourceResponse`，其中 `inputs` 的元素类型固定为 `SourceInput`。`SourceInput` 必须包含
`datasetKind`、`datasetId`、`datasetVersionId`、`datasetVersionNumber`、`schemaHash`、
`datasetName`、`connectionName`、`sourceSchemaName`、`sourceTableName`、`metadataStatus`。

`DatasetMetadataQuery.batchLoad` 的输入是去重后的 `DatasetRef(datasetKind, datasetId)`，
输出按相同键建立索引；缺失或无权限不能用空字符串伪装，必须写入枚举 `missing` 或 `restricted`。

```json
{
  "datasetKind": "sync",
  "datasetId": "...",
  "datasetVersionId": "...",
  "schemaHash": "...",
  "displaySnapshot": {
    "datasetName": "orders",
    "datasetVersionNumber": 12,
    "connectionId": "...",
    "connectionName": "prod-mysql",
    "sourceSchemaName": "sales",
    "sourceTableName": "orders"
  }
}
```

元数据缺失返回 `metadataStatus=missing`；无权限返回 `restricted` 并隐藏名称、连接和源表。查询不递归，不返回下游。

- [x] **Step 5: 运行 Java 基础集成测试**

Run: `cd data-service && TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -Dtest=PipelineApiIntegrationTest,PipelineSourceServiceTest test`

Expected: PASS。

- [x] **Step 6: 提交**

```bash
git add data-service/src/main/java/com/ontoweave/data/pipeline/api \
  data-service/src/main/java/com/ontoweave/data/pipeline/draft/DraftCommand.java \
  data-service/src/main/java/com/ontoweave/data/pipeline/domain \
  data-service/src/main/java/com/ontoweave/data/pipeline/repository \
  data-service/src/main/java/com/ontoweave/data/pipeline/service \
  data-service/src/main/java/com/ontoweave/data/pipeline/source \
  data-service/src/main/java/com/ontoweave/data/sync/repository \
  data-service/src/main/java/com/ontoweave/data/connection/repository \
  data-service/src/test/java/com/ontoweave/data/pipeline/PipelineApiIntegrationTest.java \
  data-service/src/test/java/com/ontoweave/data/pipeline/source
git commit -m "feat(data-pipeline): add pipeline API and source projection"
```

**完成记录（2026-10-21）：** 提交 `b42768b`、`1f2c1d5`、`e33df1e`、`5f4d215`、`12f2f44`。完成流程 REST、201/200 幂等创建、分页/改名/归档/复制、草稿命令与坐标/检查/撤销接口、来源投影，以及复制一致快照、归档终态和 `pipeline → draft` 写锁串行化。Task 4 定向测试 43/43 通过，规格与代码质量复审通过；归档与 rename/copy/commands/positions/undo 的两种并发顺序均有集成测试覆盖。

## 8. Task 5：预览计划与 Flink 适配器

**Files:**
- Modify: `data-service/pom.xml`
- Modify: `data-service/docker/flink/pom.xml`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/plan/LogicalPlan.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/plan/LogicalPlanNode.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/plan/PreviewPlanBuilder.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/plan/PipelinePlanCompiler.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/PipelineInputResolver.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/flink/FlinkTableExecutor.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/flink/ProcessFlinkTableExecutor.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/preview/PipelinePreviewService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelinePreviewController.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/preview/PipelinePreviewServiceTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/plan/PreviewPlanBuilderTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/preview/PipelinePreviewFlinkIntegrationTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/build/PipelineInputResolverTest.java`

- [x] **Step 1: 写预览边界与草稿输入解析测试**

```java
@Test
void rejectsStaleDraftRevisionBeforeStartingExecution() {
    ApiException exception = assertThrows(ApiException.class, () ->
        service.preview(pipelineId, ownerId, new PreviewRequest(3L, nodeId, requestId)));

    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(exception.getCode()).isEqualTo("REVISION_CONFLICT");
    verifyNoInteractions(executor);
}

@Test
void inputNodeUsesDirectIcebergReaderAndNeverStartsFlink() {
    when(checkService.check(pipelineId, ownerId, 4L)).thenReturn(readyCheck(4L));
    when(inputResolver.resolve(pipelineId, 4L)).thenReturn(inputSnapshot());
    when(reader.read("ontoweave", "ns", "orders", 100L, 501))
        .thenReturn(Optional.of(previewRows()));

    PreviewResponse response = service.preview(
        pipelineId, ownerId, new PreviewRequest(4L, inputNodeId, requestId)
    );

    assertThat(response.rows()).hasSize(1);
    assertThat(response.truncated()).isFalse();
    verifyNoInteractions(executor);
}

@Test
void draftInputResolutionUsesPublishedVersionSnapshot() {
    UUID datasetId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    when(drafts.require(pipelineId, 4L)).thenReturn(draftWithInput(datasetId));
    when(datasets.requirePublishedVersion(datasetId))
        .thenReturn(publishedVersion(versionId, 100L));

    List<ResolvedInput> resolved = resolver.resolve(pipelineId, 4L);

    assertThat(resolved).singleElement().satisfies(input -> {
        assertThat(input.datasetVersionId()).isEqualTo(versionId);
        assertThat(input.icebergSnapshotId()).isEqualTo(100L);
    });
}
```

- [x] **Step 2: 运行测试，确认预览服务与输入解析器不存在**

Run: `cd data-service && ./mvnw -Dtest=PipelinePreviewServiceTest,PipelineInputResolverTest test`

Expected: FAIL。

- [x] **Step 3: 实现预览请求与并发限制**

请求与响应类型固定为：

```java
public record PreviewRequest(long draftRevision, String nodeId, UUID requestId) {}

public record PreviewResponse(
        UUID previewId,
        UUID pipelineId,
        long draftRevision,
        String nodeId,
        List<PreviewColumn> columns,
        List<Map<String, Object>> rows,
        int returnedRows,
        Long scannedRows,
        boolean truncated,
        String truncationReason,
        long elapsedMs,
        List<InputVersion> inputVersions,
        List<PreviewIssue> errors,
        List<PreviewIssue> warnings
) {}

public interface PipelinePreviewService {
    PreviewResponse preview(UUID pipelineId, UUID ownerId, PreviewRequest request);
}
```

接口：

```text
POST /internal/v1/pipelines/{pipelineId}/previews?ownerId=...
Body: {"draftRevision": 4, "nodeId": "...", "requestId": "..."}
```

行为固定为：

- `draftRevision` 不一致返回 409 / `REVISION_CONFLICT`。
- `draftRevision` 必须在取消旧预览和注册新预览之前校验；过期请求不得影响当前预览。
- `PipelineCheckService` 有阻塞错误返回 422，不启动 Flink。
- 同一流程最多 1 个预览，同一用户最多 2 个；超时 20 秒。发起同一流程的新预览时，先取消仍在运行的旧预览，旧请求返回 `PREVIEW_CANCELED`，新请求继续执行；`PREVIEW_BUSY` 只用于用户级并发上限或旧任务在宽限期内未能释放资源。
- 构建优先；构建需要该流程资源时取消预览并返回 `PREVIEW_CANCELED`。预览必须在执行真正开始前置为可取消状态，不能等到检查/计划构建完成后才注册。
- 20 秒是端到端预算，覆盖修订/权限校验、流程检查、计划构建和 Flink 执行；后续阶段只能使用剩余预算。
- 端到端超时必须是硬上限：同步校验/检查/计划也必须放到可取消 worker 中计时等待；超时后请求立即返回 `PREVIEW_TIMEOUT`，不得在 `finally` 无限等待不响应中断的 Iceberg reader，也不得让残留 lease 阻塞后续预览。预览 worker 池必须有界，饱和时返回 `PREVIEW_BUSY`；自有 Iceberg 读取循环必须检查中断以便及时终止。第三方不可中断 I/O 无法被 JVM 强制杀死时，以“请求和 lease 已释放、线程池有界、后续请求不被阻塞”为明确边界。
- 同一流程的预览请求按进入顺序分配 epoch；revision 较慢的旧请求不得在新请求注册后反向取消新预览。
- 预览不创建 `pipeline_build`、`pipeline_version` 或 `pipeline_dataset_version`。

- [x] **Step 4: 实现逻辑计划与有界读取**

`PreviewPlanBuilder` 从目标节点反向遍历只保留上游子图。输入节点直接调用
`IcebergPreviewReader.read(catalog, namespace, table, snapshotId, 501)`；读取结果只保留
前 500 行，第 501 行用于判断 `truncated`。转换节点交给 `FlinkTableExecutor`，输入扫描上限
仍是 50,000 行，不能把输入节点的安全读取上限和转换节点的扫描上限混用。

`PipelineInputResolver` 在 Task 5 建立，供预览和 Task 6 构建共用；它负责解析已发布输入
版本并返回 `ResolvedInput`，避免预览和构建各自拼一套输入解析逻辑。

```java
public interface PipelineInputResolver {
    List<ResolvedInput> resolve(UUID pipelineId, long draftRevision);
}

public record ResolvedInput(
        String datasetKind, UUID datasetId, UUID datasetVersionId, int datasetVersionNumber,
        String schemaHash, String icebergCatalog, String icebergNamespace,
        String icebergTable, long icebergSnapshotId, DisplaySnapshot displaySnapshot
) {}

public record DisplaySnapshot(
        String datasetName, Integer datasetVersionNumber, UUID connectionId,
        String connectionName, String sourceSchemaName, String sourceTableName
) {}
```

逻辑计划节点固定为：

```text
ScanInput(catalog, namespace, table, snapshotId, scanLimit)
Filter(condition)
SelectColumns(mode, columns)
RenameColumns(renames)
DeriveColumn(outputField, expression)
Deduplicate(mode, columns)
Join(joinType, keys)
Union()
Aggregate(groupBy, aggregations)
```

`PreviewPlanBuilder.build(UUID pipelineId, UUID ownerId, long revision, String nodeId)` 返回只包含目标节点上游的 `LogicalPlan`；输入节点路径返回 `InputScanPlan`，由服务直接走 `IcebergPreviewReader`。`PipelinePlanCompiler.compile(LogicalPlan)` 只做逻辑计划到引擎计划的转换，不能拼接字符串 SQL。

逻辑计划编译必须把隐藏 cast 标记列当作每个子计划的显式元数据：select/rename 保留，join 右侧标记列改为唯一且仍带隐藏前缀的名称，aggregate/dedup/union 不得静默丢弃；最终输出前全部剥离。`PreviewIssue` 固定包含 `code`、`phase`、`level`、`nodeId`、`portId`、`field`、`rowNumber`、`message`、`hint`、`originalValue`；无法精确映射时保留节点级定位并省略不确定字段，禁止把上游失败错误地标到根节点。

去重节点必须按用户可见列决定重复语义，隐藏标记列只能作为聚合附带信息，不能参与去重键；去重后无法对应原始输入行时，`rowNumber` 置空。

每个输入固定 `snapshot-id`，默认扫描上限 50,000 行；最终查询 `LIMIT 501`，返回前 500 行。第 501 行存在时 `truncated=true`、`truncationReason=OUTPUT_ROW_LIMIT`；任一输入快照总行数超过上限时也标记 `SCAN_ROW_LIMIT`，该判断必须由可执行的元数据/指标路径产生，不能保留永远为 `false` 的占位值。

- [x] **Step 5: 实现 Flink Table 适配器**

`FlinkTableExecutor` 是接口，`ProcessFlinkTableExecutor` 负责生成受控计划并提交 Flink 批作业。计划只能来自 `LogicalPlan`，不接受前端或 Agent 的 SQL。作业释放后不写 Iceberg、不写数据库。

接口固定为：

```java
public interface FlinkTableExecutor {
    FlinkExecutionResult execute(LogicalPlan plan, int outputLimit, Duration timeout);
    void cancel(UUID executionId);
}
```

实现使用 Flink 1.20 `StreamExecutionEnvironment` + `StreamTableEnvironment` 的批模式，注册 Hadoop Iceberg catalog，输入表通过 `snapshot-id` 固定版本；每个输入追加 `scanLimit` 保护，最终结果查询 `LIMIT outputLimit + 1`。依赖固定为：

```text
org.apache.flink:flink-table-api-java:1.20.3
org.apache.flink:flink-table-api-java-bridge:1.20.3
org.apache.flink:flink-clients:1.20.3
org.apache.flink:flink-streaming-java:1.20.3
org.apache.flink:flink-table-planner-loader:1.20.3
org.apache.iceberg:iceberg-flink-runtime-1.20:1.10.1
```

`flink-table-planner-loader` 是 Table API 批作业的必需依赖；只加入 API bridge 和 clients
会在执行时找不到 planner。

这些依赖加入 `data-service/pom.xml`；`data-service/docker/flink/pom.xml` 只补运行时需要的 Iceberg Flink runtime JAR，避免和 Spring Boot 打包出的依赖重复。

`PipelinePreviewServiceTest` 使用 `FakeFlinkTableExecutor` 覆盖计划、截断、超时、取消和错误映射；真实 Flink 作业放在 `PipelinePreviewFlinkIntegrationTest`，用环境变量 `RUN_FLINK_INTEGRATION=true` 启用，普通单测不启动 Flink。

预览执行期类型转换使用 `TRY_CAST` 和隐藏错误标记列；返回前剥离标记列，把字段、行号和原值摘要写入 `errors`。

- [x] **Step 6: 运行预览与草稿输入解析测试**

Run: `cd data-service && ./mvnw -Dtest=PipelinePreviewServiceTest,PreviewPlanBuilderTest,PipelineInputResolverTest test`

Expected: PASS。

- [x] **Step 7: 提交**

```bash
git add data-service/pom.xml data-service/docker/flink/pom.xml \
  data-service/src/main/java/com/ontoweave/data/pipeline/execution \
  data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelinePreviewController.java \
  data-service/src/test/java/com/ontoweave/data/pipeline/execution
git commit -m "feat(data-pipeline): add preview planning and Flink adapter"
```

**完成记录（2026-10-21）：** 提交 `d32734a`、`f0a17c0`、`ba66536`、`ce903c3`、`a3053c0`、`dca89ac`、`995d24b`、`6b0edbf`、`654615b`。完成预览 REST、端点级 20 秒硬预算、同流程 epoch 抢占与 build cutoff、有界 worker 池、Iceberg 直读、Flink 1.20.3 Table API 适配、逻辑计划/表达式编译、隐藏 cast 标记列、可见列去重、scanLimit 判断、错误白名单与节点级定位。定向测试 118/118 通过，规格与代码质量复审通过。真实 Flink + Iceberg 集成测试保留 `RUN_FLINK_INTEGRATION=true` 条件，当前环境未启用。

## 9. Task 6：构建、发布与数据期望

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/PipelineBuildService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/PipelineBuildWorker.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/PipelineVersionFreezer.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/PipelinePublisher.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/ExpectationChecker.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/output/PipelineDatasetWriter.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineBuildController.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/pipeline/execution/build/PipelineInputResolver.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/build/PipelineBuildServiceTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/build/PipelinePublisherTest.java`
- Modify: `data-service/src/test/java/com/ontoweave/data/pipeline/execution/build/PipelineInputResolverTest.java`

- [x] **Step 1: 写冻结版本复用与输入快照测试**

```java
@Test
void reusesVersionForSameDefinitionHashAndWritesResolvedInputs() {
    UUID pipelineId = UUID.randomUUID();
    ResolvedInput ordersInput = expectedInput();
    when(hash.normalizedHash(any(), any(), any())).thenReturn("sha-256");
    when(versions.findByPipelineIdAndDefinitionHash(pipelineId, "sha-256"))
        .thenReturn(Optional.of(existingVersion));
    when(inputResolver.resolve(existingVersion)).thenReturn(List.of(ordersInput));

    FrozenBuild frozen = freezer.freeze(pipelineId, ownerId, draft(7L));

    assertThat(frozen.version().getId()).isEqualTo(existingVersion.getId());
    assertThat(frozen.resolvedInputs()).containsExactly(ordersInput);
}
```

- [x] **Step 2: 写统一发布测试**

```java
@Test
void publishesAllOutputsAndSwitchesPointersInOneTransaction() {
    publisher.publish(buildId);

    verify(datasets).activateVersion(eq(datasetA), eq(versionA));
    verify(datasets).activateVersion(eq(datasetB), eq(versionB));
    verify(pipelines).activateVersion(pipelineId, pipelineVersionId);
    verify(outbox).write(eq("pipeline"), eq(pipelineId), eq("pipeline.build.succeeded"), any());
}
```

- [x] **Step 3: 实现构建入队与 worker**

服务契约固定为：

```java
public record CreateBuildRequest(long draftRevision, UUID operationId) {}

public record BuildResponse(
        UUID id, UUID pipelineId, UUID pipelineVersionId, String status, String stage,
        String trigger, String errorCode, String errorMessage, Instant startedAt,
        Instant finishedAt, List<BuildOutputResponse> outputs
) {}

public record EnqueueScheduledBuild(
        UUID pipelineId, UUID pipelineVersionId, UUID requestedBy, UUID operationId,
        List<ResolvedInput> resolvedInputs, String logicHash
) {}

public interface PipelineBuildService {
    BuildResponse enqueue(UUID pipelineId, UUID ownerId, CreateBuildRequest request);
    UUID enqueueScheduled(EnqueueScheduledBuild command);
    BuildResponse get(UUID pipelineId, UUID buildId, UUID ownerId);
    void requestCancel(UUID pipelineId, UUID buildId, UUID ownerId);
}
```

`POST /internal/v1/pipelines/{pipelineId}/builds` 只做：

1. 校验 `draftRevision`、阻塞错误、活跃构建。
2. 写入 `pipeline_build(status=queued, trigger=manual, operation_id=...)`。
3. 同一 `operation_id` 重试返回已有构建。

`PipelineBuildWorker` 使用 `FOR UPDATE SKIP LOCKED` 领取构建，更新 `running`、`started_at`、`heartbeat_at`；启动时把心跳超时的 `running` 标记为 `BUILD_WORKER_LOST`。取消只写 `cancel_requested_at`，worker 取消 Flink 作业后写 `canceled`。

- [x] **Step 4: 实现冻结与输入解析**

锁 `pipeline` 行分配 `version_number`；对去掉坐标的规范化 `definition + outputs + input_refs` 计算 SHA-256。输入节点解析当前已发布版本，写 `resolved_inputs`，必须包含 `datasetVersionId`、`schemaHash`、Iceberg 位置和 `displaySnapshot`。解析和冻结在提交 Flink 作业前完成。

冻结和输入解析接口固定为：

```java
public interface PipelineVersionFreezer {
    FrozenBuild freeze(UUID pipelineId, UUID ownerId, long draftRevision);
}

public interface PipelineInputResolver {
    List<ResolvedInput> resolve(PipelineVersionEntity version);
}

public record FrozenBuild(
        PipelineVersionEntity version,
        List<ResolvedInput> resolvedInputs
) {}
```

任务 5 已定义并测试 `resolve(UUID pipelineId, long draftRevision)`；这里只扩展
`resolve(PipelineVersionEntity version)`，不重复声明草稿方法。草稿解析复用同一套版本解析
逻辑，保证预览和构建产出的 `ResolvedInput` 结构、快照定位和展示元数据完全一致。

同一 `definition_hash` 已存在时直接复用 `pipeline_version`，但仍必须重新解析输入并写入本次 `pipeline_build.resolved_inputs`；不能复用旧构建的输入快照。

- [x] **Step 5: 实现输出执行、期望与发布**

每个输出一个 Flink 批作业，MVP 串行执行；输出写 Iceberg 临时分支 `build_<buildId>`，不直接写 `main`。期望支持：

```text
PRIMARY_KEY_UNIQUE
ROW_COUNT_RANGE
VALUE_SET
NOT_NULL
```

期望配置使用判别联合 JSON，固定形状为：

```json
{"type": "PRIMARY_KEY_UNIQUE", "columns": ["id"]}
{"type": "ROW_COUNT_RANGE", "min": 1, "max": 100000}
{"type": "VALUE_SET", "column": "status", "values": ["active", "closed"]}
{"type": "NOT_NULL", "columns": ["id", "status"]}
```

`ExpectationChecker.check(ExpectationSet, OutputSnapshot)` 返回 `ExpectationResult`，包含 `type`、`passed`、`actual`、`expected`、`failedRows` 和 `message`。任一失败必须把当前 `pipeline_build_output.result.expectations` 写完整，并让整个构建失败。

全部输出成功且期望通过后，在一个数据库事务中写各 `pipeline_dataset_version`、切换 `pipeline_dataset.current_version_id` 和 `pipeline.current_version_id`、更新构建状态并写 outbox。事务提交后尽力快进 `main` 并删除临时分支；快进失败不回滚数据库发布。

发布接口固定为：

```java
public interface PipelinePublisher {
    void publish(UUID buildId);
}

public interface PipelineDatasetWriter {
    WrittenSnapshot write(UUID buildId, UUID outputId, LogicalPlan plan, String branch);
    void fastForwardMain(UUID datasetId, long snapshotId);
    void dropBranch(UUID datasetId, String branch);
}
```

`publish(buildId)` 必须在一个数据库事务里完成：写全部 `pipeline_dataset_version`、切换全部 `pipeline_dataset.current_version_id`、切换 `pipeline.current_version_id`、把构建置为 `succeeded`、写 `pipeline.build.succeeded` outbox。事务完成后才调用 `fastForwardMain` 和 `dropBranch`；二者失败只记录告警并由对账任务重试。

输出 schema 只允许新增列和类型放宽；删除、改名、类型收窄返回 `OUTPUT_SCHEMA_CHANGED`。

- [x] **Step 6: 运行构建测试**

Run: `cd data-service && ./mvnw -Dtest=PipelineBuildServiceTest,PipelinePublisherTest,PipelineInputResolverTest test`

Expected: PASS。

- [x] **Step 7: 提交**

```bash
git add data-service/src/main/java/com/ontoweave/data/pipeline/execution/build \
  data-service/src/main/java/com/ontoweave/data/pipeline/execution/output \
  data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineBuildController.java \
  data-service/src/test/java/com/ontoweave/data/pipeline/execution/build
git commit -m "feat(data-pipeline): add build worker and atomic publish"
```

**完成记录（2026-10-21）：** 提交 `607819f`、`a889ed5`、`a19dd6d`、`fdaab8d`、`65c6eff`、`849e386`、`75fa418`。完成数据库构建队列与 `SKIP LOCKED` 领取、独立构建执行器和取消执行器、心跳与 worker 丢失恢复、版本冻结和输入快照重解析、构建全量扫描、冻结期望、每输出串行 Flink 作业、`build_<buildId>` 临时分支、Flink 聚合期望查询、统一发布事务、取消确认、分支清理、输出 schema 兼容和失败构建 schema 隔离。

验证结果：定向单元测试 63/63、PostgreSQL Testcontainers 集成测试 14/14、schema 边界测试 7/7，合计 84/84 通过；Spring ApplicationContext 启动、并发入队、发布回滚、`SKIP LOCKED`、恢复竞争和取消链路均有真实数据库证据。规格与代码质量复审通过。

已知限制：真实 Flink + Iceberg 集群的分支写入、`fastForwardMain`、`dropBranch` 和真实 `JobClient.cancel()` 未在当前环境验证；`fastForwardMain`/`dropBranch` 失败与孤儿作业取消由后续对账任务重试；失败构建可能在物理表保留不可见的额外 optional 列，但不进入发布版本 schema；worker 停机使用 `shutdownNow()`，依赖心跳超时恢复。

## 10. Task 7：定时调度与无变化跳过

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/schedule/PipelineScheduleService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/schedule/PipelineScheduleWorker.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/schedule/PipelineScheduleDispatcher.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/schedule/CronScheduleCalculator.java`
- Create: `data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineScheduleController.java`
- Modify: `data-service/pom.xml`（加入 `cron-utils`）
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/schedule/PipelineScheduleServiceTest.java`
- Test: `data-service/src/test/java/com/ontoweave/data/pipeline/schedule/PipelineScheduleDispatcherTest.java`

- [x] **Step 1: 写无变化与 pending 合并测试**

```java
@Test
void skipsWhenInputsAndPublishedVersionAreUnchanged() {
    when(versions.current(pipelineId)).thenReturn(Optional.of(version));
    when(inputResolver.resolve(version)).thenReturn(List.of(input("orders-v1")));
    when(builds.latestTerminal(pipelineId, version.getId()))
        .thenReturn(Optional.of(successfulBuild("logic-hash")));

    ScheduleRunResponse run = dispatcher.dispatch(schedule, dueAt);

    assertThat(run.status()).isEqualTo("ignored");
    assertThat(run.reasonCode()).isEqualTo("NO_CHANGE");
    verify(buildService, never()).enqueueScheduled(any());
}

@Test
void keepsSinglePendingRunWhileBuildIsActive() {
    when(builds.hasActive(pipelineId)).thenReturn(true);
    dispatcher.dispatch(schedule, dueAt);
    dispatcher.dispatch(schedule, dueAt.plusSeconds(60));

    assertThat(runs.countByScheduleAndStatus(scheduleId, "pending")).isOne();
}
```

- [x] **Step 2: 实现 cron 与到期扫描**

使用 `cron-utils` 解析 5 段 Unix cron 和 IANA 时区。`PipelineScheduleWorker` 默认每 15 秒扫描一次，每批最多 100 条，查询条件为 `status=active AND next_run_at <= now()`，使用 `FOR UPDATE SKIP LOCKED`。停机恢复只补最近一次到期触发。

`cron-utils` 固定使用 `com.cronutils:cron-utils:9.2.1`。解析器只接受 5 段 Unix cron，拒绝秒字段、Quartz 扩展字符和无效时区。计算下一次执行时使用 `ZonedDateTime`，统一转成 UTC 存储。

调度接口固定为：

```java
public record ScheduleRequest(String cronExpression, String timezone, String name) {}

public record ScheduleResponse(
        UUID id, UUID pipelineId, String name, String status, String triggerType,
        String cronExpression, String timezone, Instant nextRunAt, Instant lastRunAt,
        Instant pausedAt
) {}

public record ScheduleRunResponse(
        UUID id, UUID scheduleId, UUID pipelineId, String status, String triggerType,
        Instant scheduledFor, UUID buildId, String logicHash, String reasonCode, String message
) {}

public interface PipelineScheduleService {
    ScheduleResponse get(UUID pipelineId, UUID ownerId);
    ScheduleResponse put(UUID pipelineId, UUID ownerId, ScheduleRequest request);
    ScheduleResponse pause(UUID pipelineId, UUID ownerId);
    ScheduleResponse resume(UUID pipelineId, UUID ownerId);
    List<ScheduleRunResponse> runs(UUID pipelineId, UUID ownerId, int limit, int offset);
    ScheduleRunResponse dispatch(PipelineScheduleEntity schedule, Instant dueAt);
}
```

- [x] **Step 3: 实现运行记录与 hash**

按 `(schedule_id, scheduled_for)` 幂等创建运行记录；同一调度最多一条 `pending`。无变化判断：

```text
logic_hash = pipeline_version.definition_hash + 排序后的 resolved input version IDs
最近一次同 pipeline_version_id 的终态构建成功且 hash 相同 → ignored / NO_CHANGE
最近一次失败或取消 → 正常派发
没有历史构建 → 正常派发
```

`PipelineScheduleDispatcher.dispatch` 的固定顺序为：解析当前已发布版本 → 解析当前输入版本 → 计算 `logic_hash` → 查询最近终态构建 → 判断 `NO_CHANGE` → 若需执行则调用 `enqueueScheduled`。判重查询和派发必须在同一事务内完成，避免同一调度被两个实例重复派发。

- [x] **Step 4: 实现暂停、恢复与接口**

提供：

```text
GET  /internal/v1/pipelines/{pipelineId}/schedule?ownerId=...
PUT  /internal/v1/pipelines/{pipelineId}/schedule?ownerId=...
POST /internal/v1/pipelines/{pipelineId}/schedule/pause?ownerId=...
POST /internal/v1/pipelines/{pipelineId}/schedule/resume?ownerId=...
GET  /internal/v1/pipelines/{pipelineId}/schedule/runs?ownerId=...
```

暂停在单个事务内设置 `status=paused`、`next_run_at=null`，并把 pending 标为 `ignored / SCHEDULE_PAUSED`；不取消运行中的构建。恢复从当前时间计算下一次，不补跑。

暂停/恢复接口的请求体为空，均返回 `ScheduleResponse`；`runs` 按 `scheduled_for DESC, id DESC` 排序，`limit` 最大 100。

- [x] **Step 5: 运行调度测试**

Run: `cd data-service && ./mvnw -Dtest=PipelineScheduleServiceTest,PipelineScheduleDispatcherTest test`

Expected: PASS。

- [x] **Step 6: 提交**

```bash
git add data-service/pom.xml \
  data-service/src/main/java/com/ontoweave/data/pipeline/schedule \
  data-service/src/main/java/com/ontoweave/data/pipeline/api/PipelineScheduleController.java \
  data-service/src/test/java/com/ontoweave/data/pipeline/schedule
git commit -m "feat(data-pipeline): add schedule worker and no-change skip"
```

**完成记录（2026-10-21）：** 提交 `9185015`、`ee7e6e8`、`12af7c0`。完成 5 段 Unix cron 与 IANA 时区解析、DST 回拨重复时刻处理、15 秒到期扫描、`FOR UPDATE SKIP LOCKED` 领取、运行记录幂等和 pending 合并、已发布版本与输入版本的 `logic_hash` 无变化跳过、暂停/恢复接口和运行记录分页。

可靠性处理：到期触发先在同一事务内持久化 `pipeline_schedule_run=pending` 再推进 `next_run_at`，pending 由独立扫描器持续恢复，进程崩溃、队列拒绝或瞬时异常不会丢触发；调度构建创建加入派发事务，构建、输出和运行记录整体回滚；暂停后 PUT 不自动恢复，`last_run_at` 单调更新；扫描和派发使用独立线程池，不阻塞共享 `@Scheduled` 线程。

验证结果：Task 7 定向单元测试、真实 PostgreSQL Testcontainers 集成测试和 MockMvc 共 53/53 通过；独立复审覆盖的 42 个测试全部通过。规格与代码质量复审通过。剩余非阻塞风险：同一 pending 在极慢任务期间可能被后续扫描重复提交，依赖 schedule 行锁和稳定 `operationId` 防止重复构建；永久异常会按 15 秒节奏持续重试，尚无退避和次数上限；首次并发创建调度仍由数据库唯一约束兜底。

## 11. Task 8：Python 会话、BFF API 与迁移

**Files:**
- Create: `backend/alembic/versions/0020_data_pipeline_conversation.py`
- Modify: `backend/app/models.py`
- Modify: `backend/app/vocabulary.py`
- Modify: `backend/app/services/conversation.py`
- Modify: `backend/app/services/conversation_event.py`
- Modify: `backend/app/services/data_service/client.py`
- Modify: `backend/app/services/data_service/mapping.py`
- Create: `backend/app/services/data_pipeline.py`
- Create: `backend/app/schemas/data_pipeline.py`
- Create: `backend/app/api/data_pipelines.py`
- Modify: `backend/app/api/router.py`
- Modify: `backend/app/schemas/conversation_event.py`
- Test: `backend/tests/test_migrations.py`
- Test: `backend/tests/test_data_pipeline_api.py`

- [x] **Step 1: 写迁移测试**

```python
def test_data_pipeline_conversation_and_turn_columns(engine) -> None:
    conversation_columns = {
        column["name"] for column in inspect(engine).get_columns("conversation")
    }
    event_columns = {
        column["name"] for column in inspect(engine).get_columns("conversation_event")
    }
    assert "archived_at" in conversation_columns
    assert "turn_id" in event_columns


def test_pipeline_ids_are_stable_for_same_operation() -> None:
    owner = uuid.UUID("11111111-1111-4111-8111-111111111111")
    operation = uuid.UUID("22222222-2222-4222-8222-222222222222")
    first = pipeline_ids(owner, operation)
    second = pipeline_ids(owner, operation)
    assert first == second
    assert first[0] != first[1]
```

- [x] **Step 2: 运行迁移测试，确认失败**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py::test_data_pipeline_conversation_and_turn_columns`

Expected: FAIL。

- [x] **Step 3: 写 Alembic 迁移**

`0020_data_pipeline_conversation.py` 必须：

- 把 `ck_conversation_kind` 替换为允许 `modeling`、`data_connection`、`data_pipeline`。
- 给 `conversation` 增加 `archived_at TIMESTAMPTZ NULL`。
- 给 `conversation_event` 增加 `turn_id UUID NULL`。
- 建索引 `ix_conversation_event_turn`。

同时更新 `backend/app/models.py` 和 `backend/app/vocabulary.py` 的 `ConversationKind`。

迁移实现要点：

```python
op.drop_constraint("ck_conversation_kind", "conversation", type_="check")
op.create_check_constraint(
    "ck_conversation_kind",
    "conversation",
    "kind IN ('modeling', 'data_connection', 'data_pipeline')",
)
op.add_column("conversation", sa.Column("archived_at", sa.DateTime(timezone=True)))
op.add_column("conversation_event", sa.Column("turn_id", sa.UUID()))
op.create_index("ix_conversation_event_turn", "conversation_event", ["turn_id"])
```

`downgrade()` 必须拒绝仍有 `data_pipeline` 会话或 `turn_id` 非空事件的库，避免静默丢数据；先删除索引和列，再恢复旧的 `ck_conversation_kind`。`conversation.list_conversations` 默认过滤 `archived_at IS NULL`；`conversation.get_conversation` 仍能按 ID 读取归档会话，但所有写接口必须拒绝归档会话。

`conversation.create_conversation` 增加可选 `conversation_id: uuid.UUID | None = None`，
设置后直接作为新会话主键；`data_pipeline` 创建必须传入 `pipeline_ids` 算出的会话 ID，
这样 Java 超时后的重试不会生成第二个会话。另新增
`archive_conversation(session, conversation_id, owner_id=None)`，只设置 `archived_at`，
不调用现有硬删除 `delete_conversation`；流程删除走归档而不是物理删除。

`conversation_event` 模型和服务同步增加 `turn_id`；`append_event`、`ingest_envelope` 的签名增加 `turn_id: uuid.UUID | None`，`_conversation_id` 不能再只接受 `data_connection`，只要 `conversationId` 指向存在的会话就投递，具体业务类型由事件 payload 决定。

`ConversationEventRead` 增加 `turn_id: uuid.UUID | None`，前端用它把同一轮 Agent 改动和画布事件归组；旧事件返回 `null`。

- [x] **Step 4: 实现稳定 ID 与创建补偿**

在 `backend/app/services/data_pipeline.py` 固定：

```python
PIPELINE_NAMESPACE = uuid.NAMESPACE_URL

def pipeline_ids(owner_id: uuid.UUID, operation_id: uuid.UUID) -> tuple[uuid.UUID, uuid.UUID]:
    pipeline_id = uuid.uuid5(
        PIPELINE_NAMESPACE, f"ontoweave:data-pipeline:{owner_id}:{operation_id}"
    )
    conversation_id = uuid.uuid5(
        PIPELINE_NAMESPACE,
        f"ontoweave:data-pipeline:{owner_id}:{operation_id}:conversation",
    )
    return pipeline_id, conversation_id
```

创建顺序固定为：计算 `pipeline_id + conversation_id` → 查找或创建
`Conversation(kind=data_pipeline, resource_id=pipeline_id, id=conversation_id)` →
在调用 Java 前 `session.commit()` 持久化会话 → 调 Java。

Java 明确返回 4xx（`InvalidRequestError`、`NotFoundError`、`InvariantViolationError`）时，
只有本次调用新建了会话才删除它；删除后 `session.commit()` 再抛出异常，确保不留下孤儿会话；
如果会话是重试时复用的已存在记录，保留会话。不要在领域服务里
调用 `session.rollback()`，请求级 `get_session` 仍负责最终异常回滚。Java 超时或 5xx
（`DataServiceUnavailableError`）时保留已提交会话，由客户端用同一 `operation_id` 重试，
Java 和 Python 两边都按稳定 ID 幂等收敛。删除先调 Java 归档，再调用
`conversation.archive_conversation`；两边都幂等。

并发创建同一 `operation_id` 时，若会话插入撞 `uq_conversation_kind_resource`，在 savepoint
内捕获唯一约束异常并重新按 `kind/resource_id` 查询，不要把可收敛的并发重试变成 500。

服务函数固定为：

```python
def create_pipeline(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    payload: PipelineCreate,
    operation_id: uuid.UUID,
) -> dict: ...

def list_pipelines(
    client: DataServiceClient, *, owner_id: uuid.UUID, limit: int, offset: int
) -> dict: ...

def get_pipeline(
    client: DataServiceClient, *, owner_id: uuid.UUID, pipeline_id: uuid.UUID
) -> dict: ...

def archive_pipeline(
    session: Session,
    *,
    owner_id: uuid.UUID,
    client: DataServiceClient,
    pipeline_id: uuid.UUID,
    operation_id: uuid.UUID | None = None,
) -> None: ...
```

`create_pipeline` 在调用 Java 前用 `conversation.find_conversation_by_resource` 检查同
`kind/resource_id` 是否已存在；存在且 owner 相同则复用，不重复插入。Java 返回 4xx 时
删除会话并提交删除结果，再让异常上抛；Java 超时或 5xx 时保留已提交的会话，由后续同
`operation_id` 重试收敛。

- [x] **Step 5: 增加 Java 客户端方法**

客户端方法固定为（关键参数一并列出，避免实施时猜路径）：

```python
def create_pipeline(self, *, owner_id: uuid.UUID, payload: dict, operation_id: uuid.UUID | None = None) -> dict: ...
def list_pipelines(self, *, owner_id: uuid.UUID, limit: int, offset: int) -> dict: ...
def get_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict: ...
def rename_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, name: str, operation_id: uuid.UUID | None = None) -> dict: ...
def archive_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, operation_id: uuid.UUID | None = None) -> dict: ...
def copy_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict, operation_id: uuid.UUID | None = None) -> dict: ...
def get_draft(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict: ...
def apply_draft_command(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict: ...
def move_node(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict: ...
def check_pipeline(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, revision: int) -> dict: ...
def undo_turn(self, pipeline_id: uuid.UUID, turn_id: uuid.UUID, *, owner_id: uuid.UUID, revision: int) -> dict: ...
def get_node_catalog(self) -> dict: ...
def list_input_datasets(self, *, owner_id: uuid.UUID, limit: int, offset: int) -> dict: ...
def get_dataset_schema(self, dataset_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict: ...
def preview_node(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict: ...
def create_build(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict) -> dict: ...
def get_build(self, pipeline_id: uuid.UUID, build_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict: ...
def cancel_build(self, pipeline_id: uuid.UUID, build_id: uuid.UUID, *, owner_id: uuid.UUID, operation_id: uuid.UUID | None = None) -> dict: ...
def get_output_sources(self, pipeline_id: uuid.UUID, dataset_id: uuid.UUID, *, owner_id: uuid.UUID, version_id: uuid.UUID | None = None) -> dict: ...
def get_schedule(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID) -> dict: ...
def put_schedule(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, payload: dict, operation_id: uuid.UUID | None = None) -> dict: ...
def pause_schedule(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, operation_id: uuid.UUID | None = None) -> dict: ...
def resume_schedule(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, operation_id: uuid.UUID | None = None) -> dict: ...
def list_schedule_runs(self, pipeline_id: uuid.UUID, *, owner_id: uuid.UUID, limit: int, offset: int) -> dict: ...
```

所有方法保持 camelCase 响应，映射层再转 snake_case。Java 的 409 统一映射为 `InvariantViolationError`，Python 浏览器层继续返回 409；400、404、5xx 沿用现有映射。

- [x] **Step 6: 暴露浏览器 API**

`backend/app/api/data_pipelines.py` 提供：

```text
POST   /api/v1/pipelines
GET    /api/v1/pipelines
GET    /api/v1/pipelines/{pipelineId}
PATCH  /api/v1/pipelines/{pipelineId}
DELETE /api/v1/pipelines/{pipelineId}
POST   /api/v1/pipelines/{pipelineId}/copy
GET    /api/v1/pipelines/node-catalog
GET    /api/v1/pipelines/input-datasets
GET    /api/v1/pipelines/input-datasets/{datasetId}/schema
GET    /api/v1/pipelines/{pipelineId}/draft
POST   /api/v1/pipelines/{pipelineId}/draft/commands
POST   /api/v1/pipelines/{pipelineId}/draft/positions
GET    /api/v1/pipelines/{pipelineId}/check
POST   /api/v1/pipelines/{pipelineId}/turns/{turnId}/undo
POST   /api/v1/pipelines/{pipelineId}/previews
POST   /api/v1/pipelines/{pipelineId}/builds
GET    /api/v1/pipelines/{pipelineId}/builds/{buildId}
POST   /api/v1/pipelines/{pipelineId}/builds/{buildId}/cancel
GET    /api/v1/pipelines/{pipelineId}/datasets/{datasetId}/sources
GET    /api/v1/pipelines/{pipelineId}/schedule
PUT    /api/v1/pipelines/{pipelineId}/schedule
POST   /api/v1/pipelines/{pipelineId}/schedule/pause
POST   /api/v1/pipelines/{pipelineId}/schedule/resume
GET    /api/v1/pipelines/{pipelineId}/schedule/runs
```

FastAPI 按注册顺序匹配路由，静态路径 `GET /pipelines/node-catalog` 和
`GET /pipelines/input-datasets` 必须注册在 `GET /pipelines/{pipelineId}` 之前；否则会被
当成 `pipelineId=node-catalog`，返回 422 或 404。`input-datasets/{datasetId}/schema` 同理。

Pydantic 入口模型固定为：

```python
class PipelineCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)

class PipelineUpdate(BaseModel):
    name: str = Field(min_length=1, max_length=200)

class DraftCommandRequest(BaseModel):
    type: str
    base_revision: int = Field(ge=0)
    operation_id: uuid.UUID
    turn_id: uuid.UUID | None = None
    node: dict | None = None
    edge: dict | None = None
    output: dict | None = None

class NodePosition(BaseModel):
    node_id: uuid.UUID
    x: float
    y: float

class DraftPositionsRequest(BaseModel):
    positions: list[NodePosition] = Field(min_length=1)

class PreviewRequest(BaseModel):
    draft_revision: int = Field(ge=0)
    node_id: uuid.UUID
    request_id: uuid.UUID = Field(default_factory=uuid.uuid4)

class BuildCreateRequest(BaseModel):
    draft_revision: int = Field(ge=0)
    operation_id: uuid.UUID = Field(default_factory=uuid.uuid4)

class SchedulePutRequest(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    cron_expression: str = Field(min_length=1, max_length=100)
    timezone: str = Field(min_length=1, max_length=100)
```

创建接口从 `X-Operation-Id` 读取 `operation_id`；缺省时返回 400。`DataPipelineRead` 固定包含 `id`、`conversation_id`、`name`、`status`、`draft_revision`、`current_version_id`、`created_at`、`updated_at`。草稿、检查、预览、构建、来源和调度响应都通过独立 Pydantic 模型声明，不能把 Java DTO 直接透传给前端。

- [x] **Step 7: 运行 Python 迁移与 API 测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_migrations.py tests/test_data_pipeline_api.py`

Expected: PASS。

- [x] **Step 8: 提交**

```bash
git add backend/alembic/versions/0020_data_pipeline_conversation.py \
  backend/app/models.py backend/app/vocabulary.py \
  backend/app/services/conversation.py \
  backend/app/services/data_service/client.py \
  backend/app/services/data_service/mapping.py \
  backend/app/services/data_pipeline.py \
  backend/app/schemas/data_pipeline.py \
  backend/app/api/data_pipelines.py backend/app/api/router.py \
  backend/app/schemas/conversation_event.py \
  backend/tests/test_migrations.py backend/tests/test_data_pipeline_api.py
git commit -m "feat(data-pipeline): add Python BFF and conversation binding"
```

**完成记录（2026-10-21）：** 提交 `a9e4eb0`、`efa8763`、`86dcd62`、`e2864aa`、`a56d6a5`、`0e93569`。完成 `data_pipeline` 会话类型、`conversation.archived_at`、`conversation_event.turn_id`、稳定双 ID、会话先提交再调用 Java、明确 4xx 补偿只删除本次新建会话、超时/5xx 保留重试、会话归档写保护、Task 8 全部 BFF 路由、独立 snake_case Pydantic 契约、Java 数据集分页接口和真实 payload 事件落库。

可靠性处理：同一 `(owner_id, operation_id)` 使用 PostgreSQL session advisory lock 跨线程/实例串行化；锁连接使用独立 `NullPool` engine，不占请求连接池；并发唯一约束冲突通过 savepoint 收敛；归档为写操作终态，入队、冻结、取消、调度和发布统一检查 `archived_at IS NULL`，归档期间运行中构建发布输出但流程保持 archived；取消构建在取得 build 行锁后重查 active，关闭归档 TOCTOU；Java 422 映射为 Python/FastAPI 422 并保留 `code/details/issues`；归档会话在 SSE 建立前返回 409；所有 pipeline outbox 事件携带 `conversationId`。

验证结果：Task 8 定向 Python 测试 98 项通过，Java 归档/构建/发布/调度定向测试 93 项通过；最终质量复审重复验证 Python 74 项、Java 17 项通过；`ruff check`、`ruff format --check`、`mypy app` 全部通过。规格与代码质量复审通过。剩余非阻塞风险：`operation_lock` 的独立 engine 以数据库 URL 为缓存键，尚未继承未来可能新增的 `connect_args` 或连接级事件；非 PostgreSQL 分支为 no-op，当前产品仅支持 PostgreSQL。

## 12. Task 9：数据整理 Agent

**Files:**
- Create: `backend/app/agent/definitions/data_pipeline.py`
- Create: `backend/app/agent/prompts_data_pipeline.py`
- Create: `backend/app/agent/tools/data_pipeline/__init__.py`
- Create: `backend/app/agent/tools/data_pipeline/context.py`
- Create: `backend/app/agent/tools/data_pipeline/ops.py`
- Create: `backend/app/agent/tools/data_pipeline/binding.py`
- Modify: `backend/app/agent/definitions/registry.py`
- Modify: `backend/app/agent/definitions/base.py`
- Modify: `backend/app/agent/runtime/stream.py`
- Modify: `backend/app/api/agent.py`
- Test: `backend/tests/test_data_pipeline_agent.py`

- [x] **Step 1: 写工具与提示词测试**

```python
def test_data_pipeline_tools_include_structured_writes(session, fake_data_service):
    ctx = _context(session, fake_data_service, uuid.uuid4())
    tools = {item.name: item for item in build_data_pipeline_tools(ctx)}

    assert list(tools) == [
        "get_pipeline_state",
        "get_pipeline_draft",
        "get_pipeline_check_result",
        "get_pipeline_events",
        "get_node_catalog",
        "list_pipeline_input_datasets",
        "get_dataset_schema",
        "get_output_sources",
        "add_pipeline_node",
        "update_pipeline_node",
        "delete_pipeline_node",
        "connect_pipeline_nodes",
        "disconnect_pipeline_nodes",
        "set_pipeline_output",
        "preview_pipeline_node",
        "build_pipeline",
        "ask_user",
    ]
```

- [x] **Step 2: 写 revision 冲突与确认测试**

```python
def test_write_tool_stops_on_revision_conflict(session, fake_data_service):
    ctx = _context(session, fake_data_service, uuid.uuid4())
    tools = {item.name: item for item in build_data_pipeline_tools(ctx)}

    def conflict(**_kwargs):
        raise RevisionConflict("draft revision is stale")

    fake_data_service.apply_draft_command = conflict
    result = tools["add_pipeline_node"].invoke({
        "type": "filter", "name": "筛选", "config": {}
    })
    assert result["error"] == "REVISION_CONFLICT"


def test_destructive_tool_requires_interrupt_confirmation(
    session, fake_data_service, monkeypatch
):
    ctx = _context(session, fake_data_service, uuid.uuid4())
    tools = {item.name: item for item in build_data_pipeline_tools(ctx)}

    import app.agent.tools.data_pipeline.binding as binding

    monkeypatch.setattr(
        binding,
        "interrupt",
        lambda payload: {"confirmed": False, "payload": payload},
    )
    result = tools["delete_pipeline_node"].invoke(
        {"node_id": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11"}
    )
    assert result["interrupted"] is True
    assert fake_data_service.last_command is None
```

`RevisionConflict` 在 `backend/app/services/data_service/client.py` 旁定义为
`class RevisionConflict(InvariantViolationError)`，只在 Java 返回
`REVISION_CONFLICT` 时抛出；Agent 工具捕获它并返回
`{"error": "REVISION_CONFLICT", "message": ...}`，不抛出到整轮运行。

- [x] **Step 3: 实现 Agent 定义与上下文**

`DataPipelineAgentDefinition` 注册到 `data_pipeline`。运行上下文和工具上下文分开：
`prepare_run` 只构造 `DataPipelineRunContext`，`build_graph` 再用它构造
`DataPipelineToolContext`。这样符合现有 `AgentDefinition` 协议，也不会在
`prepare_run` 里虚构一个不存在的工具上下文返回类型。

```python
@dataclass(frozen=True, kw_only=True)
class DataPipelineRunContext(AgentRunContext):
    pipeline_id: uuid.UUID
    pipeline_state: dict | None
    draft: dict | None
    check_result: dict | None
    load_error: str | None = None

@dataclass(frozen=True, kw_only=True)
class DataPipelineToolContext(ToolContext):
    conversation_id: uuid.UUID
    pipeline_id: uuid.UUID
    client: DataServiceClient
    draft_revision: int | None
    turn_id: uuid.UUID
    questions_asked: list[str] = field(default_factory=list)
    last_check_result: dict | None = None
    load_error: str | None = None
```

`DataPipelineAgentDefinition.prepare_run` 必须校验
`conversation.resource_id == pipeline_id`，并通过 Java 读取 `get_pipeline`、`get_draft`、
`check_pipeline`，把结果写入 `DataPipelineRunContext`。任一次读取失败时捕获
`DomainError`，设置 `load_error`、`pipeline_state=None`、`draft=None`、
`check_result=None`；构造工具上下文时再从 `draft["revision"]` 取 `draft_revision`，
`draft` 为空则保持 `None`。不要伪造 revision，也不要阻止本轮运行。

`build_graph` 从 `DataPipelineRunContext` 取得 `turn_id` 和最新状态，构造
`DataPipelineToolContext`；`load_error` 非空时，所有读写工具返回
`PIPELINE_STATE_UNAVAILABLE`，只允许 Agent 用文本告知用户服务不可用。`build_state_update`
返回 `{"pipelineId": ..., "draftRevision": ..., "check": ..., "loadError": ...}`，
字段名由 Python 内部使用，不直接暴露给前端。

- [x] **Step 4: 实现 turn_id 与撤销**

在 `AgentRunContext` 增加可选 `turn_id`；`stream_run` 接受可选 `X-Turn-Id`，未传时生成 UUID，并在 `prepare_run` 后用 `dataclasses.replace(run, turn_id=turn_id)` 注入。`/messages` 和 `/resume` 都接受同一个请求头，前端在中断后复用原 turn。

签名固定为：

```python
def stream_run(
    factory: sessionmaker[Session],
    settings: Settings,
    owner_id: uuid.UUID,
    *,
    conversation_id: uuid.UUID,
    checkpointer=None,
    text: str | None = None,
    resume: Any | None = None,
    turn_id: uuid.UUID | None = None,
) -> Iterator[str]:
    ...
```

`post_message` 和 `post_resume` 使用 `Header(alias="X-Turn-Id")` 读取并传入
`stream_run`；`DataPipelineAgentDefinition` 在每轮结束时把 `turn_id` 传给
`conversation_event.append_event`，事件表与 Java 编辑记录使用同一个值。

写入工具必须携带 `base_revision` 和 `turn_id`。Java 返回 409 时工具返回 `REVISION_CONFLICT`，不自动重试。`POST /api/v1/pipelines/{id}/turns/{turnId}/undo` 透传到 Java。

- [x] **Step 5: 实现工具绑定**

读取工具：`get_pipeline_state`、`get_pipeline_draft`、`get_pipeline_check_result`、`get_pipeline_events`、`get_node_catalog`、`list_pipeline_input_datasets`、`get_dataset_schema`、`get_output_sources`。

写入工具只接受结构化操作：`add_pipeline_node`、`update_pipeline_node`、`delete_pipeline_node`、`connect_pipeline_nodes`、`disconnect_pipeline_nodes`、`set_pipeline_output`。

动作工具：`preview_pipeline_node`、`build_pipeline`。删除节点、清空流程、修改/覆盖输出、构建必须在工具内部调用 `langgraph.types.interrupt`；模型不能传 `confirmed=true`。

每轮最多一个澄清问题：`ask_user` 工具检查 `questions_asked`，第二个问题返回 `QUESTION_LIMIT_REACHED`；确认中断不占额度。

- [x] **Step 6: 写提示词**

`build_prompt(state, events)` 必须包含固定结构：

```text
你是 OntoWeave 的数据整理助手，负责陪用户逐步搭建一条数据处理流程。

## 当前流程（每轮重新读取）
{{pipeline_state_json}}

## 最近事件
{{recent_events}}

## 工作规则
1. 先调用工具确认事实，不编造节点、字段、数据集或构建状态。
2. 每轮最多问一个最阻塞的问题，提问必须调用 ask_user。
3. 普通修改直接写草稿；删除节点、清空流程、修改或覆盖输出、构建必须确认。
4. 所有写操作使用当前 draft_revision 作为 base_revision；冲突时停止写入并重新读取。
5. 只能使用节点目录中的节点和配置，不能生成自由 SQL。
6. stage=ready 时先建议预览；用户没有明确要求时不要自动构建。
7. 用简短、直白的中文回答，不暴露内部接口和服务名。

## 本轮优先动作
{{top_next_action}}
```

- [x] **Step 7: 运行 Agent 测试**

Run: `cd backend && .venv/bin/python -m pytest -q tests/test_data_pipeline_agent.py tests/test_runtime_generic.py tests/test_runtime_interrupts.py`

Expected: PASS。

- [x] **Step 8: 运行 Python 全量校验**

Run: `cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app`

Expected: 全部通过。

- [x] **Step 9: 提交**

```bash
git add backend/app/agent/definitions/data_pipeline.py \
  backend/app/agent/prompts_data_pipeline.py \
  backend/app/agent/tools/data_pipeline \
  backend/app/agent/definitions/registry.py \
  backend/app/agent/definitions/base.py \
  backend/app/agent/runtime/stream.py \
  backend/app/api/agent.py backend/tests/test_data_pipeline_agent.py
git commit -m "feat(data-pipeline): add guided pipeline agent"
```

**完成记录（2026-10-21）：** 提交 `fe50e2d`、`c5782f5`、`fd147a0`。完成 `DataPipelineAgentDefinition`、每轮重读 pipeline/draft/check、17 个结构化读写工具、`turn_id` 贯通消息与恢复接口、固定中文系统提示词、写入携带 `base_revision` 和 `operation_id`、破坏性操作与构建中断确认、`RevisionConflict` 结构化映射，以及每轮 `pipeline.agent.turn` 幂等事件和 `ask_user` 问题事件。

可靠性处理：`build_state_update` 在提交前重新读取 Java 最新状态，读取失败降级为 `loadError`；turn 事件使用专用 upsert，pause→resume 后仍能回填最终 revision 和错误；`ask_user` 通过上下文锁与持久化问题记录限制每轮一次；Agent 省略 `position` 时由 Java 的 `DraftNodePositioner` 确定性分配坐标，不把布局规则散落到 Agent。

验证结果：Python 定向测试 98 项通过，Java 草稿服务与 HTTP 集成测试 19 项通过；`ruff check`、`ruff format --check`、`mypy app` 全部通过。规格与代码质量复审通过。剩余非阻塞风险：未运行真实 LLM/deepagents、真实 checkpoint 中断恢复和真实 Java 全链路；自动布局尚未解析真实上游关系；暂停后未恢复时会先留下“已完成”的 turn 事件，恢复后再更新为最终状态。

## 13. Task 10：前端工作台、画布与配置

**Files:**
- Create: `frontend/src/shared/types/dataPipeline.ts`
- Create: `frontend/src/shared/api/dataPipeline.ts`
- Modify: `frontend/src/shared/types/index.ts`
- Modify: `frontend/src/shared/api/index.ts`
- Modify: `frontend/src/shared/types/conversationEvent.ts`
- Modify: `frontend/src/shared/api/agent.ts`
- Modify: `frontend/src/shared/chat/hooks/useAgentRun.ts`
- Create: `frontend/src/shared/workbench/WorkbenchPaneLayout.tsx`
- Create: `frontend/src/shared/workbench/PaneResizer.tsx`
- Create: `frontend/src/shared/workbench/useWorkbenchPaneWidth.ts`
- Delete: `frontend/src/features/data-connection/components/PaneResizer.tsx`
- Modify: `frontend/src/features/data-connection/hooks/useContextPaneWidth.ts`
- Modify: `frontend/src/features/data-connection/views/DataConnectionWorkbench.tsx`
- Create: `frontend/src/features/data-pipeline/views/DataPipelineWorkbench.tsx`
- Create: `frontend/src/features/data-pipeline/components/PipelineListPane.tsx`
- Create: `frontend/src/features/data-pipeline/components/PipelineConversationPane.tsx`
- Create: `frontend/src/features/data-pipeline/components/PipelineCanvas.tsx`
- Create: `frontend/src/features/data-pipeline/components/NodePalette.tsx`
- Create: `frontend/src/features/data-pipeline/components/nodes/PipelineNode.tsx`
- Create: `frontend/src/features/data-pipeline/components/drawers/NodeConfigDrawer.tsx`
- Create: `frontend/src/features/data-pipeline/components/drawers/fields/`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineList.ts`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineDraft.ts`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineCommandQueue.ts`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineConversation.ts`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineLayout.ts`
- Create: `frontend/src/features/data-pipeline/lib/flow.ts`
- Create: `frontend/src/features/data-pipeline/lib/nodeCatalog.ts`
- Create: `frontend/src/features/data-pipeline/lib/connectionValidation.ts`
- Create: `frontend/src/features/data-pipeline/lib/constants.ts`
- Create: `frontend/src/styles/data-pipeline.css`
- Modify: `frontend/src/app/App.tsx`
- Test: `frontend/src/features/data-pipeline/lib/flow.test.ts`
- Test: `frontend/src/features/data-pipeline/lib/connectionValidation.test.ts`

- [x] **Step 1: 写画布纯函数测试**

```ts
const FILTER_A = '30000000-0000-4000-8000-000000000002'
const FILTER_B = '30000000-0000-4000-8000-000000000006'

it('builds nodes and edges from draft without mutating positions', () => {
  const result = draftToFlow(draft, catalog, null)
  expect(result.nodes.map((node) => node.id)).toEqual([
    '30000000-0000-4000-8000-000000000001',
    '30000000-0000-4000-8000-000000000002',
    '30000000-0000-4000-8000-000000000003',
  ])
  expect(result.edges.map((edge) => edge.id)).toEqual([
    '30000000-0000-4000-8000-000000000004',
    '30000000-0000-4000-8000-000000000005',
  ])
  expect(result.nodes[1].position).toEqual({ x: 320, y: 180 })
})

it('rejects occupied input ports and cycles', () => {
  expect(validateConnection(draft, candidate(FILTER_A, FILTER_B)).ok).toBe(false)
  expect(validateConnection(draft, candidate(FILTER_B, FILTER_A)).reason).toBe('CYCLE')
})
```

- [x] **Step 2: 运行测试，确认前端模块不存在**

Run: `cd frontend && npm test -- --run src/features/data-pipeline/lib/flow.test.ts`

Expected: FAIL。

- [x] **Step 3: 抽取共享三栏骨架**

把数据连接已有的分栏和会话外壳抽到 `shared/workbench`：

- `WorkbenchPaneLayout` 接收 `left`、`center`、`right`、`leftWidth`、`rightWidth` 和拖拽回调。
- `PaneResizer` 从 `features/data-connection/components/PaneResizer.tsx` 迁移，数据连接和流程工作台共用。
- `PipelineConversationPane` 复用 `shared/chat` 的消息、事件、输入和中断组件，不复制聊天实现。

抽取前先运行 `cd frontend && npm test -- --run src/features/data-connection`，记录现有测试通过；抽取后运行同一条命令，必须得到相同结果。`useWorkbenchPaneWidth` 承接现有 `useContextPaneWidth` 的 localStorage、最小/最大宽度和拖拽逻辑；把 `features/data-connection/hooks/useContextPaneWidth.ts` 改成薄封装或直接删除并更新调用点，存储 key 保持 `ontoweave.data-connection.context-pane-width` 不变，避免用户已保存宽度失效。数据连接页面的 DOM 结构和 CSS 变量名保持不变。

- [x] **Step 4: 实现路由与 API 类型**

新增路由：

```text
/pipelines
/pipelines/:pipelineId
```

`shared/api/dataPipeline.ts` 必须覆盖 Task 8 的浏览器接口，并从 `shared/api/index.ts`
统一导出。`shared/types/dataPipeline.ts` 从 `shared/types/index.ts` 统一导出；浏览器 API
包装字段使用 snake_case，草稿定义内部沿用固定草稿 JSON 的 camelCase，两者不能混写。
`ConversationEvent` 同时增加可空 `turn_id`。

创建和写操作必须带 `X-Operation-Id`；`createPipeline`、所有草稿命令、构建、取消、
调度写入都由前端生成 `crypto.randomUUID()` 并传给 API 客户端。

```ts
// 浏览器 API 包装字段：snake_case
export interface Pipeline { id: string; conversation_id: string; name: string; status: string; draft_revision: number; current_version_id: string | null; created_at: string; updated_at: string }
export interface PipelineDraft { schemaVersion: number; revision: number; nodes: PipelineNode[]; edges: PipelineEdge[] }
export interface PipelineIssue { code: string; phase: string; blocking: boolean; node_id?: string; port_id?: string; field?: string; message: string; hint?: string }
export interface PipelineCheckResult { pipeline_id: string; revision: number; stage: string; blocking_issues: PipelineIssue[]; warnings: PipelineIssue[]; next_actions: Array<Record<string, unknown>> }
export interface PipelinePreview { preview_id: string; pipeline_id: string; draft_revision: number; node_id: string; columns: Array<Record<string, unknown>>; rows: Array<Record<string, unknown>>; returned_rows: number; scanned_rows: number | null; truncated: boolean; truncation_reason: string | null; elapsed_ms: number; input_versions: Array<Record<string, unknown>>; errors: PipelineIssue[]; warnings: PipelineIssue[] }
export interface PipelineBuild { id: string; pipeline_id: string; pipeline_version_id: string; status: string; stage: string | null; trigger: string; error_code?: string; error_message?: string; outputs: Array<Record<string, unknown>> }
export interface PipelineDataset { id: string; output_id: string; name: string; version_id: string; version_number: number; row_count: number; published_at: string }
export interface PipelineSource { pipeline_id: string; dataset_id: string; version_id: string; output: PipelineDataset; build: Record<string, unknown>; pipeline_version: Record<string, unknown>; inputs: Array<Record<string, unknown>> }
export interface PipelineSchedule { id: string; pipeline_id: string; name: string; status: string; cron_expression: string; timezone: string; next_run_at: string | null; last_run_at: string | null }

// 草稿定义内部字段：camelCase，与 Java 草稿 JSON 固定契约一致
export interface PipelineNode { id: string; type: string; name: string; config: Record<string, unknown>; position: { x: number; y: number } }
export interface PipelineEdge { id: string; sourceNodeId: string; sourcePort: string; targetNodeId: string; targetPort: string }
```

- [x] **Step 5: 实现画布与命令队列**

`PipelineCanvas` 使用受控 `@xyflow/react`。节点和边由草稿派生；`useNodesState` 只保存拖动中的位置和选中态。从节点面板拖入时用 `screenToFlowPosition` 生成 UUID 和默认配置，发送 `add_node` 命令；成功后打开配置抽屉。

`usePipelineCommandQueue` 一次只允许一个写请求在途。每个命令带 `baseRevision`；409 时停止队列、重新读取草稿并提示，不静默覆盖。`move_node` 单独发送位置接口，不递增 revision。

Agent 回合由共享聊天层携带 `X-Turn-Id`：`streamAgentRun` 增加可选 `turnId` 参数并写入请求头；
`useAgentRun` 用 `turnIdRef` 保存当前轮 ID，新用户消息生成 `crypto.randomUUID()`，中断后的
resume 复用同一个 ID。`turnId` 不放进 JSON body。数据连接会话继续使用同一套共享聊天层，
行为不变，只是事件也会带可空 `turn_id`。

```ts
export async function streamAgentRun(
  conversationId: string,
  body: AgentRunInput,
  onEvent: (event: AgentEvent) => void,
  signal?: AbortSignal,
  turnId?: string,
): Promise<void> {
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  if (turnId) headers['X-Turn-Id'] = turnId
  // fetch 时使用 headers；body 仍然只发送 text 或 answer。
}
```

初始布局和「整理布局」使用 `@dagrejs/dagre`。节点拖动结束后持久化坐标；Agent 新增节点没有坐标时，Java 分配到上游右侧，找不到上游时使用自动布局。

- [x] **Step 6: 实现节点面板与配置抽屉**

节点面板从 `/pipelines/node-catalog` 读取目录，按类别展示 10 个 MVP 节点。配置抽屉采用通用字段组件加专用编辑器：

```text
filter:         ConditionTreeEditor
select_columns: FieldMultiSelect
rename_columns: RenameListEditor
derive_column:  ExpressionEditor
deduplicate:    FieldMultiSelect + mode
join:           JoinTypeSelect + JoinKeysEditor
union:          EmptyConfig
aggregate:      GroupByPicker + AggregationListEditor
output:         OutputSelect
input:          DatasetPicker
```

配置按失焦或约 400 毫秒空闲合并提交；结构合法但语义不完整时允许保存，并显示 Java 检查结果。节点和字段错误来自 `PipelineCheckResult`，前端不自行推导 schema。

- [x] **Step 7: 运行前端纯函数和类型检查**

Run: `cd frontend && npm test -- --run src/features/data-connection src/features/data-pipeline/lib && npx tsc -b`

Expected: PASS。

- [x] **Step 8: 提交**

```bash
git add frontend/src/shared/types/dataPipeline.ts frontend/src/shared/api/dataPipeline.ts \
  frontend/src/shared/types/index.ts frontend/src/shared/api/index.ts \
  frontend/src/shared/types/conversationEvent.ts \
  frontend/src/shared/api/agent.ts frontend/src/shared/chat/hooks/useAgentRun.ts \
  frontend/src/shared/workbench frontend/src/features/data-pipeline \
  frontend/src/features/data-connection/hooks/useContextPaneWidth.ts \
  frontend/src/features/data-connection/views/DataConnectionWorkbench.tsx \
  frontend/src/features/data-connection/components/PaneResizer.tsx \
  frontend/src/styles/data-pipeline.css frontend/src/app/App.tsx
git commit -m "feat(data-pipeline): add workbench canvas and node configuration"
```

**完成记录（2026-10-21）：** 提交 `031fa25`、`26c088e`、`1d17bfb`、`c0c6b18`、`58c6552`。完成 `/pipelines` 路由、流程列表、数据整理对话、共享三栏骨架、节点面板、受控 React Flow 画布、DAG 连线校验、dagre 布局、命令队列、节点目录驱动的配置抽屉、条件树/表达式/字段多选/分组/聚合/输出/数据期望编辑器和 `set_output` 绑定。

可靠性处理：浏览器 API 覆盖 Task 8 全部流程接口，外层 snake_case 与草稿 `definition` camelCase 分离；命令队列在 tail 串行回调内读取最新 revision；pipeline 代次 token 丢弃切换后的旧响应，并重置冲突状态；抽屉用同步 dirty guard 保证关闭/卸载只 flush 一次，外部 Agent 改名和 `set_output` 回包仅在安全时合并；多选拖动持久化全部变化坐标；`X-Turn-Id` 贯通 Agent 新回合与 resume，会话切换会中止旧回合并清空中断状态。

验证结果：前端 `npx tsc -b`、`npm run lint`、`npm test`、`npm run build` 全部通过，最终 28 个测试文件 / 125 项通过；Java `PipelineApiDraftIntegrationTest` 8 项通过；规格与代码质量复审通过。剩余非阻塞风险：未运行真实浏览器 E2E 与视觉回归；`usePipelineGeneration` 在 render 期同步代次，lint 有 5 条 refs-during-render warning；字段多选暂未接入上游 schema 候选，仍支持手工添加字段。

## 14. Task 11：预览、构建、调度与来源 UI

**Files:**
- Create: `frontend/src/features/data-pipeline/components/preview/PreviewPanel.tsx`
- Create: `frontend/src/features/data-pipeline/components/preview/PreviewTable.tsx`
- Create: `frontend/src/features/data-pipeline/components/preview/PreviewIssueList.tsx`
- Create: `frontend/src/features/data-pipeline/components/build/BuildBar.tsx`
- Create: `frontend/src/features/data-pipeline/components/build/BuildDetailsDrawer.tsx`
- Create: `frontend/src/features/data-pipeline/components/build/BuildStatusBadge.tsx`
- Create: `frontend/src/features/data-pipeline/components/schedule/ScheduleDrawer.tsx`
- Create: `frontend/src/features/data-pipeline/components/schedule/ScheduleRunList.tsx`
- Create: `frontend/src/features/data-pipeline/components/source/OutputSourcePanel.tsx`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelinePreview.ts`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineBuild.ts`
- Create: `frontend/src/features/data-pipeline/hooks/usePipelineSchedule.ts`
- Create: `frontend/src/features/data-pipeline/hooks/useOutputSources.ts`
- Create: `frontend/src/features/data-pipeline/lib/preview.ts`
- Create: `frontend/src/features/data-pipeline/lib/buildStatus.ts`
- Create: `frontend/src/features/data-pipeline/lib/schedule.ts`
- Test: `frontend/src/features/data-pipeline/lib/preview.test.ts`
- Test: `frontend/src/features/data-pipeline/lib/buildStatus.test.ts`
- Test: `frontend/src/features/data-pipeline/lib/schedule.test.ts`

- [x] **Step 1: 写预览和构建状态测试**

```ts
it('marks preview stale when draft revision changes', () => {
  expect(previewState({ previewRevision: 3, draftRevision: 4 })).toBe('stale')
})

it('maps build stage to a user-facing label', () => {
  expect(buildStageLabel('checking_expectations')).toBe('检查数据期望')
  expect(buildStageLabel('publishing')).toBe('发布版本')
})

it('formats skipped schedule reason', () => {
  expect(scheduleRunLabel({ status: 'ignored', reason_code: 'NO_CHANGE' }))
    .toBe('输入和逻辑未变化，已跳过')
})
```

- [x] **Step 2: 实现配置与预览互斥**

点击节点打开配置抽屉；点击抽屉底部「预览」后关闭抽屉，打开底部预览面板。预览面板打开时点击其他节点，关闭预览并打开新节点配置抽屉。配置修改后当前预览标记为「已过期」，不自动重跑。

浏览器预览请求带 `draft_revision` 和 `node_id`；409 提示重读草稿，422 展示阻塞错误。响应展示返回行数、扫描行数、截断原因、耗时、输入版本和错误列表；点击字段错误关闭预览并打开对应配置抽屉。

- [x] **Step 3: 实现构建入口与详情**

画布顶部只保留一个「构建」按钮。点击先弹确认，展示草稿修订号、输出列表和“构建期间修改草稿不会进入本次构建”。构建运行中显示阶段；点击打开详情，展示整体状态、每个输出状态、耗时、行数、期望结果和日志入口。取消按钮写入取消请求，不发布任何输出。

- [x] **Step 4: 实现调度抽屉**

调度抽屉展示启用/暂停、频率预设、自定义 cron、时区、下次运行时间、运行历史和跳过原因。草稿变化但未构建时显示「调度仍使用已发布版本 vN」；没有已发布版本时显示「首次构建后才能定时运行」。创建、修改、暂停、恢复必须由用户确认。

- [x] **Step 5: 实现输出来源区域**

在输出数据集详情增加「来源」区域，展示：

```text
输入数据集名称、版本、连接、源 schema、源表
构建 ID、构建时间、构建状态
流程版本号
输出版本号、行数、发布时间
```

只展示直接输入；不出现祖先/后代展开、影响分析、字段级血缘或血缘图入口。`metadataStatus=missing` 显示 ID 和“元数据不可用”；`restricted` 隐藏连接和源表。

- [x] **Step 6: 运行前端全量校验**

Run: `cd frontend && npx tsc -b && npm run lint && npm test && npm run build`

Expected: 全部通过。

- [x] **Step 7: 提交**

```bash
git add frontend/src/features/data-pipeline/components \
  frontend/src/features/data-pipeline/hooks \
  frontend/src/features/data-pipeline/lib \
  frontend/src/styles/data-pipeline.css
git commit -m "feat(data-pipeline): add preview build schedule and source UI"
```

- [x] **Step 8: 复审修复与验证记录**

实施提交：`c6a782c`（预览、构建、调度与来源 UI）、`7d8f014`（执行界面契约缺口）、`0b034bc`（保存与面板生命周期加固）、`fba99c2`（日志写回原子更新）。

最终验证：

```text
frontend: 43 test files / 192 tests 通过；tsc、lint、build 通过
python: 定向 7 tests 通过；ruff、ruff format --check、mypy 通过
java: 定向 49 tests 通过；真实 PostgreSQL Testcontainers 并发用例通过
review: SPEC_PASS / QUALITY_PASS
```

已知限制：真实 Flink/Iceberg 集群未在 Task 11 启动，日志链接可达性和真实作业跳转留到 Task 12 验证。

## 15. Task 12：端到端验收与文档回填

**Files:**
- Create: `docs/checks/2026-10-20-p2-data-preparation-smoke.md`
- Modify: `docs/superpowers/phases/p2-data-preparation.md`
- Modify: `docs/superpowers/ROADMAP.md`

- [x] **Step 1: 启动完整环境**

验收说明：数据面由 Docker Compose 启动；Python API 镜像缺少 `deepagents`，本次改用本地 uvicorn，其余 DB、MySQL、RabbitMQ、Flink、data-service 均为 Docker 实例。

Run:

```bash
docker compose up -d --build
docker compose ps
curl -sf http://127.0.0.1:18000/health
curl -sf http://127.0.0.1:18001/actuator/health
```

Expected: `app`、`data-service`、Flink、PostgreSQL、RabbitMQ 全部健康。

- [x] **Step 2: 准备 P1 输入数据集**

创建 MySQL 连接、测试连接、选择一张有主键的表并执行 snapshot 同步；等待 `dataset.version.published`，记录 `dataset_id` 和 `version_id`。没有已发布版本时不得继续 P2 验收。

- [x] **Step 3: 创建流程并编辑草稿**

用 Python API 创建流程：

```bash

CREATED=$(curl -sS -X POST http://127.0.0.1:18000/api/v1/pipelines \
  -H 'content-type: application/json' \
  -H 'X-Operation-Id: 11111111-1111-4111-8111-111111111111' \
  -d '{"name":"订单清洗"}')
PIPELINE_ID=$(printf '%s' "$CREATED" | jq -r '.id')
CONVERSATION_ID=$(printf '%s' "$CREATED" | jq -r '.conversation_id')

post_command() {
  curl -sS -X POST "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/draft/commands" \
    -H 'content-type: application/json' \
    -H "X-Operation-Id: $1" \
    -d "$2"
}

post_command 22222222-2222-4222-8222-222222222222 '{
  "type":"add_node","base_revision":0,"operation_id":"22222222-2222-4222-8222-222222222222",
  "node":{"id":"30000000-0000-4000-8000-000000000001","type":"input","name":"订单","config":{"datasetKind":"sync","datasetId":"DATASET_ID"},"position":{"x":0,"y":0}}
}'
post_command 33333333-3333-4333-8333-333333333333 '{
  "type":"add_node","base_revision":1,"operation_id":"33333333-3333-4333-8333-333333333333",
  "node":{"id":"30000000-0000-4000-8000-000000000002","type":"filter","name":"有效订单","config":{"condition":{"kind":"compare","field":"id","op":"is_not_null"}},"position":{"x":240,"y":0}}
}'
post_command 44444444-4444-4444-8444-444444444444 '{
  "type":"add_node","base_revision":2,"operation_id":"44444444-4444-4444-8444-444444444444",
  "node":{"id":"30000000-0000-4000-8000-000000000003","type":"output","name":"订单输出","config":{},"position":{"x":480,"y":0}}
}'
post_command 55555555-5555-4555-8555-555555555555 '{
  "type":"connect","base_revision":3,"operation_id":"55555555-5555-4555-8555-555555555555",
  "edge":{"id":"30000000-0000-4000-8000-000000000004","sourceNodeId":"30000000-0000-4000-8000-000000000001","sourcePort":"out","targetNodeId":"30000000-0000-4000-8000-000000000002","targetPort":"in"}
}'
post_command 66666666-6666-4666-8666-666666666666 '{
  "type":"connect","base_revision":4,"operation_id":"66666666-6666-4666-8666-666666666666",
  "edge":{"id":"30000000-0000-4000-8000-000000000005","sourceNodeId":"30000000-0000-4000-8000-000000000002","sourcePort":"out","targetNodeId":"30000000-0000-4000-8000-000000000003","targetPort":"in"}
}'
post_command 77777777-7777-4777-8777-777777777777 '{
  "type":"set_output","base_revision":5,"operation_id":"77777777-7777-4777-8777-777777777777",
  "output":{"id":"10000000-0000-4000-8000-000000000001","nodeId":"30000000-0000-4000-8000-000000000003","outputType":"dataset","name":"orders_clean","datasetId":"20000000-0000-4000-8000-000000000001"}
}'
```

把 `DATASET_ID` 换成 P1 已发布数据集 UUID，并把筛选条件里的 `id` 换成所选表真实存在的
主键列名；如果该列可能为空，改用 `eq` 比较一个确定的测试值。节点、边、输出和派生数据集
ID 必须都是合法 UUID；示例值是固定 UUID，实际执行时可以替换，但不能改回 `input-1` 这类
别名。每个命令的 `base_revision` 必须等于上一条命令返回的 `revision`。最后请求
`GET /api/v1/pipelines/${PIPELINE_ID}/check`，确认 `stage=ready`。

- [x] **Step 4: 验证预览**

对筛选节点和输出节点各请求一次预览，确认：

```bash
curl -sS -X POST "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/previews" \
  -H 'content-type: application/json' \
  -d '{"draft_revision":6,"node_id":"30000000-0000-4000-8000-000000000002","request_id":"88888888-8888-4888-8888-888888888888"}'
curl -sS -X POST "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/previews" \
  -H 'content-type: application/json' \
  -d '{"draft_revision":6,"node_id":"30000000-0000-4000-8000-000000000003","request_id":"99999999-9999-4999-8999-999999999999"}'
```

检查响应：

```text
返回行数 <= 500
truncated 为布尔值
input_versions 固定到同一 dataset_version_id
预览响应不写 pipeline_build / pipeline_dataset_version
```

- [x] **Step 5: 验证构建与发布**

触发构建，轮询构建状态；确认构建成功后：

```bash
BUILD=$(curl -sS -X POST "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/builds" \
  -H 'content-type: application/json' \
  -d '{"draft_revision":6,"operation_id":"30000000-0000-4000-8000-000000000006"}')
BUILD_ID=$(printf '%s' "$BUILD" | jq -r '.id')
curl -sS "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/builds/${BUILD_ID}"
```

```text
pipeline_build.status = succeeded
每个 pipeline_build_output.status = succeeded
pipeline_dataset.current_version_id 已切换
pipeline.current_version_id 已切换
历史版本仍可按 snapshot 读取
```

- [x] **Step 6: 验证来源、调度和 Agent**

- `GET /api/v1/pipelines/{pipelineId}/datasets/{datasetId}/sources` 返回直接输入，输入改名后历史来源不变。
- Agent 在 `data_pipeline` 会话中能读取状态、调用结构化工具、遇到 revision 冲突时停止并重读。

```bash
curl -sS "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/datasets/20000000-0000-4000-8000-000000000001/sources"

curl -sS -X PUT "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/schedule" \
  -H 'content-type: application/json' \
  -H 'X-Operation-Id: bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' \
  -d '{"name":"每日构建","cron_expression":"0 2 * * *","timezone":"Asia/Shanghai"}'
curl -sS -X POST "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/schedule/pause" \
  -H 'X-Operation-Id: cccccccc-cccc-4ccc-8ccc-cccccccccccc'
curl -sS -X POST "http://127.0.0.1:18000/api/v1/pipelines/${PIPELINE_ID}/schedule/resume" \
  -H 'X-Operation-Id: dddddddd-dddd-4ddd-8ddd-dddddddddddd'
```

暂停后检查 `pipeline_schedule_run`：pending 记录必须是 `ignored / SCHEDULE_PAUSED`；恢复后只从当前时间计算下一次，不补跑暂停期间的触发。来源响应必须满足 `inputs.length` 等于本次构建实际解析的输入数，且不出现递归祖先、下游或字段级血缘字段。

- [x] **Step 7: 记录验收结果**

在 `docs/checks/2026-10-20-p2-data-preparation-smoke.md` 记录：

```text
命令、请求 ID、pipeline_id、build_id、dataset_version_id、耗时、结果
失败项、修复提交、剩余风险
```

同时把 `p2-data-preparation.md` 的 Task 2.1-2.6 状态改为完成或明确未完成，把实施计划链接加入文档索引，并在 `ROADMAP.md` 更新 P2 状态为「实施完成/验收通过」或列出剩余任务。

**验收完成记录（2026-10-22）：**

- 烟测报告：[`docs/checks/2026-10-20-p2-data-preparation-smoke.md`](../../checks/2026-10-20-p2-data-preparation-smoke.md)
- 预览、手动构建、调度构建、历史快照、来源接口和 Agent 只读闭环均通过。
- 修复 checkpointer 初始化死锁，并补充启动初始化回归测试、暂停后派发 `SCHEDULE_PAUSED` 测试。
- 已知限制：Python API 容器缺少 `deepagents`；Testcontainers 因 Docker Hub 超时未执行；Java 预览取消/超时测试和 Python `test_changeset.py` 受本地环境限制。

## 16. 完成标准

- 用户能从对话或画布创建一条流程，保存后刷新仍能恢复同一草稿和同一对话。
- 10 个 MVP 节点可通过节点目录渲染，端口规则和配置错误由 Java 统一校验。
- 配置抽屉与预览面板互斥；预览最多返回 500 行，能标记截断和字段级错误。
- 构建冻结 `pipeline_version` 和精确输入版本；多输出全部成功后才统一发布。
- 失败、取消、无变化都有明确记录；调度暂停/恢复不补跑。
- 输出数据集能查看单跳来源、构建和流程版本；不出现完整血缘图和影响分析入口。
- Java、Python、前端校验命令全部通过，Docker 端到端烟测通过。
