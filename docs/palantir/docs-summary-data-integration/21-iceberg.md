# Iceberg 表

> **分类说明**：Apache Iceberg 表管理：创建、写入、维护、演化与在 Foundry 中查询 Iceberg 表

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `iceberg` |
| 文档数量 | 30 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Iceberg tables • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/overview/>
- **文档摘要**：本页概述 Foundry 中的 Iceberg tables。介绍 Apache Iceberg 作为开源表格式的背景，Foundry 如何通过原生 Iceberg REST catalog 实现托管与外部（virtual）Iceberg 表，以及 managed 与 virtual 表的区别、适用场景和入门路径，帮助用户了解何时选择 Iceberg 表。

### 2. Iceberg tables • Iceberg tables & Foundry datasets

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/iceberg-vs-datasets/>
- **文档摘要**：本页对比 Iceberg tables 与 Foundry datasets 两种表格资源类型。说明 Iceberg 表的优势（跨生态互操作性、compaction、行级 DELETE/UPDATE/MERGE、changelog、增强历史视图），并列出暂不支持的 Foundry 功能（视图、受限视图、流时序同步等）及细粒度访问控制、Marketplace、术语消歧等关键差异。

### 3. Iceberg tables • Iceberg storage architecture & settings • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/storage/>
- **文档摘要**：本页介绍 Foundry 中 Iceberg 的存储架构与设置。区分 managed Iceberg 表与 virtual Iceberg 表（由 Glue、Horizon、Polaris、Unity 等外部 catalog 管理），说明 catalog 与存储配置组合、AWS/Azure/GCP 上的存储选项，以及服务端加密（SSE，必选）与客户端加密（CSE，可选）和按项目的存储设置。

### 4. Iceberg tables • Iceberg storage architecture & settings • Configuring Iceberg settings in Control Panel

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/iceberg-settings/>
- **文档摘要**：本页说明如何在 Control Panel 中配置 Iceberg 设置。涵盖所需权限（Enrollment Administrator 或 Information Security Officer）、确认 Iceberg 已启用、配置加密（SSE 与 CSE）与存储位置（Foundry 托管或 BYOB），以及 enrollment 级默认值与项目/命名空间级覆盖，并介绍如何修改既有设置。

### 5. Iceberg tables • Iceberg storage architecture & settings • BYOB storage setup

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/iceberg-byob/>
- **文档摘要**：本指南分步骤介绍如何为 Foundry Iceberg 表配置客户自管存储桶（BYOB）。依次说明：在 AWS S3、Azure ABFS、Google GCS 上创建存储桶并配置权限；创建 Data Connection source；在 Control Panel 中添加该存储桶。仅当使用 BYOB 存储时才需执行这些步骤。

### 6. Iceberg tables • Iceberg storage architecture & settings • Format versions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/versions/>
- **文档摘要**：本页提供 Foundry 对 Iceberg 表格式版本的支持说明。Foundry 支持读取与写入 format version 2 和 3，默认版本取决于是否启用 CSE（v2 为默认，CSE 启用时为 v3），并说明如何通过 format-version 表属性覆盖默认值（v2 可升级至 v3 但不可降级）以及各版本不支持的列类型。

### 7. Iceberg tables • Catalog behavior • Transactions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/transactions/>
- **文档摘要**：本页说明 Foundry Iceberg catalog 的事务（Transactions）行为。Foundry 在构建系统中提供 all-or-nothing 的原子提交，封装作业内所有读写，保证部分更新不可见；而标准 Iceberg 仅提供单写原子更新并依赖乐观并发。该语义仅适用于 Foundry 构建作业，外部客户端写入沿用标准 Iceberg 行为。

### 8. Iceberg tables • Catalog behavior • Branching

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/branching/>
- **文档摘要**：本页介绍 Foundry Iceberg catalog 的分支隔离（Branch isolation）。Foundry 扩展标准 Iceberg，使每个分支可独立跟踪各自的 schema、partition spec 与 sort order，隔离于 main（master）分支；标准 Iceberg 中 schema 为表级共享。还说明如何在外部客户端中使用 X-PLTR-Branch 头启用该行为及相关限制。

### 9. Iceberg tables • Catalog behavior • Properties

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/properties/>
- **文档摘要**：本页列出 Iceberg 表元数据中的表属性（table properties）。分为 Foundry 专有属性（如 created-at、foundry.rid、foundry.branch.*）与 Iceberg 规范属性（如 encryption.*、format-version、history.expire.max-snapshot-age-ms），说明 Foundry 的默认值及用户可否修改，并演示如何在 transforms 中设置特定表属性。

