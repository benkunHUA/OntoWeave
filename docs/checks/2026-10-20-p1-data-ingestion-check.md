# P1「数据进来」设计落实检查

- 检查日期：2026-10-20
- 检查对象：`main@1ff348b`，P1 代码基线 `7c8e240`
- 检查依据：
  - [总体设计 §3.1、§3.6、§3.7](../superpowers/specs/2026-10-17-data-integration-and-agent-loop-design.md)
  - [技术设计 §4-§11、§12 实施记录、§14 决策记录](../superpowers/specs/2026-10-17-data-connection-sync-technical-design.md)
- 检查方式：逐项对照设计稿、阅读实现代码、运行现有测试、检查本机 Docker 运行态与数据库数据。
- 本次未修改业务代码，也未调整 `ROADMAP.md` / P1 阶段状态。

## 1. 结论

**P1 主链路已经真实可用，但不能判定为「每个设计议题都完全落实」。建议状态写为「核心完成、有条件通过」。**

已跑通的主链路包括：创建连接草稿 → 保存配置 → 测试连接 → 发现 MySQL 表 → 多表保存同步任务 → 触发 snapshot / CDC → Flink 提交作业 → Iceberg 写入 → 发布不可变数据集版本 → Outbox / RabbitMQ → Python 对话事件 → SSE → 只读 Agent 工具 → 前端三栏工作台。

按 10 个议题统计：**3 个可判定为已落实，7 个为部分落实，0 个完全未落实**。部分落实中，有 3 项属于必须先处理的高优先级缺口，不只是文案或 UI 差异。

## 2. 十议题逐项矩阵

| # | 设计议题 | 判定 | 核对结果 |
|---|---|---|---|
| 1 | 服务边界与职责 | ✅ 已落实 | 前端只调用 Python；Java 只提供 `/internal/v1`；连接、同步、运行、数据集元数据归 Java；对话与事件归 Python；内部 REST 带 `X-Internal-Token` 和 `ownerId`。架构方向与设计一致。部署形态的偏差另见议题 10。 |
| 2 | 领域模型与状态机 | 🟡 部分落实 | `DataConnection`、`SyncTask`、`SyncRun`、`Dataset`、`DatasetVersion` 和状态流转已落地；但「同一任务最多一个活跃运行」只有应用层查询，没有设计要求的数据库部分唯一索引；`DatasetVersion` 缺 `source_run_id`、`storage_path`；停止后未回填 `savepoint_path`。 |
| 3 | 跨服务 REST / MQ 契约 | 🟡 部分落实 | 内部 REST、Outbox、RabbitMQ、`event_id` 去重、`operation_id` 归组、`(sync_task_id, operation_id)` 幂等均已实现；但停止接口没有独立 `operation_id`，设计中的 `sync.run.snapshot_completed` 未发布，`sync.run.started` 被实现成 `sync.run.running`，创建连接失败时未删除草稿会话。 |
| 4 | 凭据加密与连接测试 | ✅ 已落实 | AES-256-GCM、`key_version`、`connection_id + kind` 作为 AAD、响应只返回 `secretConfigured`、连接测试失败返回 HTTP 200 + `status=failed`、配置变化重置 `untested` 均已实现；密码未进入响应或事件。 |
| 5 | 表发现与多表选择 | ✅ 已落实 | 只读取指定 database 的 `BASE TABLE`；读取字段与主键；无主键表拒绝；批量保存先全量校验，任一表失败则整批失败；未选任务置 `disabled` 而不是删除；同一任务有活跃运行时不允许多次触发。 |
| 6 | 同步执行与失败隔离 | 🟡 部分落实 | Flink CDC pipeline、snapshot / CDC 两种模式、server-id 分配、临时 YAML `0600`、Flink Job ID 查询、Supervisor 状态跟踪、失败详情格式化已实现；但连接级和全局并发没有实现，失败/停止运行没有清理新建 Iceberg 表，停止 CDC 初始化快照时可能发布不完整版本。 |
| 7 | 数据集落盘与版本发布 | 🟡 部分落实 | 每次运行独立 Iceberg 表、不可变 `DatasetVersion`、Iceberg tag、当前版本指针切换、空表空 snapshot、`live=true` 实时预览已实现；但版本分页/单版本接口缺失，`DatasetVersion` 来源信息不完整，停止路径发布门槛不符合 §9.7。 |
| 8 | Python API、对话事件与 Agent | 🟡 部分落实 | Python REST、SSE、`after_event_id` 补拉、Outbox 消费、`Conversation.kind + resource_id`、数据连接 Agent 定义、4 个只读工具、每轮注入最近 10 条事件均已实现；但缺少设计中的版本接口，停止操作不独立成卡片，快照完成事件缺失，草稿创建/表结构加载等事件覆盖不完整。 |
| 9 | 前端三栏交互 | 🟡 部分落实 | 三栏布局、连接表单、概览/表与同步/数据集三个页签、表搜索与多选、无主键置灰、失败详情默认折叠且展开最大高度 180px、预览最多 100 行均已实现；但数据集详情缺来源连接/源表/创建更新时间/版本来源运行，版本历史缺列数，没有「放大」预览弹窗，左侧连接列表只在当前连接显示最近同步。 |
| 10 | 测试与部署 | 🟡 部分落实 | Python 446 tests、ruff、format、mypy 通过；前端 tsc、lint、82 tests、build 通过；Java 39 tests 仍有 1 个过期单测失败和 3 个 JDK 26 环境错误；部署不是一个项目级 Compose，Python 和 Java 使用不同 Postgres，Python 表在 `public` 而非设计中的 `app` schema。 |

