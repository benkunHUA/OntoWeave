# Vertex 图探索

> **分类说明**：图的可视化探索、事件与时间序列、Scenario 与模型链

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `vertex` |
| 文档数量 | 27 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Vertex • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/overview/>
- **文档摘要**：本页是 Vertex 的概述。Vertex 是一套工具包，用于在真实组织的数字孪生中可视化并量化因果关系，帮助用户访问、探索已发布的系统 Graph 或新建 Graph。它支持监控、模拟与优化运营决策，带来跨职能孤岛的全局视图、聚焦关键风险与机会的告警、结合分析与运营的“假设（what-if）”模拟，以及通过学习优化模型精度以逼近全局最优。

### 2. Vertex • Example use cases

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/example-use-cases/>
- **文档摘要**：本页列举 Vertex 在组织中的若干应用示例。重点包括 Beta 阶段的“点选式流程框图（Diagramming）”，可快速配置面向任意工作流的对象支撑系统 Graph 或生产图，并根据属性/指标进行样式化；以及结合 Foundry 中已发布模型、预测与业务逻辑的模拟、动态分析与决策优化，支持跨网络运行 what-if 分析以打破数据孤岛。

### 3. Vertex • Graphs • Explore existing graphs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/graphs-explore/>
- **文档摘要**：本页讲解如何在 Vertex 中探索已有的 Graph。选中对象后可在侧栏查看其属性，并通过事件徽标关联事件；支持按对象类型、属性值、Time Series 或事件进行动态样式化，利用时间选择面板“拖拽”查看属性随时间变化。还介绍了关联事件、按时间理解当前状态与变化、筛选对象属性，以及在可编辑视图中探索对象关系（如 Search Arounds、样式与布局）。

### 4. Vertex • Graphs • Explore object relationships

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/explore-object-relationships/>
- **文档摘要**：本页介绍如何在 Vertex 中探索对象之间的关系。内容涵盖从 Foundry 工作区启动新探索、使用对象选择面板查看属性，以及基于操作关系进行“Search Around”以扩展 Graph：包括基本与多步 Search Around、查找关联对象、添加参数、保存与加载，以及使用 Histogram 筛选器按对象类型和属性筛选视图。

### 5. Vertex • Graphs • Object and edge display options

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/graphs-display-options/>
- **文档摘要**：本页说明 Vertex 中对象与边的显示选项。涵盖 Graph 布局（层级、网格、径向等自动布局及其高级参数）、按对象类型或属性分组节点、将节点组合为边，以及图层样式选项：对象样式、边样式与已保存样式（Saved styles），并介绍已保存选择（Saved selections），帮助用户配置随时间变化的动态可视化。

### 6. Vertex • Graphs • Save, share, and collaborate

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/save-share/>
- **文档摘要**：本页讲解 Vertex 中 Graph 的保存、分享与协作方式。可通过“Save as”将 Graph 保存到 Foundry 项目（仅项目内可见），并开启链接分享赋予不同权限角色；可从下拉菜单“Duplicate”复制既有 Graph 到新项目；还可启用版本控制（Enable versioning），在 Graph History 侧栏查看历史版本、只读访问并以 Revert 创建新版本。

### 7. Vertex • Graphs • Create a graph template

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/graphs-template/>
- **文档摘要**：本页介绍如何创建 Vertex Graph 模板。模板是基于参数生成带预设样式 Graph 的资源，可支持将分析从一个对象/对象集切换到另一个、构建可复用的 Graph 生成工作流，或嵌入 Object View、Workshop 等应用。文中演示了将航班延误调查分析转为模板，并说明如何通过对象/对象集 RID 等 URL 参数预载，或借助 Vertex widget 嵌入 Workshop 使用。

### 8. Vertex • Graphs • Generate a graph from other applications

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/generate-graph-apps/>
- **文档摘要**：本页说明如何从 Foundry 其他应用自动生成预填充的 Vertex Graph。介绍常用的 URL 参数：用 selectObjectRid/objectRid/objectSetRid 添加并居中对象，用 searchAroundFnRid 运行 Search Around，用 selectedTime/startTime/endTime 设置时间范围（支持 UNIX 毫秒或 ISO 格式），并说明这些参数在新建与已有 Graph URL 中的适用范围。

