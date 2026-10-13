# Foundry Rules 规则引擎

> **分类说明**：规则逻辑编写、工作流配置、部署与 Workshop 应用集成

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `foundry-rules` |
| 文档数量 | 27 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Foundry Rules • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/overview/>
- **文档摘要**：Foundry Rules（前称 Taurus）是 Foundry 中以点击式低代码界面主动管理复杂业务逻辑的工具，用户可创建规则并将其应用于 datasets、objects 和 time series，用于告警生成或数据分类等。本页概述 Foundry Rules 的组成组件（rule 是一组针对数据集列的条件）及其典型用例，包括反洗钱（标记可疑交易）、设备监控（基于传感器数据告警）、以及将实体按规则分组成 cohort 用于精准营销。

### 2. Foundry Rules • Core concepts • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/core-concepts/>
- **文档摘要**：本页介绍 Foundry Rules 的基础核心概念，包括：Object model（规则是可由 rule proposals 管理的对象）、Workshop application（用于管理规则的应用，通常包含 Rule Editor 与 Proposal Reviewer）、Rule logic（编码业务逻辑的特定结构，对数据执行一组转换）、Foundry Rules workflow configuration（用工作流配置编辑器定制规则）。并提示 2022 年 7 月前部署的版本包含更多概念与配置要求。

### 3. Foundry Rules • Core concepts • Object model

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/object-model/>
- **文档摘要**：本页讲解 Foundry Rules 的对象模型，涉及两个核心概念：Rules 与 Proposals。Rules 是标准对象，包含元数据属性、应用于筛选数据集的自定义属性（如告警模式的 alert_severity）以及以压缩 JSON blob 存储的 logic 匹配条件。Proposals 作为提交、审查和监控规则变更的机制，类比于软件开发的 pull requests，包含待编辑规则 ID、提案元数据（作者、状态、审查人）和变更差异（old/new 字段），以满足审计与审查需求。

### 4. Foundry Rules • Core concepts • Workshop application

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/workshop-application/>
- **文档摘要**：本页说明标准的 Foundry Rules 应用是一个 Workshop application，通常包含两个页面：Rule Editor（用于创建、编辑、删除规则，含过滤列表、对象列表和基于 Foundry Action 的编辑器部件）与 Proposal Reviewer（用于审查、批准/拒绝规则提案，以差异视图展示属性变更）。此外还提供只读的 Rule Viewer widget 用于查看规则逻辑。文中逐一解释各页面部件的功能与交互方式。

### 5. Foundry Rules • Core concepts • Rule logic

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/rule-logic/>
- **文档摘要**：本页介绍 Foundry Rules 的 Rule logic 构成，每条规则逻辑由三部分组成：Inputs（规则的数据输入，可为 dataset 或 object，受限视图支持的对象需配置替代 backing dataset）、Logic blocks（对输入施加的过滤、表达式、聚合、连接等转换，每块输出新的行列集并可预览）、Rule output（对应工作流配置中设定的输出 dataset，决定结果的去向与格式）。文中也说明了工作流所有者对可用输入与变换的限制。

### 6. Foundry Rules • Core concepts • Foundry Rules workflow configuration

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/foundry-rules-workflow-configuration/>
- **文档摘要**：本页说明 Foundry Rules workflow configuration 编辑器，用于调整整个工作流的配置，从 Ontology Manager 访问。涵盖 Workflow inputs（为规则作者添加可用输入及关联 link types）、Workflow outputs（指定所有规则输出的目标 dataset 与列名、类型、允许值与默认值）、Transform configuration（规则状态 dataset 位置、Spark 配置等高级项）以及 Rule execution（自动生成的 transforms pipeline，在 Data Lineage 图中应用规则以创建告警或分类数据）。

### 7. Foundry Rules • Deploy • Overview

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/deploy-foundry-rules/>
- **文档摘要**：本页是 Foundry Rules 部署总览，部署需依次部署各组件：Workshop application、支撑对象与 Actions、以及 Foundry Rules workflow configuration，整体约 30 分钟。流程为先部署模板（约 3 分钟），再配置工作流（约 10 分钟），最后编写并运行一条规则（约 10 分钟）。文中给出各步骤文档链接，并指向故障排查与定制化页面，提示 2022 年 7 月前版本配置更复杂。

### 8. Foundry Rules • Deploy • Deploy workflow

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/deploy-workflow/>
- **文档摘要**：本页讲解如何部署新的 Foundry Rules workflow：从侧边栏 Rules 应用选择 Rule-based data pipeline，由应用自动生成所需的对象与 Actions。文中给出部署步骤，并通过 FAQ 说明如何修改 action 类型的提交条件——进入 Workshop 应用编辑模式，在 Rule Editor 配置面板中通过 Create add proposal action 的 Action Configuration 调整提交标准。

