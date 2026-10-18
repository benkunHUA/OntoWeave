# 数据血缘（Data Lineage）

> **分类说明**：数据血缘（Data Lineage）：查看数据集与管道间的依赖关系、影响分析与血缘图解读

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `data-lineage` |
| 文档数量 | 19 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Data Lineage • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/overview/>
- **文档摘要**：本页概述 Data Lineage 这一交互式工具，它提供 Foundry 平台中数据流动的全局视图。借助 Data Lineage，你可以搜索发现 dataset、浏览 pipeline、展开或隐藏节点的祖先与后代、按属性批量着色，并查看 schema、构建时间与生成代码等细节，还可创建快照与团队成员协作。

### 2. Data Lineage • Navigation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/navigation/>
- **文档摘要**：本页说明如何在 Data Lineage 应用中导航。涵盖 lineage graph 的基本操作（平移、拖拽、自动布局）、branch 设置、侧边栏（搜索浏览、属性与直方图、管理构建与调度、相关工件）、节点详情、图形工具（着色、布局、展开、查找、选择）以及保存图形等区域的功能。

### 3. Data Lineage • Branching data lineage

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/branching-data-lineage/>
- **文档摘要**：本页介绍 Data Lineage 与 Global Branching 的集成，使你能够查看分支中实体的状态。内容涵盖选择分支、查看分支感知的 lineage、搜索分支上的实体、查看分支上修改的实体、节点详情与预览、着色与徽章、已保存图形及已知限制。

### 4. Data Lineage • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/faq/>
- **文档摘要**：本页汇总 Data Lineage 的常见问题及解答。例如如何查看 object type 的 backing/writeback dataset、哪些 dataset 含特定列、最后修改者是谁、含未结事务的 dataset、数据主要存储位置、共享未保存图形，以及 dataset 为何不是最新的。

### 5. Data Lineage • Graphs • Explore data lineage

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/explore-lineage/>
- **文档摘要**：本页提供在 Data Lineage 应用中探索数据 pipeline 的方法。常见路径包括：用 Search helper 添加资源、点击节点左箭头展开直接父节点、使用 Expand 定义展开层级直至原始数据，并通过底部面板预览数据、查看代码或跳转代码工作区以分析 lineage 关系。

### 6. Data Lineage • Graphs • Explore artifacts and ontology entities

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/explore-artifacts/>
- **文档摘要**：本页说明如何在 Data Lineage 中查找与 dataset 相关的 Foundry 工件与 Ontology 实体。涵盖通过 Related items 面板查看 Contour、Slate 等关联工件，以及通过节点属性查看由 dataset 定义的 object type 与 link type，并将其加入图形以可视化连接。

### 7. Data Lineage • Graphs • Save and share a graph

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/save-share-graph/>
- **文档摘要**：本页介绍 Data Lineage 中图形的保存与共享方式。可通过 Actions 菜单 Save、Save as、Open graph 将图形保存到文件或 Project；共享则可用快速共享链接（同组织内）、导出 SVG 静态图，或通过权限侧边栏开启链接共享、为指定用户或组授权。

### 8. Data Lineage • Graphs • Node coloring

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/node-coloring/>
- **文档摘要**：本页列出 Data Lineage 中多种内置的节点着色选项，用于呈现 pipeline 的更多信息。包括无着色、自定义颜色，以及按 Data Catalog、Folder、Issues、Permissions、Project、Repository、Resource overview、Resource type、Build status、Data Health、Out-of-date 等进行着色与筛选。

### 9. Data Lineage • Graphs • Graph elements reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/elements-reference/>
- **文档摘要**：本页提供 Data Lineage 图形元素的参考说明。介绍四类节点类型（data source、dataset、object type、artifact）的含义与显示方式，以及出现在 dataset 节点上的指示符：open issues、defines object type、syncs、trashed 各自代表的附加信息。

