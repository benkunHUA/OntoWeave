# 对象存储后端（Object Backend）

> **分类说明**：Object Storage v2 架构、破坏性变更与 v1 迁移

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-backend` |
| 文档数量 | 4 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Overview and getting started • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-backend/overview/>
- **文档摘要**：本页是 Foundry Ontology 后端架构的总览与入门文档。它介绍了 Ontology 作为组织的运营层，位于数据集与模型之上，连接现实世界的实体。页面概述了构成 Ontology 后端的微服务组件：Ontology Metadata Service (OMS)、Object databases、Object Set Service (OSS)、Actions、Object Data Funnel 与 Functions on Objects，并讲解了后端的三大职能（数据源管理、对象查询/搜索/聚合、写入编排），以及从 Object Storage v1 (Phonograph) 到 v2 的架构演进。

### 2. Overview and getting started • Breaking changes between OSv1 and OSv2

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-backend/object-storage-v2-breaking-changes/>
- **文档摘要**：本页列出了从 Object Storage v1 (Phonograph) 迁移到 Object Storage v2 (OSv2) 时引入的破坏性变更。OSv2 通过 Object Data Funnel 将对象同步到专门的对象数据库，带来更优的扩展、性能与安全性。变更分为永久性与临时性两类：永久性变更涵盖 Actions 与 edits（仅支持通过 Actions 编辑）、edit history、data restrictions、changelog 增量更新、monitoring 及 OSS 变化等，需在迁移前完成整改。

### 3. Overview and getting started • Migrate from OSv1 to OSv2

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-backend/osv1-osv2-migration/>
- **文档摘要**：本页是 Object Storage v1 (Phonograph) 向 Object Storage v2 迁移的操作指南。迁移对象类型与多对多链接类型在 OSv2 中是强制性的，Ontology Manager 提供集成的迁移框架，且支持批量迁移。页面详述了迁移前的注意事项（迁移行为、不兼容用法），以及实体迁移流程：启动迁移、过渡窗口、禁用 action 类型、soak period、writeback dataset 迁移、中止迁移、sync 失败与批量迁移等。

### 4. Overview and getting started • Aggregation considerations

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-backend/aggregation-considerations/>
- **文档摘要**：本页介绍 Foundry 中聚合（aggregation）的精度考量。由于高基数数据聚合的特性，部分应用可能显示不完全精确的结果（inexact aggregations）。页面说明 object-set-service API 中与精度相关的字段（如 AggregationExecutionMode 设为 PREFER_ACCURACY、AggregateResultAccuracy），并分别列举了 Object Explorer、Workshop、Quiver pivot table、Ontology SDK (OSDK) 与 Functions 中聚合不准确的表现形式及应对建议。
