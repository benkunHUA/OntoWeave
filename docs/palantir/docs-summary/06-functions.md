# 函数（Functions）

> **分类说明**：TypeScript/Python 函数的开发、部署、测试、监控与最佳实践

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `functions` |
| 文档数量 | 72 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Functions • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/overview/>
- **文档摘要**：本页是 Functions 的概述。Functions 允许代码作者在服务端隔离环境中编写可在仪表盘、应用等操作场景快速执行的逻辑，并原生支持基于 Ontology 的编程（读取属性、遍历链接、编辑 Ontology）。常见用途包括在 Workshop 中返回对象集或变量、派生列与图表聚合、通过 Function-backed Action 执行复杂编辑、在 Slate/Quiver 中计算指标、查询外部系统以及流式输出。支持 TypeScript 和 Python 两种语言。

### 2. Functions • Feature support by language

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/language-feature-support/>
- **文档摘要**：本页以对照表形式说明 Functions 各能力在不同语言中的支持情况。表格覆盖 AIP Logic、TypeScript v1、TypeScript v2 与 Python，对比项包括 Ontology 对象/接口/编辑支持、媒体处理、Workshop 查询能力、Pipeline Builder 调用、模型与语言模型集成、语义搜索、Webhook、外部 API 调用、Serverless 与 Deployed 执行等。此外还区分 TypeScript v1 与 v2 的差异，并说明 Serverless functions 的特性。

### 3. Functions • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/getting-started/>
- **文档摘要**：本页是 Functions 的入门指南，介绍在 Foundry 中使用 TypeScript v1、TypeScript v2 和 Python 三种语言开始开发函数的通用流程。内容涵盖如何创建 functions repository、在 Code Repositories 或 VS Code 工作区中选择开发环境、使用 live preview 测试、发布函数以及在平台中运行函数，并指向各语言的具体入门教程。

### 4. Functions • Types reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/types-reference/>
- **文档摘要**：本页是 TypeScript 函数的类型参考。说明要将函数发布到 registry，TypeScript 函数必须对所有输入参数添加显式类型注解并指定显式返回类型，并列出当前支持的函数 registry 类型及其对应的语言类型。页面还提示在 Pipeline Builder 中将 Python 函数作为 UDF 使用时，标量类型（如 str、int）是逐行返回的列值而非 DataFrame。

### 5. Functions • Branching functions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/branching-functions/>
- **文档摘要**：本页介绍 Branching functions，即可以在 global branch 上开发、发布和消费函数（支持 TypeScript v1/v2、Python 与 AIP Logic）。内容涵盖开发前对 repository 模板版本与本地 Ontology SDK 的要求、冲突解决与 rebase 机制，以及合并流程。在分支上可持续发布到同一版本目标，依赖方会自动拉取最新版本以加速迭代。

### 6. Functions • Navigating VS Code

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/navigating-vscode/>
- **文档摘要**：本页讲解如何在 VS Code 中使用 Palantir 扩展开发函数。涵盖通过 Activity Bar 将 Ontology SDK / Platform 资源导入 repository，使用 Palantir 面板创建或切换 branch、预览函数、提交与打标签、升级 repository，以及格式化保存、AI 开发工具、内置导航等使用技巧，帮助开发者无需离开编辑器即可完成全流程。

### 7. Functions • Function management • Function versioning

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/functions-versioning/>
- **文档摘要**：本页阐述 Functions 的版本控制系统。区分向后兼容变更（如新增可选输入、性能优化）与破坏性变更（如新增必填输入、改变输出类型），并采用 Semantic Versioning（X.Y.Z 及预发布标识）。说明如何根据变更性质选择发布版本、向后兼容检查机制、限制稳定版本标签，以及常见问题的处理建议。

### 8. Functions • Function management • Manage published functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/manage-functions/>
- **文档摘要**：本页介绍如何管理已发布的 functions。可在 Ontology Manager 的 Functions 选项卡搜索函数，查看其输入、输出、使用历史与指标（成功/失败计数、P95 执行时长）；在 Configuration 页配置超时、内存等（按版本继承）。还涵盖 consistent snapshots 快照配置、以及时间限制、内存限制、多线程、TypeScript v1 对象集限制等强制限制。

