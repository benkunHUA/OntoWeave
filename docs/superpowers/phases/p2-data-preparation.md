# P2 数据整理

- 状态：✅ 已完成（2026-10-22 端到端验收通过）；功能设计已确认（8/8；来源追踪降级）；技术实现细节已确认（8/8）；实施计划已执行完成
- 目标：把同步进来的数据集通过可视化流程整理成新的派生数据集版本，并能手动构建、定时运行和保留来源元数据。
- 完成标准：能保存并重复运行处理流程；生成新的派生版本；能查看进度、日志和失败原因；定时任务可暂停/恢复并留下运行记录；输出数据集能查看直接来源版本和构建。

## 任务拆分

| 子任务 | 状态 | 说明 |
|---|---|---|
| 2.1 可视化处理技术设计 | ✅ 已完成（8/8） | 交互骨架、数据模型、Agent 状态机、画布节点、预览、构建、调度和来源元数据的功能与技术实现均已确认；实施计划已编写并通过自检 |
| 2.2 处理流程与算子实现 | ✅ 已完成 | Java 草稿命令、revision 并发控制、编辑历史、发件箱、`set_output`、统一 DAG/字段检查、按 turn 撤销、流程 REST、幂等创建、复制/归档、来源投影、预览、构建执行、定时调度、Python BFF、数据整理 Agent 和前端工作台/画布/配置已完成（Task 3-11），端到端验收通过 |
| 2.3 按节点预览 | ✅ 已完成 | 后端预览服务、逻辑计划、Flink 适配器、20 秒硬预算和字段级错误已完成（Task 5）；前端预览面板、取消、截断与错误定位已完成（Task 11）；真实 Iceberg 预览返回 2 行且不落库 |
| 2.4 构建与派生版本 | ✅ 已完成 | 后端构建队列/worker、冻结版本与输入、Flink 批处理、数据期望和统一发布已完成（Task 6）；构建 UI、日志入口已通过；真实手动/调度构建均成功，历史快照可回读 |
| 2.5 定时调度 | ✅ 已完成 | 后端定时扫描、cron 时区解析、pending 持久化恢复、无变化跳过、暂停/恢复和运行记录已完成（Task 7）；调度触发构建、暂停、恢复和暂停后忽略路径已验证 |
| 2.6 来源追踪（降级） | ✅ 已完成 | 后端单跳来源投影已完成（Task 4）；Agent 来源工具已完成（Task 9）；前端来源区域、构建/日志入口与权限降级已完成（Task 11）；真实来源接口返回 1 个直接输入；完整血缘图与影响分析后置 |

## 已确认约束