### 10. Iceberg tables • Working with Iceberg in Foundry • Snapshots

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/snapshots/>
- **文档摘要**：本页介绍 Iceberg 表的 snapshot（快照）概念。snapshot 记录表在某一时刻由哪些数据文件构成，每次写入都会生成新 snapshot；说明其工作机制、在 Foundry 的 Dataset Preview 中查看方式、Append/Overwrite/Delete/Replace 四类快照类型，以及 snapshot 如何映射到 Foundry 数据集事务。

### 11. Iceberg tables • Working with Iceberg in Foundry • Maintenance procedures

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/maintenance-page/>
- **文档摘要**：本页介绍 Foundry Iceberg 表的 Maintenance 标签页，用于配置与运行维护作业。说明如何访问维护页、添加/编辑/删除维护任务，以及支持的任务类型：Rewrite data files、Rewrite manifests、Expire snapshots、Remove orphan files 与 Custom SQL，用以优化文件布局、控制快照过期和清理孤立文件。

### 12. Iceberg tables • Working with Iceberg in Foundry • Data pipelines

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/data-pipelines/>
- **文档摘要**：本页概括如何使用 Foundry Iceberg 表构建数据管道。介绍可选的管道编写方式（Python transforms 与 Pipeline Builder），并以表格对比各方式的计算引擎、非增量/追加增量/changelog 增量及客户端加密支持情况，帮助用户在 Spark、单节点及 Pipeline Builder 之间选择合适的工作流。

### 13. Iceberg tables • Working with Iceberg in Foundry • Data Connection syncs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/syncs/>
- **文档摘要**：本页说明如何使用 Data Connection 将外部源数据同步到 Foundry Iceberg 表。列出已支持的源类型（Custom JDBC、SQL Server、Oracle、PostgreSQL、Snowflake），并介绍 Overwrite、Append、Update 三种写入模式及基于自定义 SQL 的增量同步；同时指出 Iceberg 同步暂不支持 schema evolution。

### 14. Iceberg tables • Working with Iceberg in Foundry • Jupyter® in Code Workspaces

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/jupyter/>
- **文档摘要**：本页介绍如何在 Code Workspaces 的 Jupyter® notebook 中与 Iceberg 表交互。支持通过 PyIceberg、SQL 或 Spark 三种方式读取与写入；对 virtual Iceberg 表，SQL 不需将源加入工作区。还提供 Spark 高级用法的环境配置步骤（JAR 上传、网络策略）及示例 notebook 代码。

### 15. Iceberg tables • Python transforms • Transforms with Iceberg

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/python-transform-basics/>
- **文档摘要**：本页为在 Python transforms 中使用 Iceberg 表的入门指南。介绍核心组成部分：通过 transforms.tables 库声明 Iceberg 表输入（TableInput）与输出（TableOutput）、运行时对象（IcebergInput/IcebergOutput 等）以及计算逻辑，并给出访问底层 Iceberg 表的高级用法，适用于在 Foundry 上编写 Iceberg 管道。

### 16. Iceberg tables • Python transforms • Code examples

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/code-examples/>
- **文档摘要**：本页提供使用 transforms.tables 库在 Python transforms 中读写 Iceberg 表输入与输出的代码示例，涵盖 Polars、DuckDB、pandas 和 PySpark 四种引擎。每个示例演示如何生成简单的 Iceberg 表，以及在不同输入/输出组合（Iceberg 表与 dataset 互操作）下的写法。

### 17. Iceberg tables • Python transforms • Advanced Iceberg APIs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/advanced-apis/>
- **文档摘要**：本页介绍如何直接调用底层 Iceberg API 来执行标准 dataframe 语法无法表达的操作。内容包括 PySpark 中的行级编辑（UPDATE/DELETE/MERGE INTO）、分区增量写入，以及 PyIceberg 中的行级编辑和 snapshot 对比，均通过运行时对象句柄接入开源 Iceberg API。

### 18. Iceberg tables • Incremental and CDC • Incremental Iceberg processing

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/incremental-overview/>
- **文档摘要**：本页从概念层面概述如何对 Iceberg 表进行增量处理，即仅处理自上次运行以来发生变化的数据。文中总结了各计算引擎对增量构建的支持情况，并说明 Ontology 对象类型的增量索引、变更读取与写入，以及 copy-on-write 与 merge-on-read 两种模式的差异。

### 19. Iceberg tables • Incremental and CDC • Incremental Iceberg transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/incremental-transforms/>
- **文档摘要**：本页以语法层面介绍如何在 Iceberg 表上编写增量 Python transforms。要点包括添加 incremental 装饰器与设置读取模式（append_only / changelog）、使用增量运行时对象，以及编写增量计算逻辑；并说明了 v2_semantics、require_incremental 等参数的含义。