### 9. Functions • Function management • Functions in Marketplace

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/marketplace-functions/>
- **文档摘要**：本页说明如何将 functions 纳入 Marketplace 产品供他人安装复用。涵盖向产品添加函数、是否包含源代码、扩展函数执行能力的安装要求、版本与 API 名称解析（去重模式与稳定模式）、deployed functions 的打包与配置限制、静态函数输入、自定义别名与模型别名等内容。

### 10. Functions • Function management • Function monitoring

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/monitoring/>
- **文档摘要**：本页介绍 Functions 的监控能力，用于跟踪性能与可靠性。支持的监控规则包括函数 P95 执行时长、窗口内失败总数、面向用户失败数及非面向用户失败数；说明了通过监控视图与规则、阈值、告警订阅进行配置的步骤，并支持 Workflow Lineage、Workshop 与 OSDK 应用的动态作用域自动追踪所用函数。

### 11. Functions • Function management • Instrumentation and telemetry

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/instrumentation-telemetry/>
- **文档摘要**：本页介绍从 functions 中发出遥测数据以进行生产监控与调试。列出各语言（TypeScript v1/v2、Python）支持的遥测类型（Logs、Spans、Metrics），所有函数类型会自动创建执行时长 span 与请求日志；TypeScript v2 与 Python 还会自动埋点出站网络请求并支持自定义 span，并给出各语言输出自定义日志的方式。

### 12. Functions • Function management • Permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/permissions/>
- **文档摘要**：本页梳理 Functions 涉及的多类权限检查。分为函数编写（需授予 Ontology 实体与对象加载权限，且为 repository 级而非用户级权限）与已发布函数执行（函数权限与对象加载权限）两类，并介绍扩展函数执行（如函数内调用 action、获取长 TTL 令牌）的额外权限要求与常见问题。

### 13. Functions • Function consumption • Use functions in the platform

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/use-functions/>
- **文档摘要**：本页汇总在 Foundry 平台各处消费 functions 的方式。涵盖 Workshop（Function-backed 变量、对象表的派生列、图表的派生聚合）、Actions（Function-backed Actions、通知与 Webhook 副作用）、Slate、Quiver 与 Automate，并给出 Workshop 变量类型与 TypeScript 类型的映射关系，说明函数如何嵌入各应用。

### 14. Functions • Function consumption • Function metrics

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/function-metrics/>
- **文档摘要**：本页介绍 Function metrics，展示函数类型近 30 天的准实时使用情况，可在 Ontology Manager 概览页或 Workflow Lineage 中查看。提供成功/失败计数与 P95 执行时长指标，并可访问近 7 天运行历史；详列失败类型（运行时失败、资源超限、面向用户错误、输入/输出非法、禁止数据加载等），并说明如何配置监控告警与所需权限。

### 15. Functions • Function consumption • Optimize performance

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/optimize-performance/>
- **文档摘要**：本页提供优化函数性能与资源使用的最佳实践。说明函数计算成本由固定开销（每次执行 4 个 compute-seconds）、vCPU 计算时间与外部调用组成；介绍使用 Performance 标签分析问题，并建议优先采用对象集（Object set）作为输入以延迟加载、避免单对象参数的反模式，以最小化计算消耗并提升可扩展性。

### 16. Functions • Function consumption • Version range dependencies

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/version-range-dependencies-for-functions/>
- **文档摘要**：本页说明 Workshop、Actions 与 Automate 对函数既可依赖固定版本也可依赖版本范围（version range）。版本范围支持运行时自动升级、减少开发量并允许 deployed functions 无停机升级，但伴随风险（如 Actions 中的权限与来源影响）。页面解释版本范围解析机制（采用 NPM caret 语义）及部署函数的解析差异，帮助使用者权衡取舍。

### 17. Functions • Language-agnostic features • Ontology edits

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/edits-overview/>
- **文档摘要**：本页介绍 Ontology edits——创建、修改或删除对象的行为，函数可返回 Ontology edits 用于 Function-backed Action。分别说明 TypeScript v1（@OntologyEditFunction 与 @Edits 装饰器）、TypeScript v2（createEditBatch 与 Edits 类型）与 Python（FoundryClient 的 edits 容器）的写法，并说明链接类型编辑同样属于 Ontology edit，需声明以获得完整 provenance。

