# 时间序列（Time Series）

> **分类说明**：时间序列（Time Series）数据建模与查询：时间序列属性、索引、聚合与存储管理

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `time-series` |
| 文档数量 | 41 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Time series • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-overview/>
- **文档摘要**：本页介绍 Foundry 中 time series（时间序列）数据的基本概念与处理流程。time series 是按时间顺序（通常固定间隔）采集的测量值序列，可在 Quiver、Vertex、Workshop 中可视化分析。使用需配置 time series object type 与 time series sync 两大组件，并可将 TSP 直接加在对象类型上或设置 linked sensor object type。

### 2. Time series • Time Series Catalog

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-time-series-catalog/>
- **文档摘要**：本页介绍 Time Series Catalog，它是平台中创建、管理与发现 time series 的入口与中枢。该目录支持 time series syncs、time series object types 和 derived series 三类资源，可查看最近访问、收藏夹与最常浏览的资源，并按标签页浏览全部资源。

### 3. Time series • Concepts glossary

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-concepts-glossary/>
- **文档摘要**：本页提供 Foundry time series 创建过程中关键术语的词汇表。涵盖 time series、time series object type、Series ID、TSP、time series sync、sensor object type、derived series、Codex template 等概念的定义，帮助用户理解如何配置与使用 time series。

### 4. Time series • Time series setup • Set up a time series

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-setup/>
- **文档摘要**：本页引导用户在 Foundry 中创建与配置 time series object type 和 TSP 以进行分析。文档说明了设置前的先决条件（需将含 timestamp 的数据集接入 Foundry）、如何启动 setup assistant，并通过决策树式的 Setup checkpoints 确认起点与进度。

### 5. Time series • Time series setup • Create or select a time series object type

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/create-or-select-ts-ot/>
- **文档摘要**：本页说明如何在设置助手中选择或新建 time series object type。重点讲解准备 time series object type backing dataset 的多种方式（多数据集、单数据集/大量 series、单一测量类型多数据集），以及如何在 Pipeline Builder 中生成含 series ID 的数据集。

### 6. Time series • Time series setup • Time series properties

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-properties/>
- **文档摘要**：本页介绍 time series property（TSP）及其配置方法。TSP 是让 Foundry 应用使用 time series 数据的属性；文档说明了在 Ontology Manager 的 Capabilities 标签中设置 TSP、time series formatting、默认 TSP 等步骤与要点。

### 7. Time series • Time series setup • Time series syncs

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-syncs/>
- **文档摘要**：本页介绍 time series syncs 的作用与创建、管理方式。time series sync 以 seriesId 为键存储 time-value 对，由 dataset 或 stream 支撑，是 TSP 的后端数据源，并提供通过 Catalog 或 Pipeline Builder 创建及高级设置等说明。

### 8. Time series • Time series setup • Sensor object type setup

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/create-sensor-ot/>
- **文档摘要**：本页提供 sensor object type 的配置步骤。文档说明当仅部分对象拥有 time series 数据时，应创建 linked sensor object 而非直接加 TSP；并列出 backing dataset 的 schema 字段及 sensor name、foreign key、units 等配置项，以及从 Measures 迁移的方法。

### 9. Time series • Time series setup • Time series permissions

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-permissions/>
- **文档摘要**：本页说明使用 time series 所需的权限与访问控制。查看 TSP 需同时拥有对象及其后端数据源权限；time series sync 继承输入数据集的 marking。文档还介绍受限视图下的细粒度权限限制及相应的替代方案。

### 10. Time series • Time series setup • Time series interpolation

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/interpolation-overview/>
- **文档摘要**：本页解释 Foundry 中 time series 的 interpolation（插值）原理及自定义方式。插值用于估算缺失数据点，内部插值含 LINEAR、NEAREST、PREVIOUS、NEXT、NONE，外部插值含 NEAREST 与 NONE，并说明其在多 series 计算中的作用。

### 11. Time series • Time series setup • Advanced setup

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/advanced-setup/>
- **文档摘要**：本页面向需要底层控制的高级用户提供手动搭建 time series pipeline 的步骤。建议使用 Pipeline Builder，必要时可用 Code Repositories 手动配置，包含创建优化输入数据、设置 time series sync、创建 backing dataset 与 object type 四个阶段。

### 12. Time series • Time series alerting • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/alerting-overview/>
- **文档摘要**：本页概述 time series alerting（时间序列告警）功能。当数据满足用户设定条件时生成事件（alerts），分 batch（Spark 作业）与 streaming（Flink 作业）两类，并介绍监控可观测性、设置前提条件与注意事项。

### 13. Time series • Time series alerting • Set up a time series alerting automation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/alerting-setup/>
- **文档摘要**：本页逐步引导创建 time series alerting automation。以监测 Machine 压力损失超过 120 kPa 为例，说明在 Automate 中创建 condition、选择输出类型、根对象类型、TSP 输入、变换、搜索及保存为自动化等 11 个步骤。

