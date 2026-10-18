# Pipeline Builder

> **分类说明**：Pipeline Builder 的界面与核心功能：管道节点、检查点、预览、分支管理、输入输出配置与管道调试

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `pipeline-builder` |
| 文档数量 | 64 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Pipeline Builder • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/overview/>
- **文档摘要**：本页概述 Pipeline Builder——Foundry 主要的数据集成应用，可将原始数据源转换为可供分析的干净输出。页面说明其图形化、点选式界面如何让编码与非编码用户协作，并内置强类型函数、严格输出检查、自动构建路径剪枝等特性，底层采用 Spark 与 Flink 及专用后端模型。

### 2. Pipeline Builder • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/core-concepts/>
- **文档摘要**：本页介绍 Pipeline Builder 的核心概念。Build 部分涵盖数据集（datasets）、分支（branches）、transforms 与 pipeline outputs；Manage 部分介绍调度（schedules）与数据预期（data expectations）。建议在构建首个管道前先了解这些基础概念。

### 3. Pipeline Builder • Navigation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/navigation/>
- **文档摘要**：本页说明 Pipeline Builder 的界面、导航控件与可用工具。介绍落地页如何展示管道状态，以及 Edit、Proposals、History 三种视图；Edit 视图进一步细分为顶部工具栏、详情侧栏、图形区与预览面板，并说明分支创建、查看变更与提案等操作。

### 4. Pipeline Builder • Tips and tricks

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/tips-and-tricks/>
- **文档摘要**：本页汇总 Pipeline Builder 的使用技巧，帮助用户更快构建、优化性能并保持条理。涵盖配置用户偏好、快捷键、复制粘贴节点等加速方法，更快管道模式、计算画像、单元测试等改进手段，以及文件夹分组、文本节点、查找替换列名等组织方式。

### 5. Pipeline Builder • AIP features

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/pipeline-builder-aip/>
- **文档摘要**：本页介绍 Pipeline Builder 的 AIP 能力。自定义工作流方面包括 Use LLM node、Text to embeddings、LLM evaluation suite；助手功能包括 Explain、Suggest names and descriptions、Transform Assist、Generate，帮助用户构建并理解管道（可用性因客户而异）。

### 6. Pipeline Builder • Functions Index

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/functions-index/>
- **文档摘要**：本页是 Pipeline Builder 的函数索引，列出按层级划分的表达式与 transform。表达式分为行级（row level）、聚合（aggregations）与生成器（generators）三类，并逐项说明其支持的管道类型、参数、输出类型与示例，供用户在构建管道时查阅。

### 7. Pipeline Builder • Input datasets • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/datasets-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的输入数据集。说明管道需以数据输入为起点，支持结构化（带 schema 的表格数据）、半结构化（CSV、JSON、XML 等 schema-less 文件）与非结构化（媒体集）数据，并可用解析 transform 将半结构化文件转为表格。

### 8. Pipeline Builder • Input datasets • Add datasets

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/datasets-add/>
- **文档摘要**：本页说明如何向 Pipeline Builder 添加数据集，提供四种方法：从 Data Connection 应用导入、从 Foundry 添加已有数据集或媒体集、从电脑上传文件、手动输入数据表。手动输入表仅支持 batch 管道，不支持 faster pipelines，并建议配置项目结构以落实数据治理。

### 9. Pipeline Builder • Input datasets • Add generated data

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/datasets-generated/>
- **文档摘要**：本页说明在 Pipeline Builder 中自动生成输入数据的方法。用户可在手动输入的数据集里使用 Generate notional data 按列类别（如城市、邮箱、外键）生成示例数据，并可锁定列以保留原有值。该功能在 CBAC 栈的 faster 管道中不可用，需改用标准 Spark 管道。

### 10. Pipeline Builder • Input datasets • Configure sources and dataset syncs

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/datasets-sources/>
- **文档摘要**：本页说明如何在 Pipeline Builder 内创建与导入 Foundry 提供的 JDBC 源及数据同步（sync）。可从输入数据集导入源、搜索全部源进行导入或创建，并通过 Configure Source 配置仅需必填字段的 Foundry worker 源及网络出口策略；复杂源仍需在 Data Connection 中配置。

