# SQL 转换（Transforms SQL）

> **分类说明**：SQL 转换开发指南：在 Foundry 中编写与运行 SQL 转换

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-sql` |
| 文档数量 | 2 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. SQL • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-sql/overview/>
- **文档摘要**：本页介绍 Foundry 中的 SQL transforms。它允许在 Code Repositories 中用 Spark SQL 编写数据转换，支持过滤、聚合、派生列、窗口函数等能力，适合把 SQL 作为代码库的一部分维护而非在交互式编辑器中开发。Foundry 不支持 SQL 增量转换。

### 2. SQL • Spark SQL Reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-sql/spark-reference/>
- **文档摘要**：本页提供 Spark SQL 参考文档，说明在 Foundry 中编写 Spark SQL 数据转换与其他 SQL 查询的关键差异，并列出可用函数。内容涵盖基本查询格式（CREATE TABLE ... AS SELECT）、注释语法、数据集与列引用、派生列及别名、类型转换、日期格式化，以及各种聚合函数（如 APPROX_COUNT_DISTINCT、AVG、COUNT 等）。
