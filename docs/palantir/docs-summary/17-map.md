# Map 地图

> **分类说明**：地图的图层管理、时空可视化、对象集成与模板

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `map` |
| 文档数量 | 37 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Map • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/map/overview/>
- **文档摘要**：Map 应用提供强大的地理空间（geospatial）与时间（temporal）分析和可视化能力，可将 Foundry 中的各类数据整合为统一的地理空间体验。页面介绍了 Map 的核心能力：探索地理对象间的连接、遍历物理网络、基于包围盒与多边形相交进行地理空间查询、可视化矢量数据与卫星影像等上下文数据，以及随时间变化的目标移动路径（Track）和事件等时序数据。Map 使用 Web Mercator（EPSG:3857）投影，期望经纬度坐标为 WGS 84（EPSG:4326）。

### 2. Map • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/map/getting-started/>
- **文档摘要**：本指南演示如何使用 Map 应用，结合 Foundry Training and Resources 项目中的资源，以航空公司航线数据为例进行地理空间分析。页面逐步介绍：创建新地图、熟悉 Map 界面、将对象（如底特律都会机场 DTW）添加到地图、使用 Search Around 探索关联对象（如跑道），以及通过绘制圆形进行地理空间搜索，查找 200 公里范围内的其他机场。

### 3. Map • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/map/core-concepts/>
- **文档摘要**：本页面介绍 Map 的核心概念。Layer（图层）是构建地图的地理数据集合，支持基础图层（base layer，如浅色/深色主题、卫星影像）与对象图层等组合；Object styling 定义对象在地图上的外观；Time selection 涉及每张地图的 selected time 与 time window，用于控制时序数据的加载与时间相关样式的透明度。

### 4. Map • Interact with maps • Map interface overview

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/map/map-overview/>
- **文档摘要**：本页面描述 Map 应用界面的各个组成元素。左侧面板包含 Layers（添加、管理与样式化图层）、Find（查找对象与坐标）、Histogram（按属性与时间序列分析筛选对象）、Info（地图总览）；右侧面板有 Selection 与 Time Selection；底部右侧为 Series 面板。顶部工具栏提供 Select、Search Around、Draw、Capture、Measure、Annotate、Delete 等功能。

### 5. Map • Interact with maps • Create and save maps

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/create-save-maps/>
- **文档摘要**：本页面介绍如何在 Map 中创建和保存地图。创建地图需展开左侧 Foundry 导航栏，在 Apps 区域的 Operational Applications 中找到 Map 应用；保存地图首次需点击 Save as… 选择保存位置，之后点击 Save 即可。页面特别提示：地图不会自动保存。

### 6. Map • Interact with maps • Add data to a map

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/add-to-map/>
- **文档摘要**：本页面讲解如何向地图添加数据进行地理空间分析。可添加的地理空间数据有两类：Ontology objects 和 map overlays。添加对象时可按类型筛选并选择添加（默认上限 1000 个）；支持按地理空间形状搜索相交对象、添加 Map Layer Editor 中创建的 overlays，以及通过 Search Around 遍历 Ontology 关系添加关联对象。

### 7. Map • Interact with maps • Layer management

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/layer-management/>
- **文档摘要**：本页面介绍图层的日常管理操作。可在 Layers 面板中对图层进行显示/隐藏切换、点击重命名、拖动调整图层顺序（列表中靠上的图层渲染在上层），以及将选中对象移动至新的或已有的图层（需为相同对象类型），从而对不同集合应用不同的样式。

### 8. Map • Interact with maps • Navigation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/navigation/>
- **文档摘要**：本页面讲解 Map 中的地图导航操作。基本控制包括平移（点击拖动或方向键）、缩放（按钮、滚轮或 +/- 键）以及旋转与倾斜（按住 Ctrl/Cmd 拖动）。可按键 0 将地图居中到选中对象，并使用 Find 面板查找已添加的对象、通过 Mapbox 地理编码查找地点，或直接输入坐标导航到指定位置。