### 11. Pipeline Builder • Input datasets • Computation modes for batch

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/datasets-computation-modes-for-batch/>
- **文档摘要**：本页介绍 batch 输入数据集的两种计算模式。Snapshot 计算对全部输入执行 transform 并整体替换输出；Incremental 计算仅处理自上次构建以来新增的 APPEND/UPDATE 数据以节省算力，但受到若干限制，且可强制启用增量执行。

### 12. Pipeline Builder • Transforms • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的数据转换能力。区分 expression（输入列、输出单列）与 transform（输入整表、输出整表），二者在同一配置界面中结合使用；并介绍 Join、Union、Split、Null 处理、用户自定义函数、训练模型与子图等结构化转换方式。

### 13. Pipeline Builder • Transforms • Transform data

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-transform-data/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中对数据应用 transform。步骤包括选择数据集节点、搜索并浏览可用 transform、在配置面板填写列与表达式、复用已有表达式，以及应用后查看图形连接与转换视图（折叠面板或伪代码渲染）。

### 14. Pipeline Builder • Transforms • Null handling

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/null-handling/>
- **文档摘要**：本页解释 Pipeline Builder 如何处理 null 值。说明过滤器、Case 与 join 匹配条件的布尔结果可为 True、False 或 null，仅 True 满足条件；并对比 null-safe 等式与不安全等式的差异，以及各 transform 对 null 结果的具体处理方式。

### 15. Pipeline Builder • Transforms • Join data

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-join-data/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中执行 join。通过选择左右两个数据集并配置 join 类型（left、right、inner、outer）、匹配条件与列前缀，将含匹配列的数据集合并；可预览输出，join 要求两侧 schema 兼容，并支持高级选项控制包含列。

### 16. Pipeline Builder • Transforms • Union data

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-union-data/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中执行 union。union 合并两个或多个数据集并保留全部行（含重复），可用 union board 或多选方式一次性合并多表，要求输入 schema 一致；预览会标示缺失列，应用后可通过 Drop duplicates 去除重复行。

### 17. Pipeline Builder • Transforms • Transform media

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-transform-media/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中转换非结构化媒体数据（媒体集）。媒体集可直接作为媒体或表格输入，支持 Slice PDF、PDF 文本提取等媒体 transform 与表达式；介绍选择媒体集、搜索 transform、过滤媒体集及配置应用的具体流程。

### 18. Pipeline Builder • Transforms • Create geospatial transforms

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-geospatial/>
- **文档摘要**：本页介绍在 Pipeline Builder 中创建地理空间 transform。说明以逻辑类型（GeoPoint、Geometry、H3Index 等）建模地理空间数据，以及加载、转换（几何比较、球面几何、H3、复杂形状近似、地理空间 join）和将结果与 Ontology 结合使用的方法。

### 19. Pipeline Builder • Transforms • Create unique IDs

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/unique-id-creation/>
- **文档摘要**：本页给出在 Pipeline Builder 中创建唯一 ID 的最佳实践。推荐将字符串列拼接后计算 SHA256 哈希，因其一致、可分布式生成且适合作为对象主键；并分析单调递增 ID 的弊端，以及在缺乏可哈希唯一列时的应对方案。

### 20. Pipeline Builder • Transforms • Joins in streaming pipelines

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-streaming-joins/>
- **文档摘要**：本页说明 Pipeline Builder 流式管道中的 join。可将流与 batch 数据集（Left Lookup Join）或其他流进行连接，并分别阐述两种架构与限制：流式 join 不可在 join 前转换 batch 数据集，且左侧须为流或启用 Streaming 的 batch 数据集。

### 21. Pipeline Builder • Transforms • Use LLM node

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/pipeline-builder-llm/>
- **文档摘要**：本页介绍 Pipeline Builder 的 Use LLM node，可在数据转换间无缝嵌入大语言模型处理而无需编码。说明选择数据集、模型（Palantir 提供与注册模型）、预置提示模板（分类、摘要、翻译、情感分析、实体抽取、视觉等）及可选配置、试运行与评估套件的使用流程。

