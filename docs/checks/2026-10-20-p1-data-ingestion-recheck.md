# P1「数据进来」设计落实复检

- 复检日期：2026-10-20
- 复检对象：`main@1ff348b` 工作区（修复未提交）
- 原检查报告：[2026-10-20-p1-data-ingestion-check.md](2026-10-20-p1-data-ingestion-check.md)（正文保持不变）
- 复检方式：逐项对照原报告缺口，阅读修复代码，运行 Python / 前端 / Java 校验和 Compose 配置检查。

## 1. 结论

原检查列出的 H1-H3、M1-M6、L1-L4 均已处理。P1 主链路可以判定为「设计落实通过」，但仍有环境、运行态数据和部署验证方面的已知限制，不能等同于生产验收完成。

| 项目 | 原问题 | 修复后状态 | 残余风险 |
|---|---|---|---|
| H1 | CDC 停止时可能发布不完整版本 | ✅ 已修复并清理历史错误版本 | 无已知数据残留 |
| H2 | 连接级/全局并发和 slot 预检缺失 | ✅ 已修复，V4 已应用 | 未做多实例并发压测 |
| H3 | 失败/停止后 Iceberg 孤儿表未清理 | ✅ 已修复并清理历史孤儿表 | 无已知孤儿表 |
| M1 | 快照完成事件和判断方案 | ✅ 已处理 | 仍使用行数估算替代 Flink 指标，已在设计稿接受并回填 |
| M2 | 数据集版本接口与来源字段缺失 | ✅ 已修复 | 数据集详情仍返回全部版本以兼容前端 |
| M3 | `savepoint_path` 未回填 | ✅ 已修复 | 历史 stopped 运行不会补写 |
| M4 | 停止操作没有独立 `operation_id` | ✅ 已修复，V7 已应用并实测 | 未做浏览器端卡片归组回归 |
| M5 | 缺少项目级 Compose 与同实例 schema | ✅ 已修复 | 尚未用根 Compose 做完整端到端启动验收 |
| M6 | 创建连接失败遗留草稿会话 | ✅ 已修复 | 草稿阶段使用临时 UUID 占位，受 `resource_id` 非空约束限制 |
| L1 | 数据集详情/列表/放大预览不完整 | ✅ 已修复 | 无浏览器自动化测试 |
| L2 | 左侧连接列表不显示其他连接最近同步 | ✅ 已修复 | 无浏览器自动化测试 |
| L3 | 事件命名和队列名文档不一致 | ✅ 已修复 | 仅为文档契约对齐，无新增运行时测试 |
| L4 | 前端 lint 警告和测试门禁 | ✅ 已修复 | 浏览器自动化测试仍未接入 |

## 2. 高优先级缺口

### H1. 停止 CDC 初始化快照时发布不完整版本

- 原问题：停止路径只检查 `lastCheckpointAt`，发布器本身没有 CDC 快照完成门槛。
- 修复前状态：初始化快照未完成也可能发布 `version_source=stop` 的版本。
- 修复后状态：`DatasetVersionPublisher.publishable()` 对 CDC 统一要求 `snapshotCompletedAt != null && lastCheckpointAt != null && lastCheckpointAt.isAfter(snapshotCompletedAt)`；`snapshot_end`、`scheduled`、`stop` 全部受同一门槛约束。
- 证据：`data-service/src/main/java/com/ontoweave/data/sync/version/DatasetVersionPublisher.java`、`DatasetVersionPublisherTest.java`。
- 测试：新增 `doesNotPublishCdcSnapshotEndWithoutCheckpointAfterSnapshot`；`DatasetVersionPublisherTest` 5 个用例通过。
- 残余风险：无已知数据残留。2026-10-20 已删除 3 个不满足门槛的历史版本，并确认当前版本指针均指向有效版本。

### H2. 连接级和全局并发限制未实现