### 9. Map • Interact with maps • Selection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/selection/>
- **文档摘要**：本页面介绍 Map 中选择对象和标注的多种方式。可在地图上点击、框选（Shift 拖动）、配合 Ctrl/Cmd 多选；Select 工具栏菜单提供全选、按类型全选、反选、选择相交对象及按形状选择等；也可通过 Histogram 面板按属性选择，使用 Selection 面板查看，以及锁定图层对象和从地图移除对象。

### 10. Map • Interact with maps • Annotations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/annotations/>
- **文档摘要**：本页面介绍如何使用标注（Annotations）高亮并补充地图特定区域信息。可创建的标注类型包括文本、线、多边形、矩形与圆形，选中后可在 Selection 面板编辑标题与备注，并通过 Layers 面板调整样式（如文本颜色/大小、形状颜色/透明度/描边、测量显示等）。文本标注的标题与备注会显示在地图与悬停提示中。

### 11. Map • Interact with maps • Shapes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/shapes/>
- **文档摘要**：本页面讲解形状（Shapes）的用途与操作。形状可用于圈选地理空间区域以搜索对象、选择地图上的对象、执行动作或创建标注。形状可通过绘图工具手动绘制，或从当前选中对象创建；可经 Modify 按钮编辑。激活形状后，可执行选择相交对象、在形状内搜索、触发消费形状的 Ontology Actions，以及删除形状等操作。

### 12. Map • Interact with maps • Histogram and filtering

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/histogram/>
- **文档摘要**：本页面介绍 Histogram（直方图）面板及其筛选功能。面板按对象类型与属性展示数量分布，可展开查看各属性取值的行。点击行可选中匹配对象（支持 Shift/Ctrl 多选），使用 Filter to / Filter out 按钮可创建临时过滤器，降低不匹配对象的透明度并使其不可交互，过滤器不随地图保存。

### 13. Map • Interact with maps • Actions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/actions/>
- **文档摘要**：本页面介绍 Map 中的 Actions（动作）功能，可基于在地图上绘制的点、多边形或线创建或编辑对象。针对形状与点，右键菜单的 Actions 列出适用于地理空间点的所有动作；针对 Ontology 对象，可在 Selection 面板使用 Actions 按钮执行，按配置绘制或编辑形状后补充参数，Map 将执行动作并更新地图。

### 14. Map • Time • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/map/time-overview/>
- **文档摘要**：本页面概述 Map 中与时间相关的数据与功能。时间型数据包括 Time series（随时间变化的可测量值）、Events（带有特定时间/时间范围元数据的对象）和 Tracks（位置随时间变化的对象）。所有时序数据均遵循当前 selected time 与 time range；可开启 Latest Data 视图实时更新，并使用 timeline 调整选中时间、时间范围与过滤时间窗口。

### 15. Map • Time • Time selection

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/map/time-selection/>
- **文档摘要**：本文档介绍已被废弃（deprecated）的 Time selection 面板，其功能现已迁移至 timeline，文档将在未来版本移除。原面板用于调整当前选中时间与时间窗口，可滚动时间窗口理解数据随时间的变化，支持 View latest 实时数据模式，并使用日期范围选择器调整时间窗口。

### 16. Map • Time • Timeline

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/timeline/>
- **文档摘要**：本页面介绍 Map 中 Timeline（时间轴）的使用。可借此查看时序数据并配置 time window 与 selected time。基础控制包括开关时间轴、调整选中时间（拖动光标或用日期选择器）、调整时间范围（滚轮缩放、Cmd/Ctrl 滚动平移）、过滤时间窗口与播放；还可向时间轴添加 Events、Track breadcrumbs 与 Time series（Beta），并附有常见问题解答。

### 17. Map • Time • Events

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/map/events/>
- **文档摘要**：本页面介绍 Map 中的 Events（事件）概念。事件指包含特定时间点或时段信息的对象类型或时间轴几何（timeline geometry），常见为带有起止时间戳属性的事件对象。页面区分了 Event objects 与 timeline geometries 在渲染配置、地图画布效果与时间轴表现上的差异，并说明如何用事件对象进行基于透明度的样式化，以及关联事件（linked events）。