### 22. Pipeline Builder • Transforms • Evaluation suite

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/evaluation-suite/>
- **文档摘要**：本页介绍 Pipeline Builder 的 LLM 评估套件，用于在部署前测试 LLM transform 与逻辑。说明如何进入评估套件、新建评估、添加测试数据与评估器、选择输出数据集并运行检查；每个评估对 Use LLM node 运行测试数据并比对预期结果。

### 23. Pipeline Builder • Transforms • Split transform

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-split/>
- **文档摘要**：本页介绍 Pipeline Builder 中的 Split transform。该 transform 可根据指定条件将输入数据行划分到两个独立输出（True/False），支持标准批处理与更快的流水线，但不支持流式流水线。页面说明了添加方式、条件配置、输出预览及示例。

### 24. Pipeline Builder • Transforms • Pattern mining

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-pattern-mining/>
- **文档摘要**：本页说明 Pipeline Builder 的频繁模式挖掘（Frequent Pattern Mining）功能。它基于 FP-Growth 算法，可在大规模数据中发现频繁项集与关联规则，并给出零售购物篮分析等示例。页面介绍了该技术的原理与典型应用场景。

### 25. Pipeline Builder • Transforms • Trained model node

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/transforms-trained-model/>
- **文档摘要**：本页介绍 Pipeline Builder 中的 Trained model node，可在批处理或流式流水线中直接运行用户自定义的机器学习模型进行推理。页面涵盖模型导入、节点添加、输入/输出映射、流式配置、资源与执行模式等内容，并支持 Ontology 等下游集成。

### 26. Pipeline Builder • Pipeline outputs • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的流水线输出类型。输出是流水线的最终结果，可为数据集、媒体集、文件、地理时序同步、虚拟表（Beta）或 Ontology 组件（对象类型、对象链接、时序同步）。部署首次构建后输出即被创建，便于在其他 Foundry 应用中扩展使用。

### 27. Pipeline Builder • Pipeline outputs • Add a dataset output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-add-dataset-output/>
- **文档摘要**：本页提供在 Pipeline Builder 中添加数据集输出的操作指南。介绍如何创建输出数据集、配置输出设置（期望、写入模式、写入格式、覆盖数据集）以及构建输出。页面还说明输出 schema 与输入节点 schema 的匹配状态及常见错误处理。

### 28. Pipeline Builder • Pipeline outputs • Add a media set output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-add-media-set-output/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中添加媒体集（media set）输出。介绍先生成新媒体集再配置媒体类型与格式的流程，并强调媒体集输出始终为事务性、不支持增量处理等约束。完成后需保存并部署流水线以构建输出。

### 29. Pipeline Builder • Pipeline outputs • Add a virtual table output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-add-virtual-table-output/>
- **文档摘要**：本页介绍在 Pipeline Builder 中添加虚拟表（virtual table）输出的方法。虚拟表用于存储 Foundry 之外的数据，需在输出端配置外部源位置且源需启用导出；注意分支不支持虚拟表输出，且流水线必须至少包含一个 Foundry 数据集输出才能部署。

### 30. Pipeline Builder • Pipeline outputs • Add an Ontology output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-add-ontology-output/>
- **文档摘要**：本页提供在 Pipeline Builder 中添加 Ontology 输出的操作指南。介绍如何创建对象类型输出与链接类型输出、编辑 API 名称、启用对象编辑与接口实现，以及解决 Ontology 校验冲突与安全权限配置。支持从批处理与流式数据集生成 Ontology 对象与链接。

### 31. Pipeline Builder • Pipeline outputs • Add a geotemporal series sync output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-add-geotemporal-series-output/>
- **文档摘要**：本页说明如何添加地理时序（geotemporal series）同步输出，使 Foundry 中的批处理或流式地理时序数据可在地图等下游应用中使用。介绍前置条件、主字段映射（Series ID、时间戳、位置、海拔）、附加字段、高级设置与安全配置，并附故障排查。

