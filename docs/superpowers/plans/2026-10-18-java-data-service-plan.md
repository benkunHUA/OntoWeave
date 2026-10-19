# 第二阶段：Java 数据服务（连接、测试、表发现）

> 本计划承接《数据连接与同步技术设计》第 5-8 节，是数据连接与同步总计划的第 2 个子计划。

**Goal:** 建立独立的 Java 数据服务，承担 `DataConnection` 领域、加密凭据、MySQL 连接测试、表发现和内部 REST；Python 只通过内部接口调用，不直接读写 `data` schema。

**Scope:**
- 本阶段完成：连接创建/查询/修改、凭据加密、MySQL 连接测试、binlog/复制能力检查、表列表与表字段、连接状态摘要、Outbox 事件。
- 本阶段不做：Flink CDC 作业提交、同步任务/运行状态机、Iceberg 读写、Python 数据连接 API 与 Agent、前端工作台。

**Tech Stack:** Java 21（target）、Spring Boot 3.5.x、Maven、Spring Web、Spring Data JPA、Flyway、PostgreSQL、MySQL Connector/J、RabbitMQ/Spring AMQP、JUnit 5、Testcontainers。

## 实施状态（2026-10-18）

已完成本计划全部任务，代码位于 `data-service/`，已随提交 `9524589` 合并到 `main`。

- `./mvnw verify` 通过：13 tests，0 failures/errors/skips。
- Docker Compose 冒烟通过：健康检查、创建连接、连接测试、表列表、字段与主键读取。
- MySQL 8.0.46 上识别到 `ROW` binlog、`FULL` row image、复制权限可用。
- `connection.tested` 事件已写入 Outbox 并由 RabbitMQ 发布器标记为 `published`。
- 凭据只存 AES-GCM 密文；响应不返回 secret；容器日志未出现测试密码。
- 修复了三个冒烟发现的问题：`ownerId` 缺失可绕过归属校验、创建响应时间戳为空、初始化 SQL 中文二次编码。

## 关键决策

- 服务目录：`data-service/`，独立 Maven 项目，不混入 Python 包。
- 数据库：PostgreSQL `data` schema；Flyway 管理迁移，JPA 只操作 `data.*`。
- 连接类型可扩展：`kind` + JSONB `config`，类型专属校验器放在 `connection/config/`；第一版只实现 `mysql`。
- 凭据：AES-256-GCM，AAD 为 `connection_id + kind`；数据库只存密文与 `key_version`，响应只返回 `secretConfigured`。
- 内部接口：`/internal/v1/**`，`X-Internal-Token` 保护；错误体统一为 `{code,message,details,traceId}`。
- 连接测试失败是业务结果：HTTP 200 + `status=failed`；只有协议/服务错误使用 4xx/5xx。
- 表发现只查连接配置中指定 database 的 `BASE TABLE`，不缓存；无主键表由后续同步任务接口拒绝。
- 事件：Java 在领域变更同事务写 `data.outbox_event`；发布器投递到 RabbitMQ 的 `ontoweave.data.events`，路由键为事件类型。

## 数据表

- `data.data_connection`：公共字段 + `config jsonb` + `config_version` + 测试状态 + `conversation_id` 唯一。
- `data.data_connection_secret`：`connection_id` 主键、`ciphertext`、`key_version`。
- `data.outbox_event`：`id`、`aggregate_type`、`aggregate_id`、`event_type`、`payload jsonb`、`created_at`、`published_at`。