- 原问题：只有应用层同任务查询，没有部分唯一索引、连接/全局并发和 slot 预检。
- 修复前状态：并发触发可能同时向 Flink 提交超过容量的作业，出现 `Available Task Slots = 0`。
- 修复后状态：`V4__sync_run_active_guard.sql` 增加同任务活跃运行部分唯一索引；触发时对连接行加锁；`SyncRunAdmissionService` 使用 PostgreSQL advisory lock、全局上限（默认 4）、单连接上限（默认 1）和 Flink `/overview` slot 预检，超限运行保持 `queued` 并释放 dispatch claim。
- 证据：`SyncRunAdmissionService.java`、`SyncRunExecutor.java`、`DataConnectionRepository.java`、`application.yml`、`V4__sync_run_active_guard.sql`。
- 测试：`SyncRunAdmissionServiceTest`、`SyncRunExecutorTest` 通过；Java 全量非集成测试未出现业务失败。
- 残余风险：V4 已在当前 Docker 数据库应用；未做多实例并发压测。真实 snapshot 触发和 CDC 停止冒烟均通过。

### H3. 失败/停止运行没有清理新建 Iceberg 表

- 原问题：`dropTable()` 没有业务调用方，失败和停止运行持续留下孤儿表。
- 修复前状态：warehouse 中大量表没有被任何 `dataset_version` 引用。
- 修复后状态：`SyncRunCleanupService.cleanupIfUnreferenced()` 在失败和停止终态检查版本引用，未引用时调用 `dropTable()`；清理失败只记 warning，不改变运行终态。
- 证据：`SyncRunCleanupService.java`、`SyncRunSupervisor.java`、`DatasetVersionRepository.java`、`SyncRunCleanupServiceTest.java`。
- 测试：覆盖“无版本引用 → 清理”和“有版本引用 → 保留”。
- 残余风险：无已知孤儿表。2026-10-20 已删除 3 个无效版本和 14 张未被版本引用的 Iceberg 表，warehouse 只保留 10 张被版本引用的表；CDC 停止冒烟再次验证未发布运行的表会被清理。

## 3. 中优先级缺口

### M1. 快照完成判断和事件

- 原问题：没有 `sync.run.snapshot_completed`，且替代判断方案未回填设计。
- 修复后状态：Supervisor 在 `SnapshotCompletionDecider` 判定完成时发送 `sync.run.snapshot_completed`；技术设计 §9.6 已明确接受“预估行数 + checkpoint + Iceberg 行数”的替代方案及其巡检延迟。
- 证据：`SyncRunSupervisor.java`、`SyncRunSupervisorTest.java`、技术设计 §9.6。
- 测试：`SyncRunSupervisorTest.publishesInitialSnapshotVersionImmediately` 断言事件发送。
- 残余风险：仍未接入 Flink `isSnapshotting` / `isStreamReading` 指标，判定精度依赖估算行数。

### M2. 数据集 API 与来源字段

- 原问题：缺版本分页/单版本接口、来源字段、结构化预览列和 `row_count`。
- 修复后状态：Java/Python 均提供 `GET /datasets/{id}/versions` 和 `GET /datasets/{id}/versions/{version_id}`；`DatasetResponse` 增加 `versionCount`、`sourceSchema`、`sourceTable`；版本增加 `sourceRunId`、`storagePath`；预览列改为 `{name,type,nullable}` 并返回 `rowCount`。版本分页改为真实 `LIMIT/OFFSET`，任意 offset 不再错位。
- 证据：`DatasetController.java`、`DatasetService.java`、`DatasetVersionRepository.java`、`V6__dataset_version_provenance.sql`、`backend/app/schemas/data_connection.py`。
- 测试：新增 `DatasetServiceTest.passesArbitraryOffsetToVersionQuery`（`limit=20&offset=5`）；Python 447 tests 通过。
- 残余风险：数据集详情接口仍返回全部版本，前端如需大版本量应改用版本分页接口。

### M3. `savepoint_path` 回填

