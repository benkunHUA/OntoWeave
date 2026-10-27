# AI FDE 智能交付工程师

> **分类说明**：AI Forward Deployed Engineer：导航、模式与能力、AIP Skills、会话预填、安全治理与最佳实践

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `ai-fde` |
| 文档数量 | 7 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AI FDE • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/overview/>
- **文档摘要**：AI FDE 是 AI 驱动的前向部署工程师，作为交互式 agent 用自然语言指令替你操作 Foundry，可做数据转换、管理代码仓库、构建维护 ontology。其流程为解析意图与上下文、确定 Foundry 操作、借原生工具执行、返回解释与文档，全程遵循用户权限；需在 enrollment 启用 AIP，建议同时开启 Global Branching。

### 2. AI FDE • Navigation

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/navigation/>
- **文档摘要**：本页介绍 AI FDE 的界面、导航与控件。在底部输入框提问即可开始会话，也可用预填链接带入提示与技能。上下文只能来自显式加入聊天的内容：可让 agent 自动选模式或手选模式获得额外配置，并从工具栏添加文档包、上传媒体或拖入数据集、函数、对象类型等资源；右侧大纲记录提示、响应、工具调用与 token 消耗。

### 3. AI FDE • Modes and capabilities

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/modes-and-capabilities/>
- **文档摘要**：AI FDE 用 mode 与 capability 组织任务并管理上下文。Mode 是任务大类，可手动选择或由 agent 依据输入判定，也允许中途切换；每个 mode 通过加载对应文档、开放相关工具来聚焦 agent，避免误用工具。所列 mode 含数据集成、Data Connection、Ontology 编辑、函数编辑、探索、治理、机器学习、OSDK React 与平台问答。

### 4. AI FDE • AIP skills

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/aip-skills/>
- **文档摘要**：AIP skills 把团队知识与工作流沉淀为可复用上下文和指令，供 AI FDE 使用。每个 skill 由名称、说明何时使用的 description 与 Markdown 正文组成，AI FDE 据此判断是否加载。可在 AIP Skills 中新建并保存到项目或文件夹以继承权限，使用时需在会话中添加并启用，同时开启 Load skills 能力。

### 5. AI FDE • Pre-fill sessions with URL parameters

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/prefill-sessions/>
- **文档摘要**：说明如何用 URL 参数把提示词与 AIP 技能预装入新的 AI FDE 会话，形成可分享、可重复的起点。路径为 /workspace/ai-fde/prefill，参数 prompt 预填首条消息、skillRid 可重复添加多个技能、autoStart 为 true 时加载即执行，均需 URL 编码。示例含从 Workshop 模块交办修改、共享提示模板与新人上手引导。

### 6. AI FDE • Security and governance

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/security-and-governance/>
- **文档摘要**：AI FDE 不是独立服务账号或机器人，而是以你的 Foundry 会话身份运行，继承相同的权限校验、治理与审计日志，你无权做的操作它同样不能做。服务端权限之外还有工具审批：执行 Ontology action、发布应用每次需确认，分支上的编辑与数据集构建可自动批准、受保护分支仍需审批，只读操作放行。

### 7. AI FDE • Best practices

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/ai-fde/best-practices/>
- **文档摘要**：使用 AI FDE 的要点：所有改动默认落在分支上，须审查代码并用代表性样本验证转换逻辑后再上线；只给模型任务必需的上下文与工具，减少误判和敏感操作暴露；把复杂任务拆成小步骤迭代构建；用 AIP Evals 跟踪效果并留意基础设施约束。