## 3. 高优先级缺口

### H1. 停止 CDC 初始化快照时，可能发布不完整版本

设计要求：

- §9.7：CDC 在 `snapshot_completed_at` 后的第一个完成的 checkpoint 才发布首个版本；停止时也不发布未满足该条件的快照。
- §9.8：初始化快照尚未完成时停止，不发布版本，并删除该运行的新表。

实现事实：

- `SyncRunSupervisor.finishStopped()` 在 CDC 只要存在 `lastCheckpointAt` 就调用发布器，没有检查 `snapshotCompletedAt`，也没有检查 `lastCheckpointAt > snapshotCompletedAt`。
- `DatasetVersionPublisher.publish()` 本身也没有快照完成门槛，直接读取 Iceberg 最新 snapshot 并发布。
- 运行态存在一条反例：`demo.orders` 的 stopped CDC 运行 `af0165e4-aa33-49ef-84b6-1c2691a59786`，`snapshot_completed_at` 为空，但已发布 v3（5012 行，`version_source=stop`）。

影响：

- 用户在初始快照中途停止任务时，当前版本可能指向只包含部分数据的 snapshot。
- 该版本会被当成不可变版本供后续 P2/P3 使用，属于数据正确性问题，不只是展示问题。

证据位置：

- `data-service/src/main/java/com/ontoweave/data/sync/execution/SyncRunSupervisor.java:298`
- `data-service/src/main/java/com/ontoweave/data/sync/version/DatasetVersionPublisher.java:47`
- 技术设计 §9.7、§9.8

建议：

- 在发布器入口按运行模式加门槛：CDC 只有 `snapshotCompletedAt != null && lastCheckpointAt != null && lastCheckpointAt.isAfter(snapshotCompletedAt)` 才允许发布；snapshot 运行仍按其正常 `FINISHED` 条件发布。
- 停止路径如果未满足门槛，只落 `stopped`，不发 `dataset.version.published`，并进入 H3 的表清理流程。
- 增加「初始化快照中途停止」单测，断言不新增 `DatasetVersion`。

### H2. 连接级和全局并发限制未实现

设计要求 §9.6：

- 同一任务最多一个活跃运行。
- 同一连接默认只跑一个运行。
- Java 全局并发默认 4。
- 队列和并发用数据库锁控制，不靠进程内计数器。

实现事实：

- `SyncRunService.trigger()` 只查询同一 `sync_task_id` 是否已有活跃运行。
- 没有同一 `connection_id` 的活跃运行检查。
- 没有全局并发计数、排队或数据库锁。
- 数据库只有 `(sync_task_id, operation_id)` 唯一约束，没有设计要求的活跃运行部分唯一索引。两个不同 `operation_id` 的并发请求仍可能同时通过应用层查询并各自创建运行。

影响：

- 多条命令或一次选择很多表时，可能同时向 Flink 提交超过 slot 容量的作业，复现之前「Available Task Slots 为 0、任务因资源不足失败」的问题。
- 这是并发控制缺失，不是 Flink 本身不稳定。

证据位置：

- `data-service/src/main/java/com/ontoweave/data/sync/service/SyncRunService.java:79`
- `data-service/src/main/resources/db/migration/V2__sync_domain.sql:71`
- 技术设计 §9.6

