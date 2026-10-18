# 构建数据管道（Building Pipelines）

> **分类说明**：在 Pipeline Builder 中构建数据管道的操作指南：添加输入输出、应用转换、连接数据流、校验管道与输出数据集

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `building-pipelines` |
| 文档数量 | 43 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Building pipelines • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/overview/>
- **文档摘要**：本页概述「构建数据管道」板块，说明在 Foundry 创建管道的初期步骤：连接数据源、建立项目结构、用 Pipeline Builder 或 Code Repositories 创建 batch pipeline 清洗并关联数据、映射到 Ontology，并介绍增量与流式管道及测试、发布等增强手段。

### 2. Building pipelines • Types of pipelines

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/pipeline-types/>
- **文档摘要**：本页介绍 Foundry 三种管道类型：Batch、Incremental 与 Streaming。以对比表说明其在延迟、复杂度、计算成本与数据规模弹性上的权衡，并指出 Faster 版本可在不支持全部 transform 的前提下提升批量与增量管道性能。

### 3. Building pipelines • Supported languages

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/supported-languages/>
- **文档摘要**：本页说明管道构建支持的语言（SQL、Python、Java）及其优劣。对比表列出各语言对文件访问、TLLV、增量计算、去除继承标记、多输出 dataset 的支持，并分别介绍 SQL（最佳性能）、Python（pandas/PySpark）与 Java 的特点。

### 4. Building pipelines • Considerations: Pipeline Builder and Code Repositories

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/considerations-pb-cr/>
- **文档摘要**：本页对比两个管道工具：Pipeline Builder（图形化无代码协作环境，含 health check）与 Code Repositories（Web 版 Git IDE）。建议先在 Pipeline Builder 设计，仅在需 API 调用、自定义库时再用 Code Repositories，并附功能对照。

### 5. Building pipelines • Getting started • Create a dataset batch pipeline with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-batch-pipeline-pb/>
- **文档摘要**：本操作指南演示用 Pipeline Builder 创建输出单一 dataset（航班警报）的 batch pipeline。分六部分：初始设置、添加数据集、清洗数据、关联数据集、添加输出与构建管道，完成后可用 Contour 等工具进一步分析输出。

### 6. Building pipelines • Getting started • Create a media set batch pipeline with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-batch-pipeline-pb-media-set/>
- **文档摘要**：本操作指南演示用 Pipeline Builder 创建带 media set 的 standard batch pipeline，从 PDF 抽取文本。涵盖初始设置、添加 media set、执行 transform、添加输出与构建，并附 Ontology 应用步骤与常见问题，产物为抽取文本 Object。

### 7. Building pipelines • Getting started • Create a dataset batch pipeline with Code Repositories

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-batch-pipeline-cr/>
- **文档摘要**：本操作指南用 Code Repositories 编写 SQL transform 并构建 dataset。流程含创建仓库、导入数据、创建分支、编辑代码、测试变更（预览/提交/分支构建）、提交评审、合并 master 与回退，强调基于分支的安全协作。

### 8. Building pipelines • Getting started • Create a media set batch pipeline with Code Repositories

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-batch-pipeline-cr-media-sets/>
- **文档摘要**：本操作指南演示用 Code Repositories 处理 media set 的 batch pipeline（Python）。创建仓库、安装 transforms-media 库、用 @transform 与 MediaSetInput/Output 编写代码（如 PDF 转 PNG），以及测试、提交、合并与构建。

### 9. Building pipelines • Getting started • Create an incremental pipeline with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-incremental-pipeline-pb/>
- **文档摘要**：本操作指南演示用 Pipeline Builder 创建 incremental pipeline，以每周追加的航班数据过滤 JFK 出发航班为例。分问题陈述、校验增量前提、创建管道、部署输出（含 Replay on deploy）四部分，说明其仅处理最新数据以节省资源。

### 10. Building pipelines • Getting started • Create a streaming pipeline with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-stream-pipeline-pb/>
- **文档摘要**：本操作指南结合 Foundry Streaming 与 Pipeline Builder 创建流式管道，输出传感器温度 dataset。流程含创建单分区 stream 与 schema、用 Curl 经个人令牌推送记录、在 Pipeline Builder 转换 stream 数据，并给出后续步骤。