### 9. Vertex • Graphs • Generate graphs using Functions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/generate-graph-functions/>
- **文档摘要**：本页为参考手册，讲解如何用 Functions 编写复杂的 Search Around 函数来生成 Graph。函数需用 TypeScript 编写，且恰好接受一个 Ontology 对象（或对象列表）参数，返回类型必须为 IGraphSearchAroundResultV1，包含直接边（directEdges）、中间边（intermediateEdges）、孤立对象、分组与布局等字段。还提供排查建议与参考示例。

### 10. Vertex • Graphs • Derive properties using Functions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/derive-property-functions/>
- **文档摘要**：本页说明如何用 Functions 在对象上创建派生属性（derived properties）。这些属性可显示在对象视图、扩展节点标签中，并用于图层样式着色。函数需导出为公共方法、返回对象到值的映射、作用于对象集（而非单对象以保证大图性能）、无额外输入，并标记为已发布（tagged for release）；文中列出 TypeScript v1/v2 的具体要求。

### 11. Vertex • Graphs • Embed a graph in a Workshop module

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/embed-graph-workshop/>
- **文档摘要**：本页讲解如何将 Graph、Graph 模板与示意图嵌入 Workshop 模块，使用户在 Workshop 工作流中直接交互 Vertex 可视化。主要步骤为：添加 Vertex Graph widget、选择资源、配置 widget（输入、交互、时间配置、能力、界面等），并介绍模式如模板参数化、保存与分享探索，以及从 Workshop 变量动态传参实现数据驱动可视化。

### 12. Vertex • Graphs • Read-only mode

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/read-only-mode/>
- **文档摘要**：本页介绍 Vertex 的只读模式（read-only mode）。在只读模式下，无法新增对象（包括 Search Around）、无法重排节点、顶部工具栏隐藏。文中列出触发只读的典型场景：Graph 嵌入 Workshop 且显式开启只读设置、在 Carbon 中打开，以及嵌入 Notepad 时。

### 13. Vertex • Graphs • Media layers and image annotations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/media-layers/>
- **文档摘要**：本页讲解 Vertex 的媒体图层（Media layers）与图像标注功能，可在地图或图像上可视化并标注对象，实现空间分析与上下文理解。涵盖通过 Pipeline Builder 与 Ontology Manager 配置媒体引用、将对象添加为媒体图层、样式化与探索，以及创建与修改图像标注（annotations）；以 Wikimedia 公共领域舰船示意图为例构建可交互标注。

### 14. Vertex • Events and time series • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/events-overview/>
- **文档摘要**：本页是 Events and time series 的概述，介绍 Vertex 理解组织随时间变化的功能套件。核心为两类时序数据：Events（配置于 Ontology、含起止时间戳的对象类型）与 Time Series（随时间测量的数值，可配置为 time series properties）。还指引后续学习路径：配置事件、探索关联事件与时序、使用时间选择、配置阈值，并可结合 Scenarios 使用。

### 15. Vertex • Events and time series • Configure events

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/configure-events/>
- **文档摘要**：本页讲解如何在 Vertex 中配置 Events。需创建 time series event 对象类型，并额外添加一个 Vertex type class 来设置告警颜色/严重级别（如警告橙、危险红、主蓝、成功绿，通过 kind:vertex 与对应 name 指定）。这些 type class 可在 Ontology Manager 的对象 Capabilities 选项卡或属性 type class 中配置。

### 16. Vertex • Events and time series • Explore related events

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/explore-related-events/>
- **文档摘要**：本页介绍如何在 Vertex 中探索与对象关联的事件。事件在 Ontology 中定义并需具备明确起止时间。内容包含：在对象选择面板查看事件、配置图层样式中的事件徽标（event badges）、将关联事件对象加入 Graph 查看详情，以及在 series panel 中打开关联事件并用时间选择“拖拽”浏览多个事件。

### 17. Vertex • Events and time series • Explore related time series

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/explore-time-series/>
- **文档摘要**：本页说明如何在 Vertex 中探索关联的 Time Series。选中对象后可在选择侧栏的 Series 选项卡查看相关时序，并可将时序加入 series view 在图底部时间线浏览、打开至 Quiver 或 Object Explorer 详细查看，或加入扩展标签/读数为对象显示时序值，以呈现过去决策、当前状态与未来潜力的影响。

### 18. Vertex • Events and time series • Use time selection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/use-time-selection/>
- **文档摘要**：本页讲解 Vertex 的时间选择（Time selection）功能。通过工作区顶部的 Time selection 栏可设置时间窗口并滚动浏览系统随时间的变化，也可用日期选择器指定特定区间；支持点击“+ Compare”创建多个时间选择用于对比，扩展标签/读数的数值会随之更新，若有模拟案例研究还可从下拉中选择对比建模值。