建议：

- 增加数据库级部分唯一索引：同一 `sync_task_id` 在 `queued/preparing/submitted/running/stopping` 状态下只能有一行。
- 在同一事务内用连接行锁或 advisory lock 串行化同一连接的新启动命令；同一次多表命令仍按设计生成多条运行，由提交层统一排队，不要用连接级限制拆掉批量语义。
- 增加全局并发配置和计数，超过上限的运行保持在 `queued`，由 worker/Supervisor 按容量领取，而不是直接提交 Flink；提交前最好再校验 Flink 可用 slot，避免把资源不足当成作业失败。
- 增加并发触发测试，验证同一连接、同一任务和全局上限三个层次。

### H3. 失败/停止运行没有清理新建 Iceberg 表

设计要求 §9.8：

- 失败运行如果从未发布过版本，尽力删除它新建的 Iceberg 表。
- 初始化快照未完成时停止、快照运行停止时，删除该运行的新表。
- 仍被 `DatasetVersion` 引用的表不能删除。

实现事实：

- `IcebergCatalogClient.dropTable()` 只有接口和 Hadoop 实现，没有业务调用方。
- `SyncRunSupervisor` 的失败、停止路径只更新运行状态和事件，没有清理动作。
- 检查时 warehouse 中有 24 张表，其中 21 张没有被任何 `dataset_version` 引用；数据库里有 34 条运行记录、13 个版本。

影响：

- 每次失败或中途停止都会留下孤儿表，长期运行后 warehouse 持续膨胀。
- 这也会掩盖「当前版本到底引用哪张表」的审计关系。

证据位置：

- `data-service/src/main/java/com/ontoweave/data/sync/iceberg/IcebergCatalogClient.java:15`
- `data-service/src/main/java/com/ontoweave/data/sync/execution/SyncRunSupervisor.java:282`
- 技术设计 §9.8

建议：

- 在运行进入 `failed/stopped` 后，如果该运行的 Iceberg 表没有被任何 `DatasetVersion` 引用，调用 `dropTable()`。
- 清理必须幂等，并在 Flink 作业确认终态后执行。
- 保留 `dataset_version` 引用检查，不能清理仍被历史版本使用的表。
- 增加「失败未发布」「停止未发布」「已发布后失败」三类测试。

## 4. 中优先级缺口

### M1. 快照完成判断没有使用设计中的 Flink 指标和事件

设计要求 §9.6：

- 每 30 秒查询 `<database>.<table>.isSnapshotting` 和 `isStreamReading`。
- `isSnapshotting` 从 1 变为 0 且 `isStreamReading=1` 时记录 `snapshot_completed_at`，并发出 `sync.run.snapshot_completed`。

实现事实：

- 代码中没有 `isSnapshotting` / `isStreamReading` 查询。
- `SnapshotCompletionDecider` 使用「源表预估行数 + checkpoint 时间 + Iceberg 行数」判断快照完成。
- 运行态事件类型中没有 `sync.run.snapshot_completed`。

评价：这是一个可工作的替代实现，但没有回填设计，也没有给 Agent 提供设计承诺的快照完成事件。建议在文档中明确接受该替代方案，或补齐 Flink 指标查询和事件。

证据位置：

- `data-service/src/main/java/com/ontoweave/data/sync/execution/SnapshotCompletionDecider.java:14`
- `data-service/src/main/java/com/ontoweave/data/sync/execution/SyncRunSupervisor.java:115`
- 技术设计 §9.6

### M2. 数据集 API 与设计契约不一致

设计要求：

- `GET /datasets/{id}/versions?limit=&offset=`
- `GET /datasets/{id}/versions/{version_id}`
- `DatasetRead` 包含 `source_schema`、`source_table`、`version_count`。
- `DatasetVersionRead` 包含 `source_run_id`。
- `PreviewRead` 包含 `row_count`，`columns` 为 `{name, type, nullable}` 结构。

实现事实：

- Java 和 Python 都只有数据集详情与预览接口，没有版本分页和单版本接口。
- `DatasetService.get()` 一次返回全部版本。
- `DatasetResponse` / Python `DatasetRead` 没有来源 schema、来源表、版本数量。
- `DatasetVersion` 表和响应没有 `source_run_id`、`storage_path`。
- `DatasetPreviewResponse` 没有 `row_count`，`columns` 是 `List<String>`。

