# 管道优化（Optimizing Pipelines）

> **分类说明**：数据管道性能优化：检查点策略、资源调配、管道拆分与常见性能问题的解决方案

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `optimizing-pipelines` |
| 文档数量 | 21 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Optimizing and debugging pipelines • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/overview/>
- **文档摘要**：本页是 Foundry 管道优化与调试板块的概览，说明创建数据管道时可能需要在底层理解计算原理，以调试作业失败或提升性能。页面给出遇到计算或性能问题时的通用处理步骤，并指出批量管道可借助 Spark 引擎加速、输入快速增长的管道应改为增量处理，同时提供调试失败作业、失败管道及 Spark 核心概念的入口链接。

### 2. Optimizing and debugging pipelines • Debugging pipelines • Debug a failing job

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/debug-job/>
- **文档摘要**：本页提供调试失败 transform 作业的推荐工作流，介绍 Foundry 中可用于定位作业为何开始失败的工具。内容涵盖 Job Comparison（作业对比）工具的 Comparison Summary、Input Changes 与 Code Changes 标签页，以及处理悬挂构建（hanging builds）和 AI error enhancer (AIP) 的使用方法。

### 3. Optimizing and debugging pipelines • Debugging pipelines • Debug a failing pipeline

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/debug-pipeline/>
- **文档摘要**：本页提供一套调试失败管道的框架，可作为管道维护者在值班收到健康检查失败通知时制定标准操作程序（SOP）的基础。框架建议依次从缓解、问题分类、broader 平台影响三个问题入手，并介绍前提知识、问题类别分类，以及识别管道变更与平台问题的相关工具。

### 4. Optimizing and debugging pipelines • Debugging pipelines • Debug a failing stream

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/debug-stream/>
- **文档摘要**：本页为流式（streaming）管道调试提供推荐工作流，并说明 Foundry 中用于理解流为何失败的工具。页面将失败分为即时失败、瞬时失败与持久失败三类，分别指向用户逻辑、基础设施中断或输入数据变化等根因，并介绍检查流式集群日志、管道逻辑、输入数据与流详情等常见调试步骤。

### 5. Optimizing and debugging pipelines • Debugging pipelines • Troubleshoot out-of-memory errors

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/troubleshoot-ooms/>
- **文档摘要**：本页讲解如何排查 Spark 作业中的内存溢出（OOM）错误，列举了 "Job aborted due to stage failure"、ExecutorLostFailure 等典型报错信息。页面提供降低构建内存占用的具体步骤：将 Python/Pandas 计算迁移到 PySpark、检查 null joins 与递归 join 并使用 checkpoint 缓存中间结果、检查输入文件大小并重分区、拆分 transform 及删除不必要列等。

### 6. Optimizing and debugging pipelines • Debugging pipelines • Troubleshooting schedules

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/troubleshoot-schedules/>
- **文档摘要**：本页介绍如何排查 Foundry 调度（schedule）相关问题，建议首先查看调度器指标（scheduler metrics）页面以定位失败来源。内容涵盖已触发但失败的构建、被忽略的构建、仅构建部分数据集、未按预期触发、重试次数不符、JobSpecInputsTrashed 报错以及调度权限等多种常见场景及其处理方法。

### 7. Optimizing and debugging pipelines • Spark • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/spark-concepts/>
- **文档摘要**：本页介绍 Spark 核心概念，说明 Spark 是 Foundry 中用于大规模运行数据转换的分布式计算系统，支持 SQL、Python、Java 与 Mesa 转换。页面解释 Spark 的 driver 与 executor 架构、DAG 执行引擎与内存计算原理，并讨论如何调整 Spark profile 以在运行时间与成本间取得平衡，同时给出针对管理员与调整 profile 的推荐最佳实践。

### 8. Optimizing and debugging pipelines • Spark • Understand Spark details

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/understand-spark-details/>
- **文档摘要**：本页说明如何理解 Spark 作业的执行细节，介绍 Foundry 提供的、用于查看并分析作业在 Spark 中性能表现的内置工具。内容详细讲解从构建报告进入 Spark details 页面的入口，以及 Overview 标签页（高级作业指标、阶段执行时间线、任务并发图、阶段详情）与 Executors 标签页所展示的信息及其含义。

### 9. Optimizing and debugging pipelines • Spark • Spark UI

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/spark-ui/>
- **文档摘要**：本页介绍 Spark 自带的 Web UI，它作为 Foundry Spark details 页面的补充，提供 executor 生命周期、更丰富的任务与 executor 指标采样以及所用 Spark 配置等信息。页面说明如何以 debug job 方式重新运行作业来查看 Spark UI，并给出在 Foundry 作业中导航该 UI 的建议，包括 SQL 执行子查询的查看与 context warming 机制。

### 10. Optimizing and debugging pipelines • Spark • Understand compute usage

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/understand-compute/>
- **文档摘要**：本页说明如何使用 Resource Management 应用理解 Palantir 平台中的计算用量，指出计算用量以 Foundry compute-seconds（计算秒）为单位度量。页面列举了 Code Repositories、Code Workspaces、Compute Modules、Contour、Foundry Machine Learning、Foundry Streaming、Ontology、AIP Logic 等会消耗计算资源的应用。