### 32. Pipeline Builder • Pipeline outputs • Preview pipeline

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-preview-pipeline/>
- **文档摘要**：本页介绍 Pipeline Builder 的预览面板功能。选中节点后可运行从原始数据集到该节点的逻辑并查看列统计；支持时间戳时区选择、预览行为配置与行数计算。字符串与数值列分别提供直方图、分布及基础统计量等预览信息。

### 33. Pipeline Builder • Pipeline outputs • Deliver pipeline

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-deliver-pipeline/>
- **文档摘要**：本页说明如何交付（deliver）Pipeline Builder 流水线。区分 deploy（更新输出逻辑）与 build（执行逻辑物化结果），并介绍交付变更、重放策略、构建设置、从输出节点构建及流式流水线的重部署与重放选项。

### 34. Pipeline Builder • Pipeline outputs • Remove markings on outputs and organizations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/outputs-remove-markings-and-organizations/>
- **文档摘要**：本页提供在 Pipeline Builder 中移除输出上继承的 markings 与 organizations 的操作指南。需在启用分支保护、要求代码审批并允许安全标记变更的前提下，提出变更并撤销移除；相关操作与 job group 及多保护分支存在关联约束。

### 35. Pipeline Builder • Pipeline outputs • Breaking changes

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/breaking-changes/>
- **文档摘要**：本页介绍流式与增量流水线中的破坏性变更（breaking changes）概念。当状态函数（如聚合、外连接缓存、心跳检测等）被修改时，旧状态可能失效，需重放（replay）刷新。页面列举常见状态中断原因、状态保留修改及强制增量行为等处理方式。

### 36. Pipeline Builder • Pipeline management • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的流水线管理功能与最佳实践。支持通过参数、自定义函数复用逻辑，并通过文件夹分组、节点颜色组、显示/隐藏节点、job 分组及 checkpoint 等方式管理大型流水线，提升可读性与计算效率。

### 37. Pipeline Builder • Pipeline management • Add an input sampling strategy

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-input-sampling/>
- **文档摘要**：本页说明如何为大型输入数据集添加采样策略以加快预览速度。可右击输入节点选择百分比采样，文件数据集可用基于元数据的过滤预览采样，并支持将策略应用到所有兼容输入。注意采样仅影响预览，部署仍运行完整数据。

### 38. Pipeline Builder • Pipeline management • Pipeline parameters

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-parameter-overview/>
- **文档摘要**：本页介绍 Pipeline Builder 的流水线参数（parameters），即可在多个 transform 中复用的全局值。说明如何在参数视图中创建、编辑、删除参数，并列举常量值、正则、结构体定位、列、表达式等参数类型及其使用方式。

### 39. Pipeline Builder • Pipeline management • Build settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-build-settings/>
- **文档摘要**：本页说明 Pipeline Builder 的构建设置（build settings），用于调整批处理与流式流水线性能。涵盖批处理计算配置（compute profiles、Warm pool、原生加速）、流式配置（最大时长、流行为）及外部流水线设置。计算配置权限在批处理 Spark 流水线中按项目配置。

### 40. Pipeline Builder • Pipeline management • Compute profile permissions

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/compute-profile-permissions/>
- **文档摘要**：本页说明如何配置计算配置（compute profile）权限，权限按项目级别配置于标准批处理 Spark 流水线。自动导入（至 Medium）与需手动导入（Large/Extra Large）两类配置，并介绍从 Pipeline Builder 或 Control Panel 导入、移除配置及 Marketplace 产品中的相关行为。

### 41. Pipeline Builder • Pipeline management • Create custom functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-create-custom-functions/>
- **文档摘要**：本页提供在 Pipeline Builder 中创建自定义函数（custom functions）的操作指南。自定义函数包含自定义表达式（可带参数）与自定义 transform（将一系列 transform board 封装复用），介绍各自的创建步骤及在流水线中复用逻辑的方法。

### 42. Pipeline Builder • Pipeline management • Subgraphs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/subgraphs/>
- **文档摘要**：本页介绍 Pipeline Builder 的 Subgraphs（子图，Beta）功能，可将一个或多个 transform 封装为可复用块。说明如何从零创建子图、向流水线添加子图、从现有节点创建子图及添加多输入子图，从而减少重复逻辑、简化图结构。

