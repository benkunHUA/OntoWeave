# P3「变成对象」后端验收与性能证据

- 验收日期：2026-10-24
- 验收基线：`be67eaf`（W1 已合入）；验收发现的生产缺陷在 `0bf23cf` 修复后复测
- 验收分支：`codex/p3-w8-acceptance`
- 结论：后端功能验收通过；验收中发现的 2 项生产缺陷已修复并复测，1 项性能风险已通过索引迁移缓解；性能证据为有条件通过（20 万行降级完成，50 万行尝试未完成）

## 1. 验收范围与方法

本轮覆盖对象索引主链路：确定性 Iceberg 数据源、Testcontainers PostgreSQL、真实 Flyway 迁移、对象生成、版本切换、查询、外键关系、多对多关系和失败保留旧版本。查询直接调用 Java 服务层，不经过 HTTP 网关。

- 功能数据：2 万订单、2,000 客户、2,000 商品、2 万连接行。订单 v2 为 20,001 行，用于验证新增一行后的重建。
- 失败数据：订单 v2 的最后一个主键与倒数第二个主键重复，触发 `STAGE_VALIDATION_FAILED`。
- 性能数据：20 万行、8 个属性，批大小 1k / 10k / 50k。
- Iceberg 读取使用现有 `IcebergSnapshotReader` mock，数据由 `P3SnapshotData` 惰性生成；PostgreSQL 使用 `pgvector/pgvector:pg16` 容器。
- 重负载测试默认跳过，只有显式传入 `-Dp3.acceptance=true` 或 `-Dp3.performance=true` 才执行。
- 首次功能验收发现外键旧版本不退役；修复后重新执行全量功能验收，并新增“仅 1 个 ACTIVE 外键版本”断言。

## 2. 验收环境

| 项目 | 值 |
|---|---|
| 日期时区 | Asia/Shanghai |
| 主机 | macOS Darwin 24.6.0 x86_64，16 GiB 内存 |
| JDK | OpenJDK 21.0.12.1 |
| Docker | 29.8.2，Docker Desktop 容器内存上限 7,936 MiB |
| Testcontainers | 1.21.4 |
| PostgreSQL | 16.15 |
| PostgreSQL 镜像 | `pgvector/pgvector:pg16` |
| Spring Boot | 3.5.16 |
| 性能 JVM | `-Xmx2g` |

## 3. 可重复命令

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"

cd /Users/haobenkun/.codex/worktrees/p3-w8-acceptance/OntoWeave

# 功能验收：2 万订单 / 2,000 客户 / 2,000 商品 / 2 万连接行
./scripts/p3-acceptance.sh functional

# 性能验收：显式降级到 20 万行（默认入口为 500,000）
P3_PERFORMANCE_ROWS=200000 ./scripts/p3-acceptance.sh performance