### 11. Building pipelines • Getting started • Faster pipelines with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-faster-pipeline-pb/>
- **文档摘要**：本页介绍 Pipeline Builder 的 Faster pipeline（曾称 lightweight），基于 Rust 的 DataFusion 引擎加速 batch 与 incremental。说明如何新建、在 Faster 与 Standard 间转换列出已知限制与能力对比，运行低于 15 分钟者受益最大。

### 12. Building pipelines • Getting started • External pipelines with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-external-pipeline-pb/>
- **文档摘要**：本页介绍 Pipeline Builder 的 External pipeline，将计算下推到外部引擎（现支持 Databricks 与 Snowflake）。说明支持的引擎、如何新建 external pipeline、配置构建设置及限制，所有输入输出须为同一来源的 virtual table。

### 13. Building pipelines • Incremental pipelines • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/incremental-overview/>
- **文档摘要**：本页概述 incremental pipeline：用于处理随时间显著变化的输入，仅计算变更数据以降低延迟与成本，但开发与维护更复杂。背景涉及 transaction 理解、应对 SNAPSHOT 重算与大量小文件，并推荐创建增量同步等相关入门资源。

### 14. Building pipelines • Incremental pipelines • Creating incremental syncs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-incremental-syncs/>
- **文档摘要**：本页介绍创建 incremental syncs：端到端以 APPEND 事务将新数据同步进 Foundry，可降低源系统负载与存储需求。多数同步来自文件系统文件或 JDBC 源，并引导参阅「优化文件追加同步」与「增量 JDBC 同步」两篇操作指南。

### 15. Building pipelines • Incremental pipelines • Maintaining high performance

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/maintaining-incremental-performance/>
- **文档摘要**：本页讲解维持 incremental 管道高性能。增量 transform 仅追加新行，长期累积的大量小文件与事务会导致预览失败、Python transform 变慢乃至超时。解决方案含定期 snapshot、dataset projections、保留策略及中止事务等补充选项。

### 16. Building pipelines • Streaming pipelines • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/streaming-overview/>
- **文档摘要**：本页概述 streaming pipeline：借持续运行的专用计算以极低延迟（常 15 秒内）实时处理数据。区分 stateless 与 stateful（支持窗口、聚合、join）转换，给出规划停机、最小 profile 起步、单行 1MB 上限等最佳实践与入门资源。

### 17. Building pipelines • Streaming pipelines • Comparison: Streaming vs. batch

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/stream-vs-batch/>
- **文档摘要**：本页对比 streaming 与 batch 处理方式，助用户按用例需求（尤其端到端延迟）选型。从功能（流式支持低延迟访问且不支持 Python transform）、前端工具、性能、技术（状态管理、停机影响）与成本多维度展开分析。

### 18. Building pipelines • Streaming pipelines • Performance considerations

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/streaming-performance-considerations/>
- **文档摘要**：本页介绍创建 Foundry stream 时需考虑的延迟与吞吐预期。延迟因素含数据源产出速度、跨网络耗时、管道阶段数及是否发往外部系统；吞吐讨论扩展性，并含进阶主题，帮助用户明确流式用例性能指标与合理设计。

### 19. Building pipelines • Streaming pipelines • Streaming compute usage

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/streaming-compute-usage/>
- **文档摘要**：本页说明 Foundry Streaming 计算用量计量。流计算分实时处理与归档两类，按 compute-seconds 计量，默认 rate 0.5，流为静态分配、持续运行至用户结束。文档还介绍如何调查、理解、管理与计算流计算用量。

### 20. Building pipelines • Streaming pipelines • Keys

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/streaming-keys/>
- **文档摘要**：本页介绍 stream 的键机制：partition key 按同键分组并保持顺序（依赖 Kafka 分区）但不去重；primary key 用于 CDC 模式识别已解析记录。还涵盖键传播、如何使用与查看当前键，以及流式键的最佳实践。

### 21. Building pipelines • Streaming pipelines • Streaming stateful transforms

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/streaming-stateful-transforms/>
- **文档摘要**：本页介绍流式有状态转换（stateful transforms），每行输出可依赖先前行的 state，基于 Flink 实现低延迟。讲解 state 概念、键控状态、事件时间与水位线、状态过期、窗口与触发器，及有状态转换的最佳实践。

