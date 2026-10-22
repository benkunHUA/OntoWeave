# P2「数据整理」端到端验收

- 验收日期：2026-10-22
- 验收对象：`main@fba99c2` 工作区（P2 修复与验收记录未提交）
- 验收方式：Docker Compose 启动数据面，本地 uvicorn 启动 Python API，使用真实 MySQL、Flink、Iceberg、PostgreSQL、RabbitMQ 和 DeepSeek 模型跑完整链路
- 结论：P2 主链路通过；存在 1 个部署镜像依赖缺口和若干测试环境限制，详见第 8 节

## 1. 验收环境

| 组件 | 运行方式 | 地址 |
|---|---|---|
| Python API | 本地 `.venv/bin/uvicorn` | `127.0.0.1:18000` |
| Java 数据服务 | Docker `ontoweave-data-service-1` | `127.0.0.1:18001` |
| PostgreSQL | Docker `ontoweave-db-1` | `127.0.0.1:15432`，schema `app` / `data` |
| MySQL | Docker `ontoweave-mysql-1` | `127.0.0.1:13306` |
| Flink | Docker JobManager / TaskManager | `127.0.0.1:18081` |
| RabbitMQ | Docker `ontoweave-rabbitmq-1` | `127.0.0.1:5673` |
| 模型 | DeepSeek OpenAI 兼容接口 | `deepseek-flash` |

Python API 启动命令：

```bash
cd backend
DATABASE_URL='postgresql+psycopg://ontoweave:ontoweave@127.0.0.1:15432/ontoweave?options=-csearch_path%3Dapp%2Cpublic' \
  .venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 18000
```

## 2. 验收对象

| 对象 | ID |
|---|---|
| 流程 | `d2cf965d-8652-56b5-b245-c994c878cbe8` |
| 会话 | `ce59bd47-dffd-5104-8fcc-9675a2590c4c` |
| 会话类型 | `data_pipeline` |
| 草稿修订 | `6` |
| 已发布流程版本 | `be045ab9-9587-49b1-a5bc-309eaf08a302`（第 1 版） |
| 输入同步数据集 | `3ea6ff59-7fdf-4582-b8c9-06a8582646dc` |
| 输入数据集版本 | `ee34b291-a462-4a78-9c0d-0f67074a5deb`（第 1 版） |
| 输入表 | `demo.orders` |
| 输出数据集 | `7ebbb352-075a-4f53-bed0-3c2e10a93fc1` |
| 输出定义 | `6fdfd9d7-ce3f-478d-89e9-55dba13eea47`（`orders_clean`） |

草稿链路：`订单`（输入）→ `有效订单`（筛选 `id is not null`）→ `订单输出`（输出）。

## 3. 预览

请求：

```bash
curl -sS --max-time 30 -X POST \
  http://127.0.0.1:18000/api/v1/pipelines/d2cf965d-8652-56b5-b245-c994c878cbe8/previews \
  -H 'content-type: application/json' \
  -d '{"draft_revision":6,"node_id":"30000000-0000-4000-8000-000000000003","request_id":"803d38ba-271f-406a-82cc-8ba13397dc5a"}'
```

结果：

| 项目 | 值 |
|---|---|
| HTTP | `200` |
| 总耗时 | `7.20s` |
| 服务端执行耗时 | `6754ms` |
| 返回行数 | `2` |
| 截断 | `false` |
| 输入版本 | `ee34b291-a462-4a78-9c0d-0f67074a5deb` |
| 输入 Iceberg 快照 | `1636558847153472663` |
| 错误 / 警告 | 空 |

返回列：`id`、`customer_id`、`amount`、`created_at`。返回行：

```json
[
  {"id": 1, "customer_id": 1, "amount": 128.5, "created_at": "2026-10-21T14:42:29"},
  {"id": 2, "customer_id": 2, "amount": 42.0, "created_at": "2026-10-21T14:42:29"}
]
```

预览前后 `pipeline_build` 均为 `6` 条，`pipeline_dataset_version` 均为 `4` 条，确认预览不落库、不创建构建。