影响：前端无法可靠展示来源表、版本来源运行，也无法分页加载大版本历史；预览接口契约与前端类型存在隐性偏差。

证据位置：

- `data-service/src/main/java/com/ontoweave/data/sync/api/DatasetController.java:27`
- `data-service/src/main/java/com/ontoweave/data/sync/service/DatasetService.java:41`
- `data-service/src/main/resources/db/migration/V2__sync_domain.sql:80`
- `data-service/src/main/java/com/ontoweave/data/sync/api/dto/DatasetPreviewResponse.java:7`
- `backend/app/api/data_connections.py:275`
- 技术设计 §10.2、§10.3

### M3. `savepoint_path` 没有回填

设计要求 §9.6：CDC 停止调用 stop-with-savepoint，记录 `savepoint_path`，供后续恢复和排障。

实现事实：

- `SyncRunStopInitiator` 计算了目标目录，也调用了 `stopWithSavepoint()`，但返回值只用于判断是否发起成功，没有保存实际 savepoint 路径。
- `SyncRunSupervisor.finishStopped()` 的所有调用点都传入 `null`。
- 检查时 15 条 stopped 运行的 `savepoint_path` 全部为空。

证据位置：

- `data-service/src/main/java/com/ontoweave/data/sync/execution/SyncRunStopInitiator.java:44`
- `data-service/src/main/java/com/ontoweave/data/sync/execution/SyncRunSupervisor.java:153`
- 技术设计 §9.6、§12.1

### M4. 停止操作没有独立 `operation_id`，不会形成独立操作卡片

设计要求 §10.7、§11.3：所有写操作接受可选 `operation_id`；停止操作也应携带或生成 `operation_id`，同一操作在对话中保留一张卡片。

实现事实：

- Python `/sync-runs/{run_id}/stop` 不接收 `X-Operation-Id`。
- Java `SyncRunController.stop()` 不接收 operation id。
- 前端 `stopSyncRun()` 不发送 operation id。
- `SyncRunEventWriter` 始终使用原运行的 `operationId`，停止事件会并入最初的「开始同步」卡片。

影响：停止是一次独立用户命令，但无法在对话时间线中形成独立卡片。Agent 仍能从事件类型区分 `stopping/stopped`，但卡片级操作归组会失真。

证据位置：

- `backend/app/api/data_connections.py:245`
- `data-service/src/main/java/com/ontoweave/data/sync/api/SyncRunController.java:45`
- `frontend/src/shared/api/dataConnections.ts:111`
- `data-service/src/main/java/com/ontoweave/data/sync/execution/SyncRunEventWriter.java:31`
- 技术设计 §10.7、§11.3

### M5. 部署形态与设计不一致

设计要求 §9.10：一个项目级 Docker Compose 启 MySQL、Postgres、RabbitMQ、Flink、Java、Python、前端；Postgres 同一实例分 `app` / `data` schema。

实现事实：

- `data-service/compose.yml` 管 MySQL、Postgres、RabbitMQ、Flink、Java，不包含 Python 和前端。
- `backend/docker-compose.yml` 管 Python 和另一个 Postgres。
- Python 与 Java 使用不同 Postgres 容器。
- 当前 Python 表在 `public` schema，Java 表在 `data` schema，没有 `app` schema。
- `backend/.env.example` 的 RabbitMQ 端口是 `5672`，而 data-service Compose 映射到宿主机 `5673`，需要手工改配置才能连通。
- 前端仍通过 `npm run dev` 启动，没有 Compose service。

影响：本地开发指令分散，Python 事件消费、Java 数据和前端代理需要人工拼装；这不是单条业务链路的功能失败，但不满足设计中的一键启动与 schema 隔离。

证据位置：

- `data-service/compose.yml:1`
- `backend/docker-compose.yml:1`
- `backend/.env.example:47`
- 技术设计 §9.10

### M6. 创建连接失败时没有删除草稿会话，草稿事件覆盖也不完整

设计要求 §6.1：Python 先建 `kind=data_connection` 的对话草稿，Java 创建连接成功后再回填 `resource_id`；Java 失败则删除草稿。

实现事实：

- `create_connection()` 先创建会话，再调用 Java；没有 try/except 回滚。
- 会话创建时使用随机 `resource_id`，不是设计中的空值。
- 未发现 `connection.created` 或草稿创建事件；总体设计 §3.6 要求记录「连接草稿创建」。

影响：Java 不可用或创建失败时，会留下没有对应连接的孤立会话；对话事件链也缺少草稿创建节点。