# 清理本轮 local 输出；Testcontainers 数据库随测试 JVM 结束删除
./scripts/p3-acceptance.sh cleanup
```

脚本输出包含 `commit`、分支、JDK、OS、PostgreSQL 镜像、Docker 版本、开始/结束时间、耗时和 SQL 计数。功能测试的 SQL 计数由 `P3_FUNCTIONAL_COUNTS` 一行输出；性能测试的完整结果写入 `data-service/target/p3-acceptance/performance-summary.json`，并在标准输出打印 `P3_PERF`。

## 4. 功能验收结果

命令：

```bash
./scripts/p3-acceptance.sh functional
```

结果：`P3FunctionalAcceptanceTest`，`6 tests, 0 failures, 0 errors, 0 skipped`，修复后 Surefire 耗时 141.0 秒，脚本总耗时 159 秒。

| 测试方法 | 覆盖点 | 结果 |
|---|---|---|
| `generatesObjectsAndAtomicallySwitchesCurrentVersion` | v1 20,000 行、v2 20,001 行、当前版本原子切换、旧版本 `RETIRED` | 通过 |
| `listsPagesAndSearchesPrimaryKeyAndTitle` | 总数、第 1/2 页、主键搜索、标题大小写不敏感搜索 | 通过 |
| `appliesRepresentativeFiltersAndThreeConditionAnd` | text/enum/number/date/boolean 代表操作符、判空、三条件 AND | 通过 |
| `returnsObjectDetailInFrozenMappingOrder` | 详情字段顺序、冻结映射值、数值与空值 | 通过 |
| `queriesForeignKeyAndManyToManyRelationsInBothDirections` | 外键双向、多对多双向、关系实例分页与总数 | 通过 |
| `failedRebuildKeepsPreviousVersionQueryableAndRecordsSamples` | 重复主键失败、错误码/样例、stage 清理、旧版本仍可查 | 通过 |

### 4.1 SQL 计数

功能测试结束时实际输出：

```text
P3_FUNCTIONAL_COUNTS orderV1=20000 orderV2=20001 customers=2000 products=2000
fkInstancesAllVersions=40001 m2mInstances=20000 fkActiveVersions=1
fkInstancesCurrent=20001 m2mInstancesCurrent=20000
orderValuesV1=158000 orderValuesV2=158008
failedRun=9f246ebe-d3b6-48c0-b746-960ebc4ed61e failedCode=STAGE_VALIDATION_FAILED
```

| 表/口径 | 数量 |
|---|---:|
| 订单 v1 实例 | 20,000 |
| 订单 v2 实例 | 20,001 |
| 订单 v1 派生值 | 158,000（20,000 × 8 − 2,000 个空 notes） |
| 订单 v2 派生值 | 158,008（20,001 × 8 − 2,000 个空 notes） |
| 客户实例 | 2,000 |
| 商品实例 | 2,000 |
| 外键关系实例，当前版本 | 20,001 |
| 外键关系实例，全部版本 | 40,001 |
| 外键关系 ACTIVE 版本 | 1 |
| 多对多关系实例，当前版本 | 20,000 |

`fkInstancesAllVersions=40001` 包含订单 v1 的旧关系实例：旧版本已置为 `RETIRED`，实例等待 5 分钟延迟清理；当前只有 1 个 `ACTIVE` 外键版本，不会再无界增长。详见第 7.1 节。

### 4.2 查询与关系断言

- 订单列表总数 20,001；第 1 页返回主键 `1..20`，第 2 页返回 `21..40`。
- 标题搜索 `order 00042` 返回主键 `42`；主键搜索 `19999` 返回标题 `Order 19999`。
- 文本 `eq`、`contains`、`is_null`、`is_not_null`；枚举 `eq` / `ne`；数值 `gte`；日期 `gte`；布尔 `eq` 均有断言。
- 三条件 AND：`status = PAID AND amount >= 250 AND is_priority = true`，总数与确定性生成器的 SQL 手算结果一致。
- 对象详情 `123`：8 个属性按冻结映射顺序返回，金额为 `123.25`，空值属性正确返回空。
- 外键 `placed_by`：订单 `1` → 客户 `1`；客户 `1` ← 订单 `1, 2001, ..., 20001` 共 11 条；当前关系实例总数 20,001。
- 多对多 `contains`：订单 `1` → 商品 `1, 1001`；商品 `1` ← 订单 `1, 1001, ..., 9001` 共 10 条；关系实例总数 20,000。

### 4.3 失败保留旧版本

失败 run：`9f246ebe-d3b6-48c0-b746-960ebc4ed61e`，错误码 `STAGE_VALIDATION_FAILED`，错误详情包含 `duplicate`。失败 run 没有生成对象版本，stage 也已清理；订单绑定仍指向 v2，列表总数和详情查询均正常。Testcontainers 容器在本轮结束后删除，因此 run id 不能在此后继续查询，但可用同一命令稳定复现。

## 5. 性能验收结果

### 5.1 50 万行尝试

先尝试：

```bash
P3_PERFORMANCE_ROWS=500000 ./scripts/p3-acceptance.sh performance
```

开始于 01:47:50。约 15 分钟后，1k 批次仍处于对象 promote 阶段：

- 对象实例已写入：500,000
- 主表派生值：1,676,000 / 3,950,000
- stage 剩余派生值：2,274,000
- 三批预计超过 60 分钟

按任务允许的降级规则中止，未把这次未完成尝试记为性能结果。以下为实际完成的 20 万行三批数据，未使用外推值或伪造数据。

### 5.2 20 万行三批结果

命令：

```bash
P3_PERFORMANCE_ROWS=200000 ./scripts/p3-acceptance.sh performance
```

结果：`P3PerformanceAcceptanceTest`，`1 test, 0 failures, 0 errors`；Surefire 耗时 660.0 秒，脚本总耗时 677 秒。

| batch | 总耗时 | 读取耗时 | 写入耗时 | 列表首屏 P95 | 三条件筛选 P95 | 实例行数 | 派生值行数 | 峰值堆 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 269.21 s | 0.39 s | 266.01 s | 36.20 ms | 2,558.93 ms | 200,000 | 1,580,000 | 233.7 MiB |
| 10,000 | 110.81 s | 0.23 s | 108.63 s | 41.91 ms | 2,639.74 ms | 200,000 | 1,580,000 | 400.0 MiB |
| 50,000 | 98.44 s | 0.26 s | 95.85 s | 37.45 ms | 2,759.98 ms | 200,000 | 1,580,000 | 648.0 MiB |

- 三条件筛选每次返回 16,666 行。
- 列表和筛选各测 23 次，丢弃前 3 次，使用 20 个样本计算 P95。
- 列表首屏为 `page=1, pageSize=20`；三条件为 `status = PAID`、`amount >= 250`、`is_priority = true`。
- 读取耗时只统计 mock `Record.next()`；写入耗时只统计对象 store writer，不包含 Iceberg 实际 I/O、关系构建和版本切换。

### 5.3 推荐批大小

本机、单 worker、2 GiB JVM 堆下推荐 **50,000**：三批中总耗时最低（98.44 秒），峰值堆 648.0 MiB，仍低于 2 GiB。若生产环境采用多 worker、较小堆或更严格的尾延迟约束，推荐 **10,000** 作为保守值：总耗时增加约 12.6%，峰值堆从约 648 MiB 降到 400 MiB。1,000 批在本次数据量下明显过慢，不建议作为默认值。

完整机器可读结果在 `data-service/target/p3-acceptance/performance-summary.json`，其中包含每个批次的 `runId` 与 `indexVersionId`。该文件是本地产物，不随代码提交。

## 6. 编译与聚焦测试

| 校验 | 结果 |
|---|---|
| `./mvnw -q -DskipTests compile` | 通过 |
| `./mvnw -q -DskipTests test-compile` | 通过 |
| `P3FunctionalAcceptanceTest` | 6/6 通过 |
| `P3PerformanceAcceptanceTest`（20 万降级） | 1/1 通过 |
| `OntologyObjectPipelineIntegrationTest` | 6/6 通过 |
| `OntologyRelationPipelineIntegrationTest` | 3/3 通过 |
| `OntologyIndexSwitchIntegrationTest`（含外键退役回归） | 3/3 通过 |
| `OntologyObjectQueryApiTest` | 5/5 通过 |
| `OntologyRelationQueryTest` | 6/6 通过 |

本轮没有运行 Java 全量 `mvn test`，也没有修复已知基线失败。新增功能测试默认受 JUnit condition 保护，不会进入普通测试反馈环。

## 7. 验收中发现并修复的问题

### 7.1 外键关系旧版本不会退役，实例表持续增长（已修复）

**修复前证据**：订单从 v1 切到 v2 后，外键关系当前查询返回 20,001 条，但 `ontology_relation_instance` 按 `relation_id` 统计为 40,001 条；两个关系版本都是 `ACTIVE`。

**根因**：

- `ForeignKeyRelationBuilder.java:88-103` 以 `bindingId = null` 物化 `FK` 关系版本。
- `IndexSwitcher.java:172-177` 只有在 `bindingId != null` 时才退役上一个关系版本。
- `VersionCleanupService.java:36` 只清理 `RETIRED` 版本。
- `JdbcRelationQuery.java:17-34` 查询时选择最新的 `ACTIVE` FK 版本，所以读数仍正确，但旧版本和实例永远不会被清理。

**影响**：修复前外键关系每次对象重建都会新增一整份关系版本和实例，数据库占用无界增长。查询正确性不受影响。

**修复**：`IndexSwitcher.activateRelation` 对 `bindingId = null` 的 FK 版本退役同 relation 下所有其他 `ACTIVE` 版本；旧实例仍由既有延迟清理任务删除。新增 `OntologyIndexSwitchIntegrationTest.objectSwitchRetiresThePreviousForeignKeyRelationVersion` 回归测试，功能验收新增 `fkActiveVersions=1` 断言。修复提交：`0bf23cf`。

### 7.2 多对多去重 SQL 缺少匹配索引（已修复）

`ManyToManyRelationBuilder.java:150-168` 的 `DELETE ... USING` 自连接依赖 `run_id + relation_id + from_primary_key + to_primary_key`。生产迁移 `V9__ontology_mapping.sql` 只在 relation stage 上建了 `ix_relation_stage_run (run_id)`。未加索引的首次 2 万连接行运行超过 3 分钟。

修复：新增生产迁移 `V10__ontology_index_performance.sql`：

```sql
CREATE INDEX ix_relation_stage_pair
ON data.ontology_relation_stage
    (run_id, relation_id, from_primary_key, to_primary_key);