- 处理流程只读取数据连接同步生成的数据集，不直接访问外部数据库。
- 输出新的派生数据集版本，不覆盖原始数据集。
- 每次构建必须有运行记录；无变化不创建构建，记录为 `pipeline_schedule_run=ignored`。
- 数据集版本不可变；失败不改变当前版本。
- 所有功能沿用 Agent 对话 + 三栏工作台骨架，不另做一套菜单表单。
- 画布操作通过结构化事件回流到对话，复用已有 `ConversationEvent` 机制。
- 交互采用对话 + 画布；一个对话对应一条处理流程；Agent 通过对话引导用户逐步创建。
- 布局沿用建模工作台：左侧流程列表、中间对话、右侧画布；节点配置和预览放在画布内。
- 一个对话严格对应一条流程：新建时一起创建，删除时一起归档，复制时创建新对话。
- Agent 可以直接写草稿，不需要每一步确认；草稿自动保存，构建必须显式触发。
- 所有操作落库，记录操作者、操作前后、`operation_id` 和 `turn_id`；支持撤销本轮 Agent 改动和回退到上次构建。
- 删除节点、清空流程、覆盖输出等破坏性操作需要二次确认。
- 画布操作以结构化事件写入对话；Agent 读取流程状态和事件历史给出下一步引导。
- MVP 节点已在议题 4 确认：输入、筛选、选列、改名、列计算、去重、关联、合并、汇总、输出。
- P2 领域归 Java 数据服务；P1 `dataset` / `dataset_version` 保持同步语义，不复用为 pipeline 输出数据集。
- P2 新增 `pipeline_dataset` / `pipeline_dataset_version`；草稿和版本用整图 JSONB，不单独建节点/连线表。
- 输出采用通用 `pipeline_output` + dataset 类型表 `pipeline_dataset`；未来文件、虚拟表、Ontology 输出新增对应类型表。
- `pipeline_build` 记录整体构建，`pipeline_build_output` 记录每个输出节点的执行结果，`pipeline_dataset_version` 记录 dataset 输出物化版本。
- P3 通过只读统一数据集视图或查询服务消费 sync / pipeline 数据集，不共享物理表。
- Agent 每回合重新读取当前草稿和流程检查结果，一次只问一个最阻塞的问题。
- 普通改动由 Agent 直接写草稿；删除节点、清空流程、改输出、触发构建需要确认。
- 用户手动改画布后，Agent 以最新画布为准；并发修改通过 `base_revision` 检测，不静默覆盖。
- 可构建状态是派生视图，不单独落库；构建使用冻结的 `pipeline_version`。
- MVP 节点为输入、筛选、选列、改名、列计算、去重、关联、合并、汇总、输出；一个节点只做一种转换。
- 输入、普通转换、关联（两输入）、合并（多输入）、输出（单输入）按固定端口规则连线，不允许环。
- 画布左侧节点面板、画布右侧配置抽屉、画布底部预览面板；配置自动保存。
- 选中节点打开配置抽屉；抽屉底部「预览」按钮点击后关闭抽屉，再打开底部预览面板；配置与预览互斥，同一时间只显示一个。
- 预览按单个节点执行并自动计算上游；最多展示 500 行，服务端有内部安全读取上限，结果不完整时明确提示。
- MVP 不提供用户自定义采样；预览使用当前草稿，构建使用冻结的 `pipeline_version`。
- 预览不落库，不创建构建、流程版本或数据集版本，不阻塞构建；错误定位到节点、端口和字段。
- 构建是唯一发布动作：草稿自动保存，MVP 不单独做保存/部署；构建成功才切换当前版本。
- 构建冻结 `pipeline_version` 并解析精确输入版本；MVP 复用 Flink 1.20 批处理 + Iceberg，只做全量快照（`SNAPSHOT`）。
- 多输出先写 Iceberg 暂存快照，全部输出和数据期望通过后统一发布；失败或取消不发布任何输出。
- MVP 数据期望支持主键、行数区间、取值集合、非空四类；任一失败即构建失败。
- 同一流程同一时间只允许一个构建；手动构建总是执行，无变化跳过留给定时调度。
- 构建表补 `stage`、`log_uri`；构建输出表补 `stage`、`engine_job_id`、`log_uri`。
- 输出 schema 允许新增列和类型放宽；删除、改名、类型收窄直接构建失败。
- MVP 调度只支持时间触发（5 段 Unix cron + IANA 时区），数据更新/逻辑更新/复合触发后置。
- 一条流程最多一个调度；调度只运行已发布版本，不运行草稿。
- 无变化不创建构建，记录 `pipeline_schedule_run=ignored / NO_CHANGE`；`pipeline_build.status=skipped` 不再使用。
- P1 与 P2 通过数据集版本解耦：调度到点读取 P1 当前已发布版本，没有新版本就跳过。
- 构建中触发保留一条 `pending` 并合并后续触发；当前构建结束后再判断是否执行。
- 暂停清空 `next_run_at` 并忽略待执行运行；恢复从当前时间计算，不补跑历史。
- MVP 不做调度自动重试和自动暂停。
- 来源追踪降级：P2 只保留来源元数据和输出数据集的单跳上游列表。
- 不新增血缘边表，不做多跳血缘图、下游影响分析、字段级血缘、过期诊断图、保存图形和权限模拟。
- `resolved_inputs`、`build_id`、`pipeline_version_id` 和 `pipeline_dataset_version` 继续保留，后续补完整血缘不需要重算历史构建。
- 来源视图不新增血缘边表或图查询，按已发布版本的构建输出链路读取单跳来源；构建时额外保存 `displaySnapshot`，历史来源不受后续改名或删除影响。
- Java 维护节点目录和校验规则，前端与 Agent 共用，不在各处重复枚举。

## 待讨论议题

按顺序讨论，一次只推进一个：

