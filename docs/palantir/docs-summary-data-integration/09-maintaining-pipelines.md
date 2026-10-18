# 管道维护（Maintaining Pipelines）

> **分类说明**：数据管道日常维护：修复与重建、版本管理、管道迁移与故障处理操作

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `maintaining-pipelines` |
| 文档数量 | 5 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Maintaining pipelines • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/maintaining-pipelines/overview/>
- **文档摘要**：本页介绍数据管道进入维护模式后的维护职责与前提条件。页面说明了维护者在接手管道前应明确的数据范围、交付时间与频率等期望，并列出了管道维护者需承担的技术监控、故障调试、代码修改及联系上游团队等责任与所需技能权限。

### 2. Maintaining pipelines • Stability recommendations

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/maintaining-pipelines/stability-recommendations/>
- **文档摘要**：本页提供构建弹性稳定管道的关键建议与最佳实践。内容包括对所有生成数据集的逻辑进行版本化管理（建议用 Transforms 而非 Contour/Code Workbook）、隔离易出问题的源数据、处理共享资源以及避免部分运行等，以降低回归风险与数据质量故障的传播。

### 3. Maintaining pipelines • Recommended health checks

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/maintaining-pipelines/recommended-health-checks/>
- **文档摘要**：本页提供为管道设置健康检查以监控其运行状况的最佳实践。页面解释了以 resolved schedule 为监控单元的核心概念，并说明应在 schedule 的输入、中间产物与输出上分别安装何种检查，帮助避免检查过多、不当或不足导致的告警噪声或信号缺失。

### 4. Maintaining pipelines • Define data expectations

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/maintaining-pipelines/define-data-expectations/>
- **文档摘要**：本页介绍 Data Expectations（数据期望）功能，即在数据集、virtual table 与 Iceberg table 上以代码定义的数据质量需求。其可在构建失败时自动中止以阻断下游污染，并介绍 expectation、check、pre/post-condition 等术语及定义、运行、监控机制。

### 5. Maintaining pipelines • Recommended support processes

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/maintaining-pipelines/support-processes/>
- **文档摘要**：本页提供管道监控支持的推荐流程与最佳实践。内容建议通过 on-call 轮班方式管理监控，并定义了支持时段、告警机制、轮班排班、管道文档（含血缘图、上游团队与升级路径）等要素，以保障问题能被及时发现与响应。