### 20. Iceberg tables • Incremental and CDC • Incremental code examples

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/incremental-code-examples/>
- **文档摘要**：本页提供增量 Iceberg transforms 的代码示例，分别演示使用 Foundry API 与原生 Iceberg 写入 API 的 append-only 增量，以及使用 Foundry API 与 Iceberg API 的 changelog 增量。涵盖 Polars、DuckDB、pandas 和 PySpark 多种计算引擎。

### 21. Iceberg tables • Incremental and CDC • Changelogs and CDC

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/changelogs/>
- **文档摘要**：本页介绍如何使用 Iceberg changelog 在 PySpark transforms 中构建变更数据捕获（CDC）流水线。通过增量转换、从输入表生成 changelog、将变更应用到输出表并处理非增量构建，可在不重复处理全量输入的情况下应用插入、更新与删除。

### 22. Iceberg tables • Incremental and CDC • Changelog API technical primer

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/changelog-primer/>
- **文档摘要**：本指南深入讲解在 Python transforms 中使用 Iceberg changelog 的技术细节。涵盖 identifier changelog 与 net changes changelog 两种模式的语法与实现原理、示例演变，以及源表与目标表的唯一性要求和常见陷阱，最后给出摘要与最佳实践。

### 23. Iceberg tables • Governance • Permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/permissions/>
- **文档摘要**：本页列出 Foundry Iceberg 表的默认权限角色与操作，包括读写 Iceberg 表的 table:read / table:write 操作、main 分支保护（Beta）所需的绕过权限，以及维护任务的读/写操作权限。管理员可通过自定义角色集调整这些默认值。

### 24. Iceberg tables • Governance • Markings

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/markings/>
- **文档摘要**：本页说明 Iceberg 表的 marking（敏感数据访问标记）机制。与 dataset 不同，Iceberg 的 marking 是表级作用域的，某一分支上的标记可能影响 main 及其他分支的读取。文中介绍如何停止标记传播或清除过期标记历史来移除继承的 marking。

### 25. Iceberg tables • Governance • Table retention

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/retention/>
- **文档摘要**：本页介绍如何通过 snapshot 过期与孤立文件移除实现 Foundry Iceberg 表的保留与清理。重点说明 expire snapshots 维护任务的配置（最小保留快照数、最小保留时长），以及延迟文件删除对灾难恢复的影响，并给出实践建议。

### 26. Iceberg tables • Governance • Resource usage

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/resource-usage/>
- **文档摘要**：本页说明使用 Iceberg 表所产生的 Foundry 计算与存储用量及其在资源管理应用中的呈现方式。计算以 compute-seconds 计量，存储以 gigabyte-months 计量（涵盖各分支/标签引用的数据文件），并介绍如何通过 snapshot 过期等手段管理存储用量。

### 27. Iceberg tables • Connecting external clients • Authenticating Iceberg clients

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/authentication/>
- **文档摘要**：本页介绍外部 Iceberg 客户端如何与 Foundry 的 Iceberg REST catalog 进行身份认证。可选方式包括 API token（Bearer）与 OAuth2 客户端凭证，并说明了访问委派与凭证发放机制；最后提供连通性验证与凭据排查的故障排查建议。

### 28. Iceberg tables • Connecting external clients • Configuring network access

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/external-client-ingress/>
- **文档摘要**：本页介绍如何为连接 Foundry Iceberg catalog 的外部客户端配置网络访问。需要为 Iceberg REST catalog 与底层存储桶两个端点分别建立网络入口（ingress），并说明了如何识别存储桶信息、配置访问权限以及验证连通性。

### 29. Iceberg tables • Connecting external clients • Jupyter® in local notebooks

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/local-jupyter/>
- **文档摘要**：本页提供在本地 Jupyter 笔记本中使用 PyIceberg 读写 Foundry Iceberg 表的代码示例。示例演示如何通过 REST catalog 客户端在 Foundry 中创建表、列出表、向表中追加本地 Parquet 数据以及将表读回 Pandas，并说明 Iceberg 命名空间到 Compass 路径的映射。

### 30. Iceberg tables • Connecting external clients • Connect Foundry's Iceberg catalog to Snowflake

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/iceberg/snowflake-iceberg-integration/>
- **文档摘要**：本页介绍如何将 Foundry 的 Iceberg catalog 通过 Apache Iceberg REST catalog 集成连接到 Snowflake。包含创建 catalog 集成、验证集成、注册并读取表、可选写入表以及关联到数据库等步骤，并说明当前不支持读取客户端加密表的限制。