## 内部接口

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/internal/v1/data-connections` | 创建连接；`conversation_id` 幂等 |
| GET | `/internal/v1/data-connections?ownerId=&limit=&offset=` | 连接列表 |
| GET | `/internal/v1/data-connections/{id}?ownerId=` | 连接详情，`ownerId` 必填 |
| PATCH | `/internal/v1/data-connections/{id}?ownerId=` | 修改名称/config/secret，`ownerId` 必填 |
| POST | `/internal/v1/data-connections/{id}/test?ownerId=` | 连接测试，返回 200 + success/failed |
| GET | `/internal/v1/data-connections/{id}/tables?ownerId=` | 表列表 |
| GET | `/internal/v1/data-connections/{id}/tables/{schema}/{table}?ownerId=` | 表字段与主键 |
| GET | `/internal/v1/data-connections/{id}/state?ownerId=` | 供 Agent 的当前状态摘要 |

## Task 1：项目骨架与数据库

**Files:**
- Create: `data-service/pom.xml`
- Create: `data-service/mvnw`、`data-service/.mvn/wrapper/**`
- Create: `data-service/src/main/java/com/ontoweave/data/DataServiceApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V1__data_connection.sql`
- Create: `src/test/resources/application-test.yml`

- [x] 建立 Maven 项目，Java 21 target。
- [x] 配置 PostgreSQL `data` schema、Flyway、JPA、Jackson。
- [x] 建 `data_connection`、`data_connection_secret`、`outbox_event`。
- [x] 加内部 token 过滤器、请求 ID、统一错误处理。
- [x] 用 Testcontainers PostgreSQL 跑 Flyway + Spring Context。

## Task 2：连接 CRUD 与凭据加密

**Files:**
- Create: `connection/domain/DataConnectionEntity.java`
- Create: `connection/domain/DataConnectionSecretEntity.java`
- Create: `connection/repository/*.java`
- Create: `connection/api/DataConnectionController.java`
- Create: `connection/api/dto/*.java`
- Create: `connection/service/DataConnectionService.java`
- Create: `connection/config/MysqlConfigValidator.java`
- Create: `security/SecretCipher.java`、`security/AesGcmSecretCipher.java`

- [x] `kind` 只接受 `mysql`，config 使用 JSONB。
- [x] 创建按 `conversation_id` 幂等；`(owner_id,name)` 冲突返回 409。
- [x] 查询/修改校验 owner；secret 只写不读。
- [x] config/secret 变化时 `config_version + 1`、测试状态重置 `untested`；只改名称不重置。
- [x] 加密单测覆盖 AAD、错误密钥、密钥版本。

## Task 3：MySQL 连接测试

**Files:**
- Create: `connector/mysql/MysqlConnectionTester.java`
- Create: `connection/service/ConnectionTestService.java`
- Create: `connection/api/dto/ConnectionTestResponse.java`
- Create: `outbox/OutboxEventEntity.java`、`OutboxEventRepository.java`、`OutboxWriter.java`

- [x] 连接/读取超时各 5 秒。
- [x] 检查版本、`log_bin`、`binlog_format`、`binlog_row_image`、binlog 保留时间。
- [x] 检查 `REPLICATION CLIENT` 与 `REPLICATION SLAVE/REPLICA`；不返回授权原文。
- [x] 错误映射 `AUTH_FAILED / HOST_UNREACHABLE / TIMEOUT / DATABASE_NOT_FOUND / TLS_ERROR / UNKNOWN`。
- [x] 成功/失败都更新测试字段，并同事务写 `connection.tested` Outbox。
- [x] Testcontainers MySQL 验证成功路径与密码错误路径。

## Task 4：表发现

**Files:**
- Create: `connector/mysql/MysqlTableDiscovery.java`
- Create: `connection/api/TableController.java`
- Create: `connection/api/dto/TableDtos.java`

- [x] 只列指定 database 的 `BASE TABLE`。
- [x] 聚合 `hasPrimaryKey`；字段返回顺序、类型、可空、默认值、主键标记、注释。
- [x] 表不存在/不是普通表/源库不可达分别返回固定错误码。
- [x] 无可见表返回空列表 + `NO_VISIBLE_TABLES` 警告。
- [x] Testcontainers MySQL 验证列表、字段、主键和错误路径。

## Task 5：状态摘要、部署与验收

**Files:**
- Create: `connection/api/ConnectionStateController.java`
- Create: `deploy/docker-compose.yml` 或 `data-service/compose.yml`
- Create: `data-service/Dockerfile`
- Create: `data-service/README.md`

- [x] `state` 返回连接、空任务/运行/数据集、`missingSteps` 和 `lastEventId`。
- [x] Docker Compose 起 PostgreSQL、MySQL、RabbitMQ、Java 服务。
- [x] Dockerfile 使用 JDK 21 构建、JRE 21 运行；容器内 Maven 使用镜像源配置。
- [x] 全量验证：`./mvnw test`、`./mvnw verify`、Docker 冒烟。
- [x] 回到技术设计文档记录实施结果与后续接口。

## 验收命令

```bash
cd data-service
./mvnw test
./mvnw verify
docker compose -f compose.yml up -d --build
curl -H 'X-Internal-Token: dev-internal-token' http://127.0.0.1:18001/actuator/health
```

## 本阶段完成标准

- 连接可创建、查询、修改，凭据加密落库且不回显。
- MySQL 连接测试返回固定业务结果，并写 `connection.tested` 事件。
- 表列表与字段查询可用，主键信息完整。
- 内部接口受 token 保护，错误体统一，日志无明文密码。
- Java 单测、集成测试、Maven verify 和 Docker 冒烟通过。