- 原问题：stop-with-savepoint 的 request id 和实际路径没有落到运行记录。
- 修复后状态：`V5__sync_run_savepoint_request.sql` 增加 `savepoint_request_id`；停止发起时记录 request id；Supervisor 终态查询 Flink savepoint 详情并回填 `savepoint_path`。
- 证据：`SyncRunStopInitiator.java`、`FlinkRestClient.java`、`SyncRunSupervisor.java`、`V5__sync_run_savepoint_request.sql`。
- 测试：`SyncRunStopInitiatorTest`、`SyncRunSupervisorTest` 覆盖 request id 和路径回填。
- 残余风险：历史 stopped 运行的 `savepoint_path` 不会追溯补齐；Flink 侧 savepoint 可能已过期。

### M4. 停止操作独立 `operation_id`

- 原问题：停止事件沿用原运行 operation id，无法形成独立操作卡片。
- 修复后状态：Python 停止接口透传 `X-Operation-Id`，前端点击时生成 UUID；Java 在 `requestStop` 时写入 `stop_operation_id`；`sync.run.stopping`、`sync.run.stopped` 和停止过程中的 `sync.run.failed` 使用同一停止操作 ID。
- 证据：`SyncRunController.java`、`SyncRunService.java`、`SyncRunRepository.java`、`SyncRunSupervisor.java`、`V7__sync_run_stop_operation.sql`、`frontend/src/shared/api/dataConnections.ts`。
- 测试：`SyncRunServiceTest` 断言停止 operation id 写入和事件参数；`SyncRunSupervisorTest` 断言 stopped 事件使用同一 ID。
- 残余风险：V7 已应用；真实 CDC 停止冒烟确认 `stopping` 与 `stopped` 使用同一 `stop_operation_id`，`savepoint_path` 已回填。未做浏览器端 SSE 卡片归组回归。

### M5. 项目级 Docker Compose

- 原问题：Java/Python 使用不同 Compose 和不同 PostgreSQL，schema 不统一。
- 修复后状态：根目录 `docker-compose.yml` 统一 MySQL、PostgreSQL（`app` / `data` schema）、RabbitMQ、Flink、Java、Python 和前端；RabbitMQ 暴露 `5673` 供本地 Python 使用；`docker/postgres/init.sql` 创建 `app` / `data`。
- 证据：`docker-compose.yml`、`docker/postgres/init.sql`、`frontend/Dockerfile`、`frontend/nginx.conf`。
- 测试：`docker compose -f docker-compose.yml config -q` 通过。
- 残余风险：尚未用根 Compose 做完整端到端启动验收；`init.sql` 只对全新 PostgreSQL volume 生效；旧 Compose 文件仍保留。

### M6. 创建连接失败回滚与草稿事件

- 原问题：Java 创建失败会留下孤立 Python 会话，草稿事件缺失。
- 修复后状态：创建失败时删除草稿会话；成功后写入 `connection.draft.created`。草稿阶段使用临时 UUID 占位，成功回填真实连接 ID。
- 证据：`backend/app/services/data_connection.py`、`backend/tests/test_data_connection_service.py`、`frontend/src/features/data-connection/lib/labels.ts`。
- 测试：新增“Java 创建失败后没有孤立会话”用例；Python 全量通过。
- 残余风险：`resource_id` 非空约束导致无法真正写空值，属于已记录的实现偏差。

## 4. 低优先级与文档偏差

### L1. 数据集详情和列表展示

- 修复后状态：列表显示来源表和最近同步状态；详情显示来源连接/源表/版本数量/创建更新时间；版本历史显示来源运行和列数；新增 `DatasetPreviewDialog` 放大预览；预览最多 100 行。
- 证据：`DatasetList.tsx`、`DatasetDetail.tsx`、`dialogs/DatasetPreviewDialog.tsx`。
- 残余风险：无浏览器自动化测试。

### L2. 左侧连接列表最近同步

- 修复后状态：连接列表使用后端返回的 `last_sync_status` / `last_sync_at` 展示每个连接的最近同步，不再只看当前连接。
- 证据：`ConnectionListPane.tsx`、`DataConnectionResponse.java`、`DataConnectionService.java`。
- 残余风险：无浏览器自动化测试。