### 18. Functions • Language-agnostic features • Publish and call query functions through API gateway

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/query-functions/>
- **文档摘要**：本页介绍 Queries——函数的只读子集，可选择性通过 API gateway 暴露，不能有任何副作用（如修改 Ontology 或外部系统），需要编辑能力时应使用 Action。页面说明如何使用 Query 装饰器（decorator）定义查询函数，并给出 TypeScript v1、TypeScript v2 与 Python 的语法示例。

### 19. Functions • Language-agnostic features • Configure notifications

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/configure-notifications/>
- **文档摘要**：本页讲解如何在函数中灵活配置通知，包括发送到平台内或用户外部邮箱。配置依赖 Principal（代表 User 或 Group）与通知类型；给出在 Issue 对象分配给 User 时，编写函数向该 User 发送含 Issue 详情通知的示例，并附 Principal 与通知类型参考，覆盖 TypeScript v1/v2 与 Python 的实现。

### 20. Functions • Language-agnostic features • Make API calls from functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/api-calls/>
- **文档摘要**：本页说明如何从 TypeScript v1/v2 与 Python 函数调用外部 API，但需额外配置。介绍通过 source alias 引用外部源以提升可移植性、在 Data Connection 配置允许函数连接外部系统的源、以及将源导入 functions repository 后在函数中使用的步骤；并提醒 Webhook 与函数运行环境不完全一致，可能遇到证书链问题。

### 21. Functions • Language-agnostic features • Use platform APIs with the Foundry platform SDK

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/platform-sdk/>
- **文档摘要**：本页介绍通过 Foundry platform SDK 在函数中使用平台 API。该 SDK 可用于管理/治理工作流、调度与构建、媒体集等，TypeScript v1 不支持一流认证，建议使用 Python 或 TypeScript v2。内容涵盖安装 SDK（foundry-platform-sdk / @osdk/foundry）、Python 版本兼容性（需 3.9+）、初始化认证 client，以及调用语言模型或查询媒体集的示例。

### 22. Functions • Language-agnostic features • Deploy functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/functions-deployed/>
- **文档摘要**：本页讲解如何部署（deploy）Python 或 TypeScript v2 函数。对比 deployed 与 serverless 两种执行模式：serverless 默认且维护成本低，deployed 支持本地缓存、第三方客户端调用外部 API、GPU 分配与完整服务发现，但仅能运行单一版本且持续计费。页面还介绍架构与具体的部署步骤。

### 23. Functions • Language-agnostic features • User-facing errors

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/user-facing-error/>
- **文档摘要**：本页介绍 User-facing errors——在 Workshop、Actions 等平台场景运行函数时，可抛出带详细信息的错误以展示给用户。做法是通过抛出 UserFacingError，页面给出 TypeScript v1、TypeScript v2 与 Python 的代码示例，用于在业务逻辑出错时向最终用户返回清晰指引。

### 24. Functions • Language-agnostic features • Streaming functions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/streaming-functions/>
- **文档摘要**：本页介绍 Streaming functions——Python 与 TypeScript v2 函数可将执行结果分块流式返回，便于数据随时间产生时让消费应用提前处理。说明 Python 需用 Iterable[T] 返回类型配合 yield 关键字，TypeScript 需声明 async generator（返回 AsyncIterable<T>）并用 yield 产出值，并给出流式返回整数的示例。

### 25. Functions • Language-agnostic features • Functions settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/functions-settings/>
- **文档摘要**：本页介绍 Control Panel 中的 Functions settings 页面，供管理员管理特定 space 内函数的行为。核心是 Extended function execution 配置：允许函数内调用 action 及获取最长 4 小时 TTL 的认证令牌，并提供三类允许名单——可发布扩展函数的 repository、可执行扩展能力的具体函数、以及可通过 Marketplace 安装扩展函数的 project。

### 26. Functions • TypeScript v2 • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/typescript-v2-getting-started/>
- **文档摘要**：本页介绍如何开始使用 TypeScript v2 Functions。相比 v1，v2 提供 Node.js 运行时与一流 OSDK 支持。内容包括创建 TS v2 函数代码仓库（选模板初始化），以及在 functions 目录下新建文件、用 export default 让 Foundry 识别函数。

