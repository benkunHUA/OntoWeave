# 第三阶段：Flink CDC + Iceberg 同步链路

> 承接《数据连接与同步技术设计》第 9 节，是数据连接与同步总计划的第 3 个子计划。

**Goal:** 在 Java 数据服务中完成“选表建任务 → 触发生成运行 → 提交 Flink CDC → 跟踪 Flink/Iceberg → 发布数据集版本”的最小闭环。

**Scope:**
- 本阶段完成：`SyncTask`、`SyncRun`、`Dataset`、`DatasetVersion`；批量选表；snapshot/cdc 两种运行；RabbitMQ 执行命令；Flink CDC pipeline YAML；Flink 提交与状态跟踪；Iceberg snapshot 读取、tag 与版本发布；Docker Flink 集群。
- 本阶段不做：Python API/Agent 接入、前端工作台、数据集预览 UI、旧 Iceberg 表清理、savepoint 恢复。

## 关键决策

- 一条源表对应一条 `SyncTask` 和一个 `Dataset`；`SyncTask` 保存主键列和同步模式。
- 每次运行生成新的 Iceberg 表：`ontoweave_conn_<short>.ds_<short>_r<short>`，避免全量重跑污染旧数据。
- `snapshot` 使用 `scan.startup.mode=snapshot` + `BATCH`；`cdc` 使用 `initial` + `STREAMING`。
- Java 不读源数据、不写 Parquet；只做前置检查、YAML 生成、作业提交、状态跟踪和版本发布。
- 运行命令先落 `queued`，由 Outbox 风格的 dispatcher 投递到 RabbitMQ `sync.execute`，worker 消费执行。
- Iceberg 第一版使用 Hadoop catalog + 本地共享 warehouse；版本发布用 Iceberg tag 固定 snapshot。
- 默认版本间隔 86400 秒；失败不发布版本；旧版本不动。

## Task 1：同步领域与迁移

- 新增 `V2__sync_domain.sql`：任务、运行、数据集、版本、server-id 序列。
- 新增任务/运行/数据集/版本实体与仓库，状态枚举使用显式值。
- 运行唯一约束：`(sync_task_id, operation_id)`；数据集版本唯一约束：`(dataset_id, version_number)`。

## Task 2：选表与任务接口

- `POST /internal/v1/data-connections/{id}/sync-tasks?ownerId=`：批量校验表存在、普通表、有主键；全部通过才落库。
- 复用已有任务；已存在但本次取消的表不删除。
- 同步模式默认 `cdc`，版本间隔默认 86400 秒，且不短于 checkpoint 间隔。

## Task 3：触发、停止与查询

- `POST /internal/v1/data-connections/{id}/sync-runs?ownerId=`：按表生成 `queued` 运行，返回 `operationId + runs`。
- `POST /internal/v1/sync-runs/{id}/stop?ownerId=`：标记 `stopping`；worker/supervisor 调 Flink stop/cancel。
- `GET /internal/v1/sync-runs/{id}`、`GET /internal/v1/datasets/{id}`。
- `GET /internal/v1/data-connections/{id}/state` 返回任务、最近运行和数据集。

## Task 4：Flink CDC 作业

- `MysqlCdcPipelineBuilder` 生成 YAML，正则转义库表名，密码只在临时文件出现。
- `ProcessFlinkCdcSubmitter` 用 `ProcessBuilder` 调用 `flink-cdc.sh`，权限 `0600`，退出后删除临时文件。
- `FlinkRestClient` 查询作业、checkpoint，支持 cancel 和 stop-with-savepoint。
- `SyncRunSupervisor` 定时对账 Flink 状态，Java 重启后从数据库恢复。

## Task 5：Iceberg 与版本发布

- `HadoopIcebergCatalogClient` 读取最新 snapshot、schema、行数、文件大小，创建/删除 tag。
- `DatasetVersionPublisher` 按 `version_number` 追加版本，原子切换 `Dataset.current_version_id`。
- 空表创建空 append snapshot；发布失败不改变当前版本。

## Task 6：Docker 与验收

- Compose 增加 Flink JobManager/TaskManager、共享 warehouse/checkpoint/savepoint 卷。
- 数据服务镜像内置 Flink 1.20 Hadoop 客户端、Flink CDC 3.7.0 CLI、MySQL/Iceberg connector。
- 冒烟：MySQL 源表 → Flink snapshot → Iceberg 表 → Java 读取 snapshot → 发布版本。
- 验证：`./mvnw verify`；Docker 健康检查；无明文密码；临时 YAML 已删除。

## 完成标准

- 能从已测试的 MySQL 连接批量创建同步任务。
- 能触发 snapshot 运行并最终发布一个 `DatasetVersion`。
- CDC 运行可以提交、停止并保留当前版本；失败运行不改变当前版本。
- 运行状态、Flink 状态、Iceberg snapshot 和数据集版本都能通过内部接口查询。

## 实施状态（2026-10-18）

上述任务已落地，代码在 `data-service/`，未提交。

- 同步领域：`SyncTask`、`SyncRun`、`Dataset`、`DatasetVersion`、Outbox 命令投递和 RabbitMQ worker 已完成。
- Flink：`flink-cdc.sh` 提交单表 pipeline；Snapshot 使用 `snapshot + BATCH`，CDC 使用 `initial + STREAMING`；Supervisor 负责 Flink 状态、checkpoint、初始快照完成判断和定时版本发布。
- Iceberg：Hadoop catalog + 共享卷；发布版本时创建 tag；`byteSize` 使用 `SnapshotSummary.TOTAL_FILE_SIZE_PROP`。
- 版本发布幂等：同一数据集、Iceberg 表、snapshot 已有版本时，不再插入重复版本，只回填运行的 `lastPublishedSnapshotId`。
- Docker：Compose 已包含 PostgreSQL、MySQL、RabbitMQ、Flink JobManager/TaskManager、数据服务和共享 warehouse/checkpoint/savepoint 卷。
- 接口：`sync-tasks` 支持批量选表；`sync-runs` 支持触发/查询/停止；`datasets/{id}` 返回当前版本和历史版本。

### 验收记录

- snapshot：MySQL `demo.customers` → Flink CDC → Iceberg → `DatasetVersion v1`，2 行、4 列。
- CDC：MySQL `demo.orders` → 初始快照 + binlog → checkpoint → 停止时发布 `stop` 版本。
- 定时发布：将版本间隔设为 60 秒，初始快照后修改一条源数据；60 秒内完成新 checkpoint，随后发布 `scheduled` 版本，`byteSize=5371`。
- 验证命令：`TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify`，23 个测试通过。

### 已知限制

- 只支持 MySQL；PostgreSQL、Hive、MongoDB、Kafka 后续按连接器扩展。
- `DatasetVersion.rowCount` 对 CDC 版本是 Iceberg snapshot summary 的物理记录数，存在更新/删除时不等于源表精确行数，因此标记 `rowCountEstimated=true`。
- 旧 Iceberg 表、孤儿文件、失败运行临时表尚未清理；第一版触发 stop-with-savepoint，但 savepoint 路径回填和恢复尚未实现。
- Hadoop catalog 只适合本地共享卷；多节点部署需改 REST catalog 与对象存储。
