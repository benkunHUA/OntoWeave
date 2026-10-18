# 数据集预览（Dataset Preview）

> **分类说明**：数据集预览（Dataset Preview）：在构建管道前预览与校验数据集内容

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `dataset-preview` |
| 文档数量 | 4 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Dataset Preview • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/dataset-preview/overview/>
- **文档摘要**：本页概述 Dataset Preview 应用，集中展示数据集的元数据、构建历史、健康状态等信息。页面以界面分区（header、信息面板、标签页视图、预览表格与操作区）逐一说明，并对流数据集提供 Stream 与 Metrics 等专属标签页用于排查流作业性能。

### 2. Dataset Preview • CSV parsing

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dataset-preview/csv-parsing/>
- **文档摘要**：本页说明 Foundry 中 CSV 数据集的解析与配置方法。CSV 文件可定义分隔符、引号与转义字符等参数存于 schema；Foundry 会推断参数但需校验，委托 Spark CSV DataFrameReader 执行，可用 TextDataFrameReader 选项与 Edit Schema UI 配置。

### 3. Dataset Preview • Time Travel

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dataset-preview/time-travel/>
- **文档摘要**：本页介绍 Dataset Preview 中的 Time Travel（Beta）功能，可按提交版本查看数据集或 Iceberg 表的历史数据与 schema 变化。支持单版本查看、版本区间对比增删改、追踪 SQL 查询随时间变化，以及定位某行或某值具体在哪一版本发生变更。

### 4. Dataset Preview • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/dataset-preview/dataset-preview-faq/>
- **文档摘要**：本页汇总 Dataset Preview 的常见问题与排查方法。针对 CSV 因嵌套引号或底层文件列数不一致导致解析失败的情形，提供改用 DataSourceDataFrameReader、忽略 jagged rows 或手动编辑 schema 等解决方案，并解释下载后在 Excel 中显示异常的原因。