### 27. Functions • TypeScript v2 • Local Ontology SDKs

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/local-sdks/>
- **文档摘要**：本页介绍 TS v2 与 Python functions 的 Local Ontology SDK：OSDK 按资源导入自动生成而非手动安装，类型始终最新，且是完整 Global Branching 的前提。涵盖新建本地 SDK、用 ./rune 命令行生成，以及将现有仓库迁移到本地 SDK。

### 28. Functions • TypeScript v2 • Migrate from TypeScript v1 to TypeScript v2

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/typescript-v2-migration/>
- **文档摘要**：本指南说明将现有 TypeScript v1 函数迁移到 v2 时的语法与结构差异，建议先参考功能支持文档了解各版本能力。以「声明函数」为例：v1 用 @Function() 装饰器且函数须为导出到 index.ts 的类方法，v2 改用 export default。

### 29. Functions • TypeScript v2 • Ontology edits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/typescript-v2-ontology-edits/>
- **文档摘要**：本页介绍 TS v2 的 Ontology edits 函数——可创建对象、修改属性与链接。编辑函数需通过 Edits 类型声明被编辑实体（多实体用 \| 连接），且必须配置为 function-backed Action 才生效。还说明标准 edit batch 与提供读后写保证的 staged writes 的区别。

### 30. Functions • TypeScript v2 • Staged writes

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/typescript-v2-staged-writes/>
- **文档摘要**：本页介绍 TS v2 的 Staged writes（Beta）。它提供另一种编辑 Ontology 对象的执行模型，相比普通 edit 函数具有读后写保证、无需在函数末尾返回 edits、原子执行，以及使用 WriteableClient。还演示如何定义 staged-write 函数及其独特属性。

### 31. Functions • Python • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-getting-started/>
- **文档摘要**：本页是 Python Functions 入门指南，引导完成平台使用前的准备：如何创建 Python functions 代码仓库（选模板初始化），并建议将 Workshop 与 Ontology 应用所需函数集中到单一仓库以降低成本。仓库含 my_function.py 示例，可提交、发布与测试。

### 32. Functions • Python • Functions on objects

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-functions-on-objects/>
- **文档摘要**：本页介绍用 Python Ontology SDK 编写与 Ontology 交互的函数。涵盖生成 Python OSDK 客户端（导入对象与链接后生成安装），以及示例代码：接受对象参数返回摘要字符串、按阈值搜索 Object Set。并提醒 SDK 报导入错误时需先选择 Ontology。

### 33. Functions • Python • Create a custom aggregation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-functions-create-custom-aggregation/>
- **文档摘要**：本指南演示用 Python functions 基于 Ontology 数据计算自定义 Aggregation 并在 Workshop 图表展示：从 Ontology 加载聚合数据（按部门与日期分组求和），外推预测未来六个月支出，再返回修改后的结果。文中引用 Python aggregation types 参考。

### 34. Functions • Python • Ontology edits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-ontology-edits/>
- **文档摘要**：本页介绍 Python 的 Ontology edits 函数：用 @function(edits=[...]) 声明被编辑类型、明确 list[OntologyEdit] 返回类型并构造 edits 容器，可更新属性、编辑 struct、更新链接、创建或删除对象。须配置为 Action 生效。

### 35. Functions • Python • Staged writes

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-staged-writes/>
- **文档摘要**：本页介绍 Python 的 Staged writes（Beta）。相比普通 edit 函数，它有读后写保证、无需返回 edits、原子执行及 WriteableClient。演示定义 staged-write 函数，以及创建（含生成 ID）、更新、删除对象与链接、调用其他 staged-write 函数。

### 36. Functions • Python • Use Python functions in Pipeline Builder

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-functions-builder/>
- **文档摘要**：本页介绍在 Pipeline Builder 中使用 Python UDF（同时支持 Java）。Python 函数以 sidecar 容器运行并随管道动态扩缩容。关键：函数按行执行，每行单元格值作为参数传入，返回值成为输出表新列（返回标量而非 DataFrame）。并列出使用前提与架构说明。

### 37. Functions • Python • Use Python functions in Workshop

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-functions-workshop/>
- **文档摘要**：本指南介绍在 Workshop 中使用已发布 Python 函数。涵盖从 Variables 标签搜索函数（两种形态及运行状态）、发布新版本（Ontology Manager 升级）、调试错误，以及创建 function-backed 列将输出映射新列。前提是你已编写并发布函数。