### 11. Optimizing and debugging pipelines • Spark • Native acceleration

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/native-acceleration/>
- **文档摘要**：本页介绍通过 Velox 启用 native acceleration（原生加速）以提升 Spark 性能的技术，该技术借助底层硬件优化将计算从 JVM 语言转移到 C++ 等原生语言运行。内容涵盖在 Spark Details 的 Query Plan 中分析原生加速构建、其实现架构、为何更快，以及内存配置注意事项；native acceleration 可用于 Python transform 与 Pipeline Builder。

### 12. Optimizing and debugging pipelines • Spark • Apply Spark profiles

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/apply-spark-profiles/>
- **文档摘要**：本页说明如何将自定义 Spark profile 应用到 transform 作业，以便为特定作业指定 Spark 属性。页面演示先在代码库中导入 Spark profile，再通过代码引用该 profile，并分别给出 Python（@transform.spark.using 配合 profiles 参数）、Java（TransformProfiles 注解）与 SQL 中配置自定义 profile 的语法示例，且后定义的设置优先级更高。

### 13. Optimizing and debugging pipelines • Spark • Managed profiles

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/managed-profiles/>
- **文档摘要**：本页介绍 Foundry 的 managed profiles（托管 profile）功能，可基于作业历史资源用量自动优化 Spark profile。页面说明启用该功能需满足最近 5 次运行均成功等资格条件，并解释推荐逻辑：对各资源维度取最近 5 次运行的最大观测值（不超过原始请求值），对低并行度作业可能建议 local mode，最后给出启用托管 profile 的方法。

### 14. Optimizing and debugging pipelines • Spark • Spark profiles reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/spark-profiles-reference/>
- **文档摘要**：本页是 Foundry 中可用 Spark profile 的参考手册，逐项说明各 profile 家族所控制的 Spark 配置及其适用场景。内容涵盖 driver cores、driver memory、executor cores、executor memory、executor memory overhead、executor 数量、动态分配、自适应查询执行、每任务核心数、Arrow、Kubernetes、列统计以及完整的 profile 对照表。

### 15. Optimizing and debugging pipelines • Spark • Spark optimization

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/spark-optimization/>
- **文档摘要**：本页讲解优化 Spark 管道的策略与最佳实践，覆盖构建高效管道的基础与高级概念。基础优化强调尽早减少数据量、合理重分区（repartition）与清洗数据；高级优化涵盖 coalesce、按值重分区、哈希分区（分桶与排序）、Hive 分区、join 优化、拆分 transform 以及增加资源等具体手段。

### 16. Optimizing and debugging pipelines • Hive-style partitioning • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/hive-style-partitioning/>
- **文档摘要**：本页介绍 Hive-style partitioning（Hive 风格分区）方法，用于在数据集中优化数据布局以显著提升按特定列过滤的查询性能。页面说明在写入输出数据集时按分区列的唯一值组合写出独立文件并在路径中记录分区值，给出 Python 与 Java 的配置示例，并提醒使用 repartitionByRange 避免生成过多小文件、且该方法不适合高基数（high cardinality）列。

### 17. Optimizing and debugging pipelines • Dataset projections • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/projections-overview/>
- **文档摘要**：本页介绍 dataset projections（数据集投影），可针对过滤、join、聚合等多种查询模式优化查询性能。投影由 Noho 服务管理，仅能添加到 append-only 数据集，支持快照与增量构建的数据集，且不支持 schema 演变；页面列举了按列过滤、按列 join、聚合、读取增量管道与查询上传数据等典型使用场景。

### 18. Optimizing and debugging pipelines • Dataset projections • Set up a projection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/projections-setup/>
- **文档摘要**：本页引导完成启用、配置并构建数据集投影（projection）的全过程。步骤包括：在数据集 schema 中配置 noho: true 启用投影、进入 Projections 标签页、打开创建对话框、选择投影列与投影类型（filter-optimized 或 join-optimized）、创建投影并配置构建，投影由 Noho 服务管理。

### 19. Optimizing and debugging pipelines • Dataset projections • Advanced details

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/projections-advanced/>
- **文档摘要**：本页深入讲解 dataset projections 的内部工作机制，说明投影在底层是数据集的副本（存为子数据集 "projection datasets"），由 Noho 服务管理并随主数据集的构建保持更新。内容涵盖投影数据集、投影构建（含 Spark profile 与成本）、自动 compaction、查询规划（如何选择投影）以及 retention 等高级细节，并强调投影不会改变读取结果。

### 20. Optimizing and debugging pipelines • Dataset projections • Comparison with hive-style partitioning

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/projections-vs-hive-style-partitioning/>
- **文档摘要**：本页对比 dataset projections 与 hive-style partitioning 两种优化技术的差异，二者在过滤优化场景上有部分重叠。页面从自动 compaction、立即可用性、数据内容约束、高基数列适用性以及支持的消费引擎（如 Spark、Polars）等方面逐项比较，指出投影适合高基数列（尤其时序时间戳），而 Hive 分区写后立即生效但会产生过多小文件。

### 21. Optimizing and debugging pipelines • Optimizing pipelines • Usage optimization

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/optimizing-pipelines/usage-optimization/>
- **文档摘要**：本页提供优化 Foundry 用量的方法与最佳实践，先讲解用量的构成，再说明如何识别浪费并优化管道。内容建议避免过早优化、先理解 Foundry compute、Ontology volume 与 storage 三大用量组成部分，并通过 Resource Management 应用、Projects 结构、resource queues、增量管道（snapshot/append 事务）与调度管理（消除重复与不必要的 schedule）等步骤降低消耗。
