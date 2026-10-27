# 模型接入与模型管理

> **分类说明**：支持的 LLM 清单、兼容 API、BYOM 自建模型、REST / Compute Module 后端、自托管与代理联邦层、Model Catalog

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-models` |
| 文档数量 | 11 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. Supported LLMs

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/supported-llms/>
- **文档摘要**：本页列出 AIP 支持的 LLM 与 text embedding 模型，含 xAI、OpenAI、Anthropic、Meta 等厂商模型。可用性受 enrollment 与地理限制影响，管理员在 Control Panel 管理模型家族，也支持 bring your own model。

### 2. LLM-provider compatible APIs

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/llm-provider-compatible-apis/>
- **文档摘要**：Foundry 为 Anthropic、OpenAI、xAI、Google 等提供 LLM 代理端点，请求格式与原生 API 一致，可复用官方 SDK，并获得速率限制、数据治理与用量追踪。端点用 Bearer token 认证，需启用 AIP 并具备 builder 权限。

### 3. Bring your own model • Bring your own model to AIP

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/bring-your-own-model/>
- **文档摘要**：BYOM（registered model）让客户把自有 LLM 或模型账号作为一等模型接入 AIP。平台支持 REST API 源与 compute module 自托管等多种来源，2026 年 3 月的新版本针对主流厂商接口优化，支持原生 tool calling 与 reasoning，并内置速率限制、用量观测与权限控制，官方建议仅在合规受限或自有微调模型时使用。

### 4. Bring your own model • Models backed by external sources (REST API)

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/rest-api-backed-models/>
- **文档摘要**：介绍如何用 Data Connection 的 REST API 源作为模型后端，把外部托管的模型接入 AIP，适用于暴露标准厂商接口的模型。操作分两步：先新建 REST API 源并配置地址、认证与导出开关；再在 Control Panel 注册模型，按端点声明 reasoning、结构化输出与 tool calling 等能力，这些能力决定模型能支撑哪些功能。

### 5. Bring your own model • Models backed by compute modules

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/compute-module-backed-models/>
- **文档摘要**：允许在 compute module 中托管 LLM 并注册为 AIP 一等模型，注册后即出现在各应用的模型选择器中。适合在自有 GPU 上自托管开源或定制模型以满足数据主权、离线部署与成本控制，或构建代理层。要求容器暴露受支持的厂商 API 格式、设置 application.port、最小副本不少于 1。

### 6. Bring your own model • Self-host models with AIP

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/self-host-models/>
- **文档摘要**：说明如何用 compute module 后端自托管模型，在自有基础设施运行开源或定制 LLM 并作为一等模型使用。适用数据主权、离线部署、自有 GPU 控成本与提前试用新模型。步骤为构建推理服务镜像、设置端口标签并发布到 Artifacts、创建最小副本为一的模块、在 Control Panel 注册能力后在应用中选用。

### 7. Bring your own model • Build a proxy or federation layer with AIP

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/build-a-proxy-or-federation-layer/>
- **文档摘要**：代理或联邦层位于 AIP 与外部模型厂商之间，由 compute module 接收请求、施加自定义逻辑后转发，从而控制哪些数据离开环境及请求路由。用例含 PII 脱敏、prompt 注入、API 格式转换与多厂商联邦。除通用前置外还需经 Data Connection 源配置出网，并以 secrets 附上密钥供容器读取。

### 8. Bring your own model • Register an LLM using function interfaces [Legacy]

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/chat-completion-function-interface-quickstart/>
- **文档摘要**：本页是注册自有 LLM 的旧版教程，该方式已于 2026 年 3 月被 registered models 取代，且仅支持 TypeScript v1。教程演示为模型接口创建 REST 源与 webhook，在代码仓库引入 webhook 与对话补全接口，编写调用外部模型的函数并发布，供 AIP Logic 或 Pipeline Builder 使用，使用前须联系官方支持。

### 9. Bring your own model • Use registered LLM [Legacy]

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/use-registered-llm/>
- **文档摘要**：说明如何使用经旧版函数接口注册的 LLM，该方式已由 registered models 取代，仅适用 TypeScript v1。在 AIP Logic 的 Use LLM 板与 Pipeline Builder 的 Use LLM 转换中，从模型下拉的注册选项卡中选择函数。若后者不支持 webhook，可改用 REST 源出网，并在函数上声明错误类型以正确传递限流错误。

### 10. AIP Model Catalog • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/model-catalog/overview/>
- **文档摘要**：Model Catalog 是 Foundry 的 AIP 应用，用于发现与选择 Palantir 模型，可在 Control Panel 启用。含首页、实体页与对比页，可按生命周期（experimental、stable）筛选，并可从 Marketplace 取模板、在 sandbox 试跑。当前仅收录 LLM。

### 11. AIP Model Catalog • Model deprecation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/model-catalog/model-deprecation/>
- **文档摘要**：供应商弃用模型时，Palantir 通过 Upgrade Assistant 定位依赖资源并通知用户，需在截止日前切到替代模型；逾期进入 brownout，调用返回 LanguageModelNotAvailable。替换路径见 Pipeline Builder、Workshop、Quiver 与代码调用等。
