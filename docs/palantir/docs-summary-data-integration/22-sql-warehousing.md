# SQL 数仓（SQL Warehousing）

> **分类说明**：SQL Warehousing：在 Foundry 中使用 SQL 查询数据、物化视图、联邦查询与仓库管理

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `sql-warehousing` |
| 文档数量 | 13 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. SQL in Foundry • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/overview/>
- **文档摘要**：本页概述 Foundry 的 SQL 能力。Foundry 使用统一 Spark SQL dialect，可针对表格数据与 ontology object types 执行查询，并支持 SQL Studio、嵌入式 SQL console、Arrow Flight SQL 或 SQL REST API 接入外部客户端。

### 2. SQL in Foundry • SQL Studio

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-studio/>
- **文档摘要**：本页介绍 Foundry 专用的 SQL 编写与运行应用 SQL Studio。它基于 Furnace 与 Ontology SQL 两套引擎，支持 Data/Object 两种模式，可进行读写、结果预览与可视化、AI 辅助查询生成，并支持 worksheets 与发布 Ontology SQL functions。

### 3. SQL in Foundry • SQL worksheets

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/worksheets/>
- **文档摘要**：本页说明如何在 SQL Studio 中通过 worksheets 组织 SQL 工作。用户可从 scratchpad 起步，保存为私有或与项目共享的工作表，每次手动保存都会生成版本以便回溯；还介绍了共享编辑权限及将项目工作表发布为 Ontology SQL function 的前提要求。

### 4. SQL in Foundry • SQL console

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-console/>
- **文档摘要**：本页介绍 Foundry 内嵌的 SQL console，可在查看数据集或 object type 时提供上下文 SQL 访问。它支持自动补全、内联结果面板，并可将查询保存为 worksheet；在 Ontology Manager 提供 Data/Object 两种模式，并可一键展开至 SQL Studio。

### 5. SQL in Foundry • SQL permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-permissions/>
- **文档摘要**：本页说明管理 Foundry SQL 访问权限的角色与操作，涵盖 SQL Studio、嵌入式 SQL console 及外部客户端。页面列出 preview、read、download、worksheet 读写等操作权限，并介绍自定义角色配置与 AI 辅助查询生成的权限要求。

### 6. SQL in Foundry • Ontology SQL functions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-functions/>
- **文档摘要**：本页介绍 Beta 阶段的 Ontology SQL functions，可用 SQL 定义可复用的参数化查询，发布后可用于 Workshop、Actions、Automate 等。说明如何在 SQL Studio 的 Object mode 编写函数、使用参数，保存并发布为 worksheet 的步骤（函数只读）。

### 7. SQL in Foundry • SQL dialect

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-dialect/>
- **文档摘要**：本页是 Foundry SQL dialect 的参考手册，其语法为带 ANSI 兼容性的 Spark SQL 子集。提供常用操作、聚合/字符串/日期/数组等函数与表引用说明，并涵盖表创建与变更（INSERT/UPDATE/DELETE）、事务块、CTE、SELECT 与 SQL hints 等语法。

### 8. SQL in Foundry • Manage SQL definitions for an output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-definitions/>
- **文档摘要**：本页说明如何管理 Furnace SQL 输出的多个 SQL definitions。不同于其他计算流程，表格输出可保留多个定义，其中一个标记为 Active 用于重建。介绍如何在 Dataset Preview 查看定义、创建额外的定义（含 adhoc hint 与回填历史数据）以及查看数据血缘。

### 9. SQL in Foundry • Furnace

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/furnace/>
- **文档摘要**：本页介绍 Foundry 现代 SQL 数仓引擎 Furnace。其架构将查询解析/规划与执行解耦，借助 Apache Calcite 实现引擎无关的规划层，并可在多种计算引擎间按工作负载自动选择最佳执行引擎，统一通过 Arrow Flight SQL 等接口提供一致体验，支持 Iceberg 等现代表格式。

### 10. SQL in Foundry • Ontology SQL

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/ontology-sql/>
- **文档摘要**：本页介绍 Foundry 的 ontology 对象 SQL 引擎 Ontology SQL（Beta），采用与 Furnace 相同的 ANSI 兼容 Spark SQL dialect，通过 ontology data providers 将对象属性、主键与链接映射为 SQL 表结构并直接访问存储，列出规模等限制。

### 11. SQL in Foundry • SQL examples

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/sql-examples/>
- **文档摘要**：本页提供 Foundry 常见 SQL 任务的操作示例：Data mode 下用 Furnace 创建/读取数据集与 Iceberg 表（含增删改），以及 Object mode 下用 Ontology SQL 查询 object type 与 many-to-many link，并附故障排查说明。

### 12. SQL in Foundry • Arrow Flight SQL

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/arrow-flight-sql/>
- **文档摘要**：本页介绍 Foundry 对 Arrow Flight SQL 协议的支持（Beta），可将任意 Flight SQL 客户端连接到 Foundry 而无需定制配置。文档说明 Foundry 资源到数据库概念的映射、JDBC 驱动的安装与连接字符串配置步骤，以及时区显示注意事项与所需角色权限。

### 13. SQL in Foundry • Compute usage

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/sql-warehousing/compute-usage/>
- **文档摘要**：本页说明 Foundry SQL 数仓的算力消耗与计费归属。运行查询或构建按 compute-seconds 计量，并按查询类型（Furnace direct read、其他 SELECT、写操作、Ontology SQL 等）列出适用资源、算力费率与用量归属方式，并解释 direct read 免收算力的规则。
