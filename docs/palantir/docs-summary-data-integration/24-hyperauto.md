# HyperAuto

> **分类说明**：HyperAuto：基于源数据自动生成数据管道与 Ontology 的加速能力

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `hyperauto` |
| 文档数量 | 15 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. HyperAuto (SDDI) • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/overview/>
- **文档摘要**：本页介绍 Palantir HyperAuto（又称 Software-Defined Data Integration / SDDI）的总体能力。它是一套开箱即用的端到端数据集成方案，可基于 ERP、CRM 等系统源数据自动同步、整合与结构化数据，并自动生成可用的数据 pipeline 与 Ontology。当前 HyperAuto V2 仅支持 SAP 数据源，并新增了实时流式支持；V1 仍对 Salesforce、Oracle NetSuite 等提供支持并持续维护至 V2 全面可用。

### 2. HyperAuto (SDDI) • Architecture

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/architecture/>
- **文档摘要**：本页说明 HyperAuto V2 的架构设计。它围绕数据集成工作流的三大组件——Data Syncs、Builder Pipelines（数据转换）与 Ontology——进行编排与自动化，利用源系统元数据实时推导同步、转换逻辑与 Ontology 设计方案，并自动生成可用产出。一个 HyperAuto pipeline 由单一实例管理全部资源，从源表同步到输出数据集（及可选 Ontology Objects），并包含资源管理说明。

### 3. HyperAuto (SDDI) • Supported sources

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/supported-sources/>
- **文档摘要**：本页列出 HyperAuto V2 支持的数据源类型，目前仅支持来自 Foundry SAP source 的数据，兼容 SAP ECC 与 S/4HANA（是否配合 SLT 复制服务器，流式需 SLT）。页面给出源连接的最低版本要求（SAP Managed Source 1.25.0、SAP Connector SP26），并说明在无直接连接时可使用基于文件夹的静态 SAP 数据（需上传 DD02L、DD02T、DD03L 等数据字典表）构建 pipeline。

### 4. HyperAuto (SDDI) • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/getting-started/>
- **文档摘要**：本页是 HyperAuto V2 的快速上手指南。它一步步演示如何创建首个 HyperAuto pipeline：在 Data Connection 中找到受支持源，选择 Create HyperAuto pipeline 进入向导，定义名称与位置，选择子系统与批处理/流式摄取方式，在 Input Configuration 中挑选源表，并设定语言与转换等配置后创建。创建后可在概览页监控生成进度并管理相关资源。

### 5. HyperAuto (SDDI) • Making edits via proposals

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/proposals/>
- **文档摘要**：本页介绍如何通过 proposal 对 HyperAuto pipeline 进行编辑。每个 pipeline 同一时间仅存在一个 proposal，代表对资源与转换逻辑的分阶段重新生成；用户需选择批准应用或丢弃。页面说明了在 Configuration 页进入编辑、创建 proposal，以及在 Pipeline Builder 中查看 Changes、Merge proposal 并部署 Main 分支以使变更落到输出数据集与 Ontology 的操作流程。

### 6. HyperAuto (SDDI) • Configuration options

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/configuration-options/>
- **文档摘要**：本页详述 HyperAuto V2 的配置选项。配置向导包含名称与位置、源配置、输入配置与 pipeline 配置四个步骤；源配置涉及选择子系统与摄取方式（批处理/流式，含 Direct、SLT、Remote 三种 SAP 连接架构），输入配置用于选定源表，pipeline 配置则涵盖语言选择、转换选项与批处理计算设置等参数。

### 7. HyperAuto (SDDI) • Folder-based SAP pipelines

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/folder-based-sap/>
- **文档摘要**：本页说明如何在不建立直接 SAP 连接的情况下，基于文件夹中的静态 SAP 数据创建 HyperAuto V2 pipeline。用户需准备一个 Foundry 文件夹，内含按 SAP 表名命名的输入表数据集以及 DD02L、DD02T、DD03L、DD04T、DD05S、DD08L 等原始未过滤的数据字典表。页面给出数据准备要求、示例及通过 Setup wizard 创建文件夹式 pipeline 的步骤。