### 22. Building pipelines • Scheduling • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/scheduling-overview/>
- **文档摘要**：本页概述 Foundry 调度（Scheduling）功能：调度是数据集成层保持数据更新的关键，支持所有管道类型与灵活触发。预定构建可按时间、数据或逻辑更新触发，并可构建单 dataset、其依赖或连接两 dataset 间的所有 dataset。

### 23. Building pipelines • Scheduling • Create a schedule

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-schedule/>
- **文档摘要**：本页介绍如何在 Data Lineage 的 schedule editor 中创建新的 schedule。内容涵盖导航至编辑器、定义调度（包括目标 dataset、排除 dataset、触发时机、build 类型与范围及高级设置），最后保存。调度可应用于图中配置的 branch（含 fallback branch）。

### 24. Building pipelines • Scheduling • View and modify schedules

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/view-modify-schedules/>
- **文档摘要**：本页说明如何查看与管理已有 schedule。涵盖查看运行指标与历史、查看编辑历史版本、编辑、暂停、恢复 schedule，以及 Foundry 自动暂停连续失败 schedule 的机制与删除操作。

### 25. Building pipelines • Scheduling • Find and manage schedules

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/find-manage-schedules/>
- **文档摘要**：本页介绍如何使用 Build Schedules 应用搜索与管理 schedule。可通过 dataset、user、Project 等条件检索，并按名称或暂停状态过滤、按时间排序；列表展示各 schedule 配置与近期运行指标，并支持相应操作。

### 26. Building pipelines • Scheduling • Common scheduling configurations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/common-schedules/>
- **文档摘要**：本页提供常见 schedule 配置示例，帮助快速上手。包括定期构建 dataset、数据更新时构建、进阶的复合（多）触发器配置，以及仅在父 dataset 已更新时于特定时间更新 dataset 的 AND 触发场景。

### 27. Building pipelines • Scheduling • Trigger types reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/triggers-reference/>
- **文档摘要**：本页为触发器类型参考手册，介绍 Foundry 中可任意嵌套 AND/OR 的触发器。涵盖 time trigger（含 cron 表达式与 Unix/Quartz 格式差异）、event trigger（事件类型）以及 compound trigger 的组合规则。

### 28. Building pipelines • Scheduling • Troubleshooting reference

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/schedule-troubleshooting/>
- **文档摘要**：本页为 schedule 故障排查参考，列出常见调度问题及调试步骤。覆盖 scheduled build 失败、被忽略、未触发、仅构建部分 job spec、重试不一致、trashed 资源报错，以及编辑器权限与计算信息不可用等问题，建议从 metrics 页面入手。

### 29. Building pipelines • Scheduling • Add schedule to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/marketplace-schedules/>
- **文档摘要**：本页说明如何使用 Foundry DevOps 将 schedule 加入 Marketplace product 供他人安装复用。列出支持条件（非 user-scoped、无 fallback branch、触发器与目标 dataset 非 Restricted View 等），并给出在产品中添加 Schedule 输出的步骤。

### 30. Building pipelines • Scheduling • Parameterization

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/parameterization/>
- **文档摘要**：本页介绍 schedule 参数化（Beta），允许 transform 以不同参数值运行，目前仅支持 Python transforms。内容涵盖在 transform 中定义参数、在 schedule editor 启用参数化、标准模式与并行模式（含 run setups、union view、action types）及已知限制。

### 31. Building pipelines • Logic Flows [Sunset] • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/logic-flows-overview/>
- **文档摘要**：本页概述 Logic Flows（已进入 Sunset 阶段，不建议用于新开发）。Logic Flows 通过与 Foundry build 和调度基础设施集成来自动化常见工作流，介绍核心概念（automation、connected flow）与可用自动化，并建议迁移至 AIP Logic 或 Actions。

### 32. Building pipelines • Logic Flows [Sunset] • Create a connected flow

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/create-a-connected-flow/>
- **文档摘要**：本页说明如何在 Logic Flows 应用中创建 connected flow。介绍通过 /workspace/logic-flows 打开应用、设置名称/Project/参数与配置、进行校验，以及保存后触发构建、查看结果与创建调度的方法（以 Compass Files Lister 为例）。