### 9. Foundry Rules • Deploy • Configure workflow

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/configure-workflow/>
- **文档摘要**：本页说明部署工作流模板后如何配置 workflow。步骤包括：从 Foundry Rules 主页或项目文件访问已有规则；为受限视图支撑的对象配置 alternate backing dataset 以作为规则输入；在编辑器第三部分添加 workflow outputs（指定规则结果写入的 dataset 名称与位置）。高级配置部分介绍 alternate backing datasets 的用途，如对受限视图对象编写规则或在对象数据的子集上运行规则。

### 10. Foundry Rules • Deploy • Author and run a rule

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/author-and-run-a-rule/>
- **文档摘要**：本页逐步指导如何在 Workshop application 中编写并运行一条规则。流程为：在工作流配置界面找到 Workshop 应用；点击 Create New 填写名称与描述、编写逻辑（如简单过滤）并提交生成提案；在 Proposals 标签批准该提案以激活规则；最后构建 rule writeback 与 rules output 两个 dataset，待构建完成后输出 dataset 即包含规则结果，并可将二者设为定时更新。

### 11. Foundry Rules • Settings & customization • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/customization/>
- **文档摘要**：本页概述如何定制 Foundry Rules 以适应特定用例。默认的部署配置通用适用，但用户可进一步裁剪，常见方式包括：启用可选功能（optional features）、添加自定义属性（custom property）、设置规则编辑权限（permissions）、配置允许与默认的输出值，以及定制 Foundry Rules pipeline。页面作为定制化主题的入口，指向各具体定制操作的子文档。

### 12. Foundry Rules • Settings & customization • Enable optional features

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/enable-optional-features/>
- **文档摘要**：本页讲解如何启用或禁用 Foundry Rules 的可选功能，通过编辑 Rules Workshop application 中 Rule Editor widget 的配置实现。可启用的逻辑板（logic boards）包括 Window（窗口函数）、Aggregation（分组聚合）、Join（连接数据集或对象）、Expression（任意表达式）、Select columns（选择列）、Union（合并）等；还可启用 Contour import（导入并转换 Contour 分析逻辑为规则）以及 Time series（直接在规则中操作时序数据）支持。

### 13. Foundry Rules • Settings & customization • Add a custom property

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/add-a-custom-property/>
- **文档摘要**：本页说明如何为 Foundry Rules 的 rule 与 proposal 对象添加自定义属性以追踪额外元数据。步骤包括在 Ontology Manager 为 rule 对象添加属性（须由输入 dataset 列支撑），为 proposal 对象添加对应的 current_<PROPERTY> 与 new_<PROPERTY> 属性并标注 property-diff 类型类，修改相关 Action 类型加入新参数，并在 Workshop 中配置变量与默认值，以确保 Proposal Reviewer 正确显示差异。

### 14. Foundry Rules • Settings & customization • Permissions for editing rules

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/rule-permissions/>
- **文档摘要**：本页说明如何设置 Foundry Rules 中编辑规则的权限，以限制可修改规则的用户集合。示例方案让规则作者拥有对其规则变更的最终批准权：仅规则作者（及超级用户）可批准编辑或删除其规则的提案，仅作者与提案创建者（及超级用户）可拒绝相关提案，从而允许提案创建者撤回误建的提案。文中给出在 approve/reject proposal 等 Action 上配置基于规则对象的参数与权限验证的具体步骤。

### 15. Foundry Rules • Settings & customization • Permitted and default output values

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/permitted-and-default-output-values/>
- **文档摘要**：本文介绍 Foundry Rules 工作流输出（Workflow outputs）中的“允许与默认输出值”配置。规则输出逻辑块（rule output logic block）中，作者需为每个工作流输出分配列（column）或静态值（static value）。该设置可简化赋值流程、为新建规则自动分配默认值，并限制只能使用列值或静态值中的一种。文章通过设备故障告警示例说明其应用场景与配置方式。

### 16. Foundry Rules • Settings & customization • Customize Foundry Rules pipeline

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/customize-foundry-rules-pipeline/>
- **文档摘要**：本文讲解如何自定义 Foundry Rules pipeline，这是一个面向资深 Foundry pipeline 作者的高级功能。自定义可带来更精细的运行控制、输入预处理、增量 transform（incremental transform）内运行规则等好处，但会增加运维负担。文章给出适用场景，并逐步说明通过启用 self-managed transforms、选择自定义 transform 仓库、生成并保存 pipeline 代码来完成部署。

### 17. Foundry Rules • Time series • Time series rules [Sunset]

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/timeseries-concepts/>
- **文档摘要**：本文介绍 Foundry Rules 中的 Time series（时间序列）规则功能，目前处于 Sunset（弃用过渡）阶段，建议新需求迁移至 time series alerting automations。Foundry Rules 可编写规则识别数据中的感兴趣时间段，并作为行输出供下游消费。规则除标准逻辑外还支持 Add Timeseries 与 Timeseries Search 两类时间序列面板，对时序进行变换与区间搜索。

