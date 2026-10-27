# AIP 平台基础

> **分类说明**：AIP 平台总览、核心能力清单、入门路径、Prompt 工程最佳实践与 Agent Stack

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-platform` |
| 文档数量 | 5 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. Overview • AIP

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/overview/>
- **文档摘要**：AIP 把 AI 与企业的数据和运营流程连接以驱动自动化，工具覆盖开发者到一线用户。AIP Logic、AIP Chatbot Studio、AIP Evals 可在 Ontology 与工具链上构建生产级 workflow、agent 和 function，并与 Foundry、Apollo 组成 AI 操作系统。

### 2. AIP features

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/aip-features/>
- **文档摘要**：本页梳理各应用的内置 AIP 能力：AIP Assist 提供实时自然语言帮助，AIP Logic 无代码构建由 Ontology 数据支撑的 AI function，AIP Chatbot Studio 搭建对话式 agent；Pipeline Builder、Automate 等也可调用 LLM 并接入自建模型。

### 3. Get started with AIP

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/getting-started-with-aip/>
- **文档摘要**：本页推荐 learn.palantir.com 的三门课程：Foundry & AIP 企业入门、用例范围与优先级界定、动手课 Speedrun: Your First AIP Workflow。后者在 60-90 分钟内走完从 PDF 抽取信息、写入 Ontology、配置 AIP Chatbot 到搭建应用的流程。

### 4. Best practices for LLM prompt engineering

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/best-practices-prompt-engineering/>
- **文档摘要**：本指南给出 LLM prompt engineering 的核心策略：措辞清晰具体（如把「你了解哪些编程知识」改为「总结我开发 Web 应用的框架选项」）、补充角色与场景上下文、按输出质量反复调整 prompt 结构、用模型反馈形成迭代闭环。prompt 质量直接决定回答的相关性与准确性。

### 5. The Agent Stack • AIP

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/agent-stack/>
- **文档摘要**：Agent Stack 是 Palantir 的 agent 基础设施与产品套件，为构建、运行和观测 agent 提供统一底座，可用官方 SDK、第三方 SDK 或可视化构建器开发。它以会话承载任务状态与事件历史、用带类型的上下文项表示共享信息、靠事件与状态迁移推进任务，并可在管理器中复盘，成品可嵌入 Workshop 或经 API 集成。