### 33. Building pipelines • Logic Flows [Sunset] • Compass File Lister

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/compass-file-lister/>
- **文档摘要**：本页介绍 Compass File Lister 这一 automation，它将指定输入文件夹中资源的 rids（资源标识符）列出到 Code repository 并自动发起 PR。说明了覆盖输出路径、配置 merge_when_ready 等选项，并给出配置示例。

### 34. Building pipelines • Best practices • Recommended Project and team structure

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/recommended-project-structure/>
- **文档摘要**：本页为构建数据管道的最佳实践，概述如何使用 Project 组织管道。涵盖 Project 边界划分、五阶段管道结构（Data Connection、Datasource、Transform、Ontology、Workflow），以及成功管理生产管道所需的各类角色与职责。

### 35. Building pipelines • Best practices • Development best practices

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/development-best-practices/>
- **文档摘要**：本页为面向 transform 开发者的开发最佳实践指南。从通用实践（减少认知负荷、DRY、避免技术债）出发，依次给出项目目录结构、命名、数据类型、代码注释、仓库管理与单元测试等方面的建议与反模式。

### 36. Building pipelines • Best practices • Branching and release process

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/branching-release-process/>
- **文档摘要**：本页为分支管理与发布流程的最佳实践，面向通过 Project 与 Code Repository 管理的管道。涵盖发布范围界定、Product 分支管理（常见/特殊分支、工作流、仓库升级、维护窗口）以及功能测试与最终用户验证流程。

### 37. Building pipelines • Best practices • Scheduling best practices

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/scheduling-best-practices/>
- **文档摘要**：本页为调度最佳实践，旨在让生产管道 schedule 易于管理。给出多项准则：每条管道一个 schedule、使用 Project-scoped schedule、为 user-scoped schedule 设唯一 owner、合理使用重试、避免强制构建、快速失败及规范命名等。

### 38. Building pipelines • Best practices • Building a production pipeline

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/building-production-pipeline/>
- **文档摘要**：本页介绍构建生产管道的最佳实践，适合开发者与维护者。涵盖管道定义与期望、生产管道原则、开发流程与基础设施搭建，以及尽早开始监控，从而在维护阶段提前发现并避免 SLA 违约问题。

### 39. Building pipelines • Pipelines on unstructured data • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/unstructured-overview/>
- **文档摘要**：本页概述针对非结构化数据的管道（Pipelines on unstructured data）。说明非结构化数据同样以 dataset 中文件集合形式存储，列举其与结构化数据管道的相同点（增量、单元测试、build/schedule 等）与差异（需使用底层文件系统 API、无 schema 等）。

### 40. Building pipelines • Pipelines on unstructured data • Infer a schema for CSV or JSON files

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/infer-schema/>
- **文档摘要**：本页介绍如何为含 CSV 或 JSON 文件的 dataset 推断 schema。说明可在 dataset 视图点击 Apply a schema 自动推断，或用 Spark 在管道中动态推断（有性能开销），并给出 CSV 与 JSON 输入示例及针对 Parquet 的 sanitize_schema_for_parquet 建议。

### 41. Building pipelines • Pipeline security • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/security-overview/>
- **文档摘要**：本页为管道安全（Pipeline security）概述，介绍 Foundry 的平台安全特性以在构建数据管道时保障数据源安全。页面引导参考推荐 Project 结构、保护数据基础、敏感数据保护等文档，并给出 Marking 移除相关进阶工作流的指引。

### 42. Building pipelines • Pipeline security • Guidance on removing markings

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/remove-markings/>
- **文档摘要**：本页为移除 Markings 的指南。Markings 以全有或全无方式限制资源访问并沿依赖继承，本页给出三种移除场景（替换 severing、禁用 Propagate View Requirements、dataset 级移除）、最佳实践及常见 FAQ，并说明可用 Pipeline Builder 移除。

### 43. Building pipelines • Pipeline security • Remove inherited markings and organizations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/building-pipelines/remove-inherited-markings/>
- **文档摘要**：本页说明如何移除继承的 Markings 与 Organizations。介绍相关术语，说明可通过 stop_propagating 与 stop_requiring 输入 transform 属性（支持 Python/Java/SQL）移除，并详述基本流程、权限要求、审批模式及受保护分支等限制。
