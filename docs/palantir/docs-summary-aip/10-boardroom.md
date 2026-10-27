# Boardroom 决策会议室

> **分类说明**：AI 驱动的决策会议空间：创建、模式、配置、Skills 与模板

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `boardroom` |
| 文档数量 | 5 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. Boardroom • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/boardroom/overview/>
- **文档摘要**：Boardroom 协调成队的 AI agents 在 Foundry 和/或 Gotham 中协同工作。每个 boardroom 是与 director 及可配置专家 agents 协作的空间，团队可检索 Ontology、跑代码、分析数据；各 agent 的指令、模型、工具与上下文可定制，memory 让知识沉淀。

### 2. Boardroom • Create a boardroom

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/boardroom/create-a-boardroom/>
- **文档摘要**：创建 boardroom：填标题与描述工作的高层 prompt，再选 Prompt-driven 由 prompt 生成团队或 Ontology-informed 借助 Ontology 定义团队；配置 markings 与共享设置后生成。产出 director 与 agents，可继续定制 skills、模型与工具。

### 3. Boardroom • Boardroom modes

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/boardroom/modes/>
- **文档摘要**：Boardroom 有多种模式：Chat 与整个 agent 团队对话，director 可分派任务、跑代码、查询 Ontology、生成可视化；Panel 转录会议音频并抽取问题、想法与行动项分派给 agents；Documents 由 AI 写作团队产出文档，另有 Slide decks 与 Plans。

### 4. Boardroom • Configure a boardroom

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/boardroom/configure-a-boardroom/>
- **文档摘要**：创建 boardroom 后进入 Configure Boardroom，可分别维护团队共享的全局上下文与单个 agent profile。全局上下文支持添加 object types、saved object sets、actions、functions，也可把 Workshop 等 Foundry 应用注册为工具；关闭 Enable ontology search 后，agent 只能检索人工指定的对象。每个 agent 可独立设定 system prompt、默认模型、memory、skills、Ontology 上下文和可调用的子 agent。数据存于 Ontology，受 object security policy 管控。

### 5. Boardroom • Use skills and templates

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/boardroom/skills-and-templates/>
- **文档摘要**：skills 描述 agent 应如何完成工作，templates 规定产出的结构与格式，两者配合让 Boardroom 中的流程可复用到不同任务。skill 由 name、description、content 组成：description 说明适用场景并便于 agent 检索，content 存放详细步骤。页面以延迟发货调查为例给出可直接照搬的 skill 写法，说明 skill 的可见范围与安全边界，并用周度运营报告、运营评审演示 template 如何定义章节结构。