### 14. Time series • Time series alerting • Additional configurations

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/alerting-additional-configurations/>
- **文档摘要**：本页介绍 time series alerting automation 的额外配置项。配置分为 job 级别（影响共享同一 alert object type 的所有自动化，如 Spark profile、lookback window）与 monitor 级别（仅作用于当前自动化），涵盖 batch 与 streaming 两类作业。

### 15. Time series • Time series alerting • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/alerting-common-questions/>
- **文档摘要**：本页为 time series alerting 的常见问题解答（FAQ）。涵盖自动化是否检查全量数据、运行频率、与 Foundry Rules 区别、支持的 Quiver 卡片、streaming 不触发事件的原因（乱序点、重复时间戳、上游问题）及成本优化等内容。

### 16. Time series • Derived series • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/derived-series-overview/>
- **文档摘要**：本页概述 derived series（派生序列）功能。derived series 可保存并复用对 time series 的计算与变换，作为 Palantir 资源保存回 Ontology 作为 TSP 实时计算；分为 templated 与 single 两类，并列出创建所需的前提条件。

### 17. Time series • Derived series • Create derived series

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/derived-series-create/>
- **文档摘要**：本页说明如何从 Time Series Catalog 创建 derived series。文档依次介绍选择类型（templated/single）、添加 time series 数据、应用变换、选择输出、保存、配置 Ontology 保存选项及资源位置等步骤，并说明如何在 Quiver 中创建。

### 18. Time series • Derived series • Manage derived series

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/manage-derived-series/>
- **文档摘要**：本页介绍 derived series 创建后的管理页面。管理页包含 Overview、Logic、Ontology 三个标签页，用于预览与编辑逻辑、管理自动保存到 Ontology 的选项（如 sensor name、对象作用域、属性映射）及查看 Ontology 状态。

### 19. Time series • Derived series • Manually save derived series to the Ontology

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/manual-ontology-saving/>
- **文档摘要**：本页说明如何将 derived series 手动保存到 Ontology。若创建时未启用自动保存，需通过 codex template RID 将派生逻辑绑定到根对象类型或 sensor object type，并配置派生 series 属性数据源（使用 template RID 作为属性值）。

### 20. Time series • Derived series • Derived series permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/derived-series-permissions/>
- **文档摘要**：本页说明 derived series 的权限分组与要求。权限分为创建更新资源、查看含派生 series 的 TSP、管理 Ontology 保存（自动/手动）三类，并分别列出所需的对象类型编辑、Action 类型及可见性等条件。

### 21. Time series • Derived series • Embed in a Workshop module

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/derived-series-widget/>
- **文档摘要**：本页介绍如何在 Workshop 模块中通过 Derived Series widget 嵌入派生序列的创建、管理与发现。文档说明 widget 的配置（Ontology 选项、资源选项、显示格式）及限制（仅支持绑定 sensor object type 的派生 series）。

### 22. Time series • Derived series • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/derived-series-common-questions/>
- **文档摘要**：本页是 derived series（派生时间序列）的常见问题解答（FAQ）。内容涵盖支持的 Quiver cards、多根对象报错原因、模板复用、codex template RID 与 derived series RID 的区别，以及派生序列如何随输入变化更新等问题。

### 23. Time series • Using time series • Use time series in Foundry

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-usage/>
- **文档摘要**：本页说明如何在 Foundry 中使用 time series object types 进行可视化与分析。涵盖 Quiver、Workshop、Automate、Derived series、Vertex、Foundry Rules 与 Map 等应用中使用时间序列数据的方式。

### 24. Time series • Using time series • Use time series in Functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-in-functions/>
- **文档摘要**：本页介绍如何在 Functions 中设置并使用 time series properties。说明了初始配置、通过 Python functions 与 TypeScript functions 访问时间序列，以及如何加载时间序列数据点（TSPs）。

### 25. Time series • Using time series • Work with time series in FoundryTS

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/foundryts/>
- **文档摘要**：本页说明 FoundryTS（用于查询 time series data 的 Python 库）的使用方法。介绍了在 Code Repositories 与 Code Workbook 中的环境配置、资源导入，并给出股票数据的示例。

### 26. Time series • Time series property use case • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-properties-use-case/>
- **文档摘要**：本页是 time series properties 用例的概述。文档将引导通过 Pipeline Builder 编写 pipeline、在 Ontology Manager 配置对象，并基于航空本体创建 Quiver 仪表盘与 Workshop 模块。

### 27. Time series • Time series property use case • Create time series properties with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-properties-use-case-pipeline/>
- **文档摘要**：本页指导如何使用 Pipeline Builder 生成 time series data 并创建 time series properties。分为生成时间序列数据（聚合、关联、格式化）与创建 time series sync 两部分。

