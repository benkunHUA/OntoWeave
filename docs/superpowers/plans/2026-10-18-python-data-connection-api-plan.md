# 数据连接与同步 · 第四阶段：Python API、事件与 Agent

- 日期：2026-10-18
- 状态：已完成
- 上游设计：`docs/superpowers/specs/2026-10-17-data-connection-sync-technical-design.md` 第 10 节
- 目标：把 Java 数据服务接进 Python 对前端的 API，并把连接 / 同步状态变成对话事件与只读 Agent 能力，打通最小闭环。

## 1. 交付内容

| 模块 | 文件 | 职责 |
|---|---|---|
| 事件模型 | `backend/app/models.py` | `ConversationEvent`：事件卡片，按 `event_id` 幂等 |
| 迁移 | `backend/alembic/versions/0019_conversation_event.py` | 建表、唯一约束、`(conversation_id, created_at, id)` 索引 |
| 事件服务 | `backend/app/services/conversation_event.py` | Outbox 信封 → 会话事件；游标查询；幂等 |
| 事件总线 | `backend/app/services/event_bus.py` | 同进程唤醒 SSE；数据库仍是正确性来源 |
| SSE | `backend/app/services/conversation_event_stream.py` | 历史补发 + 增量推送 + 心跳 |
| Java 客户端 | `backend/app/services/data_service/client.py` | 内部 token、ownerId、超时、错误码映射 |
| DTO 映射 | `backend/app/services/data_service/mapping.py` | camelCase → snake_case，剥离 Iceberg 物理字段 |
| 数据连接编排 | `backend/app/services/data_connection.py` | 建会话、连接 CRUD、测试、表发现、同步、数据集 |
| REST | `backend/app/api/data_connections.py` | `/api/v1/data-connections`、`/sync-runs`、`/datasets` |
| 事件 REST/SSE | `backend/app/api/conversation_events.py` | `/conversations/{id}/events`、`/events/stream` |
| MQ 消费 | `backend/app/messaging/consumer.py` | 消费 `ontoweave.data.events`，失败重连 / 重投 |
| Agent 定义 | `backend/app/agent/definitions/data_connection.py` | 动态提示词、只读工具、状态更新 |
| Agent 工具 | `backend/app/agent/tools/data_connection/` | 状态、事件、表列表、表结构 + 共享 `ask_user` |
| 共享工具 | `backend/app/agent/tools/common.py` | 建模与数据连接共用提问中断 |

## 2. 关键约定

- 前端只调 Python；Python 不读写 Java 的 `data` schema，统一走 `/internal/v1`。
- 数据连接会话：`Conversation.kind = data_connection`，`resource_id = data_connection.id`。
- 事件落库字段：`event_id`（Java Outbox 幂等键）、`operation_id`、`run_id`、`event_type`、
  `summary`、`payload`；增量游标按 `(created_at, id)`，不能直接比 UUID。
- Agent 只读：`get_data_connection_state`、`get_connection_events`、`list_source_tables`、
  `get_source_table_schema`；保存、测试、选表、同步、停止都由右侧表单/按钮触发。
- 事件不写入 LangGraph checkpointer；每轮提示词重新注入当前状态与最近 10 条事件。
- Java 不可用 → `503`；Java `400/404/409` → Python `400/404/409`。

## 3. 与设计稿的偏差

- Java 目前没有数据集版本分页与预览接口，Python 暂不暴露 `/versions`、`/preview`；
  数据集详情仍返回当前版本和全部版本。
- Java 目前没有“未选中的同步任务置为 disabled”的端点，Python 只提交本次选中的表；
  停用能力留到后续。
- Java 目前只发 `connection.tested`、`sync.run.queued/submitted/failed` 和
  `dataset.version.published`；运行中的 `running` / `succeeded` / `stopped` 还没有独立事件，
  前端阶段如需完整时间线，要在 Java 侧补发。
- `AgentDefinition.build_state_update` 增加 `settings` 参数，用于数据连接定义构造 Java 客户端；
  建模定义行为不变。

## 4. 验证

```bash
cd backend
.venv/bin/python -m pytest -q
.venv/bin/ruff check .
.venv/bin/ruff format --check .
.venv/bin/mypy app
```

结果：全量 `443 passed`；ruff、format、mypy 全部通过。

真实闭环冒烟（本地 Docker）：

- Python `TestClient` → Java `18001` → MySQL `demo` → Flink CDC snapshot → Iceberg。
- 创建连接、连接测试、表发现、表结构、保存 snapshot 任务、触发同步均成功。
- 运行最终 `succeeded`，数据集 `demo.customers` 发布 v1、`row_count=2`。
- RabbitMQ 事件按顺序落库：`connection.tested` → `sync.run.queued` → `sync.run.submitted`
  → `dataset.version.published`。
- 同一 `event_id` 重复投递只落一条，验证了消费幂等。

## 5. 前端阶段与契约修正（2026-10-18）

第四阶段交付时列出的三项偏差已经处理：

- 连接测试成功后 Java 将连接状态切为 `active`。
- Java 补齐 `sync.run.running/succeeded/stopped`，并让 supervisor 的失败路径也发 `sync.run.failed`。
- `connection.updated`、`connection.tested`、`sync.tasks.saved` 增加 `operation_id`；
  数据集版本事件同时携带触发运行的 `operation_id`。
- `POST /sync-tasks` 改为替换语义，未选中的任务置为 `disabled`；重新选中恢复 `enabled`。

数据集行级预览已补齐：Java 新增 `/internal/v1/datasets/{id}/preview`，按版本对应的
Iceberg 表和 snapshot 读取，支持 `version_id` 与 `limit`（1–500）；Python 透传
`GET /api/v1/datasets/{id}/preview`；前端数据集详情会随版本切换加载预览，最多展示 100 行。
真实冒烟验证了 2 行数据、版本过滤、截断标记和非法 limit 的 `400` 响应。

前端交付与验证见设计稿 12.2 节。当前前端校验结果：78 tests、tsc、lint、build 全部通过。
