# AIP Assist 智能助手

> **分类说明**：平台内置 AI 助手：自定义文档、内容注册、建议动作、应用集成与 Agents

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-assist` |
| 文档数量 | 9 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AIP Assist • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/overview/>
- **文档摘要**：AIP Assist 是基于 LLM 的平台内支持工具，用户用自然语言提问即可实时获得关于 Foundry 使用与概念的回答。它支持多语言、能感知当前所在的 Foundry 应用并保持会话上下文，且不访问用户数据，遵循 Palantir 的隐私与负责任 AI 原则。需管理员在 Control Panel 启用 AIP 后使用，可从导航栏或 Cmd/Ctrl+Shift+U 打开，并用预置 modes 与 AIP Chatbots 收窄回答范围。

### 2. AIP Assist • AIP Assist best practices

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/aip-best-practices/>
- **文档摘要**：该页给出与 AIP Assist 交互的提问技巧：问题要具体、一次只问一件事、用完整句子补充背景、提供期望格式的示例，回答不准时追问或换种说法重述。文中强调 AIP Assist 能感知当前所在应用，但无法访问用户正在处理的数据或元数据。后半部分列出聚焦型示例问题，如排查环境依赖报错、配置 Contour 时区、在 Functions on Objects 中写 Aggregate Function、在 Slate 中配置 CSS。

### 3. AIP Assist • Power AIP Assist with custom content sources • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/aip-assist-custom-docs-overview/>
- **文档摘要**：自定义内容源用于把企业内部资料接入 AIP Assist，使其能回答运营流程、平台导航等公共文档覆盖不到的问题，常用于新人上手、培训和自助支持。内容源支持 Notepad 文档（推荐）与 Code Repositories 中 documentation 类型仓库的 Markdown 文件两种登记方式，登记后需在 Control Panel 配置可见范围与访问权限。使用方式有两种：并入当前 enrollment 的默认知识库，或做成独立的 AIP Chatbot。

### 4. AIP Assist • Power AIP Assist with custom content sources • Register custom content sources

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/aip-assist-registering-content/>
- **文档摘要**：说明如何把已有内容登记为 AIP Assist 的自定义内容源。Notepad 方式是在文档的 Actions 菜单选择 Add to AIP Assist，在弹窗中开启开关并填写文档标题等信息，且文档需属于某个 project；也可改用平台内自定义文档。登记后还须管理员在 Control Panel 授权，AIP Assist 才能在回答时引用。文中建议优先用 Notepad，因其可嵌入 Workshop 等应用，并解答内容更新是否同步、图片为何不显示等常见问题。

### 5. AIP Assist • Power AIP Assist with custom content sources • Serve custom content sources to users

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/adding-documentation-to-aip-assist/>
- **文档摘要**：内容源登记后，需由具备 Control Panel 权限的平台管理员在 AIP Assist 页面点击 + Add，从已摄取文档中选中加入，才能对外提供。管理员可选择该源仅在特定资源打开时生效，或始终参与回答；平台级通用内容建议设为始终提供，与官方文档共同构成检索上下文。新源默认只在创建它的 repository 或 Notepad 文档内可见，可先提问验证效果再逐步放开范围。

### 6. AIP Assist • Power AIP Assist with custom content sources • Deploy custom source-backed AIP Chatbots

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/agents-in-aip-assist/>
- **文档摘要**：介绍如何把 AIP Chatbot Studio（原 AIP Agent Studio）构建的 AIP Chatbot 部署到 AIP Assist，让应用上线时用户就能针对自定义内容获得即时交互式支持，无需写代码或具备 LLM 经验。流程分三步：先将内容以 Notepad 文档或平台内自定义文档登记为自定义内容源；再在 Chatbot Studio 中引用该源，并结合 Ontology 上下文或 functions 等工具构建 chatbot；最后发布到 AIP Assist 并对话验证。

### 7. AIP Assist • Power AIP Assist with custom content sources • Custom content source best practices

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/custom-documentation-best-practices/>
- **文档摘要**：该页围绕 AIP Assist 底层的 RAG 机制给出内容写作建议：检索按段落粒度进行，因此内容应按标题和子标题切成聚焦单一主题的小节，而不是写成多主题的长段落。文中用 Feature A 到 Feature D 的例子对比混写与分节的差异。此外应避免与公开文档产生语义碰撞或重复，也不要依赖会过期或被覆盖的内容，以免检索到冲突信息拉低回答质量。

### 8. AIP Assist • AIP Assist application integrations

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/application-integrations/>
- **文档摘要**：集中列出 AIP Assist 在 Palantir 平台各应用中的集成入口。Code Repositories 提供 Ask AIP Assist 预置代码操作，并可将整个仓库、若干文件或选中代码片段作为附件，用于解释文件关系、优化代码、跨仓库检索和总结；Carbon 工作区可开启面向特定用户群的 AIP Assist chatbot；此外还覆盖 Contour、Workshop（含 Send to AIP Assist）、Slate、Issues 和 Ontology Manager。

### 9. AIP Assist • Suggested actions in AIP Assist

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/assist/aip-assist-suggested-actions/>
- **文档摘要**：AIP Assist 除回答问题外，还会主动给出导航与站内操作建议，把用户引导到相关应用或功能，对首次使用 Foundry 的用户尤其有用。另一项能力是与 Palantir Developer Community 论坛联动：当 AIP Assist 缺少所需信息时，会引导用户发帖，并依据当前对话预填标题和描述。该跳转论坛的建议动作可由 enrollment 管理员在 Control Panel 的 AIP Assist 页面关闭。