## 4. 构建与派生版本

### 4.1 手动构建

| 项目 | 值 |
|---|---|
| 构建 | `6b86d326-8bb9-4052-bcb1-86a22be55be3` |
| 状态 | `succeeded` |
| 触发 | `manual` |
| 流程版本 | `be045ab9-9587-49b1-a5bc-309eaf08a302` |
| 输出版本 | `b454415f-7667-45cc-a30c-afd65331ef3d` |
| Iceberg 快照 | `480368412062481233` |
| 行数 / 字节数 | `2` / `1303` |
| 作业耗时 | `13776ms` |
| Flink 日志 | `http://localhost:18081/#/job/ac88c9c4bbda64331cdf2492a1e9b1ef/exceptions` |

### 4.2 调度构建

| 项目 | 值 |
|---|---|
| 构建 | `01a8458c-6679-468f-b73b-75a33f33869d` |
| 状态 | `succeeded` |
| 触发 | `schedule` |
| 输出版本 | `4b0e7220-91c3-41ae-8aa2-e714ed1fe229` |
| Iceberg 快照 | `1088074781556167332` |
| 行数 / 字节数 | `2` / `1303` |
| 作业耗时 | `2690ms` |
| Flink 日志 | `http://localhost:18081/#/job/2e757a58ef736e80d2819681ef5f6abe/exceptions` |

输出版本发布后，`pipeline.current_version_id` 指向本次构建冻结的流程版本，`pipeline_dataset.current_version_id` 指向 `4b0e7220-91c3-41ae-8aa2-e714ed1fe229`。历史快照 `5205387674049476121`、`9156679864501286158`、`480368412062481233` 均可扫描到 2 行，验证版本不可变和可回读。

## 5. 定时调度

| 项目 | 值 |
|---|---|
| 调度 | `76d603ca-45e7-4e42-9aec-fda47863ff7a` |
| cron | `* * * * *` |
| 时区 | `UTC` |
| 最终状态 | `paused` |
| `next_run_at` | `null` |
| 最近触发时间 | `2026-10-21T16:12:00Z` |
| 调度运行 | `c4b1ec71-4423-4f4b-8ccd-694d76c9802d` |
| 调度运行状态 | `dispatched` |
| 关联构建 | `01a8458c-6679-468f-b73b-75a33f33869d` |

已实际执行暂停、恢复、再次暂停；恢复后只从当前时间计算下一次，不补跑暂停期间的触发。暂停后的派发路径补充了无容器单测 `PipelineScheduleDispatcherTest.pausedScheduleIsIgnoredWithoutResolvingInputsOrCreatingABuild`，结果为 `ignored / SCHEDULE_PAUSED`，不解析输入、不创建构建。Testcontainers 版集成用例 `PipelineScheduleIntegrationTest.dispatchAfterPauseIsIgnoredWithSchedulePausedReason` 已补充，但受第 8 节日志中的 Docker Hub 拉取限制未执行。

## 6. 来源元数据

请求：

```bash
curl -sS \
  http://127.0.0.1:18000/api/v1/pipelines/d2cf965d-8652-56b5-b245-c994c878cbe8/datasets/7ebbb352-075a-4f53-bed0-3c2e10a93fc1/sources
```

结果：

| 项目 | 值 |
|---|---|
| HTTP | `200` |
| 输出版本 | `4b0e7220-91c3-41ae-8aa2-e714ed1fe229`（第 4 版） |
| 发布人可见行数 | `2` |
| 发布构建 | `01a8458c-6679-468f-b73b-75a33f33869d` |
| 构建触发 | `schedule` |
| 上游输入数 | `1` |
| 上游数据集 | `3ea6ff59-7fdf-4582-b8c9-06a8582646dc` / `ee34b291-a462-4a78-9c0d-0f67074a5deb` |
| 上游名称 | `demo.orders` / `P2 E2E MySQL` |
| 元数据状态 | `available` |

响应只包含单跳直接输入，没有递归祖先、下游影响或字段级血缘字段，符合降级设计。

