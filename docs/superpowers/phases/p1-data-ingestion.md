# P1 数据进来

- 状态：✅ 已完成（2026-10-19）
- 基线提交：`7c8e240`
- 目标：把外部系统的数据稳定接进来，生成不可变的数据集版本，并能通过对话事件看到完整操作过程。
- 完成标准：连接可测试；选表可同步；snapshot / CDC 可运行和停止；成功生成版本，失败不改变当前版本；前端三栏工作台可用。

## 任务拆分

| 子任务 | 状态 | 主要产物 |
|---|---|---|
| 会话模型与 Agent 运行时复用 | ✅ 已完成 | `Conversation.kind + resource_id`；数据连接 Agent 定义与工具 |
| Java 数据服务：连接、测试、表发现 | ✅ 已完成 | `data-service/` 连接 CRUD、凭据加密、MySQL 探测、Outbox |
| Flink CDC + Iceberg 同步 | ✅ 已完成 | `SyncTask` / `SyncRun` / `Dataset` / `DatasetVersion`；RabbitMQ 命令；Flink 提交与状态跟踪 |
| Python API、对话事件与 Agent | ✅ 已完成 | 数据连接 REST、事件消费、SSE、只读 Agent 工具 |
| 前端三栏工作台 | ✅ 已完成 | 连接列表 / 对话时间线 / 上下文面板；数据集详情与预览 |
| 停止流程与失败诊断 | ✅ 已完成 | 点击立即停止、Supervisor 确认兜底、Flink 失败详情、可折叠错误、防重复运行 |
| Demo 测试数据与端到端冒烟 | ✅ 已完成 | `data-service/scripts/seed-demo-data.sh`；snapshot / CDC / 实时预览验收 |

## 已确认约束

- 前端只调用 Python，Java 只提供内部接口。
- Java 拥有连接、同步、运行和数据集元数据；Python 拥有对话与事件。
- 第一版只支持 MySQL，且只支持有主键表。
- 每次同步启动写新 Iceberg 表；版本引用不可变 snapshot，不覆盖历史版本。
- 失败运行只记录运行结果，不生成版本，不改变当前版本。
- 停止采用「点击立即请求 Flink，Supervisor 负责确认与兜底」。
- CDC 初始快照完成后立即发布 `snapshot_end` 版本；后续按版本间隔发布 `scheduled` 版本。
- 实时预览读取最新未发布 snapshot，不生成版本，不作为对象实例输入。

## 沟通记录

| 日期 | 议题 | 详细记录 |
|---|---|---|
| 2026-10-17 | 服务边界、数据所有权、消息队列 | [技术设计 §4](../specs/2026-10-17-data-connection-sync-technical-design.md#4-议题一服务边界与职责) |
| 2026-10-17 | 领域模型、状态机、幂等 | [技术设计 §5](../specs/2026-10-17-data-connection-sync-technical-design.md#5-议题二领域模型与状态机) |
| 2026-10-17 | 跨服务 REST / RabbitMQ 契约 | [技术设计 §6](../specs/2026-10-17-data-connection-sync-technical-design.md#6-议题三跨服务契约) |
| 2026-10-17 | 凭据加密与连接测试 | [技术设计 §7](../specs/2026-10-17-data-connection-sync-technical-design.md#7-议题四凭据加密与连接测试) |
| 2026-10-17 | 表发现与多表选择 | [技术设计 §8](../specs/2026-10-17-data-connection-sync-technical-design.md#8-议题五表发现与多表选择) |
| 2026-10-17 | 同步执行、失败隔离、版本发布 | [技术设计 §9](../specs/2026-10-17-data-connection-sync-technical-design.md#9-议题六同步执行与失败隔离) |
| 2026-10-17 | Python API、对话事件与 Agent | [技术设计 §10](../specs/2026-10-17-data-connection-sync-technical-design.md#10-议题七python-api对话事件与-agent-集成) |
| 2026-10-17 | 前端三栏交互 | [技术设计 §11](../specs/2026-10-17-data-connection-sync-technical-design.md#11-议题九前端三栏交互) |
| 2026-10-18 | Agent 运行时复用 | [实施计划](../plans/2026-10-18-agent-runtime-reuse-plan.md) |
| 2026-10-18 | Java 数据服务与连接器 | [实施计划](../plans/2026-10-18-java-data-service-plan.md) |
| 2026-10-18 | Flink CDC + Iceberg 同步链路 | [实施计划](../plans/2026-10-18-flink-cdc-iceberg-sync-plan.md) |
| 2026-10-18 | Python API、事件与 Agent | [实施计划](../plans/2026-10-18-python-data-connection-api-plan.md) |
| 2026-10-19 | 停止流程、重复运行防护、失败诊断 | [技术设计 §12.2、§14](../specs/2026-10-17-data-connection-sync-technical-design.md#122-前端与契约补齐2026-10-18) |
| 2026-10-19 | 初始快照发布与实时预览 | [技术设计 §12.3](../specs/2026-10-17-data-connection-sync-technical-design.md#123-初始快照发布与实时预览2026-10-19) |

## 文档索引

- 总体设计：[2026-10-17-data-integration-and-agent-loop-design.md](../specs/2026-10-17-data-integration-and-agent-loop-design.md)
- 技术设计：[2026-10-17-data-connection-sync-technical-design.md](../specs/2026-10-17-data-connection-sync-technical-design.md)
- 实施记录：技术设计第 12 节
- 决策记录：技术设计第 14 节

## 遗留问题

- 只支持 MySQL；PostgreSQL、Hive、MongoDB、Kafka 后续按连接器扩展。
- CDC 版本的 `rowCount` 是 Iceberg 物理记录估算值，不等同于源表精确行数。
- 旧 Iceberg 表、孤儿文件和失败运行临时表尚未清理。
- 不支持从 savepoint 恢复；重新同步会创建新运行和新表。
- 数据集版本分页与单版本详情接口尚未提供。
- Java 全量集成测试受 Testcontainers Ryuk 和 JDK 26 环境问题影响；单元测试与真实 compose 冒烟已覆盖主路径。
- 前端尚未接入浏览器自动化测试。

## 下一步

进入 P2 数据整理，先完成可视化处理技术设计。见 [p2-data-preparation.md](p2-data-preparation.md)。
