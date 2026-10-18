# 数据集成总览

> **分类说明**：Foundry 数据集成模块总览：数据连接、管道构建、数据集、流、媒体集、分支、构建、调度、健康检查、Iceberg 表、虚拟表、CDC 与视图等核心概念与入门路径

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `data-integration` |
| 文档数量 | 23 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Overview • Data integration

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/overview/>
- **文档摘要**：本页概述 Foundry 的数据连接与集成能力，说明其超越传统 ETL/ELT，提供连接数据、数据转换（transform）与 Pipeline 管理等核心能力，作为复杂环境下的数据集成骨干。

### 2. Connecting to data

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/connecting-to-data/>
- **文档摘要**：本页介绍将数据源接入 Foundry 的方式，涵盖 Data Connection（批量、流式、媒体与 CDC 同步及 virtual tables）、HyperAuto 软件定义数据集成（SDDI），以及 External transforms 等能力。

### 3. What is a data pipeline?

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/data-pipeline/>
- **文档摘要**：本页解释 Foundry 中 data pipeline 的概念：数据从源系统经中间 dataset 最终生成高质量数据集，供 Ontology 与分析使用。说明 pipeline 需具备所有权、构建调度、数据质量、安全治理等特征，并区分批处理、增量与流式三种类型。

### 4. Application reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/application-reference/>
- **文档摘要**：本页提供数据集成流程中常用 Foundry 应用的参考，包括 Pipeline Builder、Code Repositories、Data Lineage、Data Connection、Dataset Preview、Data Health 与 Builds 等各自的功能定位与适用场景。

### 5. Core concepts • Datasets

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/datasets/>
- **文档摘要**：本页介绍 Foundry 的核心概念 dataset，它是包裹文件集合的数据表现形式，提供权限、schema、版本控制与更新能力，支持结构化、非结构化与半结构化数据，并说明 transactions、branches、views、schemas 与底层文件系统。

### 6. Core concepts • Streams

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/streams/>
- **文档摘要**：本页介绍 stream（流）概念，其类似 dataset 但提供低延迟视图，由 hot buffer 与 cold buffer 存储，支持 branching、版本控制与 schema 管理；并说明流式处理、分区、一致性语义（AT_LEAST_ONCE / EXACTLY_ONCE）等。

### 7. Core concepts • Media sets (unstructured data)

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/media-sets/>
- **文档摘要**：本页介绍 media set（媒体集）概念，即具有相同 schema 的媒体文件集合，专为大规模非结构化数据设计，支持音频、图像、视频与文档的处理，可与 Pipeline Builder 配合完成 PDF 内容分析、地理空间与医疗影像等工作流。

### 8. Core concepts • Branching

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/branching/>
- **文档摘要**：本页介绍 Foundry 的分支（branching）概念，借鉴软件开发的版本控制，让多人安全协作修改数据管道；涵盖创建分支、提交（commit）、拉取请求（pull request）等工作流，以及 dataset 分支与 build 分支的技术细节与保证。

### 9. Core concepts • Builds

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/builds/>
- **文档摘要**：本页介绍 Build（构建）概念，即计算 dataset 新版本的机制，由 jobs 与 JobSpec 组成并编排计算；说明 build 生命周期（解析、执行、staleness）、分支、实时日志，以及如何在 Builds 应用中探索构建。

### 10. Core concepts • Schedules

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/schedules/>
- **文档摘要**：本页介绍 schedule（调度）概念，用于周期性运行 build 以保持数据流动；说明 trigger 触发条件、运行历史（成功/忽略/失败）、在 Data Lineage 侧栏管理与查找调度，以及相关最佳实践与触发器类型。

### 11. Core concepts • Health checks

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/health-checks/>
- **文档摘要**：本页介绍 health check（健康检查）概念，用于在数据同步、转换与调度运行后校验数据质量与可靠性，涵盖 job 级、build 级与 freshness 检查等类型，并指向 Health checks 服务与推荐检查文档。

### 12. Core concepts • Iceberg tables

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/iceberg-tables/>
- **文档摘要**：本页介绍 Apache Iceberg 表格式及其在 Foundry 中的使用。Iceberg 是开源表格式，借助 catalog 记录表与文件位置；Foundry 将其作为 dataset 之外的另一种表格数据资源，提供托管表（managed tables）与经外部 catalog 的 virtual tables。

