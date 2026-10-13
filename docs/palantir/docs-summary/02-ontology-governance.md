# Ontology 治理与管理

> **分类说明**：Ontology 分支、提案评审、共享本体、迁移与用量管理

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `ontologies` |
| 文档数量 | 10 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Ontologies • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/ontologies-overview/>
- **文档摘要**：本页是 Ontologies 模块的概述，介绍 Ontology 这一用于存储本体资源的制品（包含 Object types、Link types、Action types、Interfaces、Shared properties、Object type groups）。页面说明 Ontology 与 space 是一一映射关系，并对比私有（private）与共享（shared）两种 Ontology：前者适用于单一组织内的对象协作，后者允许两个及以上组织安全地共享数据与工作流。

### 2. Ontologies • Branching the ontology • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/branching-ontology/>
- **文档摘要**：本页介绍 Ontology 与 Global Branching 的集成，支持在本体资源上进行安全、隔离的开发。内容覆盖分支上的资源增改、资源保护（支持的 Object types、Action types、Link types、Interface types、Shared property types）、变基（rebase）与冲突解决、合并前的审查流程（准备分支、请求审查、审查提案、合并分支），以及已知限制。

### 3. Ontologies • Branching the ontology • Review ontology proposals

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/review-ontology-proposals/>
- **文档摘要**：本页讲解如何审查 ontology proposal（类似于版本控制系统的 Pull Request）。当创建 Global Branching proposal 或（旧版）ontology 分支时会自动生成 proposal。页面介绍 Proposals 标签的过滤分类（我的/指派给我/审查中/已合并/已关闭），以及单个 proposal 视图中的概览页、预览状态、变更审查、变更日志和权限设置。

### 4. Ontologies • Branching the ontology • Ontology branches [Legacy]

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/ontology-branches-legacy/>
- **文档摘要**：本页为已弃用（Legacy）的 Ontology branches 文档。说明该分支机制正逐步下线，启用 Global Branching 的注册中将无法再创建 ontology branches，应改用 global branches。文档保留原有五步工作流（创建分支、准备提案、请求审查、审查提案、发布提案）及分支、提案等定义，供历史参考。

### 5. Ontologies • Shared ontologies

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/shared-ontologies/>
- **文档摘要**：本页介绍 Shared ontologies（共享本体）。共享本体是一种特殊 Ontology，允许多个组织围绕共享工作流协作，其下有一个专用的共享 space。创建新的共享 space 时会自动生成共享 ontology，并沿用相同的组织标记与角色授权；页面建议读者参考私有与共享本体的对比示例来选择合适的类型。

### 6. Ontologies • Migrating between ontologies

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/ontology-migration/>
- **文档摘要**：本页介绍如何在不同 Ontology 之间迁移本体资源。每个资源在创建时自动关联到所属 Ontology，之后可移动到其他 Ontology；迁移会改变资源的权限，但不影响底层数据与输入 datasource 的权限，且默认保留全部编辑。页面提供了通过 Ontology Manager 的 Ontology switcher 选择资源并提交迁移的操作步骤，并提醒需将关联资源一并迁移。

### 7. Ontologies • Usage • Ontology volume

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/volume-usage/>
- **文档摘要**：本页讲解 Ontology volume（本体数据量）的概念与计量。Ontology 是快速访问存储层，volume 衡量索引化对象集合及其链接的总大小，单位为 GB（瞬时）或 GB-Month（月度平均）。页面介绍如何测量 volume、在 Resource Management Application 中排查用量、驱动 volume 的因素，以及管理 volume 的方法。

### 8. Ontologies • Usage • Ontology indexing compute

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/compute-usage/>
- **文档摘要**：本页介绍 Ontology indexing（本体索引）的计算用量。Ontology 将数据存入优化的 Ontology index，索引过程通过并行化 Spark 后端运行，成本以 compute-seconds 计量。页面说明如何测量索引计算用量、在 Builds 应用中排查索引与导出（export）作业、驱动用量的因素、管理方法，并给出计算示例。

### 9. Ontologies • Usage • Ontology query compute

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/query-compute-usage/>
- **文档摘要**：本页讲解 Ontology query（本体查询）的计算用量。Foundry Ontology 是将文件数据映射为对象并服务高速查询的数据后端。页面介绍核心概念（object type 与 object set 的区别、query types 如过滤/聚合/Search Around/writeback），如何在各 Foundry 应用中排查查询计算用量，以及 2026 版 Ontology Query Compute 的计费因素（用户与代理数量、查询类型）。

### 10. Ontologies • Usage • Object Set Service limitations

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/ontologies/oss-limitations/>
- **文档摘要**：本页说明 Object Set Service（OSS）的局限性。OSS 负责从 Ontology 查询与检索对象，采用分级执行策略（下推存储层、内存执行、Spark 执行）以平衡性能与规模。页面详细介绍查询执行流程、对象集合大小限制（Object Storage v1/v2）、分页与内存限制、Search Around 结果集，以及在此类限制下的最佳实践。