### 38. Functions • Python • Local development

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/python-functions-local-development/>
- **文档摘要**：本页介绍 Python functions 仓库的本地开发。说明如何克隆仓库（token 短期只读、可回推）、设置开发环境（Java 21、JAVA_HOME、Apple silicon Mac 需 Rosetta 2），以及 VS Code 与 PyCharm 配置，并列出模板版本错误等限制与排错提示。

### 39. Functions • TypeScript v1 • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/typescript-v1-getting-started/>
- **文档摘要**：本页是 TypeScript v1 Functions 入门（建议迁移到 v2）。介绍如何创建 TS v1 函数代码仓库（选 TypeScript functions 模板），以及编写函数：函数必须定义在 TypeScript 类中并从 functions-typescript/src/index.ts 导出该类。

### 40. Functions • TypeScript v1 • Decorators

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/decorators/>
- **文档摘要**：本页（TS v1）讲解函数装饰器。TS 函数须为公开类方法并从 index.ts 导出，用 @foundry/functions-api 标注：@Function() 通用、@OntologyEditFunction() 可配 @Edits 编辑、@Query() 经 Foundry API 的只读查询。文中给出示例。

### 41. Functions • TypeScript v1 • Ontology edits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/api-ontology-edits/>
- **文档摘要**：本页（TS v1）介绍 Ontology edits：可编辑对象属性与链接，须配置为 function-backed Action 才生效。涵盖声明编辑函数（@OntologyEditFunction() 与 @Edits、void 返回）、更新属性与链接、创建/删除对象。

### 42. Functions • TypeScript v1 • Generate unique IDs for new objects

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/edits-generate-id/>
- **文档摘要**：本页讲解创建对象的 edit 函数中为新建对象生成唯一 ID。TypeScript v2 无内置 UUID，需安装标准 uuid 包并用 uuidv4()；TypeScript v1 则用默认安装的 @foundry/functions-utils 包中 Uuid.random() 生成全局唯一标识。

### 43. Functions • TypeScript v1 • Error types

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/typescript-error-types/>
- **文档摘要**：本页（TS v1）介绍错误类型处理。可用 FunctionsError 类型声明错误类型（含名称与可选数据，可联合多个），并用 FunctionsResult 在函数上声明输出与错误类型。涵盖定义错误类型、在函数中声明、返回输出与错误，以及处理查询返回的错误。

### 44. Functions • TypeScript v1 • Handle undefined values

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/undefined-values/>
- **文档摘要**：本页（TS v1）讲解处理访问属性或链接时可能返回的 undefined 值。给出两种模式：显式检查（判断字段已定义再使用，否则抛明确错误，利于检查）与非空断言运算符（用 ! 断言已定义，简洁但遇 undefined 会报错，建议优先显式检查）。

### 45. Functions • TypeScript v1 • Debug functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/debug/>
- **文档摘要**：本页（TS v1）介绍函数调试，同样适用于单元测试。讲解 Code Repositories 的 authoring debugger：设置断点暂停执行检查变量；在 live preview 或测试时运行；通过面板查看 Frames、Variables、Console，帮助定位正确性与性能问题。

### 46. Functions • TypeScript v1 • Import resources into Code Repositories

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/resource-imports-sidebar/>
- **文档摘要**：本页（TS v1）介绍 Code Repositories 的 Resource imports 侧栏，集中管理函数仓库导入的 Foundry 资源。涵盖选择 Ontology（导入对象与链接前提）、导入资源（含 resources.json 与选择器）、管理已导入资源、无 API 名称与文件式导入。

### 47. Functions • TypeScript v1 • Add NPM dependencies

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/add-dependencies/>
- **文档摘要**：本页（TS v1）介绍为函数仓库添加 npm 依赖。函数仓库用 npm 管理，运行时仅支持纯 JavaScript 库（依赖 NodeJS 或系统调用的包不支持）。涵盖启用公共 npm registry、在 Libraries 侧栏搜索添加，以及手动改 package.json 加指定版本。

