# Python 转换（Transforms Python）

> **分类说明**：Python 转换开发指南：@transform 装饰器、输入输出定义、类型系统、测试与部署

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-python` |
| 文档数量 | 39 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Python • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/overview/>
- **文档摘要**：本页概述 Foundry 中 Python 转换（Python transforms）的能力。Python 是 Foundry 中功能最完整的数据转换语言，支持批处理与增量（incremental）pipeline，支持创建与共享代码库复用，并可在代码中定义数据期望（data expectations）。Python transforms 可配置单节点（single-node）或多节点（multi-node）计算引擎，并链接到入门、测试、调试、库、外部调用等主题。

### 2. Python • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/getting-started/>
- **文档摘要**：本页是 Python transforms 的快速入门教程，引导用户完成一个简单的 Python 数据转换。教程以 NASA 陨石发现数据为例，演示如何创建 Python 代码仓库（Code repository）、编写转换将电子表格变为可分析的 dataset，并构建输出、扩展转换以及将其应用于多个输入。建议初次使用者先阅读批处理 pipeline 教程。

### 3. Python • Python version support

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/python-versions/>
- **文档摘要**：本页说明 Foundry 对 Python 版本的支持策略。自 Python 3.9 起，Foundry 遵循 Python 软件基金会的 EOL 计划，不再支持已废弃版本（存在安全风险且可能与 Spark 等技术不兼容）。页面列出各版本在 Foundry 中的支持起止时间、如何识别使用废弃版本的工作流，以及废弃（deprecation）活动的相关说明。

### 4. Python • Basic transforms • Transforms

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/transforms/>
- **文档摘要**：本页介绍 Foundry Transforms 这一数据处理 pipeline 的核心构建块，定义数据如何从输入流向输出并封装计算逻辑与资源需求。每个 transform 由装饰器（@transform）、函数签名与计算逻辑三部分组成，可配置单节点（Polars/pandas）或多节点（Spark）计算引擎，并涵盖 transform context、PySpark、Iceberg 表与事务性等内容。

### 5. Python • Basic transforms • Pipelines

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/pipelines/>
- **文档摘要**：本页介绍 Python transforms 中的 Pipeline 对象。每个 transforms 子项目会暴露一个 transforms.api.Pipeline 对象，用于在 Foundry 中注册 dataset 并定位执行构建所需的 transform 逻辑。页面对比了自动注册（discover_transforms）与手动注册（add_transforms）两种方式，并说明 transform 生成与入口点（entry points）机制。

### 6. Python • Basic transforms • Project structure

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/project-structure/>
- **文档摘要**：本页说明 bootstrapped 的 Python transforms 仓库的默认项目结构。它列出标准目录布局，并逐一介绍关键文件：pipeline.py、setup.py、examples.py 与 conda_recipe/meta.yaml（含支持的 Python 版本、运行时版本固定、pip 依赖管理）。页面还说明仓库升级（repository upgrade）时哪些文件会被覆盖、合并或保留。

### 7. Python • Basic transforms • Unit tests

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/unit-tests/>
- **文档摘要**：本页介绍如何在 Python 仓库中编写并运行单元测试（仅适用于批处理 pipeline，不支持流式 pipeline）。测试基于 pytest 框架，可在 Checks 中执行。页面说明如何启用 PEP8/Pylint 风格检查、启用与本地运行测试、编写简单及 DataFrame 测试样例、基于 CSV 创建测试输入与期望输出、查看测试结果与覆盖率，以及并行化测试等。

### 8. Python • Basic transforms • Configure resources

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/configure-resources/>
- **文档摘要**：本页介绍如何为单节点（single-node）transform 配置计算资源。通过装饰器上的 with_resources 方法可设置 cpu_cores、memory_gb/memory_mb 与 gpu_type 等参数（默认上限为 8 核、64 GB，需提升须联系 Palantir 支持）。页面还说明 GPU 资源配置以及可通过 Metrics telemetry 标签监控并调优内存与 CPU 用量。

### 9. Python • Basic transforms • Debugging

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/debugging/>
- **文档摘要**：本页概述 Python transforms 的调试技术。它介绍如何使用调试器（debugger，支持 VS Code 与 Code Repositories）设置断点、查看 DataFrame 与变量；如何阅读 Python traceback（栈追踪，最近调用位于底部）；以及通过日志（logging）与构建指标（build metrics）定位运行时问题。

### 10. Python • Basic transforms • Compute engine selection

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/compute-engines/>
- **文档摘要**：本页介绍 Python transforms 的计算引擎选择。可用的查询引擎包括 Pandas、Polars（推荐）、DuckDB 与 Spark，前三者属轻量单节点计算，Spark 用于分布式计算。页面给出各引擎功能支持对比表、适用场景（如探索性分析与大数据集）、选择建议、单节点高级选项以及从 Spark 迁移到单节点的指引。

### 11. Python • Basic transforms • Metrics

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/metrics/>
- **文档摘要**：本页介绍如何使用 Metrics telemetry 标签调试与监控构建（build）。Metrics 标签仅在作业已发出指标时显示，可通过构建报告的历史记录查看内存与 CPU 利用率时序图表。内存图用红色虚线标示请求上限（超限将触发 OOM），CPU 图用橙色虚线标示请求核数（默认 2 核），据此可调整资源请求。

### 12. Python • Basic transforms • Polars lazy API

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/polars-lazy/>
- **文档摘要**：本页介绍 Polars lazy API（惰性执行）。惰性模式在收集查询后才求值，便于引擎应用优化（推荐用于大数据量 pipeline）。设置 lazy=True 即可启用，配合 Polars 流式引擎可处理超内存数据。页面详述 filter pushdown（谓词下推）、调试惰性 pipeline、与增量转换配合、GPU 加速及适用场景。

### 13. Python • Basic transforms • DuckDB API

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/duckdb/>
- **文档摘要**：本页介绍在 Python transforms 中使用 DuckDB API 进行流式惰性执行（类似 Polars lazy API）。@transform.using 默认即为 lightweight 转换，可通过 Context 获取预配置的 DuckDB 连接，输入 dataset 已注册为未物化表。页面覆盖适用场景、增量工作流、从 .sql 文件读取、结合 Ibis/SQLFrame，以及增量输出、分区输出与 eager download 等进阶用法。

### 14. Python • Incremental transforms • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/incremental-overview/>
- **文档摘要**：本页概述增量转换（incremental transforms）。增量计算利用历史构建避免每次全量重算输出，从而提升效率；输入与输出 dataset 必须不同以防循环依赖。页面通过对比非增量与增量代码示例说明其优势，并列出限制、与 media set 配合、限制增量输入批大小等注意事项。

### 15. Python • Incremental transforms • Usage guide

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/incremental-usage/>
- **文档摘要**：本页是增量转换的使用指南，讲解 incremental() 装饰器及其输入/输出模式。它详述 IncrementalTransformInput、IncrementalTransformOutput、IncrementalTransformContext 与各类增量模式，并列出增量计算的先决条件：输入须为 append-only、快照（snapshot）输入、输出须由同一 transform 构建等，最后给出需求汇总。

### 16. Python • Incremental transforms • Abort transactions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/abort-transactions/>
- **文档摘要**：本页介绍 Python transforms 的中止事务（abort transactions）机制。当输出 dataset 未发生变化时，可在计算逻辑中调用 ctx.abort_job() 中止事务，使作业成功完成而不更新输出及下游 dataset，从而替代构建失败、节省计算资源。页面说明其与忽略作业（ignored job）及增量事务的区别，并给出自定义条件示例。

### 17. Python • Incremental transforms • Create historical dataset from snapshots

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/create-historical-dataset/>
- **文档摘要**：本页介绍如何从快照（snapshot）创建历史 dataset。当原始数据以 SNAPSHOT 每日/每周覆盖更新、且无法在摄取阶段改为 APPEND 时，可使用 incremental 装饰器将定期快照追加为历史版本，并建议增加导入日期列。页面给出工作流概览、示例代码、累积验证方法及相关警告（如资源消耗增加）。

### 18. Python • Python environment • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/environment-overview/>
- **文档摘要**：本页概述 Python 环境机制。transform 所用 Python 环境在 Checks 期间由 Hawk 包管理器依据 conda_recipe/meta.yaml 中指定的包列表解析，解析后的环境发布至 Artifacts 供构建使用；若失败则在构建时回退再用 Hawk 重新解析。页面并链接环境创建与故障排查指南。

### 19. Python • Python environment • Environment creation overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/environment-creation-overview/>
- **文档摘要**：本页是环境创建的高级概览，面向关注环境初始化性能技术细节的用户。它介绍 Conda、Mamba 与 Palantir 自研的 Hawk 包管理器，阐明 Package、Channel、Environment 等关键术语，并概述环境创建（environment creation）过程及其性能与局限性，供进阶用户参考。

### 20. Python • Python environment • Troubleshooting guide

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/environment-troubleshooting/>
- **文档摘要**：本页是 Python 环境创建的故障排查指南，帮助定位最常见的环境构建问题。涵盖 Checks 在包解析（package resolution）阶段失败（新版/旧版 Mamba 错误）、无 meta.yml 变更时的卡顿、meta.yml 变更后的作业超时、Conda 错误、Entry Point 错误，以及避免依赖冲突的最佳实践与其他问题。

### 21. Python • Libraries • Discover and use Python libraries

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/use-python-libraries/>
- **文档摘要**：本页介绍如何在 Code Repositories 中发现与使用 Python 库。文档说明了通过左侧 package 面板搜索并添加公共库与 Foundry 生成库的方法，包括添加特殊依赖、将特定库版本固定到 meta.yaml、审阅库变更，以及 Artifact 仓库设置、Task Runner 与高级设置（meta.yaml 文件、外部下载库、运行任务）等内容。

### 22. Python • Libraries • Share Python libraries

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/share-python-libraries/>
- **文档摘要**：本页说明如何跨多个 Python transforms 仓库共享代码，推荐做法是将代码发布为 Conda Python 库。文档提供了发布 Python 库的完整步骤：创建仓库、命名与包结构、编写模块、打 SLS 标签触发发布，以及如何查找某个库的使用方（consumers）。

### 23. Python • Libraries • Lightweight API evolution

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/lightweight-api-evolution/>
- **文档摘要**：本页梳理 lightweight 计算的 transforms API 演进历史，并列出各语法选项。文档对比了旧版 @lightweight 装饰器与新版 @transform.using（含 .lightweight/.spark/.databricks 变体）写法，并针对 @transform.using 的版本报错给出排障建议。新 API 需 transforms 3.68.0 及以上及相应插件版本。

### 24. Python • Set up local development

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/local-development/>
- **文档摘要**：本页介绍如何搭建 Python transforms 仓库的本地开发环境，以实现高速迭代。文档给出两种方案：推荐使用 Palantir 的 VS Code 扩展（支持全量数据集预览、本地构建、调试与单元测试），或克隆仓库后使用 Gradle 命令；并涵盖前提条件、扩展优势、常见语言服务器故障排查及本地运行单元测试等。

### 25. Python • Virtual tables and compute pushdown • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/tables-overview/>
- **文档摘要**：本页概述在 Python transforms 中使用虚拟表（virtual tables）的方式。虚拟表允许在不把数据存入 Foundry 的前提下查询和写入受支持数据平台的表，并支持 Foundry 原生 Spark 计算与计算下推（compute pushdown）两种模式。文档还列出使用前提、API 参考、快速示例、JDBC 分区与文件模板配置向导等。

### 26. Python • Virtual tables and compute pushdown • API reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/tables-api/>
- **文档摘要**：本页是 transforms-tables 库的 API 参考，说明如何在 Python transforms 中操作虚拟表。文档介绍 TableInput 与 TableOutput 参数（行为类似 dataset 的 Input/Output），并列出 BigQueryTable、DatabricksTable、SnowflakeTable 等子类；同时强调虚拟表创建后其 source 与 location 不可更改，否则校验将失败。

### 27. Python • Virtual tables and compute pushdown • Compute pushdown

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/tables-compute-pushdown/>
- **文档摘要**：本页介绍计算下推（compute pushdown）概念：由 BigQuery、Databricks 或 Snowflake 连接支持的虚拟表，可将 Foundry 编写的 transform 下推到对应数仓执行。文档说明使用该能力的前提（所有输入/输出须为同一源连接且支持下推），并指向各数据源（BigQuery、Databricks、Snowflake）的专属下推文档与代码示例。

### 28. Python • Virtual tables and compute pushdown • BigQuery compute pushdown

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/tables-bigquery/>
- **文档摘要**：本页说明如何对 BigQuery 启用计算下推。文档要求创建 Python 仓库并安装最新版 transforms-tables 库，系统会依据输入输出表的连接信息建立 Ibis 连接，使用 Ibis DataFrame API 完成数据转换；并给出基于 @transform.bigquery.using 的代码示例，同时指出当前下推模式不支持 @incremental 增量计算。

### 29. Python • Virtual tables and compute pushdown • Databricks compute pushdown

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/tables-databricks/>
- **文档摘要**：本页说明如何将计算下推到 Databricks（利用 Databricks Connect 与虚拟表）。文档通过快速上手、环境设置、API 要点（输出表类型、计算配置、用户自定义函数 UDF）等章节，演示将原有 @transform.spark 的 PySpark 管道改写为 @transform.databricks.using 的 Databricks transform，从而在 Databricks 环境中完整执行。

### 30. Python • Virtual tables and compute pushdown • Snowflake compute pushdown

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/tables-snowflake/>
- **文档摘要**：本页说明如何对 Snowflake 启用计算下推。文档要求创建 Python 仓库并安装最新版 transforms-tables 库，系统会依据表连接配置 Snowpark 会话，用 Snowpark DataFrame API 完成转换；并涵盖计算配置、通过 Spark Connect 使用 PySpark API，以及将数据转换为 pandas DataFrame 等内容，配有 @transform.snowflake.using 示例。

### 31. Python • Data expectations • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/data-expectations-getting-started/>
- **文档摘要**：本页快速介绍如何在 Python transforms 仓库中配置数据期望（data expectations）。文档说明了安装 transforms-expectations 库、导入 Check 与 expectations、按引擎选择 Polars 或 Spark 类型库，以及创建检查（Check）的基本结构（expectation、唯一名称、on_error 为 WARN/FAIL）与复杂检查的写法。

### 32. Python • Data expectations • Data expectations reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/data-expectations-reference/>
- **文档摘要**：本页是数据期望（data expectations）的完整参考，按类别列出所有可用期望。文档涵盖操作符（E.all/E.any/E.negate 等）、列期望（大小比较、列间比较、空值、正则 rlike、类型 has_type、存在性）、时间戳期望与数组期望等，并给出以 E.col('column_name') 开头的调用示例与代码。

### 33. Python • Media and unstructured data • Use media sets with Python transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/media-sets/>
- **文档摘要**：本页介绍如何在 Python transforms 中使用媒体集（media sets）处理非结构化数据。文档涵盖导入 transforms-media 库、以 @transform 配合 MediaSetInput/MediaSetOutput 读写媒体集、媒体集写入模式、从 Catalog 数据集上传文件、轻量计算支持，以及内置转换（PDF 转图、OCR 文本、音频转写等）。

### 34. Python • Media and unstructured data • Media set transforms API reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/media-set-transforms-api/>
- **文档摘要**：本页是媒体集转换 API（media set transforms API）的参考文档。文档列出按 schema 类型（Image、Document、Video、Audio、DICOM、Spreadsheet、Email、Multimodal）划分的可用方法，如 ocr、extract_raw_text、resize、convert_document_to_images、tile、transcribe 等，并介绍 transform()、write() 等入门用法与参数。

### 35. Python • Media and unstructured data • Unstructured files

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/unstructured-files/>
- **文档摘要**：本页说明如何在 Python transforms 中读写非结构化文件（如 XML、JSON、gz、zip）。文档介绍通过 TransformInput/TransformOutput 暴露的 FileSystem 对象导入、浏览、读取（解析 Excel、随机访问）与写入文件，以及使用普通方式或 PySpark 进行并行化处理，并指出文件访问属于进阶主题。

### 36. Python (Spark) • Output column metadata

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/output-column-metadata/>
- **文档摘要**：本页说明如何在 Code Repository Transforms（Python/Spark）中读写输出数据集的列元数据。文档介绍通过 write_dataframe() 的 column_descriptions 参数为列添加描述（最长 800 字符），以及通过 column_typeclasses 属性读写列类型类（typeclasses），并给出相应的代码示例。

### 37. Python • Advanced usage • Advanced compute options

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/advanced-compute/>
- **文档摘要**：本页介绍 Python transforms 的高级单节点计算选项，用于在特殊场景下扩展能力。文档说明可直接以 pandas 读取 Spark 无法处理的 Parquet 文件、借助 my_input.path() 把底层文件传给 Apache DataFusion 执行，以及使用 cuDF、基于 GPU 的 Polars 加速计算（含环境设置与写入方式）。

### 38. Python • Advanced usage • Bring your own container

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/bring-container/>
- **文档摘要**：本页介绍 Bring your own container（BYOC）工作流，允许在 Foundry 中以非 Python 引擎或脚本执行计算。文档说明可本地构建包含所需依赖或二进制的 Docker 镜像并运行轻量 transform，并以 COBOL 程序为例演示启用容器化、构建与上传镜像、添加本地 backing 仓库及编写轻量 transform 的完整过程。

### 39. Python • Advanced usage • File processing

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-python/file-processing/>
- **文档摘要**：本页介绍文件处理工作流：轻量 transform 可处理无 schema 的数据集文件。文档说明用 my_input.filesystem().ls() 列举无 schema 数据集的文件（有 schema 的数据集才可用 .path()/.pandas()/.polars() 等方法），并给出以 Polars 与线程池并行处理 CSV、解析 Excel 文件的代码示例。