### 28. Time series • Time series property use case • Add time series properties to objects with Ontology Manager

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-properties-use-case-ontology/>
- **文档摘要**：本页说明如何使用 Ontology Manager 为对象类型添加 time series properties。以航空本体为例，为 Carrier、Route、Airport 对象类型添加每日航班数、平均到港/离港延误等时间序列属性。

### 29. Time series • Time series property use case • Use time series properties on objects in a Workshop module and Quiver analysis

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/time-series-properties-use-case-operational/>
- **文档摘要**：本页指导如何在 Workshop 模块与 Quiver 分析中使用对象的 time series properties。说明了添加 Filter List、Object Table、Chart XY 等 widget，并将 Quiver 仪表盘嵌入 Workshop。

### 30. Time series • Sensor object types use case • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/sensor-object-end-to-end/>
- **文档摘要**：本页是 sensor object types 用例的概述。介绍了这一更进阶的 time series data 配置方式，并说明将通过 Pipeline Builder、Ontology Manager、Workshop 与 Quiver 构建航空本体下的传感器时序应用。

### 31. Time series • Sensor object types use case • Create sensor object type data in Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/sensor-object-end-to-end-pipeline/>
- **文档摘要**：本页指导如何在 Pipeline Builder 中创建 sensor object type 数据。包括丰富传感器读数、构建 backing dataset，以及将数据转换为 time series sync 格式并配置同步。

### 32. Time series • Sensor object types use case • Create sensor object types with Ontology Manager

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/sensor-object-end-to-end-ontology/>
- **文档摘要**：本页说明如何使用 Ontology Manager 创建 sensor object type 并将其关联到 root object type。以 Flight Sensor 对象类型链接到 Flight 根对象为例，配置时间序列属性与对象关联。

### 33. Time series • Sensor object types use case • Use sensor object type time series data in Workshop and Quiver

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/sensor-object-end-to-end-operational/>
- **文档摘要**：本页指导如何在 Workshop 与 Quiver 中使用 sensor object type 的 time series data。说明了配置初始 widget、按航线筛选航班、添加传感器数据，并创建与嵌入 Quiver 仪表盘。

### 34. Time series • Geospatial time series use case • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/geospatial-time-series-use-case/>
- **文档摘要**：本页是 geospatial time series（地理空间时间序列）用例的概述。说明如何通过 Pipeline Builder 准备数据、在 Ontology Manager 配置对象，并在地图上可视化实体（如 Ship）的轨迹。

### 35. Time series • Geospatial time series use case • Create geospatial time series properties with Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/geospatial-time-series-pipeline/>
- **文档摘要**：本页指导如何使用 Pipeline Builder 创建 geospatial time series properties。通过 unpivot 经纬度列、拼接 series_id、构建 backing dataset，并最终创建 time series sync。

### 36. Time series • Geospatial time series use case • Create geospatial time series objects with Ontology Manager

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/geospatial-time-series-ontology/>
- **文档摘要**：本页说明如何使用 Ontology Manager 创建带 geospatial time series 的 Ship 对象类型。为 Ship 对象添加 Latitude Series 与 Longitude Series 属性，并配置地理空间轨迹能力。

### 37. Time series • Geospatial time series use case • Visualize geospatial time series objects on a map

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/geospatial-time-series-use-case-map/>
- **文档摘要**：本页指导如何在 Map 中可视化 geospatial time series 对象。说明了将 Ship 对象添加至地图、将其时间序列属性加入时间轴，以实时观察位置变化与轨迹几何。

### 38. Time series • Function-backed time series • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/function-backed-time-series-overview/>
- **文档摘要**：本页介绍 function-backed time series（由函数生成的时间序列）的概念与能力。说明其如何通过 Python 函数动态生成数值时间序列，并给出使用 Prophet 进行预测的示例。

### 39. Time series • Function-backed time series • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/function-backed-time-series-getting-started/>
- **文档摘要**：本页是 function-backed time series 的入门指南。说明 prerequisites，并指导在 Code Repositories 安装 time series SDK、编写返回 pandas DataFrame 的 Python 函数，以便在 Quiver 中使用。

### 40. Time series • Compute usage

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/compute-usage/>
- **文档摘要**：本页介绍 time series query 的 compute usage（计算用量）计量方式。说明 compute-seconds 如何按查询与扫描点数计算，并给出用量调查与管理的建议及复杂度示例。

### 41. Time series • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/time-series/faqs/>
- **文档摘要**：本页是 time series 的常见问题解答（FAQ）。涵盖 TSP 单位与插值配置、Measures、Code Repositories 设置、索引、projection，以及加载失败、缓慢或缺失数据的原因。