### 10. Data Lineage • Understand and manage datasets • View dataset preview and logic

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/dataset-preview-logic/>
- **文档摘要**：本页说明如何在 Data Lineage 中查看选定 dataset 或 media set 的预览及生成逻辑。涵盖 Dataset/Media set 预览（可滚动前 300 行、按列搜索），以及通过 Code 标签查看、编辑代码逻辑；上传与 writeback dataset 无关联代码可供查看。

### 11. Data Lineage • Understand and manage datasets • View build timeline

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/build-timeline/>
- **文档摘要**：本页介绍使用 Data Lineage 的 Build timeline 工具查看 dataset 的构建历史。可在窗口左下角展开甘特图，选择 1 小时至 10 天的时间范围，按 schedule 或 job 状态着色，并查看特定 dataset（可多选）的构建作业详情。

### 12. Data Lineage • Understand and manage datasets • Understand out-of-date datasets

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/stale-datasets/>
- **文档摘要**：本页帮助理解 dataset 为何过期（out-of-date）。列举常见情形：构建失败、上游 dataset 未构建、源数据未更新，并说明如何在 Data Lineage 中通过展开祖先节点、使用 Build status 着色选项来诊断 stale dataset。

### 13. Data Lineage • Understand and manage datasets • Find datasets with a given column

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/find-column/>
- **文档摘要**：本页说明如何在 Data Lineage 图形中搜索含特定列的 dataset。先将相关 dataset 加入图形并多选（拖拽选择或 Ctrl/Cmd 多选、全选），再通过侧边栏的 selection properties 直方图查看最频繁列，点击某列即可高亮含此列的 dataset。

### 14. Data Lineage • Understand and manage datasets • Build datasets

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/build-datasets/>
- **文档摘要**：本页介绍如何在 Data Lineage 中直接触发构建。涵盖 Builds helper 的三种常见工作流：构建全部祖先、构建所选 dataset 之间的全部 transform、仅构建所选 dataset；构建在图形配置的（含 fallback）分支上执行，并可在确认前预览待构建列表。

### 15. Data Lineage • Understand and manage datasets • Manage schedules

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/manage-schedules/>
- **文档摘要**：本页说明在 Data Lineage 中管理构建调度（schedule）。通过右侧栏 Manage schedules 打开调度详情，查看与所选 dataset 相关的调度，包括最新运行状态、上次更新、目标 dataset、构建触发时机（When to build）与构建范围（Build scope）等信息。

### 16. Data Lineage • Understand and manage datasets • Roll back a pipeline

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/pipeline-rollback/>
- **文档摘要**：本页介绍 pipeline 回滚功能（已进入计划弃用阶段，建议改用 dataset 回滚）。可将某上游 dataset 的事务及其下游依赖整体回滚至早期版本，并在确认前预览最终 pipeline 状态；文中列出警告与注意事项、限制以及 Object Storage v2 的额外处理要求。

### 17. Data Lineage • Understand and manage datasets • Roll back a dataset

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/dataset-rollback/>
- **文档摘要**：本页介绍 dataset 回滚功能，用于将 dataset 回退至早期版本以修复逻辑错误、错误数据或故障。说明两种回滚类型（回滚至更早事务、下次构建强制快照）及注意事项与限制，例如仅支持事务型 dataset、只能回滚到成功事务、需 Editor 角色等。

### 18. Data Lineage • Understand permissions • Check resource permissions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/check-permissions/>
- **文档摘要**：本页说明如何使用 Data Lineage 的 Permissions 着色选项检查用户对 dataset 或工件的权限。介绍两类着色：Data access in datasets（按数据访问排障，受 lineage 影响）与 Resource access（查看用户对资源的角色）；并提示共享资源时需确保底层数据可访问。

### 19. Data Lineage • Understand permissions • See the impact of marking changes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-lineage/see-impact-marking-changes/>
- **文档摘要**：本页介绍如何使用 Data Lineage 模拟 dataset Marking 变更对派生 dataset 的影响。涵盖开启访问模拟模式、模拟添加或移除 Marking、分析着色后受影响的数据集状态（已应用、访问受影响/不受影响、无可见事务），并提示模拟不计入未定稿变更。