### 48. Functions • TypeScript v1 • Use webhooks

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/webhooks/>
- **文档摘要**：本页（TS v1）讲解在函数中用 webhooks 调用外部系统。需先将数据连接源与 webhook 启用为可导入，再在函数发起请求，并给出多次调用与错误处理示例。还说明限制、版本变更处理、权限、监控排错与最佳实践。TS v2/Python 应发布为 webhook function 经 OSDK 调用。

### 49. Functions • Functions on objects • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/functions-on-objects/>
- **文档摘要**：本页是「Functions on objects（FOO）」概述。Foundry 函数原生支持访问与修改 Ontology 中对象与链接：导入后自动生成代码绑定，支持对象与 Object Set 作参数、按需搜索对象集、用 Ontology edits 修改对象，并受数据安全与血缘保障。按语言列出读取对象的 API。

### 50. Functions • Functions on objects • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/foo-getting-started/>
- **文档摘要**：本页是 Functions on objects 的入门教程，介绍如何在 Foundry 中基于 Ontology 数据编写函数。核心内容包括前置条件（需先创建 TypeScript 代码仓库）、导入 Ontology 类型（对象/接口/链接类型），以及在代码中编写一个对象支撑（object-backed）的 Function。页面还区分了 TypeScript v1 与 v2 在导入 @foundry/ontology-api 与生成 Ontology SDK 上的差异，并说明了私有 Ontology 的包名替换方法。

### 51. Functions • Functions on objects • Object identifiers

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/object-identifiers/>
- **文档摘要**：本页讲解 Foundry 中对象的多种标识方式（面向 TypeScript v1），帮助开发者写出正确的函数代码。介绍了三个类别的标识符：Object RID（创建时分配，新对象为 undefined）、Primary keys（object type 加主键值，始终存在）、Object specifiers（需 typeId 加 primaryKey 共同唯一标识）。还说明了在函数中比较对象相等性时的注意事项——同一 Ontology 对象可能对应多个 JavaScript 对象。

### 52. Functions • Functions on objects • Create a custom aggregation (TSv1)

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/create-custom-aggregation/>
- **文档摘要**：本页（面向 TSv1）讲解如何使用 Function 计算自定义聚合（custom aggregation），并将结果在 Workshop 的图表组件中展示。指南涵盖用 .groupBy()、.segmentBy() 及聚合方法对对象集（Object Set）进行链式聚合的写法，说明字符串属性的两种分桶策略（.topValues() 与 .exactValues()）及各自上限，并给出按月估算各部门支出的完整示例。同时指向 Python 与 TSv2 的对应参考。

### 53. Functions • Functions on objects • Import object, interface, and link types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/ontology-imports/>
- **文档摘要**：本页介绍如何在函数代码仓库中导入对象、接口和链接类型，使其可在函数里使用。步骤包括通过 Resource Imports 侧边栏添加所需 Ontology 类型、选择 Ontology、保存后 Code Assist 自动重启以重新生成代码绑定。页面说明了不同语言仓库对应的导入包：TSv1 用 @foundry/ontology-api，TSv2 与 Python 需先生成 Ontology SDK 后分别从 @ontology/sdk 与 ontology_sdk 导入。

### 54. Functions • Functions on objects • Objects and links (TSv1)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/api-objects-links/>
- **文档摘要**：本页是 TypeScript v1 的对象与链接 API 参考，说明导入的对象类型会被转换为 TypeScript v1 接口以便访问和操作 Foundry 中的对象。重点介绍了 Properties：每个对象类型的属性会被转换为接口上的字段，字段名使用 Ontology 中定义的 API Name，可用点符号直接访问。TSv2 与 Python 则通过 Ontology SDK 读取对象。

### 55. Functions • Functions on objects • Object sets (TSv1)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/api-object-sets/>
- **文档摘要**：本页是 TypeScript v1 的对象集（Object Set）API 参考，介绍对象集作为单一类型对象的无序集合，可用于过滤、Search Around 关联对象、聚合计算及取回具体对象。说明了通过 Objects.search() 发起搜索、以及过滤/排序/聚合仅对启用 Searchable 渲染提示的属性生效。还对比了对象集相比对象数组作为输入更高效（延迟加载），并指向性能优化与 TSv2 的对应文档。