1. ✅ 对话 + 画布的交互骨架与资源绑定：已确认，见[技术设计 §4](../specs/2026-10-19-data-preparation-technical-design.md)。
2. ✅ 处理流程的数据模型：已确认，见[技术设计 §5](../specs/2026-10-19-data-preparation-technical-design.md)。
3. ✅ Agent 引导与流程状态机：已确认，见[技术设计 §6](../specs/2026-10-19-data-preparation-technical-design.md)。
4. ✅ 画布节点、连线与配置：已确认，见[技术设计 §7](../specs/2026-10-19-data-preparation-technical-design.md)。
5. ✅ 预览与字段级错误：已确认，见[技术设计 §8](../specs/2026-10-19-data-preparation-technical-design.md)。
6. ✅ 执行、构建与派生版本：已确认，见[技术设计 §9](../specs/2026-10-19-data-preparation-technical-design.md)。
7. ✅ 定时调度与无变化跳过：已确认，见[技术设计 §10](../specs/2026-10-19-data-preparation-technical-design.md)。
8. ✅ 来源追踪与影响分析（降级）：功能与技术实现均已确认，见[技术设计 §11](../specs/2026-10-19-data-preparation-technical-design.md)。P2 只做来源元数据和单跳上游列表，完整血缘与影响分析后置。

## 验收结果

- 验收报告：[2026-10-20-p2-data-preparation-smoke.md](../../checks/2026-10-20-p2-data-preparation-smoke.md)
- 验收日期：2026-10-22
- 主链路：预览、手动构建、调度构建、派生版本、历史快照、来源元数据、Agent 只读闭环全部通过。
- 运行时修复：checkpointer 改为应用启动阶段初始化，避免请求事务与 `CREATE INDEX CONCURRENTLY` 互等导致的 SSE 超时。
- 已知限制：Python API 容器镜像缺少 `deepagents`，本次用本地 uvicorn 完成验收；Testcontainers 因 Docker Hub 拉取超时未执行；Java 预览取消/超时测试和 Python `test_changeset.py` 仍受本地环境限制。

## 沟通记录