证据位置：

- `backend/app/services/data_connection.py:24`
- 技术设计 §6.1；总体设计 §3.6

## 5. 低优先级与文档偏差

### L1. 数据集详情和列表展示不完整

设计要求 §11.2.4：

- 数据集列表显示名称、来源表、当前版本、行数、更新时间和最近同步状态。
- 详情显示来源连接与源表、当前版本、版本数量、行数、列数、创建与更新时间。
- 版本历史显示版本号、发布时间、来源运行、行数、列数。
- 预览支持「放大」弹窗。

实现事实：

- 数据集列表只有名称、当前版本/行数、更新时间。
- 详情只有当前版本、版本数量、行数、列数、文件大小、发布时间。
- 没有来源连接、来源表、创建时间、更新时间。
- 版本历史没有来源运行和列数。
- `frontend/src/features/data-connection/dialogs/` 为空，没有 `DatasetPreviewDialog`。

证据位置：

- `frontend/src/features/data-connection/components/context/DatasetList.tsx:23`
- `frontend/src/features/data-connection/components/context/DatasetDetail.tsx:91`
- `frontend/src/features/data-connection/components/context/DatasetDetail.tsx:123`
- 技术设计 §11.2.4

### L2. 左侧连接列表只对当前选中的连接显示最近同步

设计要求 §11.1：左侧连接列表显示每个连接的最近同步结果。

实现事实：`latestRun` 只在 `isActive` 为真时取 `selectedState.recent_runs[0]`，其他连接固定显示「暂无」。

证据位置：`frontend/src/features/data-connection/components/ConnectionListPane.tsx:67`

### L3. 事件命名和文档契约不一致

- 设计 §6.2 写 `sync.run.started`，实现使用 `sync.run.running`；§12.2 已记录 `running`，但 §6.2 未同步修正。
- 实现额外使用 `sync.run.stopping`，设计路由键未列出；这本身合理，但契约文档没有补充。
- 设计队列名是 `python.conversation-events`，示例配置是 `ontoweave.python.data-events`。

这类问题不是业务失败，但会让后续 Agent、监控和跨服务契约对不上。

### L4. 前端质量门禁不是全绿

- `npm run lint` 退出码为 0，但有 8 个 `react(set-state-in-effect)` 警告，集中在 `useDataConnectionState`、`useDataConnectionConversation`、`DatasetDetail`、`TableSchemaPanel`、`useDataConnectionList`、`DataConnectionWorkbench`、`TablePicker`。
- 前端没有针对数据连接组件的浏览器自动化测试。

## 6. 已验证通过的部分

以下内容有代码或运行态证据，可以作为 P1 的有效成果：

- **服务边界**：前端 API 只调用 Python；Java 内部接口统一 `/internal/v1`，带内部 token 和 owner 校验。
- **凭据安全**：AES-256-GCM 加密、密钥版本、AAD 绑定、密码不回显。
- **连接测试**：业务失败返回 200 + `failed`，配置变化重置 `untested`。
- **表发现**：只读 `BASE TABLE`，读取主键，无主键拒绝。
- **多表选择**：批量全有或全无；未选任务 `disabled`；有活跃运行时禁止取消选择。
- **同步链路**：snapshot / CDC、Flink job 名唯一、server-id 分配、临时 YAML 权限、Flink 状态跟踪。
- **失败诊断**：Flink 异常详情格式化，失败事件包含 `errorCode` 和 `errorMessage`。
- **停止流程**：点击后立即请求 Flink，Supervisor 5 秒巡检确认，savepoint 超时兜底 cancel。
- **版本发布**：Iceberg tag、不可变版本、当前版本指针、空表空 snapshot、`live=true` 实时预览。
- **事件链路**：Outbox + RabbitMQ + `event_id` 去重 + SSE + `after_event_id` 补拉；运行态已看到 `connection.updated`、`connection.tested`、`sync.tasks.saved`、`sync.run.queued/submitted/running/stopping/stopped/failed`、`dataset.version.published`。
- **Agent**：数据连接独立定义，4 个只读工具，每轮注入最近 10 条事件，不直接写业务数据。
- **前端关键修复**：失败详情默认折叠、展开最大高度 180px；预览固定最多 100 行；表选择支持搜索、多选、无主键置灰；数据集版本切换和实时预览可用。

## 7. 校验结果

### Python 后端

