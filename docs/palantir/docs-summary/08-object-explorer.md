# 对象浏览器（Object Explorer）

> **分类说明**：对象搜索、筛选、图表分析、对象集对比与探索保存

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-explorer` |
| 文档数量 | 16 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Object Explorer • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/overview/>
- **文档摘要**：Object Explorer 是 Foundry 中用于检索和分析 Ontology 数据的搜索与分析工具。用户可通过关键词搜索或属性过滤找到对象，并以图表、表格或 Object View 探索结果；还支持对比 Object Set、批量执行 Action（如 writeback）、导出到其他应用，并可保存 Explorations 随时复访。该工具几乎无需配置，主要面向非技术用户。

### 2. Object Explorer • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/getting-started/>
- **文档摘要**：本文介绍打开 Object Explorer 后的主页（Home Page）布局与基本操作。主页是探索入口，用户可通过全局搜索栏（Global search bar）跨 Ontology 搜索对象、对象类型、已保存 Explorations 与模块；可按对象类型分组（Group exploration）浏览或用图谱（Graph）查看类型间关联；并可将对象类型加入收藏、预览，以及访问已保存的 Explorations 与 Lists。

### 3. Object Explorer • Search and explore objects • Search for objects

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/search-objects/>
- **文档摘要**：本页说明使用全局搜索栏后，搜索结果页的呈现与导航方式。结果按 All、Objects、Object Types、Artifacts 等标签页分类，并可通过左侧边栏按类型筛选；隐藏的对象与属性不会出现在结果中。对象结果先按 prominent/non-prominent 类型、再按结果数量排序；悬停单个对象可探索其关联对象。

### 4. Object Explorer • Search and explore objects • Search syntax

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/search-syntax/>
- **文档摘要**：本页是 Object Explorer 全局搜索栏的 Search Syntax 参考。介绍引号（""）用于精确短语匹配；逻辑运算符 NOT、AND、OR 用于组合或排除条件；通配符（如 term*）用于前缀匹配；以及模糊搜索（Fuzzy searching）的用法。掌握这些语法可帮助用户更精确地检索 Ontology 中的对象与属性。

### 5. Object Explorer • Search and explore objects • Understanding text search

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/understanding-text-search/>
- **文档摘要**：本页从底层原理讲解 Foundry（含 Object Explorer、Workshop、Functions API）的文本搜索机制。平台通过 analyzer 处理字符串：默认 standard analyzer 按空格与标点分词、转小写，故搜索默认大小写不敏感；还介绍 simple、not analyzed 等 analyzer 类型、特殊字符处理，以及 Object Explorer 与 Workshop 中的搜索行为、前导通配符（leading wildcard）的启用与限制。

### 6. Object Explorer • Search and explore objects • Analyze using SQL

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/analyze-sql/>
- **文档摘要**：本页介绍 Object Explorer 中的 Beta 功能“Analyze using SQL”。它提供一个只读 SQL 草稿区，可对对象类型运行查询、做快速分析，支持对象类型 RID 自动补全、反引号引用其他类型以执行 JOIN、快捷键与结果预览表。文中列出启用要求（如编辑禁用需单一数据源）、数据新鲜度（编辑后约 30 秒延迟）与兼容、限制等注意事项。

### 7. Object Explorer • Analyze and compare • Filter results

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/filter-results/>
- **文档摘要**：本页讲解在 Exploration 视图中通过搜索栏对当前 Object Set 做进一步过滤。支持按主对象类型属性过滤、按关键词（跨全部或指定属性）搜索，以及通过链接（links）过滤关联对象。还介绍术语修饰符：Is not（取反）、Starts with（前缀通配）、Exact（精确匹配），并说明前导通配需开启相应 render hint。

### 8. Object Explorer • Analyze and compare • Explore with charts

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/explore-charts/>
- **文档摘要**：本页介绍在 Object Explorer 的 Explore 视图中用图表进行搜索与过滤。图表是过滤交互的核心，默认显示各 prominent 属性的聚合；可添加、删除、排序图表，并可在关联对象上建图。支持 Listogram、Pie Chart、Histogram、Grid Plots、Single Statistic、Statistics Table、Maps 等多种图表类型，并支持撤销/重做与保存布局。

### 9. Object Explorer • Analyze and compare • View results

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/view-results/>
- **文档摘要**：本页说明 Object Explorer 的 Results View（表格视图）及其配置。表格可按 Sortable renderHint 列排序；列顺序可拖拽、可调整宽度、可增删与隐藏；点击标题列可在新标签打开 Object View，勾选对象可预览 Selection Preview。还支持查看时间序列（time series）属性，以及对配置了 inline edit 的属性直接在表格内编辑。

### 10. Object Explorer • Analyze and compare • Pivot to explore linked objects

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/pivot-linked/>
- **文档摘要**：本页介绍 Exploration 中的 Pivot（透视）功能：可将探索的主对象类型切换到任意关联对象类型。以“查找美国东部大型机场未来 30 天出发航班”为例，先筛选机场再 pivot 到 Departing Flights，主类型即变为 Flights 并保留先前过滤。可经多层链接 pivot，跨 Ontology 灵活探索。

### 11. Object Explorer • Analyze and compare • Compare object sets

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/compare-object-sets/>
- **文档摘要**：本页介绍 Comparison Views（对比视图），用于比较两个过滤后的 Object Set，对象可来自动态过滤或已保存 Explorations。进入对比模式后可选对比集（如某已保存 Exploration 或全量对象），并可在两侧联合应用过滤；对比视图可像 Explorations 一样保存与分享（Share），分享不会共享底层对象访问权限。

### 12. Object Explorer • Analyze and compare • Save explorations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/save-explorations/>
- **文档摘要**：本页说明如何将工作保存为 Exploration 以便复用。保存的 Exploration 会保留搜索参数、已应用过滤与布局配置；点击右上角 Save 按钮并填写标题等元数据即可保存，enrollment 配置可能禁止存到个人文件夹。已保存 Explorations 可通过全局搜索或顶部 Explorations 下拉访问，分享不共享底层对象权限。

### 13. Object Explorer • Analyze and compare • Save lists

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/save-lists/>
- **文档摘要**：本页介绍将探索结果保存为 List（列表）。与 Exploration 不同，List 是静态对象列表，除非手动更新否则不变；保存时可整批保存或勾选部分对象。与 Exploration 类似，List 的访问也通过全局搜索或顶部 Lists 下拉，分享仅共享列表本身而非底层对象权限，enrollment 可能禁止存到个人文件夹。

### 14. Object Explorer • Analyze and compare • Apply Actions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/apply-actions/>
- **文档摘要**：本页说明在 Exploration 中如何对当前 Object Set 或所选对象执行操作。顶部按钮分为三类：Actions（数据 writeback，如提交 Action，默认传入所选对象，超 1000 个对象不可用）、Open In（在其他应用打开，如 Quiver）、Export（导出，如导出 Excel 或复制对象 ID）。

### 15. Object Explorer • Generate Object Explorer URLs

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/generate-urls/>
- **文档摘要**：本页介绍如何生成指向 Object Explorer 特定对象或搜索的 URL，便于在 Slate 或外部系统集成。涵盖关键词搜索 URL（需 encodeURIComponent 编码）、链接到对象类型/已保存 Explorations/新搜索（可附加 perspectiveId=results 切换结果视图），以及进阶的复杂过滤 JSON 结构。还提及链接到 Object View 的方法。

### 16. Object Explorer • Configure Object Explorer

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-explorer/configure/>
- **文档摘要**：本页是 Object Explorer 的配置指南。介绍在 Ontology Manager 中将对象类型加入主页分组（未分组者归入“Other”）；配置 Action 成功提示（toast）链接到 Object View；用 type class 隐藏某些 Action；以及 Dynamic Object Set 上的 Action、默认布局管理（Default Layout Administrative Users）等配置项。