### 18. Foundry Rules • Time series • Deploy time series Foundry Rules

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/deploy-timeseries-foundry-rules/>
- **文档摘要**：本文提供启用 Foundry Rules 时间序列功能的部署步骤。前提是平台已配置 time series，且已按标准流程部署 Foundry Rules。启用需满足：至少一个工作流输入为 time series root object type，并为目标输入对象类型开启 Enable time series 开关。若使用 time series properties 则无需额外配置；若使用 measures，还需选择 Series 到 Root 的关联并添加对应的 time series syncs。

### 19. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/legacy-foundry-rules-setup-taurus/>
- **文档摘要**：本文概述 2022 年 7 月前的旧版 Foundry Rules（原称 Taurus）与新版的差异。旧版需用户自行创建并维护运行规则的 Code Repository，规则输入需在 Workshop application 与 transforms pipeline 两处配置，输出则在 Workshop、pipeline 与 Ontology Manager 三处配置；输出 schema 通过 Foundry Actions（即 rule Actions）强制实现。文章为后续遗留配置章节的入口。

### 20. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Migrate to Foundry Rules

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/migrate-to-foundry-rules/>
- **文档摘要**：本文指导将旧版 Taurus 工作流迁移至 Foundry Rules。迁移可获得简化部署、单步修改维护、开箱即用的流式执行及内置代码生成器等收益。文章列出迁移前需考虑的因素（如复杂 Taurus 用例需重构），并说明使用迁移向导将 Taurus 工作流转为 Foundry Rules 工作流、验证迁移结果，以及常见 FAQ。

### 21. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Configure Workshop application

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/configure-workshop-app/>
- **文档摘要**：本文面向 2022 年 7 月前部署的用户，说明如何在 Workshop application 中配置旧版 Foundry Rules。步骤包括：在生成的 Workshop 模块中配置规则输入（添加对象类型或数据集）、添加 rule Actions、启用可选功能（optional features），以及为 proposal 部件配置属性类型类以正确显示 diff。新增对象或数据集还需同步到 transforms pipeline。

### 22. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Configure transforms pipeline

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/configure-transforms-pipeline/>
- **文档摘要**：本文说明旧版 Foundry Rules（Taurus）中 transforms pipeline 的配置方式，仅适用于 2022 年 7 月前部署的用户。规则经 Workshop 编写审核后，编码逻辑在 transform 中执行。文章解析 transform 各组成部分，并涵盖使用 @AdditionalInputs 添加 Ontology 输入、输入/输出数据集、Foundry Rules rule runner、Rule Action datasets 及参考实现示例。

### 23. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Configure rule Actions

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/configure-rule-actions/>
- **文档摘要**：本文讲解旧版 Foundry Rules 中 rule Actions 的配置，rule Actions 用 Foundry Actions 强制规则输出 schema。Action 参数会呈现给用户在 Workshop 中映射到规则逻辑输出的列，并以参数类型约束列类型；required 参数在应用中必填，参数 ID 成为输出数据集的列名。文章还说明 Submission Criteria 的定义及 taurus_rule_id 列的附加。

### 24. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Configure time series for Foundry Rules

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/configure-timeseries-foundry-rules/>
- **文档摘要**：本文面向 2022 年 7 月前部署的用户，说明如何为旧版 Foundry Rules 配置 time series 支持。步骤包括：在 Workshop 的 Rule Editor 中开启 Enable time series rules；在 transforms pipeline 中配置权限，通过 @AdditionalInputs 添加 time series sync RID、tick 数据集 RID、root/sensor 对象及关系 RID，并给出示例与 Project references。

### 25. Foundry Rules • Legacy Foundry Rules setup (Taurus) • Upgrade to use rule Actions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/upgrade-to-use-rule-actions/>
- **文档摘要**：本文为旧版 Taurus 用户提供升级以使用 objects 与 rule Actions 的步骤，新版默认已包含。建议升级以充分发挥对象能力。步骤包括：将 tau-execution-core 依赖升级至至少 0.60.4、在 Rule Editor 中将 Logic Version 改为 V1（不可逆）、更新 transforms pipeline 代码。迁移可渐进进行，不会造成停机。

### 26. Foundry Rules • Troubleshooting reference

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/common-issues/>
- **文档摘要**：本文是 Foundry Rules 的故障排查参考，列举常见问题及调试步骤。涵盖 ReadonlyObjectError、400: Actions:InvalidParametersForApply 等报错原因与修复，以及 proposal diff 显示异常的处理；并对 Taurus 时期的旧错误（如 MissingOntologyInformation、UnknownMeasureName 等）给出排查方法，涉及 writeback dataset、Ontology 权限与 @AdditionalInputs 检查。

### 27. Foundry Rules • Add Foundry Rules to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/foundry-rules/marketplace/>
- **文档摘要**：本文介绍如何使用 Foundry DevOps 将 Foundry Rules 工作流纳入 Marketplace 产品，供其他用户安装复用。除已编写的规则本身外，所有 Foundry Rules 功能均受支持（因对象无法打包，Rule/Proposal 对象及其逻辑不包含在产品内）。添加时需选择 Workshop Application 内容类型与工作流应用，并在 Production 模式下对对象类型开启 Only allow edits via actions 以防权限错误。
