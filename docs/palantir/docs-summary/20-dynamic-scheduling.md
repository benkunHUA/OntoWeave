# 动态排程（Dynamic Scheduling）

> **分类说明**：排程的概念、甘特图 Widget、校验规则与交互配置

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `dynamic-scheduling` |
| 文档数量 | 19 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Dynamic Scheduling • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-overview/>
- **文档摘要**：本页是 Dynamic Scheduling 的总览，介绍 Palantir 的动态排程与资源分配能力。它说明该能力以 Foundry Ontology 为基础，借助 Scheduling Gantt Chart 和 Program Scheduling 等 Workshop widget 实现可视化、拖拽编辑与建议推荐。页面还列举了员工排班优化、物流韧性、制造产能提升等典型场景，并概括其三大原则：用 Ontology 建模资源、固化约束条件、建模资源与约束间的依赖关系。

### 2. Dynamic Scheduling • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-getting-started/>
- **文档摘要**：本页是 Dynamic Scheduling 的快速入门指南，提供搭建首个排程工作流的步骤。核心是先创建 Ontology 对象：一个 Schedule 对象类型（表示待分配的事件或任务，需含起止时间与外键）以及一个或多个 Resource 对象类型，并在两者之间建立链接与 save handler action。随后在 Workshop 中配置 widget。页面详细说明了对象属性、动作参数 ID 与类型类的匹配要求。

### 3. Dynamic Scheduling • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-concepts/>
- **文档摘要**：本页介绍 Dynamic Scheduling 的核心概念，包括 Schedule object（表示任务或活动，含起止时间）与 Resource object（被分配的资源实体）。它讲解两类 Scheduling widget：Gantt Chart 与 Calendar；并说明 Scenarios（假设分析沙箱）、Schedule save action handler、Suggestion Function、Search Function 以及 Validation rule 等机制如何共同支撑可交互的排程应用。

### 4. Dynamic Scheduling • Ontology primitives

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-ontology-primitives/>
- **文档摘要**：本页是 Dynamic Scheduling 的 Ontology primitives 参考，说明底层数据模型配置。最小要求是同时具备 Schedule 对象（含资源外键、起止时间等必填属性）和 Resource 对象，并通过 Ontology link 关联（支持多对一或多对多）。页面还以飞机维修排程为例，分别展示简单双对象类型配置与更复杂的进阶配置，帮助构建者规划 schema。

### 5. Dynamic Scheduling • Scheduling Gantt Chart widget

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-gantt-chart-widget/>
- **文档摘要**：本页讲解 Scheduling Gantt Chart widget 的配置，这是用于排程与资源分配的交互式 Workshop widget。配置前需先准备好动态排程 Ontology。内容涵盖 widget 布局，以及 Timeline data、Timeline configuration、Row data、Row configuration、Input data（pucks）、Layout、Metrics、Output、Scenarios 等设置项，并标注了必填项与可选项的区别。

### 6. Dynamic Scheduling • Schedule layer (puck) styling

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-schedule-layer-style/>
- **文档摘要**：本页介绍 Schedule layer（即 Gantt 图上的 pucks）的视觉样式定制。提供三种定制方式：Puck style（三种可用样式改变外观）、Coloring（静态、分段、条件着色三种颜色定义）、Properties（设置在 puck 上或悬停弹卡中显示的本体属性，支持函数属性并可重命名）。通过这些手段可为工作流创建丰富的动态可视化效果。

### 7. Dynamic Scheduling • Suggestion Functions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-suggestion-functions/>
- **文档摘要**：本页介绍 Suggestion Functions，用于按组织定义的逻辑以视觉方式提示 puck 放置的合适位置。每个函数由 TypeScript 函数支撑，可将区域高亮为可分配或不可分配，并可在 Workshop 中开启强制吸附到最近高亮区。需注意其结果是静态的（初始加载时运行），后续操作不会被纳入考量，文档提供了函数接口说明。

### 8. Dynamic Scheduling • Search Functions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-search-functions/>
- **文档摘要**：本页介绍 Search Functions，帮助用户快速查找并评估排程工作流中独特问题的可行解。每个函数由 TypeScript 函数支撑，可返回 pucks、time slots 或二者组合，并分为基于行（row-based）或基于 puck（puck-based）两类，分别通过右键空白或 puck 触发并传入对象与时间。页面给出了 IPuckSearch、IRowSearch 等接口类型定义。

### 9. Dynamic Scheduling • Validation rules

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-validation-rules/>
- **文档摘要**：本页介绍 Validation rules，用于将排程约束以代码化规则固化，使终端用户了解组织运营的边界。每条规则由 TypeScript 函数支撑，在初始加载与每次修改时评估 Schedule 对象是否满足约束，并在 Gantt Chart 前端呈现结果。文档还说明了编排流程（saveHandler 与规则交替调用）及函数接口与示例。

