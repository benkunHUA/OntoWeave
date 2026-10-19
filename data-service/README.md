# OntoWeave Java Data Service

数据连接与同步领域的独立 Java 服务：连接配置、加密凭据、MySQL 连接测试、binlog/复制能力检查、表发现、Flink CDC 同步、Iceberg 数据集版本和内部 REST。前端不直接调用本服务；Python 通过 `/internal/v1/**` 访问。

## 技术栈

- Java 17 target、Spring Boot 3.5.x、Maven Wrapper
- PostgreSQL `data` schema、Flyway、Spring Data JPA
- MySQL Connector/J
- RabbitMQ Outbox 发布
- Flink 1.20.3 + Flink CDC 3.7.0 + Iceberg 1.10.1（Hadoop catalog）
- JUnit 5、Testcontainers

## 本地启动

```bash
cd data-service
docker compose -f compose.yml up -d --build
curl -H 'X-Internal-Token: dev-internal-token' http://127.0.0.1:18001/actuator/health
```

Compose 会启动：

- PostgreSQL `127.0.0.1:15432`（库 `ontoweave`，Flyway 自动建 `data` schema）
- MySQL `127.0.0.1:13306`（库 `demo`，已开启 ROW binlog）
- RabbitMQ `127.0.0.1:5673`，管理端 `http://127.0.0.1:15673`
- Flink JobManager `127.0.0.1:18081`，TaskManager 与共享 checkpoint/savepoint 卷
- Java 服务 `127.0.0.1:18001`

Iceberg 数据写在 Compose 卷 `data-service_iceberg-warehouse`，Java 服务和 Flink 共享该卷。停止接口会立即向 Flink 发起 stop-with-savepoint（CDC）或 cancel（快照）；Supervisor 只做后续状态确认、失败重试，并在 savepoint 超过 `SYNC_STOP_SAVEPOINT_TIMEOUT_SECONDS`（默认 30 秒）未完成时兜底 cancel。第一版不做 savepoint 路径回填和恢复。

## 测试

```bash
./mvnw test
./mvnw verify
```

测试使用 Testcontainers 启动 PostgreSQL 与 MySQL。镜像仓库受限时，可先把本地已有的 `pgvector/pgvector:pg16` 和 `mysql:8.0` 配好；无 Ryuk 环境可加 `TESTCONTAINERS_RYUK_DISABLED=true`。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DATA_DATABASE_URL` | `jdbc:postgresql://localhost:5432/ontoweave` | PostgreSQL JDBC URL |
| `DATA_DATABASE_USER` / `DATA_DATABASE_PASSWORD` | `ontoweave` | `data` schema 账号 |
| `ONTOWEAVE_INTERNAL_TOKEN` | `dev-internal-token` | Python 调 Java 的内部令牌，生产必须替换 |
| `ONTOWEAVE_SECRET_KEY` | 全零 32 字节 Base64 | AES-256-GCM 密钥，生产必须替换 |
| `ONTOWEAVE_SECRET_KEY_VERSION` | `1` | 当前密钥版本 |
| `ONTOWEAVE_OUTBOX_ENABLED` | `true` | 是否启动 RabbitMQ Outbox 发布器 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 地址 |
| `FLINK_REST_URL` | `http://localhost:8081` | Flink REST 地址 |
| `FLINK_HOME` / `FLINK_CDC_HOME` | `/opt/flink` / `/opt/flink-cdc-3.7.0-1.20` | Flink 与 CDC CLI 目录 |
| `ICEBERG_WAREHOUSE` | `/data/iceberg/warehouse` | Iceberg warehouse 共享卷 |
| `FLINK_CHECKPOINT_DIR` / `FLINK_SAVEPOINT_DIR` | `file:///data/flink/...` | Flink checkpoint/savepoint |
| `SYNC_DEFAULT_VERSION_INTERVAL_SECONDS` | `86400` | CDC 默认版本发布间隔 |
| `SYNC_CHECKPOINT_INTERVAL_SECONDS` | `300` | Flink checkpoint 间隔 |

密钥轮换：保留旧版本的 `keys.<version>` 配置用于解密，写入统一使用 `current-version`。

## 内部接口

