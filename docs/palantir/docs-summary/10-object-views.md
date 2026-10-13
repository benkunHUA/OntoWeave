# 对象视图（Object Views）

> **分类说明**：Object View 的配置、版本管理、Widget 布局与分享

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-views` |
| 文档数量 | 21 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Object Views • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/overview/>
- **文档摘要**：本页是整个 Object Views 分类的总览。Object Views 是对象数据的可复用表示，提供与某个对象相关的所有信息中枢，包含属性数据、对象链接及相关应用。页面区分了 Standard Object Views（开箱即用、自动反映对象类型配置）与 Configured Object Views（基于 Workshop 构建、可定制、创建后成为默认视图但可切换回标准视图），并介绍了两种形态：Full Object Views（全面概览）与 Panel Object Views（用于嵌入其他应用的精简视图）。

### 2. Object Views • Standard Object Views

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/standard-object-views/>
- **文档摘要**：本页介绍 Standard Object Views。当在 Ontology 中创建并配置对象类型时，Foundry 会自动生成一个标准化的标准 Object View，以表格或可视化格式展示其属性与链接，确保用户全面理解其 schema。页面说明了突出属性（Prominent properties）的展示行为：媒体引用、时间序列、地理空间属性分别以专属查看器、交互图表、地图形式呈现，普通属性以表格展示、隐藏属性不可见；并介绍了 Linked objects 组件及 Panel 标准 Object View 的展示行为。

### 3. Object Views • Manage configured Object View versions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/manage-versions/>
- **文档摘要**：本页讲解 Configured Object View 的版本管理。对 Object View 的保存编辑会存储为新版本，一个版本可包含新增、编辑、删除 tab 等多项变更，便于安全迭代、控制发布版本、回滚出错版本及多人协作。页面说明如何保存新版本（默认自动发布，多用户协作时建议关闭自动发布）、如何通过编辑器头部查看并预览历史版本，以及当前/历史已发布版本的勾选标识。

### 4. Object Views • Configured Object View overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-overview/>
- **文档摘要**：本页是 Configured Object View 的总览。Configured Object Views 是基于 Workshop modules 构建的可定制、可复用的对象数据表示，可同时构建 full 与 panel 两种形态，创建后成为用户默认视图（可切换回标准视图）。页面介绍默认配置（默认展示突出属性或所有非隐藏属性及链接，编辑后转为用户管理需手动更新）、所需权限（取决于对象类型使用的权限模型：legacy datasource-derived、Ontology roles 或 project-based）以及编辑方式。

### 5. Object Views • Branching object views

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/branching-object-views/>
- **文档摘要**：本页介绍 Object Views 与 Global Branching 的集成，支持在分支上对 Object View 配置进行安全、隔离的开发。内容涵盖：在分支上添加/移除/修改资源（OV-managed modules 与 full Object View tabs 两类分支兼容资源）、跨应用兼容性（可在分支上面向最新 Ontology 状态编辑、在 Workshop 中嵌入分支视图、在 Ontology Manager 中预览）、合并要求（可部署性与审批检查）、变基与冲突解决，以及已知限制。

### 6. Object Views • Full Object Views • Use full Object Views in the platform

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/use-full-views-in-platform/>
- **文档摘要**：本页说明 Full Object Views 在平台中的使用方式。Full Object Views 提供对象数据的全面展示，是平台内对象的主视图，既可被平台应用访问，也可嵌入自定义 Workshop 应用。页面分述三种场景：Workshop 通过 Object View widget 在浮层/模态框中展示；Object Explorer 中搜索并选中对象后打开完整视图；Vertex、Maps、Gaia 等应用使用 panel Object View，点击对象标题可在可移动可调大小的模态框中打开 full 视图。

### 7. Object Views • Full Object Views • Configure full Object Views

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-object-views/>
- **文档摘要**：本页讲解如何配置 Full Object Views。默认的 configured full Object View 显示一个 Property List widget（展示突出属性）和一个 Links widget（展示链接）。页面说明两种编辑方式：编辑 Object View tabs（每个 tab 由 Workshop module 支撑，可灵活布局、用变量动态加载、用 Scenarios 展示模拟结果）与使用 Object View editor（含 header、object title bar、Workshop module 三部分，可切换 full/panel 形态、预览对象、保存发布、管理 tab 的增删改序）。

### 8. Object Views • Panel Object Views • Use panel Object Views in the platform

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/use-panel-views-in-platform/>
- **文档摘要**：本页说明 Panel Object Views 在平台中的使用方式。Panel Object Views 被嵌入 Palantir 各平台应用，为用户提供一致的对象数据体验；所有对象类型默认有标准 panel，也可构建 configured panel 来展示单个对象实例或对象集（object set）。页面分述平台应用中的两类面板——Object instance panels（展示单个实例，位于选择面板）与 Object set panels（聚合展示多个实例），以及如何在自定义 Workshop 应用中通过 Object View widget 配置 panel 形态使用面板。

### 9. Object Views • Panel Object Views • Configure panel Object Views

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-panel-views/>
- **文档摘要**：本页讲解如何配置 Panel Object Views，包含两种类型：object instance panels（展示单个对象实例，默认含一个 Property List widget）与 object set panels（聚合展示多个同类型实例，默认提供 Charts 与 List 两个 tab）。两者共享相同的编辑入口与配置体验。页面介绍如何在 editor 中切换 instance/set 类型、通过左侧面板的 Module Type 配置编辑画布尺寸以适配紧凑模块，以及调整预览显示大小等设置。

### 10. Object Views • Legacy Object Views • Configure legacy Object Views

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-legacy-object-views/>
- **文档摘要**：本页讲解 Legacy Object Views 的配置方式（通过 Manage tabs 齿轮、悬停 tab 选择 legacy editor 图标进入）。Legacy 基于 widget 构建 tab，widget（也称 Sections/Plugins）是展示数据的核心构件，用于把数据可视化为 KPI/图表、布局整页或操作数据。页面概述 widget 类型（属性与链接、可视化、过滤、布局、嵌入应用与文件）、widget 专有设置（选择对象模型与可视化配置）与格式设置（标题等），以及如何复用 widget 配置。

### 11. Object Views • Legacy Object Views • Configure tabs

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-tabs/>
- **文档摘要**：本页讲解 Object View 中 tab 的配置。tab 是 Object View 内导航与内容分组的主要方式，每个 tab 包含一个 Workshop module，支持条件可见性、过滤与布局设置。页面介绍两类 tab：Managed Workshop（推荐，功能强大且权限与 Object View 同步、不可复用）与 Standalone Workshop（可复用已有模块），以及不再新增但受支持的 Legacy builder tab。内容涵盖 Manage Tabs（新增、删除、移动）与 tab 设置（可见性条件、tab 专属设置）。

### 12. Object Views • Legacy Object Views • Configure the applications sidebar

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-app-sidebar/>
- **文档摘要**：本页讲解如何配置 applications sidebar（应用侧边栏）。该侧边栏用于展示并嵌入与当前对象相关的应用、分析、Action 及其他资源（支持 Workshop、Quiver、Slate、Action types 等，并可通过参数化 URL 链接外部应用），在视觉上与主体内容区分。页面说明其为按 Object View 可选加入的功能（需添加分组后才对用户可见），介绍如何建立侧边栏、编辑分组与卡片（标题、排序、可见性、移除等）以及在侧边栏中增改应用。

### 13. Object Views • Legacy Object Views • Configure profiles

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/config-profiles/>
- **文档摘要**：本页讲解如何配置 profiles（配置文件）。Profiles 允许针对不同角色的用户定制 Object View 的呈现方式，控制各 tab 对用户的可见性。页面说明需借助 Platform Settings 中 Groups 标签的 Hubble 属性（hubble:isProfile、hubble:displayName、hubble:isDiscoverable）来定义 profile，如何在 tab 级别分配 profile，以及用户/编辑者如何切换 profile、如何为用户设置默认 profile（每个 Object View 最多 10 个 profile）。

### 14. Object Views • Legacy Object Views • Properties and Links

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/widgets-properties-links/>
- **文档摘要**：本页是 Legacy Object Views 中“属性与链接”类 widget 的参考。这类 widget 以简单表格形式展示对象的数据模型与 Ontology，突出当前对象的属性及其与其他对象的链接，并呈现预计算或基于 Linked Objects 聚合的统计/KPI。页面详述 Properties widget（其配置与 Ontology Manager 中属性定义紧密相关，含可见性、突出/普通/隐藏、Render Hints 等）、Property Cards、Links widget 与 Edit History 的配置方式。

### 15. Object Views • Legacy Object Views • Visualization

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/widgets-visualization/>
- **文档摘要**：本页是 Legacy Object Views 中“可视化”类 widget 的参考。Visualization widgets 以图表、透视表、时间线、地图等形式展示与当前对象相关的数据，也可嵌入其他 Foundry 应用（如 Slate、Contour、Quiver）创建的 visualization。页面详述 Linked Object View（以表格/列表/卡片展示某类 Linked Objects，支持选择与执行 Object Actions）、Timeline 与 Grouped Events Timeline and Table 的配置方法，以及常见问题和注意事项。

### 16. Object Views • Legacy Object Views • Filtering

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/widgets-filtering/>
- **文档摘要**：本页是 Legacy Object Views 中“过滤”类 widget 的参考。Filter Widgets 让用户对图表、表格、KPI 等视觉组件施加各类过滤，从而下钻到特定子集的 Linked Objects。页面列举并讲解多种过滤 widget：Multiselect Filter、Dropdown Filter、Button Filter、Date Range Filter、Linked Object Filter Sidebar、Filter Sandbox Container、Filter Container 与 Active Filters 的配置；并提示需勾选“cross-filtering”设置，过滤才能跨同 tab 或跨 tab 的 widget 生效。

### 17. Object Views • Legacy Object Views • Layout

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/widgets-layout/>
- **文档摘要**：本页是 Legacy Object Views 中“布局”类 widget 的参考。Layout widgets 用于在某个 tab 内组织与结构化 Object View 页面布局，是内容之上的容器层。页面介绍几种布局容器：Horizontal Distribution 与 Vertical Stack Container（页面视觉排布）、Tabbed Container（tab 内再嵌套内容）、Conditional Container（依据用户交互的过滤显示/隐藏内容）以及 Markdown widget（展示富文本并嵌入对象值），并分别给出各自的配置要点与注意事项。

### 18. Object Views • Legacy Object Views • Apps and Files

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/widgets-apps-files/>
- **文档摘要**：本页是 Legacy Object Views 中“应用与文件”类 widget 的参考。Apps and Files widgets 支持在当前 Object View 中嵌入、展示并链接其他 Foundry 应用与资源（如 Quiver、Slate），也可展示媒体、上传文件、添加超链接或评论。页面详述 Quiver Dashboard、Slate Application（支持参数传递、访问对象视图过滤、从 Slate 触发事件）、Media Preview、Hyperlink、Linked Files、Iframe 等 widget 的配置，并提醒部分 widget 非对象感知、与其他 widget 交互有限。

### 19. Object Views • Generate Object View URLs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/generate-urls/>
- **文档摘要**：本页讲解如何生成指向 Object View 的 URL，便于在开发中将视图链接到特定对象或进行对象搜索。页面说明若通过 iframe 嵌入而非作为链接，应追加查询参数 embedded=true 以去除 Workspace 侧边栏加载；并给出两种生成对象链接的方式：基于对象类型 ID 与主键属性值（/workspace/hubble/external/object/v0/<object-type-id>?...）或基于 objectRid（/workspace/hubble/external/search/v2/?objectId=...，主键含特殊字符时推荐），以及无包裹加载的 /workspace/hubble/objects/<objectRid> 写法。

### 20. Object Views • Comment on objects

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/comment-on-objects/>
- **文档摘要**：本页介绍如何对对象进行评论。多个用户常协作处理同一对象，Object Explorer 允许用户在对象上评论、提及（@）其他用户，并附加文件与图片。用户可在任意 Object View 头部通过 View comments 按钮打开该对象的 Comments Helper。页面提醒：Object Explorer 中对对象的评论与 Workshop 中的 Comment widget 无关。

### 21. Object Views • Add Object Views to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-views/marketplace-object-views/>
- **文档摘要**：本页讲解如何将 Object Views 加入 Marketplace 产品（借助 Foundry DevOps），供其他用户安装与复用。页面列出支持范围：Marketplace 产品仅支持使用 Workshop tab builder 构建的 Object View tab，不支持 legacy builder（需先重建为 Workshop tab）。操作步骤为：先创建产品，添加 outputs 后选择 Add ontology entities，选中目标 Object View 并勾选要纳入的 tab。
