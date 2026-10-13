# Machinery 流程挖掘

> **分类说明**：流程图的绘制、数据连接、流程挖掘与监控分析

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `machinery` |
| 文档数量 | 6 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Machinery • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/machinery/overview/>
- **文档摘要**：Machinery 是 Palantir Foundry 中用于理解和管控真实世界流程（如业务流程、政府运作、医疗流程）的应用。它通过将文档、设备或人员等实体随时间的状态变化建模为 process container，并支持借助 AIP 的 LLM 能力编排多步自动化。本页概述了 Machinery 的核心能力与典型用例，包括识别流程低效、端到端管理 AIP 用例、从事件日志做 Process Mining、定义与监控性能指标、构建带人工干预的运营应用等，并列举了保险理赔、医院运营、采购到付款、订单到收款等场景。

### 2. Machinery • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/machinery/core-concepts/>
- **文档摘要**：本页介绍在使用 Machinery 前应掌握的核心概念：Process（用 process container 建模状态与动作，可嵌套链接）、State（对象当前状态，必须是可枚举的字符串属性，状态转换以图边表示）、Action and automations（状态间的动作与自动化）、Process log（记录实体状态变化的日志）。文中说明了状态值在 Ontology 中以字符串存储、可用枚举类型避免错误，以及如何用状态属性刻画对象生命周期，为多进程场景建立层级模型提供指引。

### 3. Machinery • Draw a graph

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/machinery/draw-a-graph/>
- **文档摘要**：本页讲解如何在 Machinery 中绘制流程模型。创建流程模型有两种方式：通过 Process Mining 从历史数据自动推导，或手动绘制状态与转移。文中说明手动绘制的步骤——先创建代表实体类型及其状态的 process container，多个相互交互的实体可添加并列或嵌套的容器；随后介绍添加 states、actions、automations 的典型模式，默认开启的 auto-layout 自动布局功能，以及用多个 process container 表示多对象类型并连接其状态与动作的方法。

### 4. Machinery • Connect data to Machinery

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/machinery/connect-data/>
- **文档摘要**：本页说明如何将数据连接到 Machinery，以桥接抽象流程定义与真实观测。Machinery 连接 Ontology 中两类对象数据：process objects（经历流程的实体，其状态由名为 state 的字符串属性显式跟踪）与 log objects（记录实体状态变化的个体，需包含 log ID、process ID、旧状态、新状态、时间戳等字段）。文中介绍数据配置侧栏的使用、状态值依赖关系的行为，以及日志对象 schema 的必填与可选字段定义。

### 5. Machinery • Process mining

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/machinery/process-mining/>
- **文档摘要**：本页介绍 Machinery 的 Process Mining 功能，即从历史观测数据自动推导流程模型。流程挖掘作为 Machinery 中的独立模式，在配置好数据连接后通过主工具栏访问。文中说明 Settings（选择使用 process objects、log objects 或两者作为挖掘数据源，以及选择挖掘 states、transitions 或两者）与 Filtering（通过按出现频率截断的长尾 transition 过滤和排除错误元素列表，生成更可用的流程表示）。

### 6. Machinery • Analyze and monitor a process

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/machinery/analyze-and-monitor/>
- **文档摘要**：本页讲解如何使用 Machinery widget 对已配置的流程进行分析与监控，提供运营洞察与瓶颈识别能力。内容包括在 Workshop 模块中添加并配置 Machinery Overview widget（默认 v2，需升级旧版应用），配置每个根流程的输入对象集与预置的指标视图（历史/当前计数与时长），以及 widget 特性：指标视图循环与固定、流程一致性过滤、聚焦父流程、图选择与输出、图功能和分析模式等。