### 56. Functions • Functions on objects • Attachments

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/attachments/>
- **文档摘要**：本页介绍函数中的 Attachments（附件）概念与用法。说明 TSv1 函数运行环境有严格内存限制，建议仅处理 20MB 以下附件，而 Python 与 TSv2 默认 1GB 且可在 Ontology Manager 调整。附件是作为对象属性存在的文件，可通过 action 上传并持久化。页面还说明附件可作为函数输入、对象属性访问，也可在函数中创建和返回，并列出各语言的导入语法。

### 57. Functions • Functions on objects • Media

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/media/>
- **文档摘要**：本页介绍函数对 Media（媒体）的访问与修改能力，覆盖 TSv2、Python 与 TSv1。TSv2 与 Python 使用 Media 类型读写、上传和转换媒体，支持通过 Ontology edits 与 OSDK 上传，并暴露获取内容、元数据和附着到对象的高级操作；TSv1 提供内置 MediaItem 类型。还说明了媒体可作为函数输入/输出类型（uploadMedia 临时上传、从媒体引用属性取回），以及 live preview 对媒体参数的支持。

### 58. Functions • Models • Functions on models

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/functions-on-models/>
- **文档摘要**：本页介绍在 Ontology 上下文中通过函数调用模型（Functions on models），即用函数封装实时模型部署来添加业务逻辑、集成对象或编排多次模型调用。涵盖两类部署来源（直接模型部署、Modeling Objective 实时部署）、在仓库中导入模型函数、Ontology 或 space 绑定的函数、以及从 Python/TSv2 与 TSv1 调用模型函数的示例代码，并给出性能考量与向 ontology-bound 函数迁移的说明。

### 59. Functions • Models • Function interfaces

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/function-interfaces/>
- **文档摘要**：本页介绍 Function interfaces（函数接口），它定义函数与消费应用之间的契约（输入、输出、错误），让 Foundry 原生功能发现并调用实现该接口的函数。重点说明 AIP Logic 的 Use LLM 块依赖接口（如 ChatCompletion）来接入用户自定义 LLM，并列出 Palantir 提供的接口。故障排查部分给出三种常见错误的成因与解决：输入名不匹配、缺少接口所需参数、类型不可赋值。

### 60. Functions • Models • Language models in TypeScript v2 and Python functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/language-models-python-tsv2/>
- **文档摘要**：本页讲解在 TypeScript v2 与 Python 函数中使用 Palantir 提供的 Language Model。前置条件为启用 AIP 并拥有 AIP builder 权限。步骤包括：在 Resource imports 的 Platform SDK 标签页导入语言模型（每个模型需唯一 alias），然后编写函数通过代理端点（如 OpenAI chat completion proxy endpoint）调用模型，并提示需用 Libraries 面板安装第三方库（如 openai）。

### 61. Functions • Models • Language models in TypeScript v1 functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/language-models/>
- **文档摘要**：本页讲解 TypeScript v1 函数中使用 Palantir 提供的 Language Model（LLM）。涵盖前置条件、通过 Model Imports 面板导入模型、编写使用模型的函数（模型提供强类型方法如 createChatCompletion、createChatVisionCompletion、createChatCompletionStreamed，并用 @Uses 装饰器声明静态分析未捕获的调用）。还介绍了 Embeddings、从旧版模型升级，以及性能考量。

### 62. Functions • Models • Legacy language models in functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/language-models-legacy/>
- **文档摘要**：本页记录 Functions 中旧版（Legacy）语言模型的用法，面向 TSv1（更早的 @foundry/models-api 导入方式）。涵盖前置条件、导入模型、编写函数（如用 GPT_4o 的 createChatCompletion 做情感分析）、Embeddings 与性能考量，并提示可通过升级指南迁移到支持视觉与流式等能力的新版 TSv1 模型，TSv2/Python 则改用代理端点加模型 alias 的流程。

### 63. Functions • Aliases • Custom aliases

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/custom-aliases/>
- **文档摘要**：本页介绍 Custom aliases（自定义别名），即存储字符串值的具名引用，可用于配置参数、功能开关或环境相关设置，从而将函数逻辑与具体配置解耦、实现跨环境可移植。说明在 TSv2 或 Python 仓库的 Platform SDK 标签页中定义别名、编辑别名（就地改值、改键或删除），以及在代码中通过 alias 工具按 key 引用别名的用法。