| 日期 | 议题 | 结论/记录 |
|---|---|---|
| 2026-10-17 | 数据整理能力范围 | [总体设计 §3.2](../specs/2026-10-17-data-integration-and-agent-loop-design.md#32-第二层数据整理) |
| 2026-10-17 | 处理输入与输出边界 | [总体设计 §5](../specs/2026-10-17-data-integration-and-agent-loop-design.md#5-已确认边界)：只读同步数据集，输出派生版本 |
| 2026-10-17 | 待讨论事项 | [总体设计 §6](../specs/2026-10-17-data-integration-and-agent-loop-design.md#6-待讨论) 第 1-4 项 |
| 2026-10-19 | MVP 处理算子范围 | [技术设计 §3](../specs/2026-10-19-data-preparation-technical-design.md)：候选为筛选、选列、改名、列计算、按字段名合并、关联、去重、汇总；后续已在议题 4 确认并扩展。 |
| 2026-10-20 | 交互模型与议题重组 | [技术设计 §2](../specs/2026-10-19-data-preparation-technical-design.md)：采用对话 + 画布；一个对话对应一条处理流程；议题调整为 8 个，先交互骨架和 Agent 状态机。 |
| 2026-10-20 | 议题 1：交互骨架与资源绑定 | [技术设计 §4](../specs/2026-10-19-data-preparation-technical-design.md#4-议题一对话--画布的交互骨架与资源绑定)：左侧流程列表、中间对话、右侧画布；一个对话严格绑定一条流程；Agent 直接写草稿并留痕；草稿自动保存、构建显式触发；支持撤销本轮 Agent 改动和回退到上次构建；破坏性操作需确认。 |
| 2026-10-20 | 议题 2：处理流程的数据模型 | [技术设计 §5](../specs/2026-10-19-data-preparation-technical-design.md)：P2 领域归 Java；P1 `dataset` 不动；新增 `pipeline_dataset` / `pipeline_dataset_version`；草稿和版本用整图 JSONB；通用输出 + dataset 类型表；`pipeline_build_output` 记录逐输出结果；P3 通过统一数据集视图消费。 |
| 2026-10-20 | 议题 3：Agent 引导与流程状态机 | [技术设计 §6](../specs/2026-10-19-data-preparation-technical-design.md)：Agent 每回合读取最新草稿和流程检查结果；一次只问一个最阻塞问题；普通改动直接写草稿，破坏性操作和构建需确认；画布变更通过事件同步；可构建状态派生不落库。 |
| 2026-10-20 | 议题 4：画布节点、连线与配置 | [技术设计 §7](../specs/2026-10-19-data-preparation-technical-design.md)：MVP 节点为输入、筛选、选列、改名、列计算、去重、关联、合并、汇总、输出；一个节点一种转换；点击节点打开右侧配置抽屉；Java 统一节点目录，前端和 Agent 共用。 |
| 2026-10-20 | 议题 5：预览与字段级错误 | [技术设计 §8](../specs/2026-10-19-data-preparation-technical-design.md)：点击节点打开右侧配置抽屉，抽屉底部「预览」按钮关闭抽屉并打开底部预览面板，配置与预览互斥；预览按单节点计算上游，最多 500 行，服务端有内部安全读取上限；预览使用当前草稿、不落库、不阻塞构建；错误定位到节点、端口和字段。 |
| 2026-10-20 | 议题 6：执行、构建与派生版本 | [技术设计 §9](../specs/2026-10-19-data-preparation-technical-design.md)：构建是唯一发布动作；冻结 `pipeline_version` 并锁定输入版本；复用 Flink 批处理 + Iceberg；MVP 只做全量快照；多输出暂存后统一发布；数据期望支持主键、行数、取值集合、非空；同一流程只允许一个构建。 |
| 2026-10-20 | 议题 7：定时调度与无变化跳过 | [技术设计 §10](../specs/2026-10-19-data-preparation-technical-design.md)：MVP 只做时间触发；一条流程一个调度；调度只运行已发布版本；无变化不创建构建，记录 `pipeline_schedule_run=ignored`；P1/P2 通过数据集版本解耦；构建中触发保留一条 `pending`；支持暂停/恢复，不做自动重试和自动暂停。 |
| 2026-10-20 | 议题 8：来源追踪与影响分析（降级） | [技术设计 §11](../specs/2026-10-19-data-preparation-technical-design.md)：P2 只保留来源元数据和输出数据集的单跳上游列表；不新增血缘边表，不做多跳血缘图、下游影响分析、字段级血缘、过期诊断图和权限模拟；完整血缘后置到 P3/P4 之后。 |
| 2026-10-20 | 议题 1 技术实现：资源创建与绑定 | [技术设计 §4.6](../specs/2026-10-19-data-preparation-technical-design.md)：Python 统一入口 + Java 内部接口；`operation_id` 派生稳定双 ID；Java 按 `pipeline_id`/`conversation_id` 幂等创建；4xx 回滚、超时重试对账；会话归档增加 `archived_at`；复制只复制草稿；前端 `/pipelines/:pipelineId`；抽取共享三栏和会话外壳；Java 业务事务与发件箱事件同步。 |
| 2026-10-20 | 议题 2 技术实现：数据模型落地 | [技术设计 §5.6](../specs/2026-10-19-data-preparation-technical-design.md)：`pipeline*` 放 `data` schema，`conversation` 放 `public` schema；草稿 JSON 带 `schemaVersion`；输出节点只引用 `outputId`；草稿条件更新 + 编辑历史 + 发件箱同事务；版本按规范化哈希复用；dataset 版本发布后才切换当前版本；补 `requested_by`、`created_at`、`conversation_event.turn_id`；P3 用只读目录视图消费。 |
| 2026-10-20 | 议题 3 技术实现：Agent 运行与状态机 | [技术设计 §6.7](../specs/2026-10-19-data-preparation-technical-design.md)：新增 `DataPipelineAgentDefinition`；每轮重读状态；Java 统一计算派生状态；单 Agent + 工具；写入带 `base_revision`；`turn_id`/`operation_id` 留痕；每轮最多一个澄清问题；破坏性操作和构建通过 `interrupt` 确认；支持按 turn 撤销；提示词只注入紧凑状态和最近事件。 |
| 2026-10-20 | 议题 4 技术实现：画布节点、连线与配置 | [技术设计 §7.8](../specs/2026-10-19-data-preparation-technical-design.md)：Java 代码维护版本化节点目录；前端按 ETag 缓存目录；配置使用结构化 JSON 和受限表达式树；DAG 按固定顺序校验并共用 `PipelineCheckService`；前端使用受控 React Flow、命令队列和 `base_revision`；配置抽屉采用通用组件加专用编辑器；Agent 只调用结构化节点工具；输入节点不固定版本；坐标不参与语义修订、过期判断和版本哈希。 |
| 2026-10-20 | 议题 5 技术实现：预览与字段级错误 | [技术设计 §8.6](../specs/2026-10-19-data-preparation-technical-design.md)：预览接口校验草稿修订号并共用 `PipelineCheckService`；输入节点复用 P1 Iceberg 快照读取；转换节点编译为 Flink 批处理 Table API 作业，每个输入固定 `snapshot-id` 并设置扫描上限；最终查询 501 行返回 500 行；预览使用独立通道、不占构建锁；结果只在内存短期缓存；执行期转换用 `TRY_CAST` 和隐藏标记收集字段级警告。 |
| 2026-10-20 | 议题 6 技术实现：执行、构建与派生版本 | [技术设计 §9.13](../specs/2026-10-19-data-preparation-technical-design.md)：构建接口只入队，数据库 worker 用 `SKIP LOCKED` 领取并心跳；新增 `heartbeat_at`、`cancel_requested_at`；冻结版本并固定输入快照；每个输出一个 Flink Table API 批作业并按序执行；输出写 Iceberg `build_<buildId>` 临时分支并覆盖写；期望通过后用一个数据库事务写版本并切换指针；事务后尽力快进 `main`；schema 只允许新增和放宽。 |
| 2026-10-20 | 议题 7 技术实现：定时调度与无变化跳过 | [技术设计 §10.13](../specs/2026-10-19-data-preparation-technical-design.md)：Spring 定时扫描 + `FOR UPDATE SKIP LOCKED`；`cron-utils` 解析 5 段 Unix cron 和 IANA 时区；按 `(schedule_id, scheduled_for)` 幂等、同一调度最多一条 `pending`；调度只读已发布版本并解析当前输入，按 `logic_hash` 判断无变化；派发复用构建入队服务；暂停清空 `next_run_at` 并忽略 pending，恢复不补跑。 |
| 2026-10-20 | 议题 8 技术实现：来源追踪与影响分析 | [技术设计 §11.6](../specs/2026-10-19-data-preparation-technical-design.md)：不新增血缘边表或图查询；按 `pipeline_dataset_version.build_output_id → pipeline_build_output → pipeline_build.resolved_inputs → pipeline_version` 读取单跳来源；构建时保存 `displaySnapshot` 固定历史展示；新增只读来源接口和 Agent 工具；P1 元数据批量读取，缺失/无权限降级；不做多跳、下游影响和字段级血缘。 |
| 2026-10-20 | 实施计划编写 | [实施计划](../plans/2026-10-20-data-preparation-plan.md)：拆为 12 个可独立验证的任务，顺序为 Java 基础（表、节点目录、草稿校验、REST）→ Java 执行（预览、构建发布、调度）→ Python BFF 与 Agent → 前端工作台与画布 → Docker 端到端验收；每个任务先写失败测试、通过后单独提交。 |
| 2026-10-20 | 实施计划自检 | 修正计划中的契约冲突：浏览器 API 字段统一 snake_case、草稿节点/边 ID 统一 UUID、输入节点预览直读固定 501 行、Agent 运行上下文与工具上下文分离、Python 创建补偿使用请求级事务、前端补齐 `X-Turn-Id`；进一步拆分 `PipelineInputResolver` 的草稿解析与冻结版本解析，统一测试归属并修正 UUID 断言；计划检查通过，待选择执行方式。 |
| 2026-10-20 | Task 1：数据库与领域模型 | 完成 V8 迁移、11 个实体、9 个枚举、11 个仓储和 schema 行为测试；新增构建队列索引与调度时间不变量；提交 `b9c72c2`，`PipelineSchemaIntegrationTest` 4/4 通过，规格与代码质量评审通过。 |
| 2026-10-20 | Task 2：节点目录与配置 Schema | 完成 10 个节点、端口规则、严格 JSON Schema、受限筛选/表达式树、UI Schema、确定性目录哈希和 ETag 接口；提交 `c73323a`，目录相关测试 44/44 通过，规格与代码质量评审通过。 |
| 2026-10-20 | Task 3：草稿命令、编辑历史与统一检查 | 完成 sealed 草稿命令、悲观锁 + revision 条件更新、编辑历史与发件箱同事务、`set_output` 幂等、输入 schema 批量解析、DAG/字段/表达式/输出校验和按 turn 撤销；提交 `5399845`、`c8291e3`、`a41e0a8`、`c4adcc9`、`35ca239`，Task 3 定向测试 56/56 通过，规格与代码质量评审通过。 |
| 2026-10-21 | Task 4：流程 REST、幂等创建与来源查询 | 完成流程 REST、201/200 幂等创建、分页/改名/归档/复制、草稿命令与坐标/检查/撤销接口、来源投影；复制锁定源草稿保证一致快照，归档为写操作终态，所有流程写操作按 `pipeline → draft` 锁序串行化；历史来源使用冻结 `datasetVersionNumber` 和 `displaySnapshot`，元数据批量读取并支持 `available/missing/restricted` 降级。提交 `b42768b`、`1f2c1d5`、`e33df1e`、`5f4d215`、`12f2f44`，定向测试 43/43 通过，规格与代码质量复审通过（含归档并发测试）。 |
| 2026-10-21 | Task 5：预览计划与 Flink 适配器 | 完成预览 REST、端点级 20 秒硬预算、同流程 epoch 抢占与 build cutoff、有界 worker 池、Iceberg 直读、Flink 1.20.3 Table API 适配、逻辑计划与表达式编译、隐藏 cast 标记、可见列去重、scanLimit 判断、错误白名单和节点级定位。提交 `d32734a`、`f0a17c0`、`ba66536`、`ce903c3`、`a3053c0`、`dca89ac`、`995d24b`、`6b0edbf`、`654615b`，定向测试 118/118 通过，规格与代码质量复审通过；真实 Flink + Iceberg 集成仍需 `RUN_FLINK_INTEGRATION=true` 环境。 |
| 2026-10-21 | Task 6：构建、发布与数据期望 | 完成构建入队与活跃唯一约束、独立构建/取消执行器、心跳和 worker 丢失恢复、冻结版本与输入快照、构建全量扫描、冻结数据期望、每输出串行 Flink 作业、Iceberg `build_<buildId>` 临时分支、Flink 聚合期望查询、统一发布事务、取消确认、失败分支清理和输出 schema 兼容。提交 `607819f`、`a889ed5`、`a19dd6d`、`fdaab8d`、`65c6eff`、`849e386`、`75fa418`，定向测试 84/84 通过，规格与代码质量复审通过；真实 Flink + Iceberg 集群仍未启用。 |
| 2026-10-21 | Task 7：定时调度与无变化跳过 | 完成 5 段 Unix cron 与 IANA 时区、DST 回拨处理、15 秒扫描、`SKIP LOCKED` 领取、`(schedule_id, scheduled_for)` 幂等、pending 持久化与自动恢复、已发布版本 `logic_hash` 无变化跳过、暂停/恢复和运行记录分页。提交 `9185015`、`ee7e6e8`、`12af7c0`，定向测试 53/53 通过，规格与代码质量复审通过；剩余风险为 pending 慢任务重复提交、永久异常无退避和首次并发 PUT 依赖唯一约束兜底。 |
| 2026-10-21 | Task 8：Python 会话、BFF API 与迁移 | 完成 `data_pipeline` 会话类型、会话归档、`turn_id`、稳定双 ID、创建补偿、全部 BFF 路由、Java 数据集分页接口和真实事件落库。并发创建使用独立 `NullPool` 连接的 PostgreSQL advisory lock；归档为写操作终态，取消构建在 build 行锁后重查 active；Java 422 映射为 422 并保留 details。提交 `a9e4eb0`、`efa8763`、`86dcd62`、`e2864aa`、`a56d6a5`、`0e93569`；定向 Python 98 项、Java 93 项通过，规格与代码质量复审通过。 |
| 2026-10-21 | Task 9：数据整理 Agent | 完成每轮重读状态、17 个结构化工具、固定提示词、`turn_id` 贯通、破坏性操作中断确认、revision 冲突结构化返回、轮次与提问事件持久化，以及 Java 缺省节点坐标分配。修复暂停恢复后的轮次状态回填和真实 add_node 坐标契约；提交 `fe50e2d`、`c5782f5`、`fd147a0`，定向 Python 98 项、Java 19 项通过，规格与代码质量复审通过。 |
| 2026-10-21 | Task 10：前端工作台、画布与配置 | 完成 `/pipelines` 工作台、流程列表、共享三栏骨架、节点面板、受控画布、DAG 连线校验、dagre 布局、串行命令队列、节点目录驱动配置抽屉和专用编辑器；补齐输出绑定、revision 代次隔离、切流程竞态、抽屉 flush、多选拖动和 Agent turnId 生命周期。提交 `031fa25`、`26c088e`、`1d17bfb`、`c0c6b18`、`58c6552`；前端 28 文件 / 125 项、Java 8 项通过，规格与代码质量复审通过。 |
| 2026-10-21 | Task 11：预览、构建、调度与来源 UI | 完成预览面板（500 行上限、截断、扫描行数、输入版本、字段错误与取消）、构建确认/进度/逐输出状态/取消/恢复、调度 cron 与运行历史、来源区域和输出数据集详情；补齐保存后 revision、四面板互斥、结构化错误、日志 URI、restricted 降级与真实 PostgreSQL 并发写回测试。提交 `c6a782c`、`7d8f014`、`0b034bc`、`fba99c2`；前端 43 文件 / 192 项、Python 定向 7 项、Java 定向 49 项通过，规格与代码质量复审通过；真实 Flink/Iceberg 与日志链接可达性留到 Task 12。 |
| 2026-10-22 | Task 12：端到端验收与文档回填 | 使用真实 MySQL → Flink → Iceberg 链路完成预览（2 行、不落库）、手动构建（`6b86d326-8bb9-4052-bcb1-86a22be55be3`）、调度构建（`01a8458c-6679-468f-b73b-75a33f33869d`）、历史快照回读、来源单跳接口和 Agent 只读闭环（168 个 SSE 事件、4.05 秒）。修复请求事务内 `PostgresSaver.setup()` 导致的 checkpointer 死锁，改为应用启动阶段初始化并补回归测试；补充暂停后派发 `ignored / SCHEDULE_PAUSED` 单测。验收证据见[烟测报告](../../checks/2026-10-20-p2-data-preparation-smoke.md)；Python 530 项通过、前端 192 项通过；Python 容器 `deepagents` 依赖缺口和 Testcontainers 拉取超时记录为已知限制。 |

## 文档索引

- 现有设计：[总体设计 §3.2、§5、§6](../specs/2026-10-17-data-integration-and-agent-loop-design.md)
- 技术设计：[2026-10-19-data-preparation-technical-design.md](../specs/2026-10-19-data-preparation-technical-design.md)
- 实施计划：[2026-10-20-data-preparation-plan.md](../plans/2026-10-20-data-preparation-plan.md)
- 验收报告：[2026-10-20-p2-data-preparation-smoke.md](../../checks/2026-10-20-p2-data-preparation-smoke.md)
- 可复用实现：数据连接工作台、`ConversationEvent`、SSE、Agent 运行时、数据集版本与预览

## 下一步

P2 已完成并通过端到端验收。下一步进入 P3「变成对象」：先确认字段映射、对象标识、关系生成和 P2 数据集消费方式，再写技术设计与实施计划。

新对话开场：

```text
继续 OntoWeave 数据集成计划，当前进入 P3「变成对象」。
先读 docs/superpowers/ROADMAP.md 和 docs/superpowers/phases/p2-data-preparation.md。
当前 main 基线为 fba99c2（P1 验收基线为 7c8e240），P1「数据进来」已完成。
P2「数据整理」已完成并通过 2026-10-22 端到端验收，验收报告见 docs/checks/2026-10-20-p2-data-preparation-smoke.md。
下一步进入 P3「变成对象」，先讨论字段映射、对象标识和关系生成，不要直接写实现代码。
交互模型已确认：对话 + 画布，一个对话对应一条处理流程。
议题 1「交互骨架与资源绑定」功能已确认，技术实现已确认。
议题 2「处理流程的数据模型」已确认，技术实现已确认。
议题 3「Agent 引导与流程状态机」已确认，技术实现已确认。
议题 4「画布节点、连线与配置」已确认，技术实现已确认。
议题 5「预览与字段级错误」已确认，技术实现已确认。
议题 6「执行、构建与派生版本」已确认，技术实现已确认。
议题 7「定时调度与无变化跳过」已确认，技术实现已确认。
议题 8「来源追踪与影响分析」已确认（降级：只做来源元数据和单跳上游列表），技术实现已确认。
技术实现细节已确认 8/8；Task 1-12 已完成；P2 验收通过；下一步从 P3 开始。
```