### 43. Pipeline Builder • Pipeline management • Show and hide nodes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-show-hide-nodes/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中显示与隐藏节点以管理大型流水线。支持手动或框选后隐藏节点、按颜色组隐藏，并在其他标签页（如变更提案、解决冲突）中操作。隐藏后相邻节点间以虚线标示，可查看隐藏节点数量。

### 44. Pipeline Builder • Pipeline management • Folders in Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-file-tree/>
- **文档摘要**：本页介绍 Pipeline Builder 中的文件夹功能，用于组织大型流水线的节点。可创建文件夹与子文件夹、将节点拖入分组，并支持删除文件夹与通过眼睛图标显示/隐藏节点区段，从而聚焦关注部分、提升导航与编辑效率。

### 45. Pipeline Builder • Pipeline management • Color groups

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-color-groups/>
- **文档摘要**：本页介绍 Pipeline Builder 中的节点 color groups（颜色分组）功能，用于更好地组织和管理 pipeline 图。颜色分组可通过纵向或横向划分来区分集成阶段、聚合相关对象的上游血缘，并可折叠或隐藏以简化视图。页面说明了如何创建分组、向分组添加节点，以及如何折叠、展开、显示/隐藏、修改（移除节点、编辑、删除、选择、复制、查看变更）颜色分组。

### 46. Pipeline Builder • Pipeline management • Charts

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-charts/>
- **文档摘要**：本页说明 Pipeline Builder 中的 Charts（图表）功能，可在不离开工作区的情况下无缝分析与验证中间数据。图表可从任意 transform node 添加，目前支持柱状图（含直方图）。页面介绍了如何在一个 transform node 上通过 Edit 或 Visualize 选项添加图表，并在 Configure 面板设置 X 轴、Y 轴及聚合方式，在 Format 面板修改标题与轴标签。

### 47. Pipeline Builder • Pipeline management • Text nodes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-text-nodes/>
- **文档摘要**：本页介绍 Pipeline Builder 中的 Text nodes（文本节点）功能，可添加到 pipeline 中用于为图添加说明、强调细节。文本节点渲染在其他图节点背景层，可用作分区分隔符或分组标签，采用 Markdown 语法编写，可像普通节点一样着色且不受布局选项影响。页面说明了如何添加文本节点（点击 Text 后编辑）、使用 Mod+Enter 或 Escape 退出，以及如何为文本节点着色与调整大小。

### 48. Pipeline Builder • Pipeline management • Checkpoints

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-checkpoints/>
- **文档摘要**：本页介绍 Pipeline Builder 中的 Checkpoints（检查点）概念，可将共享的 transform node 标记为检查点以在下次构建时保存中间结果，使上游逻辑对所有共享输出只计算一次，从而节省计算资源、缩短构建时间。页面说明了如何添加检查点节点，以及三种检查点策略：Save in short-term disk（默认）、Save in memory（最快）、Save as output data，并提示检查点会产生存储成本、且需保证输出位于同一 job group 才能提升效率。

### 49. Pipeline Builder • Pipeline management • Job groups

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-job-groups/>
- **文档摘要**：本页介绍 Pipeline Builder 中的 Job groups（作业分组）概念，用于控制构建时输出如何打包执行。默认情况下批处理（batch）pipeline 的每条输出作为独立 job，流处理（streaming）pipeline 的所有输出捆绑在单个 Flink 集群上；作业分组允许在批处理中捆绑输出、或在流处理中拆分输出，并可指定计算配置。页面说明了如何分配作业分组，以及作业分组与 Markings（安全标记）的关系，并提示流处理/增量 pipeline 跨组移动输出属于破坏性变更。

### 50. Pipeline Builder • Pipeline management • Export pipeline code

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/export-pipeline/>
- **文档摘要**：本页说明 Pipeline Builder 的 Export pipeline code（导出 pipeline 为代码）功能，仅支持默认批处理（Spark）pipeline，更快 pipeline 与流处理 pipeline 不支持。导出可将 pipeline 转换为 Java transform 代码推送至目标仓库，或导出为 PySpark。页面说明了通过 Settings > Export code 选择目标 Java transforms 仓库与分支的操作步骤，并提示现有代码将被删除、导出不可逆、部分转换无法转为代码、输出可能不完全一致等注意事项。