所有接口要求 `X-Internal-Token`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/v1/data-connections` | 创建连接；`conversation_id` 幂等 |
| GET | `/internal/v1/data-connections?ownerId=&limit=&offset=` | 列表 |
| GET | `/internal/v1/data-connections/{id}?ownerId=` | 详情，`ownerId` 必填 |
| PATCH | `/internal/v1/data-connections/{id}?ownerId=` | 修改名称/config/secret，`ownerId` 必填 |
| POST | `/internal/v1/data-connections/{id}/test?ownerId=` | 连接测试，200 + success/failed |
| GET | `/internal/v1/data-connections/{id}/tables?ownerId=` | 表列表 |
| GET | `/internal/v1/data-connections/{id}/tables/{schema}/{table}?ownerId=` | 字段与主键 |
| GET | `/internal/v1/data-connections/{id}/state?ownerId=` | Agent/前端状态摘要 |
| POST | `/internal/v1/data-connections/{id}/sync-tasks?ownerId=` | 批量创建/复用同步任务 |
| GET | `/internal/v1/data-connections/{id}/sync-tasks?ownerId=` | 任务列表 |
| POST | `/internal/v1/data-connections/{id}/sync-runs?ownerId=` | 触发一轮同步，返回 `operationId + runs` |
| GET | `/internal/v1/sync-runs/{id}?ownerId=` | 运行状态、Flink 与 Iceberg 进度 |
| POST | `/internal/v1/sync-runs/{id}/stop?ownerId=` | 停止运行 |
| GET | `/internal/v1/datasets/{id}?ownerId=` | 数据集与版本历史 |

同一 `sync_task` 同时只允许一个活动运行（`queued/preparing/submitted/running/stopping`）。
重复触发返回 `409 SYNC_RUN_ACTIVE`，必须先停止当前运行；运行中也不能把该表从同步任务里取消选择。
运行失败时 `errorMessage` 会带上 Flink `/jobs/{id}/exceptions` 的根因、失败任务和堆栈摘要，供 Agent 排查。

创建示例：

```bash
curl -sS -X POST http://127.0.0.1:18001/internal/v1/data-connections \
  -H 'X-Internal-Token: dev-internal-token' \
  -H 'Content-Type: application/json' \
  -d '{
    "ownerId": "00000000-0000-0000-0000-000000000001",
    "conversationId": "00000000-0000-0000-0000-000000000002",
    "name": "本地 demo",
    "kind": "mysql",
    "config": {"host":"mysql","port":3306,"database":"demo","username":"ontoweave"},
    "secret": {"password":"ontoweave"}
  }'
```

响应永不返回 `secret`，只返回 `secretConfigured`。

## 同步链路

`sync-tasks` 只接受有主键的普通表；`syncMode` 支持 `snapshot` 和 `cdc`。一条源表对应一条任务和一个数据集，每次运行创建新的 Iceberg 表，避免全量重跑污染旧版本。

触发示例：

```bash
curl -sS -X POST \
  'http://127.0.0.1:18001/internal/v1/data-connections/<connection-id>/sync-runs?ownerId=<owner-id>' \
  -H 'X-Internal-Token: dev-internal-token' \
  -H 'Content-Type: application/json' \
  -d '{
    "operationId":"00000000-0000-0000-0000-000000000003",
    "tables":[{"schema":"demo","table":"orders"}]
  }'
```

运行状态依次经过 `queued → preparing → submitted → running → succeeded/stopped/failed`。CDC 在初始化快照完成后按 `versionIntervalSeconds` 发布 `scheduled` 版本；正常停止补一个 `stop` 版本。

已知限制：

- 仅支持 MySQL；其他数据库连接器后续扩展。
- `rowCountEstimated=true` 的 CDC 版本，行数来自 Iceberg snapshot summary 的物理记录数，存在更新/删除时可能偏大，不是精确逻辑行数。
- 旧 Iceberg 表、孤儿文件和失败运行产生的临时表尚未清理。
- 第一版不执行 savepoint 恢复，也不做多节点部署。

## Docker 构建说明

容器内 Maven 使用 `docker/maven-settings.xml` 的阿里云镜像。构建镜像使用 JDK 21 编译、产物 target Java 17，运行在 `flink:1.20.3-scala_2.12-java17` 上。若本机无法访问 Docker Hub，可先从可用镜像源拉取 `maven:3.9.9-eclipse-temurin-21`、`flink:1.20.3-scala_2.12-java17`、`rabbitmq:3.13-management` 并打上同名本地标签。

默认构建会从 Apache 归档下载 Flink CDC 包；网络受限时可先把相同文件放到本地 HTTP 服务，再覆盖构建参数：

```bash
docker compose build --build-arg FLINK_CDC_TARBALL_URL=http://host.docker.internal:8899/flink-cdc-dist.tar.gz data-service
```