### 19. Vertex • Events and time series • Configure thresholds

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/configure-thresholds/>
- **文档摘要**：本页简要说明如何在 Vertex 中配置阈值（Thresholds）。在 Ontology Manager 的 Vertex Capabilities 中，可对 Time Series 度量配置阈值，当阈值被突破时系统将给出相应提示，便于在可视化中识别异常情况。

### 20. Vertex • Events and time series • View and filter events on a timeline

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/timeline/>
- **文档摘要**：本页讲解如何在 Vertex 时间线（timeline）上查看与筛选事件。可用 Timeline 按钮显示/隐藏时间线，通过 Shift+左键拖拽或 Time filter 按时间范围筛选（匹配节点不透明、不匹配则淡化），并支持改变光标位置、展开时间线、样式化以及播放（playback）自动移动时间光标（速度可调，循环遍历窗口）。

### 21. Vertex • Scenarios • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/scenarios-overview/>
- **文档摘要**：本页是 Scenarios 的概述。Scenario 让你与建模后的宇宙交互，提出“What if”问题以模拟不同运营条件；它基于系统 Graph，沿一个或多个建模输入评估动作并计算输出，反映数字孪生的真实交互。Vertex 可串联多个模型自动将输出作为另一模型输入，端到端理解变更影响，并依赖 Foundry 中发布的模型与 Functions（作为 Actions）运行。

### 22. Vertex • Scenarios • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/scenarios-getting-started/>
- **文档摘要**：本页是 Scenarios 的快速入门。介绍如何借助 Foundry 既有模型提出 what-if 问题：添加 Scenario 与 Actions、选择模型（旧模型选择流程已进入 Sunset，建议改用函数支撑的 Action）、选择输入/输出参数、运行 Scenario、构建案例研究，以及配置链式模型（Chained models）以串联多模型量化端到端影响。

### 23. Vertex • Scenarios • Scenario options

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/scenarios-options/>
- **文档摘要**：本页说明 Vertex Scenario 的各项选项。涵盖时间窗口选择（应选有已知数据的时间）、高级选项（Time Series 平滑周期）、作用域（Scope，仅限图中对象）、是否运行基线 Scenario 作为对比，以及在对象选择面板中选择输入/输出参数（可添加单个时序、对象属性或度量，输入参数可在运行前手动覆盖）。

### 24. Vertex • Scenarios • Configure chained models [Sunset]

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/chained-models/>
- **文档摘要**：本页介绍配置链式模型（Chained models，功能已进入 Sunset 阶段，未来将被废弃，建议改用函数支撑的 Action）。Vertex 可将相互连接的模型串接，自动将一个模型的输出作为后续模型输入（以链接图标标注），从而量化端到端变更影响；在所选模型输入添加 override 会触发基线 Scenario（若启用），支撑 what-if 模拟。

### 25. Vertex • Configuration • Configure settings in Control Panel

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/vertex-settings-control-panel/>
- **文档摘要**：本页讲解如何在 Control Panel 中配置组织级的 Vertex 设置（需 Vertex Admin 角色）。包括：Graph 默认值（默认时间跨度、图模式 diagram/graph、图标类别）、数据加载（Time Series 轮询间隔、缺失数据告警、对象搜索与 Search Around 上限、模板嵌套深度）、模型配置（旧版模型配置及 Modeling objectives 新体验）。

### 26. Vertex • Configuration • Configure link merging

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/link-merging/>
- **文档摘要**：本页说明如何配置 Link Merging（链接合并）。在 Ontology Manager 的 Vertex Ontology Capabilities 中启用后，对象会被自动归组为两个节点之间的边，从而更直观地展示节点间的交易流向并简化 Graph 生成；用户可在 Search Around 菜单的“link via”选项中使用此能力。

### 27. Vertex • Add Vertex graph templates to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vertex/marketplace-vertex/>
- **文档摘要**：本页讲解如何将 Vertex Graph 模板添加到 Marketplace 产品，供其他用户安装复用。使用 Foundry DevOps 创建产品后添加 outputs，通过 Add files 从 Compass 文件系统定位并加入 Graph 模板；除已保存的 Search Arounds 外，全部 Vertex Graph 模板功能均受支持。