### L3. 事件命名和队列契约

- 修复后状态：技术设计 §6.2 已改为实际使用的 `sync.run.running`、`sync.run.snapshot_completed`、`sync.run.stopping`，事件队列改为 `ontoweave.python.data-events`，并注明业务字段位于 `payload`。
- 证据：技术设计 §6.2。
- 残余风险：历史运行事件仍会保留旧命名语义，不做数据回写。

### L4. 前端质量门禁

- 修复后状态：8 个 `react(set-state-in-effect)` 警告已消除；`npx tsc -b`、`npm run lint`、`npm test`、`npm run build` 全部通过。
- 证据：前端全量命令输出。
- 残余风险：浏览器自动化测试仍未接入。

## 5. 复检校验结果

```text
backend: 447 passed, 1 warning
backend ruff check: All checks passed
backend ruff format --check: 183 files already formatted
backend mypy: Success, 114 source files

frontend: 11 files / 82 tests passed
frontend tsc / lint / build: 通过

data-service: 50 tests, 47 passed, 3 errors
```

Java 的 3 个错误全部是 `HadoopIcebergPreviewReaderTest` 在本机 JDK 26 下的
`java.lang.UnsupportedOperationException: getSubject is not supported`，与本次修复逻辑无关。
定向运行的 `DatasetVersionPublisherTest`、`SyncRunServiceTest`、`SyncRunSupervisorTest`、
`DatasetServiceTest` 共 18 个用例全部通过。

```text
docker compose -f docker-compose.yml config -q: 通过
```

### 运行态校验（2026-10-20）

- 当前 `data-service` Compose 已重新构建并重启；Flyway 从 v3 升到 v7，V4-V7 全部成功。
- Java 健康检查：`{"status":"UP"}`；Flink `slots-total=4`、`slots-available=4`、`jobs-running=0`。
- 清理前有 13 个数据集版本；删除 3 个不满足 CDC 发布门槛的历史版本后为 10 个，全部能关联到有效运行，当前版本指针均存在。
- 回填 13 条历史版本的 `source_run_id` / `storage_path`；删除 14 张未被任何版本引用的 Iceberg 表，warehouse 只剩 10 张被引用表。
- 真实 snapshot 冒烟：`demo.customers` 新运行 `25f3fd0f-a58c-4ca0-96de-88238b1ca819` 成功，发布 v2，`source_run_id`、`storage_path` 正确写入。
- 真实 CDC 停止冒烟：`demo.orders` 运行 `6e1d3fb3-2379-4336-a83b-4a26263dbd59` 从 `running → stopping → stopped`；`stop_operation_id=3585335c-93e9-4d1e-8706-f65fcfa935ea`，savepoint 路径已回填；`sync.run.stopping` 与 `sync.run.stopped` 使用同一停止操作 ID；未发布版本，运行表已被清理。
- 重启过程中发现并修复了两个单元测试覆盖不到的启动问题：Spring Data 把 `findByIdAndOwnerIdForUpdate` 当作属性解析；`SyncProperties` 的多构造器导致配置绑定失败。
- 冒烟结束后的最终计数：`dataset_version` 11 条、被引用 Iceberg 表 11 张、无效 CDC 版本 0、活动运行 0、悬空 `current_version_id` 0。
- 清理 13 个无引用的历史 savepoint 目录；保留 1 个被成功停止运行引用的 savepoint，避免 `savepoint_path` 悬空。数据库备份保留。

## 6. 仍需注意

- 本次复检基于工作区代码和测试，不等于生产验收。
- Java 容器已重新构建并应用 V4-V7；Python 容器当前未运行，本轮未做 Python 侧 SSE 联调。
- 历史错误版本和历史孤儿表已清理；旧 stopped 运行的 savepoint 路径仍无法追溯补齐。
- Java 集成测试、浏览器自动化和根 Compose 端到端启动仍未完成。