### 64. Functions • Aliases • Model aliases

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/model-aliases/>
- **文档摘要**：本页介绍 Model aliases（模型别名），即指向 Language Model 的具名引用，便于在代码中按名引用模型。说明在 TSv2 或 Python 仓库的 Platform SDK 标签页中定义模型别名，以及在函数中通过 alias 工具按名引用——别名会解析为模型 RID，可传给模型客户端使用。完整用法参见 TSv2/Python 语言模型文档。

### 65. Functions • Aliases • Source aliases

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/source-aliases/>
- **文档摘要**：本页介绍 Source aliases（数据源别名），即指向 Data Connection sources 的具名、可移植引用，用 alias key 代替具体源标识以解耦函数逻辑、提升跨环境可移植性。步骤包括导入源（Ontology SDK 标签页）、为源添加别名、编辑别名，以及在代码中通过别名 key 配合 sources 配置与 getSource/get_source 使用。

### 66. Functions • Unit testing (TSv1) • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-getting-started/>
- **文档摘要**：本页是 TypeScript v1 函数单元测试的入门指南，介绍如何搭建基于 Jest 的单元测试。说明默认仓库已含位于 __tests__ 目录的测试用例，给出测试 addOne 函数的示例，并说明通过右上角 Test 按钮运行全部测试、或行号旁播放按钮运行单个测试。下一步指向创建 stub 对象、验证 Ontology edits、stub 对象搜索与聚合等高级主题。

### 67. Functions • Unit testing (TSv1) • Create stub objects

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-stub-objects/>
- **文档摘要**：本页讲解在单元测试中创建 stub（桩）对象，使用 Objects.create() 像普通函数一样创建并定义 mock 对象，以便在测试中使用。给出用 create().exampleDataAirport() 创建机场对象、设置属性并断言函数返回值的示例，使用 Jest 的 expect(...).toEqual(...) 语法验证结果。

### 68. Functions • Unit testing (TSv1) • Verify Ontology edits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-ontology-edits/>
- **文档摘要**：本页介绍用 verifyOntologyEditFunction() API（来自 @foundry/functions-testing-lib）验证函数执行的 Ontology 编辑。列出每类编辑的单数与复数验证方法（对象创建、属性修改、删除、链接增删），并说明 .hasNoMoreEdits() 必须置于验证链末尾。还涵盖异步编辑、多次验证，以及验证新建对象上的编辑等示例。

### 69. Functions • Unit testing (TSv1) • Stub object searches and aggregations

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-object-searches/>
- **文档摘要**：本页介绍在单元测试中为对象集搜索与聚合创建 stub（预设返回值），以控制代码调用的响应。说明使用 whenObjectSet（来自 @foundry/functions-testing-lib）对对象集过滤、聚合（如 .thenReturn(55)）以及特定对象搜索设定桩值，并可对多个不同对象集搜索重载桩，便于隔离测试业务逻辑。

### 70. Functions • Unit testing (TSv1) • Mock dates, timestamps, and UUIDs

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-dates-timestamps/>
- **文档摘要**：本页介绍在单元测试中用 jest.spyOn() 注入 mock，以固定非确定性函数（日期、时间戳、UUID）的输出。TSv1 使用 @foundry/functions-utils 的 Uuid 工具，TSv2 则直接 mock 标准 uuid 库；并分别说明对 Uuid.random()、Timestamp.now()、LocalDate.now() 的 mock 写法与高级用法（通过构造器注入 supplier）。

### 71. Functions • Unit testing (TSv1) • Mock users and groups

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-users-groups/>
- **文档摘要**：本页介绍在单元测试中 mock 用户与用户组。说明用 createUser（来自 @foundry/functions-testing-lib）创建部分 mock 用户（除 id、username 外属性可选）和 createGroup 创建部分 mock 组（除 id 外可选），并给出测试按 id 查询用户与组的示例代码，用于验证依赖 Users/Group API 的函数行为。

### 72. Functions • Unit testing (TSv1) • Debug

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/functions/unit-test-debugging/>
- **文档摘要**：本页介绍在 Code Repositories 中使用 debugger 工具调试单元测试：设置断点以在测试运行时暂停执行，进而检查变量、理解函数与库的行为。页面指向更详细的函数调试文档，涵盖设置断点、运行 debugger 与检查变量的方法。
