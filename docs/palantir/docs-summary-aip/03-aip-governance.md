# 安全、治理与算力管控

> **分类说明**：AI 伦理治理、AIP 安全、算力用量与可观测性、功能启用开关、Markings 数据访问控制、LLM 容量与速率限制

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-governance` |
| 文档数量 | 8 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AI ethics and governance

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/ethics-governance/>
- **文档摘要**：文档说明 Palantir 把负责任 AI 视为技术构建基础而非事后合规，伦理考量贯穿数据基础、处理管道、界面到人工决策的整个生命周期。内容按公平、可解释、可靠、可追溯、协作、可问责、以人为本等八个主题展开，每个主题列出对应平台能力与工作流，如 Sensitive Data Scanner 识别偏差来源。

### 2. AIP security and privacy

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/aip-security/>
- **文档摘要**：本页以 FAQ 说明 AIP 安全与隐私：AIP 沿用 Foundry 的全部安全措施，提供访问控制、加密与审计，保留 AI 操作的问责与历史血缘；对接第三方托管模型时优先使用美英欧区域端点。页面还回答服务商是否存储客户数据、是否用其再训练模型、能否访问 prompt 内容及合同承诺。

### 3. Compute usage with AIP

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/aip-compute-usage/>
- **文档摘要**：本页解释 AIP 计算用量：LLM 以文本输入输出，文本量按 token 计量（约 4 字符，各厂商定义不同），再按厂商价格折算为 compute-seconds。AIP Assist、AIP Logic、Pipeline Builder 及直接调用 Language Model Service 都消耗 token。

### 4. Ontology and AIP observability

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/aip-observability/>
- **文档摘要**：Ontology and AIP observability 是 Workflow Lineage 的一组能力，可查看 AIP、Ontology 工作流执行情况，涵盖 metrics、执行历史、distributed tracing、logging 与日志搜索。它属于平台可观测性策略，用于诊断 AI 工作流运行表现。

### 5. Administration • Enable AIP features

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/enable-aip-features/>
- **文档摘要**：本页面向 enrollment 管理员，说明在 Control Panel > AIP settings 开启 AIP：新 enrollment 默认启用，老 enrollment 需手动开启。权限分两层：AIP 与核心 assistant 功能，以及为自定义工作流授予的 AIP capabilities。

### 6. Administration • Control LLM data access with Markings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/control-llm-data-access-with-markings/>
- **文档摘要**：本指南说明如何限定 AIP 语言模型请求的 mandatory Markings，避免模型收到持有受限 Markings 的 Foundry 会话，策略覆盖交互与自动化 workflow。Data access settings 可选任意数据或白名单，白名单按请求 token 校验，Marking 未列入即拒绝。

### 7. Administration • LLM capacity management

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/llm-capacity-management/>
- **文档摘要**：本页说明 AIP 的 LLM 容量管理：LLM 容量是行业稀缺资源，厂商按账户设限，统一以 TPM 和 RPM 计量。容量分三层：enrollment 级 rate limits 设总上限，project/user rate limits 分别控制项目份额与个人流量，默认 medium tier 可升级。

### 8. Administration • LLM enrollment rate limits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/aip/llm-enrollment-rate-limits/>
- **文档摘要**：本页用表格列出各模型与后端的默认 TPM 和 RPM 限额，分别给出 per-user 限额与 Small、Medium、Large、XLarge 四档 enrollment tier 数值，并区分商业环境与 USG 各 impact level。同一 enrollment 的多个后端限额累加，仅限单区域时可能更低。
