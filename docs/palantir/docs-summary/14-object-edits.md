# 对象编辑（Object Edits）

> **分类说明**：编辑的应用方式、历史记录、权限校验、Schema 迁移与实体化

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-edits` |
| 文档数量 | 6 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Object edits and materializations • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-edits/overview/>
- **文档摘要**：本页是 Object edits and materializations 的总览。Foundry Ontology 通过 Action 让用户编辑属性值、增删链接、创建与删除对象——一个 Action 是基于用户定义逻辑、更改一个或多个对象属性的单笔事务，可从 Workshop、Object Views 或外部 API 触发。其余页面将讨论启用 Action 所需的对象/链接类型配置及用户驱动编辑的底层机制。注意：基于 Foundry stream datasources 的对象类型尚不支持 Actions。

### 2. Object edits and materializations • How user edits are applied

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-edits/how-edits-applied/>
- **文档摘要**：本页讲解用户 edits 如何被应用。用户编辑可通过 Ontology Manager 的 Edits 开关启用/禁用，编辑经由 Action 立即应用到对象数据库索引并周期性刷入 Funnel 管理的 Foundry datasets 持久存储。页面涵盖 Action 编辑对象、live data 上的用户编辑（通过 Funnel 队列与 offset 跟踪）、如何丢弃/清除/删除已有编辑、Ontology 实体版本控制、用户编辑的持久化存储，以及冲突编辑与 datasource 更新的两种解决策略（默认应用用户编辑、应用最近值）。

### 3. Object edits and materializations • Enable user edit history

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-edits/user-edit-history/>
- **文档摘要**：本页介绍如何启用用户编辑历史（user edit history），用于追踪索引进 OSv2 的对象编辑记录。可通过 Ontology Manager 的 Track user edit history 开关启用/禁用，记录每次编辑（无论来源），使用 UTC 提交时间戳。页面说明编辑历史时间戳、常见问题与注意事项（启用前变更不被追踪、初始化期间无法操作、迁移时需勾选保留、同一主键可访问全部历史），以及如何禁用编辑历史。

### 4. Object edits and materializations • Permission checks for Actions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-edits/permission-checks/>
- **文档摘要**：本页讲解应用 Action 时的权限检查（permission checks），取决于编辑的是 single-datasource 还是 multi-datasource 对象。单一数据源对象要求用户能查看对象并通过 action 提交条件，新建对象还需能查看输入 datasource。多数据源对象因访问级别不一，页面为创建/编辑/删除对象、创建/删除链接等不同 Action 分别列出更细化的权限规则。

### 5. Object edits and materializations • Manage schema changes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-edits/schema-migrations/>
- **文档摘要**：本页介绍如何管理 schema 变更（Manage schema changes）。在 OSv1 中 breaking schema changes 可能导致已有用户编辑丢失，而 OSv2 移除了该限制，提供 schema migration 框架以对已有用户编辑应用预定义迁移。页面列出破坏性变更（如改 datasource、改主键、改属性类型等）、非破坏性变更，以及 OSv2 支持的 schema migration 列表，并给出示例工作流。

### 6. Object edits and materializations • Materializations

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-edits/materializations/>
- **文档摘要**：本页介绍 Materializations，即对 Ontology 中已索引数据创建包含每个对象最新状态（融合输入 datasource 与用户编辑）的物化数据集。两大用例：构建需要最新对象状态的下游 Foundry pipeline，以及下载包含最新状态的 Ontology 数据。页面说明如何创建物化数据集，并对比 OSv1 的 writeback datasets 与 OSv2 的 materialized datasets 在构建调度、保留、schema、restricted view 物化及 branching 等方面的差异。
