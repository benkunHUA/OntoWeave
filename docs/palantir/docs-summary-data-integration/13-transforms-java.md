# Java 转换（Transforms Java）

> **分类说明**：Java 转换（Transforms Java）开发指南：项目结构、库依赖与 API

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-java` |
| 文档数量 | 12 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Java • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/overview/>
- **文档摘要**：本页介绍 Transforms Java 的概览。Java transforms 允许使用常见 Java 库进行数据转换，并提供高层与低层两类 API 来操作 Foundry 数据集，同时支持批处理与增量（incremental）流水线，以及创建和共享可复用的代码库。页面引导读者从入门教程开始，并可进一步探索基础 transform、增量 transform、单元测试、非结构化文件处理、代码共享与高级配置等主题。

### 2. Java • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/getting-started/>
- **文档摘要**：本页提供 Transforms Java 的快速入门指引。页面逐步演示如何创建 Java 转换仓库（Repository）、导入 titanic 示例数据集，并通过修改模板中的 @Compute、@Input、@Output 注解与 compute 函数，编写第一个按性别过滤乘客的高层 Transform。完成后提交分支即可构建输出数据集，适合初次接触 Java 数据转换的用户。

### 3. Java • Basic transforms • Transforms and pipelines

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/transforms-pipelines/>
- **文档摘要**：本页介绍 Transforms Java 中 Transform 与 Pipeline 的基本概念。Transform 描述如何计算数据集，包含输入输出数据集、计算函数及额外配置；文档说明高层与低层 Transform 的差异、自动或手动注册方式的区别、Entry Point 机制以及向 Pipeline 添加 Transform 的方法。在 Java API 中 DataFrame 以 Spark 的 Dataset<Row> 形式呈现。

### 4. Java • Basic transforms • Examples

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/transforms-examples/>
- **文档摘要**：本页提供 Transforms Java 中定义 Transform 的代码示例。内容包括高层 Transform（支持 Dataset<Row> 输入输出的自动与手动注册）以及低层 Transform（需显式调用 asDataFrame() 访问数据）的多种写法示例，覆盖了基于 DataFrame 对象的常见数据读取、处理与写入模式，供开发者参考实现。

### 5. Java • Basic transforms • Read and write unstructured files

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/unstructured-files/>
- **文档摘要**：本页说明如何使用 Transforms Java 读写非结构化文件。页面给出若干以文件为单位的数据转换示例，包括利用 Spark 并行处理、解压 .zip 文件并写入输出 FileSystem 或输出 DataFrame，以及合并数据集文件等。由于需要访问底层数据集文件，此类任务必须使用 low-level Transform 并通过 FoundryInput 与 FoundryOutput 对象操作。

### 6. Java • Basic transforms • Unit tests

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/unit-tests/>
- **文档摘要**：本页介绍 Transforms Java 的单元测试能力。Java Transforms 支持在 CI 检查中运行单元测试，但仅适用于批处理流水线，不支持流式流水线，也不支持交互式调试。文档说明如何在子项目中应用 com.palantir.transforms.lang.java-defaults 插件引入 JUnit、Mockito、AssertJ，并借助 JUnit5 扩展或 JUnit4 规则获取托管的 SparkSession 进行基于 Spark 的测试。

### 7. Java • Basic transforms • Advanced configuration

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/advanced-configuration/>
- **文档摘要**：本页说明 Transforms Java 的高级配置选项。文档重点介绍「最大构建时长（Maximum build duration）」配置，可通过 MaxAllowedDuration 与 @Compute 装饰器限制任务运行时间，以确保数据新鲜度或控制成本（例如应对外部服务无响应）。由于任务按 5 分钟轮询，实际取消时间会向上取整到最近的轮询点。

### 8. Java • Incremental transforms

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/incremental-transforms/>
- **文档摘要**：本页介绍 Transforms Java 的增量（incremental）计算功能。增量计算属高级特性，通过感知构建历史仅对新增数据进行计算，避免每次全量重算以节省资源。文档讲解如何编写增量 Transform（解析输入、读取、转换、写出），如何用文件编写低层增量 Transform，以及快照与增量切换等最佳实践与忽略删除、忽略 schema 变更等高级选项。

### 9. Java • User-defined functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/user-defined-functions/>
- **文档摘要**：本页说明如何在 Pipeline Builder 或 Code Repositories 中创建、实现与部署用户自定义函数（UDF）。当现有转换能力或外部 Java/Python 库无法满足需求时，可编写可版本化、可复用的 UDF；文档涵盖本地与 Code Repositories 环境搭建、row map 与 flat map 等函数实现、发布至 Pipeline Builder 或部署仓库，以及代码生成、类版本等故障排查。

### 10. Java • Share code across repositories

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/share-code/>
- **文档摘要**：本页介绍在多个仓库之间共享 Java 代码的工作流。文档说明如何创建独立共享代码仓库（shared code repository），并为依赖仓库（dependent repository）配置权限与 Gradle 依赖，使其能引用共享仓库经 Jemma 自动发布的 artifact。内容包含先决条件、权限设置、Gradle 配置以及获取仓库 RID 等信息的具体步骤。

### 11. Java • Set up local development

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/local-development/>
- **文档摘要**：本页说明如何搭建 Transforms Java 的本地开发环境。文档介绍通过「Work locally」克隆仓库进行高速迭代开发的方法、克隆令牌的时效与只读限制，以及本地预览数据集的能力；并给出环境搭建先决条件（如安装 Java 17 并配置 JAVA_HOME）与在 IntelliJ IDEA 中配置 IDE 的步骤，同时说明了本地开发的若干限制。

### 12. Java • Syntax cheat sheet

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-java/spark-syntax/>
- **文档摘要**：本页提供 Java Spark SQL 常用语法速查表。作为快速参考指南，文档汇集了最常见模式与函数，包括通用模式（日志、导入、过滤、连接）、字符串、数值、日期与时间戳、数组与结构体、聚合及高级操作等示例代码片段，方便开发者在编写 Transforms Java 时快速查阅，并指向官方 Java Spark 文档获取更多信息。