### 18. Map • Time • Time series

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/time-series/>
- **文档摘要**：本页面介绍 Map 中的 Time series（时间序列）。时间序列是随时间变化的可测量值，可在 Ontology 中配置为 time series properties。页面讲解如何查看关联时间序列（在 Selection 面板的 Series 标签）、以时间序列为依据进行值样式化以响应时间选择，以及如何在时间轴中交互时间序列（Beta 阶段，需联系 Palantir Support 申请）。

### 19. Map • Time • Series panel [Planned deprecation]

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/map/series-panel/>
- **文档摘要**：本文档介绍 Map 中已进入计划废弃（planned deprecation）阶段的 Series 面板，将于 2026 年 1 月 31 日后不可用，建议改用 timeline 查看时序数据。该面板用于进一步查看与 timeline 选中时间及窗口同步的 time series 与 linked events，可通过右键菜单或 Selection 面板的 Series 标签添加时间序列并进行时间浏览。

### 20. Map • Visualize Ontology data • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-objects/>
- **文档摘要**：本页概述如何在 Map 中可视化并分析来自 Ontology 的地理空间数据。主要介绍 object layer 的 displays（如图标、线、多边形等）以及基于值与固定的图层样式配置，包括颜色、数值、透明度、标签和按缩放级别控制显示可见性等要点。

### 21. Map • Visualize Ontology data • Points (icons and circles)

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-points/>
- **文档摘要**：本页讲解 Map 中两种点数据显示方式：icon 与 circle。涵盖 geometry source（geopoint 属性或 tracks）、icon 配置（对象默认、媒体图片、固定图标、属性驱动、旋转、标记形状）以及 circle 配置与加载方式，帮助用户根据工作流定制点数据可视化。

### 22. Map • Visualize Ontology data • Lines and polygons

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-polygons-lines/>
- **文档摘要**：本页介绍如何在 Map 中渲染 line 与 polygon 显示。说明两种几何来源（geoshape 属性与两点间的 line segment），以及描边宽度、描边样式（实线/虚线/点线）和填充多边形的配置，并指出大量对象时可支持基于 tile 的加载方法。

### 23. Map • Visualize Ontology data • Choropleths

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-choropleths/>
- **文档摘要**：本页讲解 Choropleth（分级统计图）地图，即按区域内所有对象的聚合值着色以呈现空间分布模式。介绍将对象分组为区域的方法（boundary identifiers 如 ISO 国家码、US 州代码，或 linked objects），以及基于标准聚合与表达式聚合的着色配置。

### 24. Map • Visualize Ontology data • Clusters

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-clusters/>
- **文档摘要**：本页介绍 Cluster 显示，适用于基于 geopoint 位置属性的大规模对象集，按地理邻近度聚合对象并以大小/颜色表示聚合度量。说明基于聚合的着色（类似 circle 几何）以及 cluster 文本标签在 beta 阶段的行为，聚类会随缩放和平移自动更新。

### 25. Map • Visualize Ontology data • Timeline events

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-timeline/>
- **文档摘要**：本页讲解如何在 Map 的时间轴（timeline）上可视化图层中所有对象的时间属性。介绍通过 Layers 面板添加 Timeline geometry，并配置起始/结束时间属性、形状与颜色（可基于属性或 measures 着色），时间线几何需完整设置属性后才会出现。

### 26. Map • Visualize Ontology data • Tracks (moving objects)

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/visualize-tracks/>
- **文档摘要**：本页介绍 Track 显示，用于渲染随时间移动的 Object，包括 track lines 与 track breadcrumbs。说明移动几何插值模式（线性、最后已知位置、最大时间间隔），以及 track 显示支持基于时间的透明度样式，并可作为 icon 和 circle 的位置来源。

### 27. Map • Visualize Ontology data • Loading methods

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/objects-loading-methods/>
- **文档摘要**：本页讲解 Map 的加载方法，默认加载图层全部对象存在规模限制，tile-based 加载方法只加载可见范围所需数据以支持高规模数据集。说明如何在样式面板配置加载方法，以及 tile 加载在样式、过滤、形状创建等方面的功能兼容性限制。

