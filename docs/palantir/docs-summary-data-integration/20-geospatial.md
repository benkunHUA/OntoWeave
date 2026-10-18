# 地理空间（Geospatial）

> **分类说明**：地理空间（Geospatial）数据支持：地理类型、空间函数与坐标系处理

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `geospatial` |
| 文档数量 | 12 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Geospatial and geotemporal • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/overview/>
- **文档摘要**：本页概述 Foundry 中的 geospatial（地理空间）与 geotemporal（地理时间）数据。说明 Foundry 简化了这两类数据的转换与分析流程以支持基于地图的工作流，并指引读者了解数据类型、geotemporal series、常见工作流，以及如何将 geotemporal series 与 Ontology 集成。

### 2. Geospatial and geotemporal • Types of geospatial and geotemporal data

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/types-of-geospatial-and-geotemporal-data/>
- **文档摘要**：本页介绍 Foundry 中常见的 geospatial 与 geotemporal 数据类型。说明栅格（raster）数据（如卫星影像、DEM）与矢量（vector）数据（点/线/面）两种 geospatial 类型，以及带时间成分的 geotemporal 数据，并解释 geotemporal series（由 series ID 标识的位置-时间戳序列）等核心概念，同时提醒注意 CRS 与投影。

### 3. Geospatial and geotemporal • Coordinate reference systems and projections

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/coordinate-reference-systems-and-projections/>
- **文档摘要**：本页介绍坐标参考系统（CRS）与地图投影对 geospatial 数据的影响。说明所有 2D 投影都会以形状、面积、距离、方向（SADD）方式扭曲地球数据，列举 WGS 84 (EPSG:4326) 等常见投影及 Web Mercator，并提醒不同 CRS 数据在地图上无法对齐，应先确认所用 CRS。

### 4. Geospatial and geotemporal • Example workflows

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/example-workflows/>
- **文档摘要**：本页列举 Foundry 中 geospatial 工作流的若干常见模式，帮助用户判断适用场景。涵盖通用作战图（Common Operating Picture）、geospatial 工作规划（Work Planning）与供应链优化（Supply Chain Optimization）三类用例，分别说明其目的、geospatial 数据输入与输出形态。

### 5. Geospatial and geotemporal • Use raster data

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/raster-data/>
- **文档摘要**：本页说明如何在 Foundry 中处理栅格（raster）数据。介绍支持的媒体集（media set）格式（如 TIFF/GeoTIFF、NITF、JPEG2000）与仅文件级处理的格式（PNG、JPEG），并演示如何将栅格导入 media set、在 Pipeline Builder 或 transform 中使用，以及推荐的 Python 库与代码示例（注意大于 67MB 的栅格无法在地图显示）。

### 6. Geospatial and geotemporal • Use vector data in transforms [Legacy]

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/vector-data-in-transforms/>
- **文档摘要**：本页（旧版/Legacy）介绍 geospatial-tools Python 库，用于在 Foundry 的 Python transform 中对矢量（vector）geospatial 数据执行常见转换。说明该库已被 Pipeline Builder 的 geospatial 能力取代、不再维护，并保留其安装方式与数据清洗、空间函数、spatial joins 等辅助函数的参考用法。

### 7. Geospatial and geotemporal • Use geospatial data in the Ontology

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/ontology/>
- **文档摘要**：本页说明如何在 Foundry Ontology 中配置与使用 geospatial 数据。介绍用 geopoint 属性表示点（支持 latitude,longitude 或 Geohash）、用 geoshape 属性表示面/线（GeoJSON Geometry 字符串），以及如何在地图中可视化、探索、发布数据并使用外部图层与 Mapbox Boundaries。

### 8. Geospatial and geotemporal • Add Ontology data to Gaia

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/add-ontology-data-to-gaia/>
- **文档摘要**：本页说明如何将 Foundry Ontology 中的 geospatial 数据添加到 Gotham 的 Gaia 应用。介绍在类型映射（type mapping）后可用的六种方法（拖放对象、Gaia 内搜索、搜索模板、实时 geotemporal series 同步、Function-backed/versioned object set-backed Enterprise Map Layer），并对比其对象数量与刷新延迟。

### 9. Geospatial and geotemporal • Geotemporal series • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/geotemporal-series-overview/>
- **文档摘要**：本页（测试版）概述 geotemporal series（地理时间序列），用于追踪实体随时间的地理位置变化。说明其类似 time series 但含 geospatial 成分，并列出使用所需的两个组件：基于数据集或流、用 Pipeline Builder 配置的 geotemporal series sync，以及关联元数据的 geotemporal series object type。

### 10. Geospatial and geotemporal • Geotemporal series • Data modeling

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/data-modeling/>
- **文档摘要**：本页说明 geotemporal series 的数据建模方法。介绍 observation schema 的必需字段（Series ID、Timestamp、Position/GeoPoint，可选 Altitude），如何选择能跨观测持续标识实体的 series ID（如按航班号+起降地+日期拼接），以及 live 与 static 属性的区分。

### 11. Geospatial and geotemporal • Geotemporal series • Integrate geotemporal series with the Ontology

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/integrate-geotemporal-series-with-the-ontology/>
- **文档摘要**：本页指导如何用 Pipeline Builder 将 geotemporal series 集成到 Ontology，以驱动 Gaia 地图等应用的分析与可视化。分步说明创建流式 pipeline、导入并转换数据集、建立 geotemporal series sync 与 object type、通过 GTSR 属性连接二者、部署 pipeline 并可视化数据的过程。

### 12. Geospatial and geotemporal • Geotemporal series • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/geospatial/faq/>
- **文档摘要**：本页汇集 geotemporal series 的常见问题。解答其是否正式可用（目前为测试版）、应何时选用 geotemporal series 而非 time series 在 Gaia/Foundry 地图显示 geospatial 数据，以及能否在同一 object type 上同时实现两者（结果均为只读）。
