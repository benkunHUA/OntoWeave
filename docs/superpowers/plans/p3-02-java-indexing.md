# P3 W1-02：Java 索引运行与生成

> 依赖：W0（表、实体、仓储、数据集目录、Iceberg 读取、writer 接口）。
> 写作用域：`com.ontoweave.data.ontology.index`、`application.yml` 的 `ontoweave.ontology` 段，以及两处发布钩子。不要改 `ontology/binding`、`ontology/query`、Python/前端。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §6

## 目标

交付 DB 队列表驱动的索引 worker：入队合并、领取、租约、恢复、取消、对象生成、关系生成、原子切换、旧版本清理和状态事件。生成结果只通过版本指针对外可见。

## 允许改动的既有文件

只允许各加一处调用，不改原有逻辑：

- `sync/version/DatasetVersionPublisher.java`：发布成功后调 `IndexEnqueuer.enqueueOnPublish(SYNC, datasetId, versionId)`。
- `pipeline/execution/build/PipelinePublicationTransaction.java`：每个输出版本发布后调 `enqueueOnPublish(PIPELINE, datasetId, versionId)`。

## 交付物

### 入队

`ontology/index/queue/OntologyIndexEnqueuer implements IndexEnqueuer`

- `enqueueManual`：校验对象/关系绑定，写 queued run。
- `enqueueOnPublish`：
  - 查 `active_dataset_kind/id` 命中的绑定。
  - 用新版本 schema 校验生效 mapping；失效则 `markNeedsRepair`，不入队。
  - 命中多个绑定各自 upsert 一条 queued run。
- `enqueueRetry`：复制失败 run 的目标版本、mapping 快照、`attempt + 1`。
- 合并：同 subject 已有 queued 时原地更新目标版本、快照、`requested_at`、trigger。

### Worker

`ontology/index/worker/OntologyIndexWorker`

- 每 1 秒 `lockNextQueued()`；领取时置 `RUNNING`、`phase=READING`、租约 60 秒。
- 每 10 秒续租；每批检查 `cancel_requested_at`。
- 启动与每 30 秒 `recoverLost()`：租约过期标 `FAILED(WORKER_LOST)`，删 stage。
- 并发上限可配，默认 2。
- 处理顺序：对象 run → 关系 run。

### 对象生成

`ontology/index/pipeline/ObjectIndexPipeline`

1. 解析 run 的目标 `DatasetRef`、冻结的 mapping。
2. 固定 snapshot 流式读取。
3. 逐行编码主键、标题、`properties`，写 `object_stage` 与 `object_value_stage`。
4. 每 `batchSize`（默认 10000）flush 一次。
5. stage 校验：主键空、主键重复、必填为空、枚举越界、类型失败。
6. 阻断则失败并清 stage；通过后分批 `INSERT ... SELECT` 到正式实例表。
7. 创建 `BUILDING` 对象版本，写计数。

### 关系生成

`ontology/index/pipeline/RelationIndexBuilder`

- 范围从 `ontology_relation_definition` 查 `source_type_id` 或 `target_type_id` 命中的关系。
- 外键：
  - 用源对象 mapping 中外键属性对应的列值匹配目标 `primary_key_value`。
  - 目标本轮重建用新版本，否则用当前版本。
- 多对多：
  - 用关系绑定的生效 mapping 与生效连接表版本，读固定 snapshot。
  - 两列按两端主键类型编码，join 两端对象版本。
- 缺失端跳过计数，重复对 `ON CONFLICT DO NOTHING`。
- 对象 run 内同时生成外键和多对多关系版本；连接表发布触发的独立 run 只生成该关系。
- 快照缺失或端点定义缺失：告警并跳过该关系。

### 切换

`ontology/index/switch/IndexSwitcher`

- 事务内校验 `run.status = RUNNING AND lease_owner = ?`。
- 关系版本校验 `from/to_object_version_id` 仍是当前版本；多对多再校验连接表版本仍是当前版本，否则整条关系版本 `SUPERSEDED`。
- 写版本 `ACTIVE`，旧版本 `RETIRED`，更新绑定指针与 `active_*`。
- run `SUCCEEDED` + stats，写 outbox 事件。
- 任一异常回滚，run `FAILED`，指针不动。

### 清理

`ontology/index/cleanup/VersionCleanupWorker`

- 每 30 秒扫描 `RETIRED` 且 `retired_at < now() - 5min` 的版本。
- 每批 5000 行删除派生值、关系实例、对象实例，最后删版本行。
- 记录 `cleanup_started_at`、`cleanup_completed_at`、`cleanup_attempts`。
- stage 清理按 `run_id`，失败/取消时立即执行。

### 事件

- `ontology.index.run.started/succeeded/failed/cancelled` 写现有 outbox。
- 负载只含 id、subject、status、计数、错误码；不含属性值。

## 实施步骤

- [x] 1. `OntologyIndexEnqueuer` + 合并/发布命中/待修复测试。
- [x] 2. 发布钩子两处 + 集成测试（P1/P2 各一例）。
- [x] 3. Worker 领取、租约、心跳、恢复、并发测试。
- [x] 4. 对象生成 stage 写入与三类阻断校验测试。
- [x] 5. promote 分批写入与计数测试。
- [x] 6. 外键关系生成测试：命中、缺失、重复、目标本轮重建。
- [x] 7. 多对多关系生成测试：命中、缺失端、重复、连接表版本变化。
- [x] 8. 原子切换测试：成功、任一版本失败回滚、租约丢失拒绝切换。
- [x] 9. 取消测试：排队取消、运行中每批退出。
- [x] 10. 清理测试：延迟、分批、重试。
- [x] 11. 全量 `./mvnw -q test` 并更新阶段文档。

## 验收命令

```bash
cd data-service && ./mvnw -q test -Dtest='OntologyIndex*,Ontology*Pipeline*,Ontology*Cleanup*' && ./mvnw -q test
```

## Definition of Done

- 同一 subject 不会有两个 queued 或两个 running。
- 运行中进程被杀后，租约过期能恢复；不会出现永久 `RUNNING`。
- 主键重复、必填为空、类型失败都整批失败，旧对象仍可查。
- 对象与受影响关系在同一事务切换；任一失败全部回滚。
- 过期关系结果不会覆盖新结果。
- 清理按批执行，单批不超过 5000 行。
- 发布钩子不改变 P1/P2 原有行为和测试结果。

## 交接说明

- 不实现查询接口；查询走 W1-03。
- 不实现映射校验规则；复用 W0 的 `MappingValidator` 和冻结的 mapping 数据。
- 切换后只更新指针，不碰 `revision` 和草稿字段。