### 28. Map • Integrate data for the map • Ontology objects for the map

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/map/integrate-objects/>
- **文档摘要**：本页介绍 Map 支持的带地理空间数据的 Ontology 对象类型及其配置方式。涵盖 Ontology-native 的 geopoint/geoshape、circle、choropleth（boundary identifiers 与 linked objects）、H3 六边形、地理配准影像、track 与 event 对象等，指导如何为地图集成数据。

### 29. Map • Integrate data for the map • Search Arounds for the map

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/integrate-searcharounds/>
- **文档摘要**：本页讲解 Map 的 Search Arounds，即基于 link 或 function 的搜索以探索相关对象。介绍 link Search Arounds、两步 link merged Search Arounds（含中间对象配置）以及 Search Around functions 的实现、排查建议与示例，可在 Control Panel 配置数量限制。

### 30. Map • Integrate data for the map • Functions for the map

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/integrate-functions/>
- **文档摘要**：本页介绍 Map 中基于 Function 的样式（function-based styling），可用 Function 生成动态值并显示在 Selection panel 或用于 value-based 着色。说明用于样式配置的 Function 需满足的条件：单一数组或 object set 参数，返回以对象为键、字符串或数值为值的映射。

### 31. Map • Integrate data for the map • Ontology Actions for the map

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/integrate-actions/>
- **文档摘要**：本页讲解如何在 Ontology 中配置应用于 Map 地理空间 Object 的 Actions。介绍 Point Actions（geopoint 参数）与 Shape Actions（geojson 参数），以及如何配置 Action 让用户在地图上编辑对象的 geoshape 属性（如添加点、缓冲、平移等）。

### 32. Map • Integrate data for the map • Map Layer Editor

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/map/layer-editor/>
- **文档摘要**：本页介绍 Map Layer Editor 应用，用于创建、编辑和预览包含地理空间数据的 map layer，可在 Map 应用与 Workshop map widget 中使用。说明如何创建新图层，以及 vector、raster、object 和 Mapbox JSON 四种图层类型的数据来源与配置方式。

### 33. Map • Templates and Workshop widget • Map templates

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/templates/>
- **文档摘要**：本页讲解 Map templates，用于生成包含完整数据的地图并嵌入 Workshop 模块。介绍创建模板（从标准地图另存为模板）、配置参数（object 与非 object 参数）、Search Arounds、Layers、Series、Interface，以及如何使用模板生成地图。

### 34. Map • Templates and Workshop widget • Embed a Map template in a Workshop module

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/widget/>
- **文档摘要**：本页讲解如何将 Map template 嵌入 Workshop 模块，参数值可直接由 Workshop 变量传入。说明三步流程：添加 Map widget、选择模板资源、配置 widget（含资源、参数、刷新键、基础图层选择、场景数据加载等），并介绍交互与输出选项。

### 35. Map • Configuration • Settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/settings/>
- **文档摘要**：本页介绍 Map 的设置菜单，分为用户级与地图级设置。用户设置含距离单位、启用 GeoJSON 面板；地图设置含轮询间隔、时间序列桶数、时区、时间格式、主题（浅/深模式）与实验性标签等，其中部分地图设置对所有用户生效。

### 36. Map • Configuration • Control Panel

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/control-panel/>
- **文档摘要**：本页介绍通过 Control Panel 配置组织级的 Map 设置，需 Map Admin 角色。涵盖 Map Defaults（默认视口、时间选择、单位）、data loading（轮询间隔、时间序列点数、对象与 Search Around 数量限制）、API Keys（Mapbox、Bing）以及 Base maps（Mapbox、自定义、水印）等。

### 37. Map • Add Map templates to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/map/marketplace-map-templates/>
- **文档摘要**：本页讲解使用 Foundry DevOps 将 Map templates 打包进 Marketplace 产品，供其他用户安装复用。说明除 Map Layers 外所有模板功能均受支持，并通过创建产品、添加输出、从 Compass 文件系统选择模板文件加入产品来完成发布。