```

验收夹具已移除专用索引，复测通过，确认生产迁移生效。修复提交：`0bf23cf`。

### 7.3 外键关系 JOIN 缺少对象值联合索引（已加索引，未单独 benchmark）

`ForeignKeyRelationBuilder.java:55-86` 以 `version_id + primary_key_value` 连接 `ontology_object_value`，但原主键是 `(version_id, attribute_id, primary_key_value)`，缺少以 `primary_key_value` 为第二列的路径。V10 新增：

```sql
CREATE INDEX ix_object_value_primary_key
ON data.ontology_object_value
    (version_id, primary_key_value, attribute_id);
```

功能链路已通过，但本轮没有单独用 `EXPLAIN (ANALYZE, BUFFERS)` 对比该索引的收益；仍建议在真实 20 万行以上外键重建场景补一次执行计划验证。

## 8. 未覆盖项与已知限制

- 前端建模页、对象工作台、画布和字段映射 UI：不在本报告范围；W2/W3 已合入并通过前端全量测试，但没有浏览器级截图或拖拽 E2E。
- Agent 建议映射、解释错误、确认链路、409 重读：未覆盖。
- P1 新版本自动入队、队列领取/恢复、取消、并发保存 409：未覆盖；本轮直接构造 run 并调用执行器。
- 换源、解绑、字段删除/改名后的 `NEEDS_REPAIR`：未覆盖。
- 旧版本 5 分钟后清理：未覆盖；测试只验证旧版本可查和失败不影响当前版本。
- HTTP 控制器、鉴权、租户隔离：未覆盖；查询测试走 Java service。
- 真实 Flink、Iceberg、MySQL、RabbitMQ 和 Python API：未覆盖；性能测试使用 mock reader，不能代表真实 Iceberg I/O。
- 性能测试只测对象类型索引，不测外键/多对多关系构建、并发 worker 或关系版本切换。
- 50 万行三批未完成；本报告只把 20 万行三批作为正式性能证据。
- 峰值堆是 `Runtime.totalMemory - freeMemory` 的 50 ms 采样近似值，不包含 PostgreSQL 容器内存。
- 性能测试只覆盖对象索引，不覆盖新增索引后的外键关系重建耗时。

## 9. 结论

P3 后端对象主链路的功能验收通过：2 万订单生成、原子切换、分页/总数/搜索、代表操作符、三条件 AND、详情、外键与多对多双向查询、失败保留旧版本均有可重复断言。外键版本泄漏和多对多去重索引缺失已在 `0bf23cf` 修复并复测；外键 JOIN 索引已随 V10 添加，但未单独 benchmark。性能在 20 万行降级数据上完成三批对比，推荐本机单 worker 使用 50k（内存受限时用 10k）；50 万行在本机本轮不可承受，已如实记录中止点。本轮结论为“功能通过、性能有条件通过”。

前端场景由 W2/W3 的前端全量测试覆盖；本报告不替代浏览器截图、Agent 确认链路或真实 Flink/Iceberg 端到端结果。
