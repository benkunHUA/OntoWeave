# Private Link 私有连接

> **分类说明**：Private Link 私有连接：通过私有网络连接云数据源的安全配置

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `private-link` |
| 文档数量 | 5 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Private links (VPC connectivity) • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/private-link/overview/>
- **文档摘要**：本页概述 Foundry 的 Private Link（VPC 私有连接）能力。流量可经公网或云服务商私有网络路由，Private Link 提供不经由公网的安全 VPC 端点连接；页面列出 AWS、Azure、GCP 各云商的入站/出站能力可用性及配置方式。

### 2. Private links (VPC connectivity) • Supported private link providers • AWS Private Link

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/private-link/aws-private-link/>
- **文档摘要**：本页说明通过 AWS PrivateLink 连接 Foundry 的配置方法。介绍入站（从客户 VPC 到 Foundry VPC）的设置步骤，包括提交 AWS 账户 ID、创建 VPC Endpoint、提供 Endpoint ID 与区域等，并附带常见错误与跨区连接的 FAQ。

### 3. Private links (VPC connectivity) • Supported private link providers • Azure Private link

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/private-link/azure-private-link/>
- **文档摘要**：本页说明通过 Azure Private Link 连接 Foundry 的配置方法。首先帮助判断需要入站、出站或两者，随后分别给出入站（共享 Azure Subscription ID）与出站（如连接 Azure SQL Database）的设置步骤，并说明 Azure Storage 可走自助网络策略。

### 4. Private links (VPC connectivity) • Supported private link providers • GCP Private Service Connect

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/private-link/gcp-private-service-connect/>
- **文档摘要**：本页说明通过 GCP Private Service Connect 连接 Foundry 的配置方法。流量经 Google 骨干网在客户 VPC 与 Foundry VPC 间传输，支持跨区连接；入站与出站配置均需联系 Palantir 代表协助完成，页面未提供自助文档。

### 5. Private links (VPC connectivity) • Customer-owned domain with Private Link

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/private-link/customer-owned-domain-private-link/>
- **文档摘要**：本页说明在客户自有域名（非 *.palantirfoundry.com）下配置 Private Link 的额外步骤。需配置独立的二级域名供内部 Foundry 容器服务使用、设置 DNS CNAME 指向 VPC Endpoint、签署并返回 CSR 证书请求，使全部流量经私有连接路由。