### 8. HyperAuto (SDDI) • AIP features

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/aip/>
- **文档摘要**：本页介绍 HyperAuto 中由 AIP 驱动的功能。目前提供 Input suggestions（输入建议）能力：AIP Assist 可在对应源（及子系统）中推荐与工作流相关的输入表，并解释每个输入的相关性及用法。用户可在 Input Configuration 页点击 Suggest，输入对目标工作流的描述作为提示词来调用该功能。

### 9. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-overview/>
- **文档摘要**：本页是已进入 Sunset 阶段的 HyperAuto V1（亦称 SDDI / Bellhop）的概述。V1 是 Palantir 源到价值自动化套件的首个版本，仍支持 Salesforce、Oracle NetSuite、SAP（V1 不推荐）等源，但建议迁移至 V2。页面介绍其由 Connectors、Source exploration 与自动 pipeline 生成（含 Ontology 创建）三大组件构成的架构与生成机制。

### 10. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-getting-started/>
- **文档摘要**：本页是 HyperAuto V1 的上手指南（已进入 Sunset 阶段）。它引导用户通过 SDDI Cockpit 创建 SDDI 实例：选择源类型、指定仓库与输出文件夹位置、配置 Data Connection 源、设定原始数据与元数据文件夹，并在 Sync Data 步骤同步数据库元数据表以在后台生成 pipeline。创建后实例即可用于清洗源表数据。

### 11. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • Source exploration

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-source-exploration/>
- **文档摘要**：本页介绍 HyperAuto V1 中的 Source exploration（源探索）工具，允许在将数据集同步到 Foundry 之前于界面内浏览外部数据系统。该工具提供 Lineage Graph、Graph Tools、Dataset Shopping Cart（购物车式点选同步配置）、Dataset Preview、搜索面板等界面，支持发现、搜索、查看关系并直观创建数据集同步。Salesforce 与 NetSuite 源将逐步迁移至默认源探索。

### 12. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • Cockpit

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-cockpit/>
- **文档摘要**：本页说明 HyperAuto V1 的 SDDI Cockpit 控制台，它是配置与执行 SDDI 流的中心。左侧面板列出 Sync Data、Pipeline Generation、Ontology Changes 三阶段及状态，右侧主区提供各阶段可执行操作。页面覆盖初始状态、访问已有 Cockpit、各控件用法（含数据同步、pipeline 生成、Ontology 变更），以及访问故障排查等内容。

### 13. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • Configuration reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-configuration-reference/>
- **文档摘要**：本页是 HyperAuto V1 的配置参考手册。SDDI pipeline 由全自动代码仓库生成，通常通过 Cockpit 配置，但高级参数或非标准源需手动修改 transforms-bellhop/src/config/ 下的两个文件：SourceConfig.yaml 与 PipelineConfig.yaml。页面给出两文件的示例结构与各参数说明，并提醒高级手动设置可能使 pipeline 进入异常状态，需先在分支验证。

### 14. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • Migrating from HyperAuto V1 to V2

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-to-v2-differences/>
- **文档摘要**：本页讲解从 HyperAuto V1 迁移到 V2 及两者差异。V2 带来点选式向导、可生成 Pipeline Builder pipeline（提供处理透明性与变更管理）、实时流式等增强能力。页面对比显著特性差异，包括源类型支持、多源支持、外键生成、列重命名、自定义清洗函数与输入批处理 union，并给出将现有 V1 pipeline 迁移至 V2 的方法。

### 15. HyperAuto (SDDI) • HyperAuto V1 [Sunset] • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/hyperauto/v1-faq/>
- **文档摘要**：本页为 HyperAuto V1 的常见问题解答（Sunset 阶段）。涵盖通用使用技巧，如如何在 SDDI 仓库中调试与预览代码、配置新表自动加入的调度、MODULE_UNREACHABLE（多为 Spark 驱动内存不足，需在 SourceConfig.yaml 应用 Spark profile）、AssertionError 等报错的处理，以及语义版本与中间阶段开关等问题。