### 13. Core concepts • Virtual tables

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/virtual-tables/>
- **文档摘要**：本页介绍 virtual table（虚拟表），允许直接查询外部数据平台中的表而无需先存入 Foundry dataset，抽象底层源系统并可与 Foundry dataset 组合；说明支持的源（S3、BigQuery、Databricks 等）、工作流、设置连接、更新检测及与同步到 dataset 的取舍。

### 14. Core concepts • Change data capture (CDC)

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/change-data-capture/>
- **文档摘要**：本页介绍 change data capture（CDC）概念，用于流式同步关系数据库的实时变更；说明 Foundry 在 Data Connection、streams、Pipeline Builder、Ontology 与 Workshop 中的 CDC 支持，以及 changelog 元数据与使用注意事项。

### 15. Core concepts • Views

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/views/>
- **文档摘要**：本页介绍 View（视图）概念，它类似 dataset view 但不存储数据文件，而是对其 backing datasets 求并集，可借助主键自动去重并自动触发 build；说明创建步骤、使用方式、限制（仅可作 transform 输入）及增量读取的去重语义。

### 16. Overview • Data integration

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/source-type-overview/>
- **文档摘要**：本页概述 Data Connection 可用的连接器（connectors），按类别列出可接入的数据源，包括文件系统与对象存储、JDBC 数据库/数据仓库/ERP-CRM、流式源、NoSQL、REST API，以及 IoT、地理空间与生产力工具等其它类型。

### 17. Palantir-provided drivers for JDBC sources

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/foundry-provided-drivers/>
- **文档摘要**：本页说明如何使用 Palantir 提供的 JDBC 驱动连接外部源，作为 Data Connection 中的 Foundry 源。涵盖设置步骤、JDBC 属性等配置选项、同步配置（含 SQL 查询）、CData 驱动选项（导出、证书、OAuth 2.0）及可用驱动列表。

### 18. Resource guides • Streaming

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/streaming-guide/>
- **文档摘要**：本页汇总实现端到端流式工作流所需的资源指南，涵盖核心概念、概览、数据源连接、流式数据 transform、监控与开发工具，并列出 Kafka、Kinesis 等专用连接器及通过 external transforms 接入的流式源。

### 19. Resource guides • Flink fundamentals

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/flink-streaming/>
- **文档摘要**：本页介绍 Foundry 流式处理底层引擎 Apache Flink 的基本原理，包括 Flink job（由 operator 构成的 job graph 与物理图）、Job Managers 与 Task Managers 的运行架构，以及 job state 等，帮助理解流式作业配置。

### 20. Resource guides • Reset stream

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/reset-stream/>
- **文档摘要**：本页说明如何重置 ingest stream（摄取流），即清除流中现有记录并可更改 schema、吞吐量与配置，便于开发调试而不破坏 Pipeline Builder 中的引用；同时提示需重放下游管道及重置的不可逆影响与操作步骤。

### 21. Resource guides • Stream monitoring

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/stream-monitoring/>
- **文档摘要**：本页介绍 stream monitoring（流监控），围绕流的「健康」状态配置告警，含 ingest、pipeline 与 output 监控（如记录数阈值），以及指标查看、通知与告警触发；以 Data Health 应用创建监控为例说明配置流程。

### 22. Resource guides • Streaming profiles

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/streaming-profiles/>
- **文档摘要**：本页介绍 streaming profiles（流式作业配置档），用于在默认 Flink 配置不足以应对高吞吐、大记录或大状态时调整资源；说明何时使用、如何设置，以及以 Project references 方式导入与组合多个 profile。

### 23. S3-compatible API for Foundry datasets

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-integration/foundry-s3-api/>
- **文档摘要**：本页介绍 Foundry dataset 的 S3 兼容 API，可将 dataset 当作 S3 bucket 访问，并给出 S3 概念到 Foundry（bucket→dataset、分支、transactions）的映射、设置指南、支持的操作及客户端示例（AWS CLI、Boto3、Spark 等）。