### 51. Pipeline Builder • Pipeline management • Find and replace

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/management-find-and-replace/>
- **文档摘要**：本页介绍 Pipeline Builder 的 Find and replace（查找与替换）功能，用于管理与调试 pipeline。可通过搜索面板（放大镜图标或 Cmd/Ctrl+F）按节点名称、列、参数、transform 名称、常量、RID、schema、transform ID 等参数搜索。Replace columns（替换列）功能可单条或批量将列名替换为另一名称，页面说明如何打开替换菜单、输入替换字符串、预览受影响实例并选择 Replace all 或逐条 Replace。

### 52. Pipeline Builder • Branches • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/branches-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的 Branches（分支）机制，版本控制是其关键部分。每条 pipeline 工作流有唯一版本称为 pipeline branch（类似 Git 分支），其中一个为 main 分支，用户可在分支上迭代而不影响主 pipeline，满意后可提议合并回 main。页面说明了全局分支与 Pipeline Builder 分支的区别、分支管理（活跃分支、分支保护、提案模板），以及分支如何支持安全协作、保护生产主分支。

### 53. Pipeline Builder • Branches • Create a branch

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/branches-create-a-branch/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中 Create a branch（创建分支）。操作路径为：点击顶部工具栏的分支下拉菜单（新建 pipeline 后默认在 Main 分支），选择 Create new branch，在窗口中命名并选择自动生成的 Global Branch 名称或重命名，数据集输入将使用此 Global Branch 或不存在时回退 main 分支，点击 Create 即可复制 Main 当前状态。用户可随时从分支下拉菜单切换其他分支。

### 54. Pipeline Builder • Branches • Propose a change

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/branches-propose-a-change/>
- **文档摘要**：本页说明 Pipeline Builder 中 Propose a change（提议变更）的流程。先在分支上保存编辑后点击顶部 Propose，在提案视图命名并说明以发起向 Main 分支审批者的请求。为减少合并冲突，可在最终合并前用 Resolve changes 将目标分支合并进当前分支（但对受保护分支无效）；针对长期分支的反复冲突，可用 Resolve changes 快进（fast-forward）以保持线性提交历史。页面还说明了如何重置（Reset）分支以匹配另一分支。

### 55. Pipeline Builder • Branches • Approve a change

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/branches-approve-a-change/>
- **文档摘要**：本页说明 Pipeline Builder 中 Approve a change（审批变更）的流程，拥有工作流项目 Edit 权限的用户可审批对主分支的提案。审批者在 Builder Proposal 视图中查看合并行为、描述与错误，切换 Changes 标签对比提案与 main 分支差异（按 ADD/MOD/DEL 标记），并查看与对比列统计、将变更标记为已审阅；还可通过评论、解决错误与合并冲突（含 Resolution settings）完成审批。

### 56. Pipeline Builder • Branches • Branch protection

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/branches-protected-branches/>
- **文档摘要**：本页说明 Pipeline Builder 的 Branch protection（分支保护）机制，当多作者协作或 pipeline 支撑关键数据资产时，可保护分支以获得更强治理。受保护分支只能通过 pull request 修改并需满足预定义要求。页面说明了如何在 Settings > Manage branches 启用 Require proposals，以及其与安全审批/标记移除、注册级分支保护、项目审批策略的交互；提示所有受保护分支当前共享相同审批规则，Resolve changes 对受保护分支无效。

### 57. Pipeline Builder • Branches • Fallback branches

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/branches-fallback-branches/>
- **文档摘要**：本页介绍 Pipeline Builder 的 Fallback branches（回退分支）机制：若输入数据集在当前分支尚未构建，系统会按回退分支列表查找已构建版本，默认将主分支设为回退分支。可为不同分支设置不同回退分支、支持多回退；将 Pipeline Builder 分支与 Code Repositories 分支同名可共用输入数据集。页面说明了通过 Settings > Manage branches > Fallback branches 标签配置、排序、新增与删除回退分支的操作。

