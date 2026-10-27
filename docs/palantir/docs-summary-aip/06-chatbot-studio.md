# Chatbot Studio

> **分类说明**：低代码构建对话式 AI 应用：应用状态、检索上下文、引用、工具、命令、发布为函数与 Marketplace

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `chatbot-studio` |
| 文档数量 | 12 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AIP Chatbot Studio • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/overview/>
- **文档摘要**：AIP Chatbot Studio（原 AIP Agent Studio）用于构建 AIP Chatbot（原 AIP Agents）这类交互式助手，为其装配企业专属信息与工具，并可在平台内或通过 Ontology SDK 与平台 API 对外部署。Chatbot 由 LLM、Ontology、文档和自定义工具驱动，可嵌入应用实现带上下文的读写工作流，例如以筛选后的视频转录 object set 为上下文回答提问，也可放进 Workshop。其沿用平台安全模型，只向 LLM 开放完成任务所需的最小权限。

### 2. AIP Chatbot Studio • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/core-concepts/>
- **文档摘要**：系统梳理使用 AIP Chatbot Studio 必须掌握的术语：AIP Chatbot 是装配了企业信息与工具的交互式助手；application state（原 parameters）是 prompt 中动态控制模型行为的应用变量；instructions、工具描述和变量描述会被编译进 system prompt。页面还解释 RAG 与 retrieval context 的取数流程、tools 的作用、vector embeddings、context window，以及把 chatbot 封装为 Functions 复用的方式。

### 3. AIP Chatbot Studio • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/getting-started/>
- **文档摘要**：手把手带用户完成第一个 AIP Chatbot：可从工作区导航栏、Cmd/Ctrl+J 快速搜索或 Files 的 + New 进入 Chatbot Studio 并新建 chatbot 文件。配置包括设置名称、描述与头像，选择信息与工具类型（retrieval context 最简最快、application state 用于 Workshop 场景、tools 适合需要执行动作的复杂场景），挑选 enrollment 已启用的 LLM，改写 system prompt、调整 temperature、添加 conversation starters，最后保存发布并跟踪反馈与用量。

### 4. AIP Chatbot Studio • Application state

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/application-state/>
- **文档摘要**：AIP Chatbot 的 application state（原 parameters）用 string 或 object set 变量保存对话状态。配置时先选定 LLM 要交互的变量、为其命名并撰写描述说明用途，描述会注入 prompt 告知何时使用；再用 value visibility 控制 LLM 能否读取变量值，建议仅在必要时开放以免混淆。变量可作为 Ontology 检索的确定性输入，嵌入 Workshop 后能映射为 Workshop 变量，供其他组件展示。

### 5. AIP Chatbot Studio • Retrieval context • Context types

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/retrieval-context/>
- **文档摘要**：Retrieval context 在每条新用户消息时确定性地执行，把检索结果注入 LLM，可组合 Ontology、Document 和 Function-backed 三类上下文。Ontology 上下文可传固定 N 个对象，或按向量属性做语义搜索取最相关的 K 个，并指定打印哪些属性；Document 上下文有全文和相关分块两种模式；函数式上下文可读取 application variables，并支持自定义 citation。

### 6. AIP Chatbot Studio • Retrieval context • Citations

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/citations/>
- **文档摘要**：配置 document context 或 Ontology context 的 chatbot 会输出可点击的 citation 链接回原文，并在每条消息底部的 Sources 菜单汇总所用上下文；其他上下文默认不产生 citation，需用 prompt 引导 LLM 按指定格式输出才能渲染。可用格式包括对象 RID、objectTypeId（可在 Ontology Manager 查得）加 primaryKey，以及 PDF 的 mediaSetKey 与 mediaItemKey，并可加 page 标签指定页码。

### 7. AIP Chatbot Studio • Tools • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/tools/>
- **文档摘要**：Tools 是供 LLM 调用的外部功能或 API，用于执行动作或获取模型本身不具备的信息，尤其适合让 LLM 决定控制流并构造输入。共有六类：Action 执行 Ontology 编辑、Object query 限定可访问的对象与属性以省 token、Function 调用任意 Foundry function、Update application variable、Command 触发其他应用的操作、Request clarification 暂停并向用户追问。tool mode 决定工具如何提供给 LLM。

### 8. AIP Chatbot Studio • Tools • Use commands as tools in AIP Chatbot Studio

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/commands-as-tools/>
- **文档摘要**：把 command 加为工具后，chatbot 能在用户所在应用中代为操作：command 直接运行在用户应用内，可访问当前应用状态与屏幕，例如把地图视图定位到指定位置，这是后端工具难以实现的。操作时在配置面板选 Add tool > Commands 搜索并添加，同一 command 可重复添加以配置不同 prompt，还可设置输入参数与执行前是否需要用户批准。

### 9. AIP Chatbot Studio • Chatbots as Functions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/chatbots-as-functions/>
- **文档摘要**：Chatbot 发布为 Function 后可在平台任何能执行 Function 的地方复用，例如在 AIP Evals 中评估、用 Automate 自动化流程或在 Code Repositories 中调用；版本可按发布或保存触发。输入含 userInput、可选 sessionRid（省略即开新会话，传入则延续该会话）以及全部 application state 变量；输出含 markdownResponse、sessionRid 和仅在被更新时才返回的变量值。

### 10. AIP Chatbot Studio • Session logging

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/session-logging/>
- **文档摘要**：每条用户消息算一次执行，会被记录为结构化事件，可用 configure logging 导出到 streaming dataset，每次执行的唯一 trace identifier 串联该次全部日志。事件覆盖 session metadata、user request、chat messages、tool calls 及其结果、final response 和执行错误。日志含用户提示、标识符与工具输入输出，因此导出需权限，并应 Apply markings、限制访问、设置保留期。

### 11. AIP Chatbot Studio • Distribute AIP Chatbots using Marketplace

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/marketplace/>
- **文档摘要**：用 Foundry DevOps 可把 chatbot 打包进 Marketplace product 供他人安装复用：创建 product 时在 Add resources 步骤从 Add files 搜索并选择它；若 chatbot 通过 AIP Chatbot widget 嵌入 Workshop，则添加该模块即自动包含。除 Assist agents 外功能均受支持；带 document context 时会打包整个 media set，若只想带实际所需的文档，应先新建只含这些文档的 media set 再重配，以免把无关文件一并分发。

### 12. AIP Chatbot Studio • Use AIP Chatbots through Foundry APIs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/chatbot-studio/foundry-apis/>
- **文档摘要**：可用 Palantir APIs 把 chatbot 嵌入自建应用：创建 session 开启新会话，用 streaming 或 blocking API 编排多轮任务，会话状态由平台维护，传回 sessionRid 即可延续上一次会话；通过 parameterInputs 传入 application state，用 parameterUpdates 读取自定义输出。单次任务可改用 chatbots as functions，经 OSDK 在第三方应用调用。部署到 Developer Console 时只能用单一 Ontology 的 object、action 与 function types，并勾选全部相关资源。
