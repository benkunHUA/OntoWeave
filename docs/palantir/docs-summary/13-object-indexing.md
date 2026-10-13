# 对象索引（Object Indexing）

> **分类说明**：对象索引的管线方式、直连数据源与数据限制

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-indexing` |
| 文档数量 | 6 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Indexing • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-indexing/overview/>
- **文档摘要**：本页是 Indexing 的总览。在 Ontology 中，indexing 指将 Foundry datasources 中的表格等数据通过专门数据库提供给更快检索的过程。针对 Object Storage v2，indexing 由 Object Data Funnel 服务（“Funnel”）负责编排，包含 funnel batch pipelines 与 funnel streaming pipelines 两类机制，并提及 direct datasources 用于低延迟写入与编辑，以及 OSv1 (Phonograph) 的旧版索引文档。

### 2. Indexing • Funnel batch pipelines

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-indexing/funnel-batch-pipelines/>
- **文档摘要**：本页介绍 funnel batch pipelines，即编排 Foundry datasources 与用户 edits 批量高效索引进 OSv2 的内部任务管道。一个管道由 Changelog、Merge changes、Indexing、Hydration 等 Foundry 构建作业组成；区分 live 与 replacement 管道，默认进行增量索引、特殊情形做全量 reindexing。页面还说明了如何监控 Funnel 管道及调试管道的方法。

### 3. Indexing • Funnel streaming pipelines

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-indexing/funnel-streaming-pipelines/>
- **文档摘要**：本页介绍 funnel streaming pipelines，除批处理索引外，OSv2 通过 Foundry streams 作为 input datasources 支持低延迟（秒/分钟级）的流式数据索引。页面列出流式 object types 的当前产品限制（如不支持用户 edits、不支持 MDOs、多数前端应用不支持实时刷新等），并说明如何配置 streaming object types 以及调试 streaming 管道的方法。

### 4. Indexing • Direct datasources

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-indexing/direct-datasources/>
- **文档摘要**：本页介绍 direct datasources（Beta），提供对 Ontology 的低延迟写入能力，包括在基于流数据的对象类型上的 edits，目前仅 Pipeline Builder 作为受支持的写入应用。它用吞吐量换取更健壮的 Ontology 写入（流上用户编辑、基于时间的排序）。页面涵盖如何配置、可选的 source timestamp 属性以实现时间排序、调试方法（写入方与对象类型互查、liveness、流指标、日志）及若干 FAQ。

### 5. Indexing • Data restrictions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-indexing/data-restrictions/>
- **文档摘要**：本页讲解 Object Storage v2 的 data restrictions，用于在索引时确保进入 Ontology 的数据质量、确定性与平台可读性。限制包括主键与唯一性（禁止重复主键、限制可用作主键的数据类型）、属性类型一致性（schema 与对象类型必须一致、禁止 NaN/无穷大、空字符串转为拒绝等）、属性大小限制，以及对大数据的处理建议。批处理与流式对象违反限制时分别导致作业失败或被丢弃记录。

### 6. Indexing • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/object-indexing/faq/>
- **文档摘要**：本页是 Indexing 的常见问题解答（FAQ），汇总 OSv2 索引相关的疑问。内容包括如何判断 object type 已索引完成、为何原本在 OSv1 成功的索引作业在 OSv2 失败（因校验更严格）、如何触发重建、索引数据大小限制、流式历史数据回填、流式延迟与吞吐、去重时间戳、CDC 支持、部分行写入、多对多链接与 materializations 的流式支持等。