### 58. Pipeline Builder • Schedules • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/schedules-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的 Schedules（调度）功能，用于按周期运行构建以保持数据最新。调度构建可触发的时机包括：特定时间、数据更新时、逻辑更新时及其组合；构建范围可覆盖单个数据集、其全部依赖、依赖该数据集的全部数据集，或连接两个数据集之间的全部数据集。默认同一调度运行期间不重叠，可启用 Allow overlapping runs；基本调度可在 Pipeline Builder 内配置，高级设置与状态报告在数据集视图中。

### 59. Pipeline Builder • Schedules • Create a schedule

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/schedules-create-schedule/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中 Create a schedule（创建调度）。先选中 pipeline 中的数据集节点并右键 Open 打开预览窗口，点击 Schedules 标签选择 Add build schedule；可在 New schedule 面板选择父资源更新时构建或按特定时间构建（如按小时、按月及具体时刻）。点击 Advanced 可跳转 Data Lineage 应用配置构建范围、失败动作与通知等高级调度特性。

### 60. Pipeline Builder • Schedules • Create a schedule with AIP

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/schedules-scheduler-aip/>
- **文档摘要**：本页说明如何使用 AIP 在 Scheduler 中为数据集构建调度生成配置（针对特定时间触发）。在 New schedule 侧栏输入调度触发提示，AIP 可生成复杂触发器的 cron 格式。操作路径为：右键数据集节点 Manage schedules > Create new schedule 进入 Data Lineage，When to build 选 At a specific time，再点 AIP 的 Suggest 图标并输入偏好描述，接受则 Save、拒绝可重新提示或手动配置 cron。AIP 功能可用性因客户而异。

### 61. Pipeline Builder • Data expectations • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/dataexpectations-overview/>
- **文档摘要**：本页概述 Pipeline Builder 的 Data expectations（数据期望）功能，即对数据集输出施加的要求（expectations），可用于建立检查以提升 pipeline 稳定性，任一期望失败则构建失败。支持类型包括：Primary key（主键无空且组合唯一）、Row count（行数在预期范围）、Value is one of、Value is not null、Floats and doubles are not NaN，以及 Row-level（逐行布尔表达式）。页面以主键期望为例说明了组合唯一性的判定逻辑。

### 62. Pipeline Builder • Data expectations • Configure data health checks

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/dataexpectations-configure-health-check/>
- **文档摘要**：本页说明如何在 Pipeline Builder 中 Configure data health checks（配置数据健康检查）。可通过图中数据集预览面板或 Dataset Preview 应用打开，在 About 标签的 Health Checks 区域查看或配置。健康检查类型包括：Job-level status（作业状态）、Build-level（构建在预期时长内成功）、Freshness（数据新鲜度）。页面说明了如何搜索并添加检查、在配置侧栏设置 Rule（含严重级别与连续失败升级）、Group（监控视图）、Notes 与 issue prompt 等参数。

### 63. Pipeline Builder • Data expectations • Configure unit tests

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/dataexpectations-unit-tests/>
- **文档摘要**：本页介绍 Pipeline Builder 的 Unit testing（单元测试）功能，用于调试、检测破坏性变更以提升 pipeline 可靠性。单元测试由测试输入、transform 节点与期望输出组成，以手动录入表定义。页面说明了什么是单元测试，以及如何创建（在右侧面板选 Unit tests 图标、选节点、填输入/输出数据并设 schema）、运行、删除、编辑既有测试，并覆盖提案中测试与流处理 pipeline 中测试的相关说明。

### 64. Pipeline Builder • Add pipeline to a Marketplace product

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/pipeline-builder/marketplace-pipeline-builder/>
- **文档摘要**：本页说明如何将 Pipeline Builder pipeline 加入 Marketplace 产品以便其他用户安装复用，需借助 Foundry DevOps。除含时间序列目标的流处理 pipeline、含训练模型节点的流处理 pipeline 及部分参数类型外，Pipeline Builder 功能基本均被 Marketplace 支持。页面说明了如何用 Marketplace linter 检查兼容性、如何将 pipeline 加入产品、以及 Pipeline parameters 与 Packaging settings 等配置要点，含训练模型自动打包为 Include with content 资源。