```text
446 passed, 1 warning
ruff check .                 通过
ruff format --check .        通过
mypy app                     通过（114 source files）
```

### 前端

```text
npx tsc -b                   通过
npm run lint                 退出码 0，8 个 set-state-in-effect 警告
npm test -- --run            11 files / 82 tests passed
npm run build                通过
```

### Java

```text
./mvnw test -Dtest='!*IntegrationTest'
39 tests, 1 failure, 3 errors
```

- 失败：`DatasetVersionPublisherTest.returnsExistingVersionAndRepairsPublishedMarker` 仍期望调用 `runs.save(run)`，而实现已改为 `runs.markSnapshotPublished(...)`。判断为测试过期，不是当前业务逻辑回归，但它仍然使 Java 单测门禁为红。
- 错误：`HadoopIcebergPreviewReaderTest` 3 个用例因本机 JDK 26 下 Hadoop `Subject.getSubject is not supported` 失败，属于运行环境兼容问题，不应归因于 P1 业务逻辑。
- 未完成 `./mvnw verify` 全量验证；Testcontainers / Ryuk 环境限制仍然存在。

### 运行态

检查时本机状态：

| 项目 | 结果 |
|---|---|
| Java health | `{"status":"UP"}` |
| Python `/health` | `{"status":"ok"}` |
| Flink | `1.20.3`；`slots-total=4`，`slots-available=4`，`jobs-running=0`，`jobs-finished=3`，`jobs-cancelled=1`，`jobs-failed=0` |
| Java `data` schema | 4 connections、4 sync tasks、34 sync runs、4 datasets、13 dataset versions |
| 运行状态分布 | 18 failed、15 stopped、1 succeeded |
| Python `public` schema | 1 个 `data_connection` 会话、125 条 conversation events |
| 停止运行 savepoint | 15 条 stopped 运行中 15 条 `savepoint_path` 为空 |
| 快照完成反例 | `af0165e4-aa33-49ef-84b6-1c2691a59786`：`snapshot_completed_at` 为空，但 `demo.orders` 已发布 v3、5012 行 |
| Iceberg 孤儿表 | 24 张表中 21 张没有被任何 `dataset_version` 引用 |

这些数据说明主链路实际跑通过，也同时暴露了 H1、H2、H3 的真实风险。

## 8. 建议处理顺序

1. **先修 H1**：给版本发布加 `snapshot_completed_at + checkpoint` 门槛，补「初始化快照中途停止」测试。
2. **再修 H2**：数据库部分唯一索引 + 连接锁 + 全局并发上限，补并发触发测试。
3. **修 H3**：失败/停止清理未被版本引用的 Iceberg 表，补三类生命周期测试。
4. **补 M1**：决定是接受行数估算替代方案，还是改为 Flink 指标，并补 `sync.run.snapshot_completed` 事件。
5. **补 M2/M3/M4**：版本分页接口、数据集来源字段、savepoint 路径、停止 operation id。
6. **收敛 M5/M6**：项目级 Compose、同实例 `app`/`data` schema、草稿回滚和草稿事件。
7. **最后补 L1/L2 和测试门禁**：数据集详情、连接列表最近同步、放大预览、前端组件测试、Java 过期测试。

## 9. 对 P1 状态的建议

不建议继续用无条件的「✅ 已完成」表示 P1。更准确的表述是：

> **P1 核心完成、有条件通过。** 主链路可用，可进入 P2 设计；但 H1（停止时可能发布不完整版本）、H2（并发控制缺失）、H3（孤儿表清理缺失）应作为 P1 的收尾缺陷优先修复，不能带入 P2/P3。

如果团队接受「P1 只要求主链路可用，上述三项后置」，则需要在 `ROADMAP.md` 和 `phases/p1-data-ingestion.md` 中把「已完成」改为「核心完成、有条件通过」，并明确三项缺陷的所属阶段和验收条件；否则当前状态与设计稿的验收口径不一致。

## 10. 检查限制

- 本次没有执行真实的「初始化快照中途停止」新实验，H1 结论来自代码路径和已有运行态反例。
- 本次没有重新跑 `./mvnw verify` 全量集成测试；Java 结果仅覆盖排除 `*IntegrationTest` 后的单测。
- 本次没有做浏览器端逐屏人工验收；前端结论来自组件代码、单元测试和构建结果。
- 运行态数据来自本机 Docker 和本地 Python 进程，不代表生产环境；但用于验证代码路径已经足够。