### 10. Dynamic Scheduling • Inline metrics

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-inline-metrics/>
- **文档摘要**：本页介绍 Inline metrics，为构建者提供在图表中直接展示关键数据的便捷方式。Header metrics 对齐时间轴（x 轴）提供全局聚合，Row metrics 对齐各行提供单个资源对象的排程洞察。实现需提供返回特定自定义类型列表的函数，文档列出了 TypeScript v1/v2 的函数签名要求。

### 11. Dynamic Scheduling • Row-level interactions • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-row-level-interactions-overview/>
- **文档摘要**：本页是 Row-level interactions 的概述，说明 Gantt 图中每一行对应一个 Resource 对象（如任务指派的技术人员、待维保车辆、行程机组等）。借助行级交互，用户可修改行对象的部分属性、创建新对象，或触发 Workshop 模块中的事件，从而让排程应用更具操作性。

### 12. Dynamic Scheduling • Row-level interactions • Triggering actions and events

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-row-level-triggering-actions-and-events/>
- **文档摘要**：本页讲解如何在行上触发 Ontology actions 与 Workshop events。动作可由行右键菜单或「On row select」事件触发：右键菜单可配置 Action types（如编辑属性、在时间点创建对象）与 Workshop events（弹窗、切换标签等），并支持用 Workshop 变量或 Gantt 专属变量预填参数；行选中事件则在点击行头时触发任意 Workshop 事件。

### 13. Dynamic Scheduling • Schedule layer-level interactions • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-schedule-layer-level-interactions/>
- **文档摘要**：本页是 Schedule layer-level interactions 的概述，说明 Schedule layer 不仅可视觉配置，也能定义专属交互集合。由于同一对象类型可复用于多个 layer，可据此创建高度特定的交互定义，包括修改 Schedule 对象属性、新建对象、触发事件，以及自定义拖拽行为与粒度。这些选项组织在每层 Input Data（Pucks）下的 Drag & Drop 与 Interactions 子项中。

### 14. Dynamic Scheduling • Schedule layer-level interactions • Drag to create

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-drag-to-create/>
- **文档摘要**：本页讲解 Drag to create 功能，允许终端用户在 Gantt 图上通过拖拽即时创建新的 Schedule 对象（在指定行上、以拖拽起止时间戳初始化）。使用前需在 Ontology Manager 为对象类型创建 Create Action Type（含主键、起止时间、外键参数），再到 Workshop 的 Interactions > Drag Cursor to Create Action 中绑定，并用 Gantt 变量预填起止时间与外键值。

### 15. Dynamic Scheduling • Schedule layer-level interactions • Drag and drop

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-drag-and-drop/>
- **文档摘要**：本页讲解 Gantt Chart widget 的 Drag and drop 功能，是用户与 pucks 交互的主要方式，可将 puck 重新指派到其他行或时间点。配置需在 widget 中提供 save action handler（modify 动作类型，含对象、起止时间、外键参数），并用 Gantt 变量预填。文档还说明 snap behavior、进阶 save handler、Suggestion function 等自定义项。

### 16. Dynamic Scheduling • Program Scheduling widget • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/program-scheduling-overview/>
- **文档摘要**：本页是 Program Scheduling widget 的概述，这是一个在 Workshop 中按时间顺序展示项目、计划与任务的 widget，用于跟踪跨工作流进展、可视化任务依赖并识别下游阻塞。它适用于任何含起止时间的对象类型，支持 Standard（任务条）、Background（阶段底色）、Event（里程碑竖标）三种 puck 类型，并以箭头呈现依赖关系。

### 17. Dynamic Scheduling • Program Scheduling widget • Widget configuration

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/program-scheduling-configuration/>
- **文档摘要**：本页讲解 Program Scheduling widget 的配置项。涵盖 Timeline（起止时间戳变量）、Input data（layers，可多层叠加，含对象集、puck 类型、起止时间、依赖属性、分组与详情属性、颜色）、Interface 与 Output 等部分，并明确标注必填项（如 Data Object set、Puck type、Start time property）。依赖属性用于以箭头展示任务间关系。

### 18. Dynamic Scheduling • Scheduling Calendar widget • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-calendar-overview/>
- **文档摘要**：本页是 Scheduling Calendar widget 的概述，这是一个基于对象日期或时间戳属性展示数据的 Workshop widget。用户可按日、周、月视图浏览，并可选启用拖拽功能以修改 Schedule 对象。典型用例包括会议/预约排程、任务与里程碑截止跟踪、员工班次分配等。

### 19. Dynamic Scheduling • Scheduling Calendar widget • Widget configuration

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/dynamic-scheduling/scheduling-calendar-widget/>
- **文档摘要**：本页讲解 Scheduling Calendar widget 的配置。widget 有只读与动态两种模式：默认为只读（查看分析），切换动态模式需为对象起止时间属性添加 schedules:schedulable-start-time / end-time 类型类，方可增删改对象。文档还说明 Input data（pucks）的显示配置、Drag to create 设置，以及常见配置错误（如动作参数 ID 需与属性 ID 对齐）。