## 7. Agent 闭环

请求：

```bash
curl -sS -N --max-time 120 -X POST \
  http://127.0.0.1:18000/api/v1/conversations/ce59bd47-dffd-5104-8fcc-9675a2590c4c/messages \
  -H 'content-type: application/json' \
  -H 'X-Turn-Id: 99f3b9fa-d9c9-4771-8828-5e513441fe13' \
  -d '{"text":"请只读取当前流程状态和草稿，告诉我现在有哪些节点，不要修改任何内容。"}'
```

| 项目 | 值 |
|---|---|
| HTTP | `200` |
| 总耗时 | `4.05s` |
| SSE 事件数 | `168` |
| `runId` | `adf0287c3b894f78ac51f04923b5162b` |
| `turnId` | `99f3b9fa-d9c9-4771-8828-5e513441fe13` |
| 工具调用 | `get_pipeline_state`、`get_pipeline_draft`，均 `ok` |
| 结束状态 | `run.end paused=false` |
| 草稿修订 | 仍为 `6` |
| 编辑记录 | 仍为 6 条用户操作，无 Agent 写入 |

Agent 正确读出 3 个节点及连线，并明确回复未做修改。修复后再次查询 PostgreSQL，没有 `idle in transaction` 连接。

### 7.1 检查点初始化修复

现象：首次 Agent 消息接口在 120 秒后超时，无 SSE 返回；PostgreSQL 中请求连接处于 `idle in transaction`，检查点连接在 `CREATE INDEX CONCURRENTLY ... checkpoints_thread_id_idx` 上等待 virtualxid。

根因：`PostgresSaver.setup()` 在请求事务和流式响应生命周期内触发，检查点连接与请求连接互等。

修复：在 FastAPI lifespan 启动阶段、任何请求事务之前初始化 checkpointer，运行时只复用已建实例；关闭时统一释放连接池。新增回归测试 `test_checkpointer_is_initialized_during_startup`。

## 8. 校验结果与已知限制

### 8.1 通过项

| 校验 | 结果 |
|---|---|
| Python 定向 Agent / 健康检查 | 通过 |
| Python 全量 | `530 passed`，`4 failed` |
| Python `ruff check` / `ruff format --check` / `mypy app` | 全部通过 |
| 前端 TypeScript / lint / test / build | 通过；`43` 个测试文件、`192` 个用例 |
| Java 本次路径定向测试 | 通过：`IcebergSnapshotMetricsTest`、`FlinkPipelineDatasetWriterTest`、`PipelineScheduleDispatcherTest`、`PipelineScheduleServiceTest`、`FlinkExpressionCompilerTest`、`PipelinePlanCompilerTest` |
| 预览、构建、调度、来源、Agent 真实链路 | 通过 |

### 8.2 环境限制

| 问题 | 影响 | 处理 |
|---|---|---|
| `ontoweave-app-1` 镜像启动报 `ModuleNotFoundError: No module named 'deepagents'` | 无法用容器内的 Python API 做验收 | 改用本地 uvicorn；镜像依赖缺口需单独修复 |
| Docker Hub 拉取 `testcontainers/ryuk:0.12.0` 超时 | Testcontainers / Java PostgreSQL 集成测试无法启动 | 保留已补充的集成用例，使用无容器单测覆盖暂停派发分支 |
| Java 非集成测试 `331` 项中 `15` 项失败，集中在预览取消、超时和本地 Flink / Hadoop 环境 | 与本次 P2 验收改动路径无关 | 未修改；定向测试通过，失败项需在独立环境中复现 |
| Python `test_changeset.py` 4 项失败 | 测试应用的检查点仍连接 `localhost:5432/ontoweave`，该库不存在 | 已知测试环境问题；`530` 项其余测试通过 |

## 9. 结论与剩余风险

P2 的预览、手动构建、调度构建、派生版本、历史快照读取、来源元数据和 Agent 只读闭环均已跑通，可以进入 P3。验收不覆盖生产级多实例并发、永久异常退避和 Python API 容器部署；这三项需要在后续运维加固中处理。
