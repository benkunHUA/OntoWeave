# AIP Analyst 分析智能体

> **分类说明**：面向数据分析的 AI 智能体：能力、使用方式、分析资源、算力用量、Workshop widget 与嵌入

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-analyst` |
| 文档数量 | 8 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AIP Analyst • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/overview/>
- **文档摘要**：AIP Analyst 是自然语言驱动的 agentic 分析界面：提问后 agent 自主检索 Ontology、建 object set、变换数据，再产出摘要与可视化。示例中它检索 Northampton 周边 10km 咖啡店、做地理过滤与品牌聚合。分析可存为 Compass 资源。

### 2. AIP Analyst • Capabilities

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/capabilities/>
- **文档摘要**：本页列出 AIP Analyst 的工具分类：Ontology 类含对象查询、object set、SQL 与聚合，另有 Visualization、Datasets、Functions、Actions、Workshop 及文件媒体。分类可在 Tools 菜单启停；工具多转发给既有 Foundry 系统计算。

### 3. AIP Analyst • Using AIP Analyst

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/using-aip-analyst/>
- **文档摘要**：本页说明 AIP Analyst 会话的组成。Context 指分析期间可访问的信息，含历史消息、工具结果与通过 + 按钮、拖拽或 RID 加入的 Foundry 资源；Settings 分四页，可设主题、折叠思考过程、通知与未保存分析的成本归属；此外还有 tabs 分支、outline、graph、音频输入与导出。

### 4. AIP Analyst • Analysis resources

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/analysis-resources/>
- **文档摘要**：AIP Analyst 可把分析保存为 Compass 资源以便回访与共享。分析是动态的：只存重建所需状态（消息、调用工具、引用资源），不存工具结果与回复，重开时按 Ontology 最新状态重跑，结果始终反映当前数据并遵循查看者权限。保存前可预览 Messages/tools 与 References。

### 5. AIP Analyst • Compute usage

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/compute-usage/>
- **文档摘要**：AIP Analyst 消耗分两类：LLM tokens 按模型费率折算 compute-seconds，以及 federated Foundry compute，即工具调用背后的 Ontology、数据集或 function 计算。token 随对话增长，历史与工具结果每轮重发，大结果最耗。故应缩小上下文与搜索范围。

### 6. AIP Analyst • Workshop widget

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/workshop-widget/>
- **文档摘要**：介绍把 AIP Analyst 作为 Workshop widget 嵌入的配置。Base 段可追加自定义 system prompt、直接选模型或用 Fast/Balanced/Smart 类别、设置空态 suggested prompts；Input 段可预加载上下文并在变量变化时自动触发分析。

### 7. AIP Analyst • Embed AIP Analyst

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/embed/>
- **文档摘要**：AIP Analyst 可用 iframe 嵌入 Workshop 或 OSDK，并以 URL 参数定制：ontologyRid 指定 Ontology，objectTypeGroupRids 限定对象类型范围，initialMessage 预填首条提问。参数只覆盖部分设置，更细管控需改嵌模块。

### 8. AIP Analyst • Admin configuration

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-analyst/admin-configuration/>
- **文档摘要**：通过 Control Panel 的 AIP Analyst extension 配置：enrollment 级可关闭分析保存、action 执行（可设例外组）与文件上传；Group-level skills 页可给用户组分配 skills；organization 级可设默认搜索范围、模型类别与导出管控。
