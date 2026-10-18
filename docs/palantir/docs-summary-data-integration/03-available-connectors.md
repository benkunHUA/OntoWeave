# 可用连接器（Available Connectors）

> **分类说明**：Foundry 支持的各外部系统连接器（数据库、对象存储、SaaS 应用、消息系统等）的配置步骤、功能与限制说明

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `available-connectors` |
| 文档数量 | 213 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Available connectors • Act! CRM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/act!-crm/>
- **文档摘要**：本页介绍 Act! CRM 连接器（Palantir 提供的驱动，Beta 阶段），用于将 Act! CRM 数据接入 Foundry。页面列出其支持的能力（探索、批量同步、增量、表导出，均为 Beta），以及 ActDatabase、ActEdition、User、Password、URL 等必填/推荐配置项与出网（networking）要求。

### 2. Available connectors • Act-On

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/act-on/>
- **文档摘要**：本页介绍 Act-On 连接器（Palantir 提供的驱动，Beta 阶段），用于将 Act-On 营销自动化数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置项包括 AuthScheme、InitiateOAuth、OAuthClientId、OAuthClientSecret 等，并需配置出网策略。

### 3. Available connectors • ActiveCampaign

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/activecampaign/>
- **文档摘要**：本页介绍 ActiveCampaign 连接器（Palantir 提供的驱动，已正式可用），用于将该 CRM 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置需提供 APIKey 与 Base URL，并开放相应出网（egress）策略。

### 4. Available connectors • Acumatica

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/acumatica/>
- **文档摘要**：本页介绍 Acumatica 连接器（Palantir 提供的驱动，已正式可用），用于将 Acumatica ERP 数据接入 Foundry。支持探索、批量同步、增量与表导出；可配置 AuthScheme（Basic/OAuth 等）、Schema（Contract-Based REST/OData/ODataV4）、URL、Company 等属性。

### 5. Available connectors • Adobe Analytics

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/adobe-analytics/>
- **文档摘要**：本页介绍 Adobe Analytics 连接器（Palantir 提供的驱动，Beta 阶段），用于将该分析平台数据接入 Foundry。支持探索、批量同步、增量与 OAuth 2.0 认证；配置含 AuthScheme、GlobalCompanyId、InitiateOAuth、OAuthClientId/Secret 等。

### 6. Available connectors • Adobe Commerce

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/adobe-commerce/>
- **文档摘要**：本页介绍 Adobe Commerce 连接器（Palantir 提供的驱动，已正式可用），用于将该电商平台数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme、URL 与 AccessToken 等属性。

### 7. Available connectors • ADP

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/adp/>
- **文档摘要**：本页介绍 ADP 连接器（Palantir 提供的驱动，已正式可用），用于将 ADP 人力资源数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；需配置 SSLClientCert、TestConnectionEndpoint、UseUAT 等。

### 8. Available connectors • Agent-level filesystem

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/filesystem/>
- **文档摘要**：本页介绍代理主机级文件系统（filesystem）源类型，可将 agent 磁盘（含挂载的 NFS/NAS）上的文件同步进 Foundry。支持探索、批量导入、增量与文件导出；需配置 rootDirectory，并可设定 fileMustNotChangeDuration 等参数。

### 9. Available connectors • Airtable

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/airtable/>
- **文档摘要**：本页介绍 Airtable 连接器（Palantir 提供的驱动，已正式可用），用于将该协作数据库数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 DisplayValue、AuthScheme、BaseId、BaseName 等。

### 10. Available connectors • AlloyDB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/alloydb/>
- **文档摘要**：本页介绍 AlloyDB 连接器（Palantir 提供的驱动，Beta 阶段），用于将 Google AlloyDB 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme、Server、User、Password、UseSSL、Database、Port 等。

### 11. Available connectors • Amazon DynamoDB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/amazon-dynamodb/>
- **文档摘要**：本页介绍 Amazon DynamoDB 连接器（Palantir 提供的驱动，已正式可用），用于将 AWS DynamoDB 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme、AWSAccessKey、AWSRegion、AWSSecretKey 等。

### 12. Available connectors • Amazon Kinesis

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/amazon-kinesis/>
- **文档摘要**：本页介绍 Amazon Kinesis 连接器，可将 Kinesis 流数据实时读入 Foundry 流（支持流式同步与导出）。页面说明数据模型（partition_key、data 等字段）、性能限制、消息顺序，以及连接、认证、STS Role、出网与同步/导出配置。

### 13. Available connectors • Amazon Marketplace

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/amazon-marketplace/>
- **文档摘要**：本页介绍 Amazon Marketplace 连接器（Palantir 提供的驱动，Beta 阶段），用于将卖家/供应商数据接入 Foundry。支持探索、批量同步、增量与 OAuth 2.0 认证；配置含 IncludeReports、Marketplace、OAuthClientId 等。

### 14. Available connectors • Amazon S3

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/amazon-s3/>
- **文档摘要**：本页介绍 Amazon S3 连接器，支持在 S3 与 Foundry 之间读写与同步数据（含批量导入、增量、媒体集、虚拟表与文件导出）。页面说明连接详情、读取/导出所需权限、S3 代理、STS Role、Cloud Identity 配置及虚拟表用法。

### 15. Available connectors • Apache CouchDB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/apache-couchdb/>
- **文档摘要**：本页介绍 Apache CouchDB 连接器（Palantir 提供的驱动，已正式可用），用于将 CouchDB 文档数据库数据接入 Foundry。支持探索、批量同步与增量；配置含 AuthScheme、URL、User、Password 等，并需配置出网策略。

### 16. Available connectors • Apache HBase

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/apache-hbase/>
- **文档摘要**：本页介绍 Apache HBase 连接器（Palantir 提供的驱动，Beta 阶段），用于将 HBase 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 Server、AuthScheme（None/Basic/Negotiate-Kerberos）、Port 等。

### 17. Available connectors • Apache Hive

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/apache-hive/>
- **文档摘要**：本页介绍 Apache Hive 连接器（Palantir 提供的驱动，Beta 阶段），用于将 Hive 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme（Anonymous/Plain/LDAP/NoSasl/Kerberos）、Server、UseSSL、Port 等。

### 18. Available connectors • Apache Phoenix

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/apache-phoenix/>
- **文档摘要**：本页介绍 Apache Phoenix 连接器（Palantir 提供的驱动，Beta 阶段），用于将 Phoenix 数据接入 Foundry。支持探索、批量同步与增量；配置含 AuthScheme、URL、LoadBalanceStrategy、User、Password 等。

### 19. Available connectors • Asana

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/asana/>
- **文档摘要**：本页介绍 Asana 连接器（基于 CData JDBC 驱动），可将 Asana 项目管理数据导入 Foundry 数据集。页面说明数据模型、个人/服务访问令牌认证、出网、日志设置及增量同步、列选择、行过滤等同步配置。

### 20. Available connectors • Authorize.Net

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/authorize-net/>
- **文档摘要**：本页介绍 Authorize.Net 连接器（Palantir 提供的驱动，Beta 阶段），用于将支付网关数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 LoginID、TransactionKey 与 UseSandbox 等。

### 21. Available connectors • Avalara

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/avalara/>
- **文档摘要**：本页介绍 Avalara 连接器（Palantir 提供的驱动，Beta 阶段），用于将税务合规（AvaTax）数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme（Basic/LicenseKey）、User、Password 等。

### 22. Available connectors • AWS Redshift

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/aws-redshift/>
- **文档摘要**：本页介绍 AWS Redshift 连接器，支持在 Redshift 数据库与 Foundry 数据集之间读写数据（含批量同步、增量与表导出）。页面说明数据模型、性能限制、连接端点/端口等配置、Cloud Identity 与表导出的配置选项。

### 23. Available connectors • Azure Active Directory

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/azure-active-directory/>
- **文档摘要**：本页介绍 Azure Active Directory 连接器（Palantir 提供的驱动，已正式可用），用于将 AAD 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 DefaultGroups、DefaultUser、AuthScheme、AzureTenant 等。

### 24. Available connectors • Azure Cosmos DB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/azure-cosmos-db/>
- **文档摘要**：本页介绍 Azure Cosmos DB 连接器（Palantir 提供的驱动，已正式可用），用于将该多模型数据库数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 AccountEndpoint、AuthScheme、AccountKey 等。

### 25. Available connectors • Azure Data Catalog

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/azure-data-catalog/>
- **文档摘要**：本页介绍 Azure Data Catalog 连接器（Palantir 提供的驱动，Beta 阶段），用于将目录数据接入 Foundry。支持探索、批量同步、增量与 OAuth 2.0 认证；配置含 AuthScheme、AzureTenant、CatalogName 等。

### 26. Available connectors • Azure DevOps

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/azure-devops/>
- **文档摘要**：本页介绍 Azure DevOps 连接器（Palantir 提供的驱动，已正式可用），用于将 DevOps 数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 AzureDevOpsEdition、Organization、AuthScheme 等。

### 27. Available connectors • Azure Synapse

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/azure-synapse/>
- **文档摘要**：本页介绍 Azure Synapse 连接器（Palantir 提供的驱动，已正式可用），用于将 Synapse 数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 AuthScheme、Database、Encrypt、Server 等。

### 28. Available connectors • Azure Table Storage

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/azure-table-storage/>
- **文档摘要**：本页介绍 Azure Table Storage 连接器（Palantir 提供的驱动，已正式可用），用于将表存储数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 Account、UseSSL、AccessKey、AuthScheme 等。

### 29. Available connectors • Basecamp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/basecamp/>
- **文档摘要**：本页介绍 Basecamp 连接器（Palantir 提供的驱动，Beta 阶段），用于将项目管理数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 AccountId、Schema、OAuthClientId/Secret 等。

### 30. Available connectors • BigCommerce

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/bigcommerce/>
- **文档摘要**：本页介绍 BigCommerce 连接器（Palantir 提供的驱动，Beta 阶段），用于将该电商平台数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 StoreId、AuthScheme、IncludeCustomFields 等。

### 31. Available connectors • BigQuery

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/bigquery/>
- **文档摘要**：本页介绍 Google BigQuery 连接器，支持在 BigQuery 表与 Foundry 数据集之间读写与同步（含批量导入、增量、虚拟表、计算下推与导出任务）。页面说明所需 IAM 角色、认证方式、连接详情、同步/导出配置及代码用法。

### 32. Available connectors • Blackbaud Raisers Edge NXT

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/blackbaud-raisers-edge-nxt/>
- **文档摘要**：本页介绍 Blackbaud Raisers Edge NXT 连接器（Palantir 提供的驱动，Beta 阶段），用于将该筹款 CRM 数据接入 Foundry。支持探索、批量同步、增量与 OAuth 2.0 认证；配置含 SubscriptionKey、OAuthClientId/Secret 等。

### 33. Available connectors • Bugzilla

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/bugzilla/>
- **文档摘要**：本页介绍 Bugzilla 连接器（Palantir 提供的驱动，Beta 阶段），用于将缺陷跟踪数据接入 Foundry。支持探索、批量同步与增量；配置需提供 APIKey 与 Bugzilla 服务器 URL，并开放相应出网策略。

### 34. Available connectors • Bullhorn CRM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/bullhorn-crm/>
- **文档摘要**：本页介绍 Bullhorn CRM 连接器（Palantir 提供的驱动，Beta 阶段），用于将该招聘 CRM 数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 DataCenterCode、IncludeCustomFields 等。

### 35. Available connectors • Cassandra

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/cassandra/>
- **文档摘要**：本页介绍 Cassandra 连接器（Palantir 提供的驱动，Beta 阶段），用于将 Cassandra 数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme、Database、Password、Port、Server、User、UseSSL 等。

### 36. Available connectors • Certinia

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/certinia/>
- **文档摘要**：本页介绍 Certinia 连接器（Palantir 提供的驱动，Beta 阶段），用于将该（原 FinancialForce）ERP/CRM 数据接入 Foundry。支持探索、批量同步、增量、OAuth 2.0 认证与表导出；配置含 AuthScheme、APIVersion、LoginURL 等。

### 37. Available connectors • Cloudant

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/cloudant/>
- **文档摘要**：本页介绍 Cloudant 连接器（Palantir 提供的驱动，Beta 阶段），用于将该 IBM 文档数据库数据接入 Foundry。支持探索、批量同步、增量与 OAuth 2.0 认证；配置含 AuthScheme、URL，并需开放相应出网策略。

### 38. Available connectors • CockroachDB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/cockroachdb/>
- **文档摘要**：本页介绍 CockroachDB 连接器（Palantir 提供的驱动，Beta 阶段），用于将 CockroachDB 分布式数据库数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 Database、Password、Port、Server、User、UseSSL 等。

### 39. Available connectors • Confluence

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/confluence/>
- **文档摘要**：本页介绍 Confluence 连接器（Palantir 提供的驱动，已正式可用），用于将 Atlassian 文档/wiki 数据接入 Foundry。支持探索、批量同步、增量与 OAuth 2.0 认证；配置含 AuthScheme、Timezone、URL、APIToken 等。

### 40. Available connectors • Couchbase

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/couchbase/>
- **文档摘要**：本页介绍 Couchbase 连接器（Palantir 提供的驱动，Beta 阶段），用于将该 NoSQL 数据库数据接入 Foundry。支持探索、批量同步、增量与表导出；配置含 AuthScheme、ConnectionMode、CouchbaseService、Server、UseSSL 等。

### 41. Available connectors • Databricks

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/databricks/>
- **文档摘要**：本页介绍 Databricks 连接器，可基于 Databricks 中的数据、计算与模型构建能力（含批量导入、增量、虚拟表、计算下推与外部模型）。页面说明连接详情（Hostname、HTTP Path）、认证、SSL、虚拟表配置与代码用法。

### 42. Available connectors • Db2

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/db2/>
- **文档摘要**：本页介绍 Db2 连接器，支持在 Db2 数据库与 Foundry 之间读写同步（含批量同步、增量、变更数据捕获 CDC 与表导出）。页面说明通过 JDBC URL 与 JDBC 属性进行配置，以及 SQL 查询与表导出设置。

### 43. Available connectors • Directory

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/directory/>
- **文档摘要**：本页介绍 Directory 连接器（已废弃 sunset，仅兼容旧版 agent worker），用于直接摄取运行 Data Connection agent 主机上的文件。支持探索、批量同步与增量；建议优先使用 SFTP、SMB、FTP 或外部 transform 替代。

### 44. Available connectors • DocuSign

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/docusign/>
- **文档摘要**：本页介绍 DocuSign 连接器，它是 Palantir 提供的用于对接 DocuSign 电子签名平台的驱动。页面列出其支持能力（探索、批量同步、增量同步与 OAuth 2.0 认证等），并给出连接所需的配置属性、网络访问与授权设置。

### 45. Available connectors • Domino

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/domino/>
- **文档摘要**：本页介绍 Domino 连接器（Beta），是 Palantir 提供的用于连接 Domino 数据科学平台的驱动。文档列出支持的探索、批量同步、增量同步与 OAuth 2.0 认证能力，以及 DatabaseScope、TableTypes、URL 等配置项与网络要求。

### 46. Available connectors • eBay

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/ebay/>
- **文档摘要**：本页介绍 eBay 连接器（Beta），是 Palantir 提供的用于对接 eBay 电商平台的驱动。文档说明其支持探索、批量同步、增量同步、OAuth 2.0 认证及表导出等能力，并提供 AuthScheme、RuName 等配置属性与网络设置。

### 47. Available connectors • eBay Analytics

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/ebay-analytics/>
- **文档摘要**：本页介绍 eBay Analytics 连接器（Beta），是 Palantir 提供的用于对接 eBay 数据分析的驱动。页面列出支持的探索、批量同步、增量同步与 OAuth 2.0 认证能力，并给出 AuthScheme、MarketplaceId 等配置项与网络要求。

### 48. Available connectors • EnterpriseDB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/enterprisedb/>
- **文档摘要**：本页介绍 EnterpriseDB 连接器（Beta），是 Palantir 提供的用于连接 EnterpriseDB 数据库的驱动。文档列出探索、批量同步、增量同步与表导出能力，并提供 Server、User、Password、UseSSL、Database、Port 等配置属性与网络访问要求。

### 49. Available connectors • Epicor Kinetic

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/epicor-kinetic/>
- **文档摘要**：本页介绍 Epicor Kinetic 连接器，是 Palantir 提供的用于对接 Epicor Kinetic ERP 系统的驱动。页面列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出等能力，并给出 ERPInstance、Service、URL、ApiVersion 等配置项与网络设置。

### 50. Available connectors • Exact Online

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/exact-online/>
- **文档摘要**：本页介绍 Exact Online 连接器（Beta），是 Palantir 提供的用于对接 Exact Online 云 ERP 的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 Region、Division 等配置属性、区域映射与网络要求。

### 51. Available connectors • Facebook

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/facebook/>
- **文档摘要**：本页介绍 Facebook 连接器（Beta），是 Palantir 提供的用于对接 Facebook 社交平台的驱动。页面列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并给出 AuthenticateAsPage、OAuthClientId 等配置属性与网络设置。

### 52. Available connectors • Facebook Ads

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/facebook-ads/>
- **文档摘要**：本页介绍 Facebook Ads 连接器，是 Palantir 提供的用于对接 Facebook 广告平台的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，并提供 AccountId 等配置项与网络访问要求。

### 53. Available connectors • Foundry

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/foundry/>
- **文档摘要**：本页介绍 Foundry 连接器，用于在一个 Foundry 实例与另一个实例之间共享数据，以源实例和目标实例协同工作。文档涵盖支持能力、连接方式（直连或 agent）、认证、批量/流式/增量同步、虚拟表、调用 Foundry API 及故障排查等内容。

### 54. Available connectors • FreshBooks

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/freshbooks/>
- **文档摘要**：本页介绍 FreshBooks 连接器（Beta），是 Palantir 提供的用于对接 FreshBooks 云会计平台的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 AccountId、OAuthClientId 等配置属性与网络设置。

### 55. Available connectors • Freshdesk

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/freshdesk/>
- **文档摘要**：本页介绍 Freshdesk 连接器（Beta），是 Palantir 提供的用于对接 Freshdesk 客服平台的驱动。文档列出探索、批量同步、增量同步与表导出能力，并给出 APIKey、Domain 等配置属性（基于 API key 认证）与网络访问要求。

### 56. Available connectors • FTP/FTPS

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/ftps/>
- **文档摘要**：本页介绍 FTP/FTPS 连接器，用于将本地或云端 FTP/FTPS 服务器上的文件夹与 Foundry 数据集之间同步文件。文档涵盖数据模型、性能限制、连接设置（认证、网络、证书、主动/被动模式）、配置选项、数据同步与常见故障排查。

### 57. Available connectors • Generic connector

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/generic/>
- **文档摘要**：本页介绍通用连接器（Generic connector），可用于在代码中代表任意外部系统的连接，并以「Use in code」方式实现批量/流式同步、导出等。文档说明其设置、网络、配置选项，以及在 Python 外部转换、计算模块、TypeScript/Python 函数中的用法与转换方式。

### 58. Available connectors • GitHub

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/github/>
- **文档摘要**：本页介绍 GitHub 连接器，是 Palantir 提供的用于对接 GitHub 代码平台的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，说明以关系表形式呈现仓库、Issue、PR 等数据，并介绍在代码中读取或更新 GitHub Issue 的用法。

### 59. Available connectors • Gmail

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/gmail/>
- **文档摘要**：本页介绍 Gmail 连接器，是 Palantir 提供的用于对接 Gmail 邮件系统的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 Schema（REST/IMAP）、OAuthClientId 等配置属性与网络设置。

### 60. Available connectors • Google Campaign Manager

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-campaign-manager/>
- **文档摘要**：本页介绍 Google Campaign Manager 连接器（Beta），是 Palantir 提供的用于对接 Google Campaign Manager 360 的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，以及 ProfileID、OAuthClientId 等配置项与网络要求。

### 61. Available connectors • Google Cloud Storage

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-cloud-storage/>
- **文档摘要**：本页介绍 Google Cloud Storage 连接器，用于在 Foundry 数据集与 GCS 存储桶之间同步文件，并支持虚拟表与 Iceberg。文档涵盖数据模型、性能限制、连接设置（认证、网络）、配置选项、数据同步、虚拟表（Delta/Iceberg/Parquet）及导出到 GCS。

### 62. Available connectors • Google Contacts

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-contacts/>
- **文档摘要**：本页介绍 Google Contacts 连接器（Beta），是 Palantir 提供的用于对接 Google 通讯录的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 AuthScheme（OAuthJWT）、OAuthClientId 等配置属性与网络设置。

### 63. Available connectors • Google Data Catalog

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-data-catalog/>
- **文档摘要**：本页介绍 Google Data Catalog 连接器，是 Palantir 提供的用于对接 Google Cloud 数据目录的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，并提供 OrganizationId、ProjectId 等配置属性与网络要求。

### 64. Available connectors • Google Directory

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-directory/>
- **文档摘要**：本页介绍 Google Directory 连接器（Beta），是 Palantir 提供的用于对接 Google Directory（用户/组目录）的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，并提供 AuthScheme、OAuthClientId 等配置属性与网络设置。

### 65. Available connectors • Google Drive

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-drive/>
- **文档摘要**：本页介绍 Google Drive 连接器，是 Palantir 提供的用于对接 Google 云端硬盘的驱动。文档说明如何选取操作（探索文件元数据、批量同步提取文件、按需读写 blob 文件），列出支持能力与 OAuth 2.0 认证，并给出基于 Drive API 的代码用法。

### 66. Available connectors • Google Pub/Sub

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/pubsub/>
- **文档摘要**：本页介绍 Google Pub/Sub 连接器，用于实时将 Pub/Sub 主题中的消息读取到 Foundry 数据流，也支持反向导出。文档涵盖数据模型、性能限制、连接设置、订阅与导出配置，以及流式同步和流式导出的使用方式。

### 67. Available connectors • Google Search

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-search/>
- **文档摘要**：本页介绍 Google Search 连接器（Beta），是 Palantir 提供的用于对接 Google 自定义搜索的驱动。文档列出探索、批量同步与增量同步能力，并提供 APIKey、CustomSearchId 等配置属性与网络访问要求（无需 OAuth）。

### 68. Available connectors • Google Sheets

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-sheets/>
- **文档摘要**：本页介绍 Google Sheets 连接器，是 Palantir 提供的用于对接 Google 表格的驱动。文档列出探索、批量同步与增量同步能力，说明可通过源浏览器与批量同步进行批量读取，并介绍在 Python 管道或 Functions 中以服务账号/委托 OAuth 读写单元格范围的代码用法。

### 69. Available connectors • Google Spanner

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/google-spanner/>
- **文档摘要**：本页介绍 Google Spanner 连接器（Beta），是 Palantir 提供的用于对接 Google Spanner 云数据库的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 DatabaseDialect、InstanceId 等配置属性与网络设置。

### 70. Available connectors • GraphQL

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/graphql/>
- **文档摘要**：本页介绍 GraphQL 连接器，是 Palantir 提供的用于对接任意 GraphQL 服务的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 URL、AuthScheme 等配置属性与网络要求。

### 71. Available connectors • Greenplum

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/greenplum/>
- **文档摘要**：本页介绍 Greenplum 连接器，是 Palantir 提供的用于对接 Greenplum 数据库的驱动。文档列出探索、批量同步、增量同步与表导出能力，并提供 Server、User、Password、UseSSL、Database、Port 等配置属性与网络访问要求。

### 72. Available connectors • HDFS

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/hdfs/>
- **文档摘要**：本页介绍 HDFS（Hadoop 分布式文件系统）连接器，用于将 HDFS 中的数据读取并同步到 Foundry 数据集。文档涵盖数据模型、性能限制、连接设置（网络、证书）、配置选项（含高级选项）、数据同步以及遗留导出任务等内容。

### 73. Available connectors • Highrise

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/highrise/>
- **文档摘要**：本页介绍 Highrise 连接器（Beta），是 Palantir 提供的用于对接 Highrise CRM 的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 AccountId、APIToken、OAuthClientId 等配置属性与网络设置。

### 74. Available connectors • HubSpot

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/hubspot/>
- **文档摘要**：本页介绍 HubSpot 连接器，用于在 Foundry 与 HubSpot 之间导入数据并创建、修改、删除 HubSpot 记录。文档涵盖连接设置（以 private app 访问令牌认证、网络、连接详情）以及从 HubSpot 迁移相关的说明。

### 75. Available connectors • IBM Cloud Data Engine

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/ibm-cloud-data-engine/>
- **文档摘要**：本页介绍 IBM Cloud Data Engine 连接器（Beta），用于对接 IBM Cloud 数据分析引擎的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，并提供 ApiKey、SqlQueryCRN 等配置项与区域映射。

### 76. Available connectors • IBM Cloud Object Storage

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/ibm-cloud-object-storage/>
- **文档摘要**：本页介绍 IBM Cloud Object Storage 连接器（Beta），是 Palantir 提供的用于对接 IBM Cloud 对象存储的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，并提供 ApiKey、CloudObjectStorageCRN 等配置属性与网络要求。

### 77. Available connectors • Instagram

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/instagram/>
- **文档摘要**：本页介绍 Instagram 连接器（Beta），是 Palantir 提供的用于对接 Instagram 社交平台的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 BusinessAccountId、OAuthClientId 等配置属性与网络设置。

### 78. Available connectors • JDBC (custom)

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/custom-jdbc-sources/>
- **文档摘要**：本页介绍自定义 JDBC 连接器，用于通过 JDBC 在 Foundry 与大多数关系型数据库/数据仓库之间读写和同步数据。文档涵盖源设置、JDBC 驱动与属性配置、SQL 查询与增量同步、性能优化、Iceberg 同步、数据导出以及健康检查和遗留导出任务。

### 79. Available connectors • Jira Service Management

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/jira-service-management/>
- **文档摘要**：本页介绍 Jira Service Management 连接器（Beta），用于对接 Jira Service Management 的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 AuthScheme（APIToken）、URL 等配置属性与网络设置。

### 80. Available connectors • Kafka

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/kafka/>
- **文档摘要**：本页介绍 Kafka 连接器，用于实时将 Kafka 队列中的消息读取到 Foundry 数据流，并支持反向导出。文档涵盖数据模型、性能限制、连接设置（Bootstrap servers、认证、SSL、证书）、Schema Registry 集成、数据导出以及通过代码读写 Kafka 的用法。

### 81. Available connectors • Kintone

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/kintone/>
- **文档摘要**：本页介绍 Kintone 连接器，是 Palantir 提供的用于对接 Kintone 低代码平台的驱动。文档列出探索、批量同步、增量同步与表导出能力，并提供 AuthScheme、Schema、URL、User、Password 等配置属性与网络访问要求。

### 82. Available connectors • Lakehouse Iceberg REST catalog

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/lakehouse-iceberg-rest-catalog/>
- **文档摘要**：本页介绍 Lakehouse Iceberg REST catalog 连接器，通过 GCS 连接器将 GCP Lakehouse 配置为 Iceberg catalog。文档说明如何在该 GCS 源上启用 Lakehouse Catalog、注册虚拟表（手动/自动），及其支持的能力与限制。

### 83. Available connectors • LDAP

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/ldap/>
- **文档摘要**：本页介绍 LDAP 连接器，是 Palantir 提供的用于对接 LDAP 目录服务的驱动。文档列出探索、批量同步、增量同步与表导出能力，并提供 Server、UseSSL、BaseDN、User、Password 等配置属性与网络访问要求。

### 84. Available connectors • LinkedIn

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/linkedin/>
- **文档摘要**：本页介绍 LinkedIn 连接器（Beta），是 Palantir 提供的用于对接 LinkedIn 社交平台的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 CompanyId、OAuthClientId 等配置属性与网络设置。

### 85. Available connectors • LinkedIn Marketing Solutions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/linkedin-marketing-solutions/>
- **文档摘要**：本页介绍 LinkedIn Marketing Solutions 连接器（Beta），是 Palantir 提供的用于对接 LinkedIn 营销解决方案的驱动。文档列出探索、批量同步、增量同步与 OAuth 2.0 认证能力，并提供 InitiateOAuth、OAuthClientId 等配置属性与网络要求。

### 86. Available connectors • Mailchimp

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/mailchimp/>
- **文档摘要**：本页介绍 Mailchimp 连接器（Beta），是 Palantir 提供的用于对接 Mailchimp 邮件营销平台的驱动。文档列出探索、批量同步、增量同步、OAuth 2.0 认证与表导出能力，并提供 AuthScheme（APIKey/OAuth）、APIKey、Datacenter 等配置属性与网络设置。

### 87. Available connectors • Marketo

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/marketo/>
- **文档摘要**：本页介绍 Marketo 连接器（Palantir 提供的驱动），用于将数据从 Marketo 营销自动化平台同步到 Foundry。页面列出其能力（探索、批量同步、增量、OAuth 2.0、表导出，均为 Beta），并给出连接 URL、OAuth 客户端等配置项，含网络与 OAuth 2.0 认证说明。

### 88. Available connectors • MarkLogic

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/marklogic/>
- **文档摘要**：本页介绍 MarkLogic 连接器（Palantir 提供的驱动），用于连接 MarkLogic NoSQL 数据库并同步数据到 Foundry。能力处于 Beta 阶段，配置需指定服务器地址、用户、密码及是否启用 SSL，并提供 Networking 网络访问要求。

### 89. Available connectors • Microsoft Access

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-access/>
- **文档摘要**：本页介绍 Microsoft Access 连接器（Palantir 提供的驱动，已正式可用），用于读取本地 Access 数据库文件并同步到 Foundry。配置需指定 ConnectionType 与数据库文件路径，支持表导出。

### 90. Available connectors • Microsoft Ads

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-ads/>
- **文档摘要**：本页介绍 Microsoft Ads 连接器（Palantir 提供的驱动，Beta），用于从微软广告平台同步数据到 Foundry。配置需提供 CustomerId、DeveloperToken，并支持 OAuth 2.0 认证，含网络访问要求。

### 91. Available connectors • Microsoft Bing

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-bing/>
- **文档摘要**：本页介绍 Microsoft Bing 连接器（Palantir 提供的驱动，Beta），用于连接 Bing 并同步数据到 Foundry。配置需提供 APIKey，可选 APIVersion 与 Domain，页面含 Networking 网络要求。

### 92. Available connectors • Microsoft Dataverse

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-dataverse/>
- **文档摘要**：本页介绍 Microsoft Dataverse 连接器（Palantir 提供的驱动，已正式可用），用于从 Dataverse 同步数据到 Foundry。配置需指定 AuthScheme、OrganizationURL 与 Schema，支持 OAuth 2.0 与表导出。

### 93. Available connectors • Microsoft Dynamics 365

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-dynamics-365/>
- **文档摘要**：本页介绍 Microsoft Dynamics 365 连接器（已正式可用），用于从 Dynamics 365 ERP/CRM 同步数据到 Foundry。配置需指定 AuthScheme、Edition 与 OrganizationURL，支持 OAuth 2.0 认证与表导出。

### 94. Available connectors • Microsoft Dynamics 365 Business Central

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-dynamics-365-business-central/>
- **文档摘要**：本页介绍 Microsoft Dynamics 365 Business Central 连接器（已正式可用），用于从 Business Central ERP 同步数据到 Foundry。配置需指定 AuthScheme 与 OrganizationURL，支持 OAuth 2.0 认证，含网络要求。

### 95. Available connectors • Microsoft Dynamics CRM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-dynamics-crm/>
- **文档摘要**：本页介绍 Microsoft Dynamics CRM 连接器（已正式可用），用于从 Dynamics CRM 同步数据到 Foundry。配置需指定 CRMVersion 与 URL，支持 OAuth 2.0 认证，含网络访问要求。

### 96. Available connectors • Microsoft Dynamics GP

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-dynamics-gp/>
- **文档摘要**：本页介绍 Microsoft Dynamics GP 连接器（Palantir 提供的驱动，Beta），用于从 Dynamics GP ERP 同步数据到 Foundry。配置需提供 CompanyId 与 URL，支持表导出，含网络要求。

### 97. Available connectors • Microsoft Dynamics NAV

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-dynamics-nav/>
- **文档摘要**：本页介绍 Microsoft Dynamics NAV 连接器（Palantir 提供的驱动，Beta），用于从 Dynamics NAV ERP 同步数据到 Foundry。配置需提供 ServerInstance 与 URL，支持表导出，含网络要求。

### 98. Available connectors • Microsoft Excel

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-excel/>
- **文档摘要**：本页介绍 Microsoft Excel 连接器（Palantir 提供的驱动，Beta），从文件存储读取工作簿并将其工作表、单元格区域暴露为关系表。支持通过 OAuth 2.0 认证、批量同步与表导出，并可在代码中读写工作簿。

### 99. Available connectors • Microsoft Excel Online

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-excel-online/>
- **文档摘要**：本页介绍 Microsoft Excel Online 连接器（Palantir 提供的驱动，已正式可用），用于从 OneDrive/SharePoint 上的在线 Excel 同步数据到 Foundry。配置可选 AuthScheme、AzureTenant 等，支持 OAuth 2.0 与表导出。

### 100. Available connectors • Microsoft Exchange

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-exchange/>
- **文档摘要**：本页介绍 Microsoft Exchange 连接器（已正式可用），用于从 Exchange 邮件系统同步数据到 Foundry。配置需指定 DefaultGroups、DefaultUser 与 Server，支持 OAuth 2.0 认证与表导出。

### 101. Available connectors • Microsoft Office 365

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-office-365/>
- **文档摘要**：本页介绍 Microsoft Office 365 连接器（已正式可用），用于从 Office 365 同步数据到 Foundry。配置需指定 DefaultGroups、DefaultUser 与 MessageContentType，支持 OAuth 2.0 认证与表导出。

### 102. Available connectors • Microsoft OneDrive

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-onedrive/>
- **文档摘要**：本页介绍 Microsoft OneDrive 连接器（Palantir 提供的驱动，Beta），用于从 OneDrive 提取文件到 Foundry。配置支持 AuthScheme、AzureTenant 与 OAuth 2.0 认证，含网络访问要求。

### 103. Available connectors • Microsoft OneNote

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-onenote/>
- **文档摘要**：本页介绍 Microsoft OneNote 连接器（已正式可用），用于从 OneNote 同步数据到 Foundry。配置支持 AuthScheme、AzureTenant 与 OAuth 2.0 认证，含网络访问要求。

### 104. Available connectors • Microsoft Planner

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-planner/>
- **文档摘要**：本页介绍 Microsoft Planner 连接器（已正式可用），用于从 Planner 任务管理同步数据到 Foundry。配置支持 AuthScheme、AzureTenant 与 OAuth 2.0 认证，支持表导出。

### 105. Available connectors • Microsoft Power BI® XMLA

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-power-bi-xmla/>
- **文档摘要**：本页介绍 Microsoft Power BI® XMLA 连接器（已正式可用），用于通过 XMLA 连接 Power BI 数据集并同步数据到 Foundry。配置支持 AuthScheme、AzureTenant 与 OAuth 2.0 认证，含网络要求。

### 106. Available connectors • Microsoft Project

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-project/>
- **文档摘要**：本页介绍 Microsoft Project 连接器（已正式可用），用于从 Project 项目计划同步数据到 Foundry。配置需指定 URL，支持 OAuth 2.0 客户端认证，含网络访问要求。

### 107. Available connectors • Microsoft SharePoint

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-sharepoint/>
- **文档摘要**：本页介绍 Microsoft SharePoint 连接器（Palantir 提供的驱动，Beta），用于从 SharePoint 同步数据到 Foundry。配置需指定 URL，支持 OAuth 2.0 认证与表导出，含网络访问要求。

### 108. Available connectors • Microsoft SharePoint Excel

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-sharepoint-excel/>
- **文档摘要**：本页介绍 Microsoft SharePoint Excel 连接器（Palantir 提供的驱动，Beta），用于读取 SharePoint 上的 Excel 文件并同步到 Foundry。配置需指定 AuthScheme、File、SharePointVersion 与 URL。

### 109. Available connectors • Microsoft SQL Server

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-sql-server/>
- **文档摘要**：本页介绍 Microsoft SQL Server 连接器，用于在 SQL Server 数据库与 Foundry 之间读写与同步数据。支持探索、批量同步、增量、Iceberg、变更数据捕获（CDC）与表导出，提供多种认证方式与网络、连接详情配置，并含故障排查说明。

### 110. Available connectors • Microsoft SQL Server Analysis Services

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-sql-server-analysis-services/>
- **文档摘要**：本页介绍 Microsoft SQL Server Analysis Services (SSAS) 连接器（已正式可用），用于从 SSAS 多维/表格模型同步数据到 Foundry。配置需指定 URL，可选 AuthScheme、用户与密码，含网络访问要求。

### 111. Available connectors • Microsoft Teams

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/microsoft-teams/>
- **文档摘要**：本页介绍 Microsoft Teams 连接器（已正式可用），通过 Microsoft Graph API 读取组织的 Teams，将团队、频道、聊天与消息暴露为关系表。支持 OAuth 2.0 认证、批量同步、增量与表导出，并可在代码中读写。

### 112. Available connectors • Monday

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/monday/>
- **文档摘要**：本页介绍 Monday 连接器（已正式可用），用于从 Monday.com 工作管理平台同步数据到 Foundry。配置需指定 AuthScheme，支持 OAuth 2.0 认证与表导出，含网络访问要求。

### 113. Available connectors • MYOB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/myob/>
- **文档摘要**：本页介绍 MYOB 连接器（Palantir 提供的驱动，Beta），用于从 MYOB 会计软件同步数据到 Foundry。配置需提供 Instance，支持 OAuth 2.0 认证，含网络访问要求。

### 114. Available connectors • NoSQL stores

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/nosql-stores/>
- **文档摘要**：本页概述如何在 Foundry 中同步各类 NoSQL 数据库（如 DynamoDB、MongoDB、Cassandra、Redis 等）。根据数据库类型，可选择专用连接器、REST API 源、通用 JDBC 连接器或联系 Palantir 支持。

### 115. Available connectors • OData

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/odata/>
- **文档摘要**：本页介绍 OData 连接器（已正式可用），用于通过 OData 协议从支持的服务同步数据到 Foundry。配置需指定 AuthScheme 与 URL，支持 OAuth 2.0 认证与表导出，含网络访问要求。

### 116. Available connectors • Odoo

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/odoo/>
- **文档摘要**：本页介绍 Odoo 连接器（已正式可用），用于从 Odoo ERP 同步数据到 Foundry。配置需提供 APIToken、URL 与 User，支持表导出，含网络访问要求。

### 117. Available connectors • OneLake and Azure Blob Filesystem (ABFS)

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/onelake-and-azure-blob-filesystem/>
- **文档摘要**：本页介绍 ABFS 连接器，用于连接 OneLake、Azure Data Lake Storage Gen2 等 Azure 产品，在 Foundry 与 Azure 间读写文件。支持探索、批量导入、增量、媒体集、虚拟表与文件导出，并提供多种认证方式与性能限制说明。

### 118. Available connectors • Oracle Database

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle/>
- **文档摘要**：本页介绍 Oracle Database 连接器，用于在 Oracle 数据库与 Foundry 之间读写与同步数据。基于 Oracle JDBC 21.x 驱动，支持探索、批量同步、增量、Iceberg、CDC 与表导出，提供认证、网络、连接详情与故障排查说明。

### 119. Available connectors • Oracle Eloqua

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle-eloqua/>
- **文档摘要**：本页介绍 Oracle Eloqua 连接器（Palantir 提供的驱动，Beta），用于从 Eloqua 营销云同步数据到 Foundry。配置需指定 AuthScheme 与 Schema，支持 OAuth 2.0 认证与表导出，含网络要求。

### 120. Available connectors • Oracle Fusion Cloud Financials

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle-fusion-cloud-financials/>
- **文档摘要**：本页介绍 Oracle Fusion Cloud Financials 连接器（已正式可用），用于从 Oracle Fusion 财务云同步数据到 Foundry。配置需提供密码、URL 与用户，含网络访问要求。

### 121. Available connectors • Oracle Fusion Cloud HCM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle-fusion-cloud-hcm/>
- **文档摘要**：本页介绍 Oracle Fusion Cloud HCM 连接器（已正式可用），用于从 Oracle Fusion 人力云同步数据到 Foundry。配置需提供密码、URL 与用户，支持表导出，含网络访问要求。

### 122. Available connectors • Oracle Fusion Cloud SCM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle-fusion-cloud-scm/>
- **文档摘要**：本页介绍 Oracle Fusion Cloud SCM 连接器（已正式可用），用于从 Oracle Fusion 供应链云同步数据到 Foundry。配置需提供密码、URL 与用户，支持表导出，含网络访问要求。

### 123. Available connectors • Oracle NetSuite • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/netsuite-overview/>
- **文档摘要**：本页概述 Foundry 连接 Oracle NetSuite 的多种方式：SuiteAnalytics（大批量读取推荐，需 JDBC 驱动与用户名/密码）、SuiteTalk (JDBC)（SOAP 服务，支持广泛实体，仅 TBA）、SuiteQL (JDBC)（读取性能优，仅 TBA）。

### 124. Available connectors • Oracle NetSuite • NetSuite SuiteAnalytics

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/netsuite-suiteanalytics/>
- **文档摘要**：本页介绍通过 SuiteAnalytics Connect 连接 Oracle NetSuite 的连接器，用于将 NetSuite ERP 数据同步到 Foundry。支持探索、批量同步与增量，使用用户名/密码认证，并详细说明 NetSuite 用户角色与权限配置。

### 125. Available connectors • Oracle NetSuite • NetSuite SuiteQL (JDBC)

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/netsuite-suiteql-jdbc/>
- **文档摘要**：本页介绍 NetSuite SuiteQL (JDBC) 连接器，通过 SuiteQL 框架将 NetSuite ERP 数据同步到 Foundry。支持探索、批量同步与增量，使用基于令牌的认证（TBA），并提供用户角色权限配置与连接详情说明。

### 126. Available connectors • Oracle NetSuite • NetSuite SuiteTalk (JDBC)

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/netsuite-suitetalk-jdbc/>
- **文档摘要**：本页介绍 NetSuite SuiteTalk (JDBC) 连接器，通过 SuiteTalk 框架将 NetSuite ERP 数据同步到 Foundry。支持探索、批量同步与增量，使用基于令牌的认证（TBA），并提供用户角色权限配置说明。

### 127. Available connectors • Oracle Sales

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle-sales/>
- **文档摘要**：本页介绍 Oracle Sales 连接器（已正式可用），用于从 Oracle Sales 云同步数据到 Foundry。配置需提供 HostURL、密码与用户名，含网络访问要求。

### 128. Available connectors • Oracle Service Cloud

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/oracle-service-cloud/>
- **文档摘要**：本页介绍 Oracle Service Cloud 连接器（Palantir 提供的驱动，Beta），用于从 Oracle 服务云同步数据到 Foundry。配置需提供密码、URL 与用户，支持表导出及自定义字段，含网络要求。

### 129. Available connectors • Outreach

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/outreach/>
- **文档摘要**：本页介绍 Outreach 连接器（Palantir 提供的驱动，Beta），用于从 Outreach 销售互动平台同步数据到 Foundry。支持 OAuth 2.0 认证并可选自定义字段，含网络访问要求。

### 130. Available connectors • Paylocity

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/paylocity/>
- **文档摘要**：本页介绍 Paylocity 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Paylocity 人力资源系统读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并给出该连接的配置参数、网络设置与表导出说明。

### 131. Available connectors • PayPal

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/paypal/>
- **文档摘要**：本页介绍 PayPal 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 PayPal 读取数据，支持探索、批量同步、增量与 OAuth 2.0 认证；页面列出 Schema、OAuthClientId、OAuthClientSecret 等必需/推荐配置项及网络设置。

### 132. Available connectors • PI System

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/pi/>
- **文档摘要**：本页介绍 AVEVA PI System（原 OSIsoft PI）连接器，用于将 Foundry 对接该工业时序数据平台以读取数据，目前为 Beta。页面涵盖接口（PI Web API）、安装、认证、网络、数据模型，以及批量与流式（Streaming）同步功能。

### 133. Available connectors • Pinterest

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/pinterest/>
- **文档摘要**：本页介绍 Pinterest 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Pinterest 读取数据，支持探索、批量同步、增量与 OAuth 2.0 认证，并给出该连接的配置参数与网络设置说明。

### 134. Available connectors • Pipedrive

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/pipedrive/>
- **文档摘要**：本页介绍 Pipedrive 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Pipedrive CRM 读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出 CompanyDomain 等配置项及网络设置。

### 135. Available connectors • PostgreSQL

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/postgresql/>
- **文档摘要**：本页介绍 PostgreSQL 连接器，使用官方 PostgreSQL 驱动（42 大版本），用于在 PostgreSQL 数据库与 Foundry 之间读取和同步数据。页面涵盖安装、认证、网络（含 Amazon RDS）、连接详情、变更数据捕获（CDC，Beta）以及在代码中使用源读写数据。

### 136. Available connectors • Presto

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/presto/>
- **文档摘要**：本页介绍 Presto 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Presto 查询引擎读取数据，支持探索、批量同步、增量与表导出，并列出 AuthScheme 等配置参数及网络设置说明。

### 137. Available connectors • Quickbase

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/quickbase/>
- **文档摘要**：本页介绍 Quickbase 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Quickbase 低代码平台读取数据，支持探索、批量同步、增量与表导出，并给出 AuthScheme 等配置项及网络设置。

### 138. Available connectors • QuickBooks Desktop

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/quickbooks-desktop/>
- **文档摘要**：本页介绍 QuickBooks Desktop 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接本地 QuickBooks Desktop 读取数据，支持探索、批量同步、增量与表导出，并给出该连接的配置参数与网络设置说明。

### 139. Available connectors • QuickBooks Online

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/quickbooks-online/>
- **文档摘要**：本页介绍 QuickBooks Online 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 QuickBooks Online 云端会计系统读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出相关配置项。

### 140. Available connectors • QuickBooks POS

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/quickbooks-pos/>
- **文档摘要**：本页介绍 QuickBooks POS 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 QuickBooks POS 销售点系统读取数据，支持探索、批量同步、增量与表导出，并给出该连接的配置参数与网络设置。

### 141. Available connectors • Raisers Edge NXT

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/raisers-edge-nxt/>
- **文档摘要**：本页介绍 Raisers Edge NXT 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Blackbaud Raisers Edge NXT 筹款平台读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出配置项。

### 142. Available connectors • Reckon

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/reckon/>
- **文档摘要**：本页介绍 Reckon 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Reckon 会计软件读取数据，支持探索、批量同步、增量与表导出，并给出该连接的配置参数与网络设置说明。

### 143. Available connectors • Reckon Accounts Hosted

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/reckon-accounts-hosted/>
- **文档摘要**：本页介绍 Reckon Accounts Hosted 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Reckon Accounts Hosted 云端会计读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出配置项。

### 144. Available connectors • Redis

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/redis/>
- **文档摘要**：本页介绍 Redis 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Redis 内存数据库读取数据，支持探索、批量同步、增量与表导出，并列出 AuthScheme 等认证方式等配置参数及网络设置。

### 145. Available connectors • REST API plugin (legacy)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/magritte-rest-v2/>
- **文档摘要**：本页记录旧版 REST API 插件（magritte-rest-v2）用法，仅供历史参考，已不再开发且不建议使用。页面说明其架构、自定义源创建、同步、提取器与表达式等细节；新集成应改用 REST API 源类型。

### 146. Available connectors • REST APIs

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/rest-apis/>
- **文档摘要**：本页介绍 Foundry 对接外部 REST API 的多种集成方式。涵盖 REST API 源（通过 Actions/Webhooks 从应用发起交互式 HTTP 请求）、代码库中的外部变换，以及 Foundry REST API，适用于同步、导出与交互式调用场景。

### 147. Available connectors • RSS

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/rss/>
- **文档摘要**：本页介绍 RSS 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 RSS feed 读取数据，支持探索、批量同步与增量，并列出 URI 等配置参数说明，可按需指定订阅源地址。

### 148. Available connectors • Sage 200

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sage-200/>
- **文档摘要**：本页介绍 Sage 200 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Sage 200 企业管理软件读取数据，支持探索、批量同步、增量与 OAuth 2.0 认证，并给出该连接的配置参数与网络设置。

### 149. Available connectors • Sage 300

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sage-300/>
- **文档摘要**：本页介绍 Sage 300 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Sage 300 企业管理软件读取数据，支持探索、批量同步与增量，并给出该连接的配置参数与网络设置说明。

### 150. Available connectors • Sage 50 UK

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sage-50-uk/>
- **文档摘要**：本页介绍 Sage 50 UK 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接英国版 Sage 50 会计软件读取数据，支持探索、批量同步、增量与表导出，并给出该连接的配置参数与网络设置。

### 151. Available connectors • Sage Business Cloud Accounting

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sage-business-cloud-accounting/>
- **文档摘要**：本页介绍 Sage Business Cloud Accounting 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Sage 云端会计读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出配置项。

### 152. Available connectors • Salesforce

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/salesforce/>
- **文档摘要**：本页介绍 Salesforce 连接器，用于在 Salesforce 与 Foundry 数据集之间同步数据（当前版本）。页面涵盖数据模型（标准/自定义对象映射）、性能限制、安装、认证（JWT、用户名密码）、网络、配置选项与增量/过滤同步，并说明旧版迁移。

### 153. Available connectors • Salesforce Marketing Cloud

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/salesforce-marketing-cloud/>
- **文档摘要**：本页介绍 Salesforce Marketing Cloud 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Marketing Cloud 读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出相关配置项及网络设置。

### 154. Available connectors • Salesforce Marketing Cloud Account Engagement

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/salesforce-marketing-cloud-account-engagement/>
- **文档摘要**：本页介绍 Salesforce Marketing Cloud Account Engagement（原 Pardot）连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接该营销自动化平台读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出。

### 155. Available connectors • Salesloft

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/salesloft/>
- **文档摘要**：本页介绍 Salesloft 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 Salesloft 销售互动平台读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并给出该连接的配置参数与网络设置。

### 156. Available connectors • SAP • SAP ERP

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-erp/>
- **文档摘要**：本页介绍 SAP ERP 连接器，用于将 Foundry 对接 SAP ECC 与 S/4 HANA（本地及 Cloud Private）。它可读取应用表、CDS 视图、HANA 信息视图、BW 抽取器、事务码、函数模块与媒体等，并涵盖安装、认证、批量/流式同步与 Webhooks 写回。

### 157. Available connectors • SAP • SAP SLT

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-slt/>
- **文档摘要**：本页介绍 SAP SLT 连接器，通过 SAP Landscape Transformation（SLT）复制服务器将源 SAP 系统的数据基于数据库触发器复制到 ODQ，再同步至 Foundry。页面涵盖安装、认证、网络与批量/流式同步参数。

### 158. Available connectors • SAP • SAP (Custom source)

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-custom-source/>
- **文档摘要**：本页介绍 SAP（Custom source）连接器，使用手动配置的 magritte-sap-source 类型对接 SAP 系统，基于自定义 YAML 设置。它面向既有集成，新集成建议改用 SAP ERP 或 SAP SLT；页面涵盖源配置、网络、批量/流式同步与 Webhooks。

### 159. Available connectors • SAP applications • SAP Ariba Procurement

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-ariba-procurement/>
- **文档摘要**：本页介绍 SAP Ariba Procurement 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAP Ariba 采购云读取数据，支持探索、批量同步、增量等能力，并给出该连接的配置参数与网络设置说明。

### 160. Available connectors • SAP applications • SAP Business One

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-business-one/>
- **文档摘要**：本页介绍 SAP Business One 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接面向中小企业的 SAP Business One ERP 读取数据，支持探索、批量同步、增量与表导出，并给出该连接的配置参数与网络设置。

### 161. Available connectors • SAP applications • SAP BusinessObjects BI

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-businessobjects-bi/>
- **文档摘要**：本页介绍 SAP BusinessObjects BI 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAP BusinessObjects 商务智能平台读取数据，支持探索、批量同步、增量等能力，并给出该连接的配置参数与网络设置。

### 162. Available connectors • SAP applications • SAP ByDesign

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-bydesign/>
- **文档摘要**：本页介绍 SAP ByDesign 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAP Business ByDesign 云端 ERP 读取数据，支持探索、批量同步、增量与表导出，并给出该连接的配置参数与网络设置。

### 163. Available connectors • SAP applications • SAP Cloud for Customer

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-cloud-for-customer/>
- **文档摘要**：本页介绍 SAP Cloud for Customer 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 SAP C4C 客户关系/云服务读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出相关配置项。

### 164. Available connectors • SAP applications • SAP Concur

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-concur/>
- **文档摘要**：本页介绍 SAP Concur 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAP Concur 差旅与费用管理平台读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并给出配置参数与网络设置。

### 165. Available connectors • SAP applications • SAP Fieldglass

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-fieldglass/>
- **文档摘要**：本页介绍 SAP Fieldglass 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAP Fieldglass 灵活用工管理云读取数据，支持探索、批量同步、增量与 OAuth 2.0 认证，并给出该连接的配置参数与网络设置。

### 166. Available connectors • SAP applications • SAP HANA XSA

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-hana-xsa/>
- **文档摘要**：本页介绍 SAP HANA XSA 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAP HANA Extended Application Services（XSA）读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出配置项。

### 167. Available connectors • SAP applications • SAP SuccessFactors

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sap-successfactors/>
- **文档摘要**：本页介绍 SAP SuccessFactors 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 SAP SuccessFactors 人力资本管理云读取数据，支持探索、批量同步、增量、OAuth 2.0 认证与表导出，并列出相关配置项。

### 168. Available connectors • SAS Data Sets

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sas-data-sets/>
- **文档摘要**：本页介绍 SAS Data Sets 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAS 数据集文件读取数据，支持探索、批量同步、增量与 OAuth 2.0 认证，并给出该连接的配置参数说明。

### 169. Available connectors • SAS Xpt

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sas-xpt/>
- **文档摘要**：本页介绍 SAS Xpt 连接器（Palantir 提供的驱动，目前为 Beta）。它用于连接 SAS 传输格式（XPT）文件读取数据，支持探索、批量同步、增量与 OAuth 2.0 认证，并给出该连接的配置参数说明。

### 170. Available connectors • SendGrid

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sendgrid/>
- **文档摘要**：本页介绍 SendGrid 连接器（Palantir 提供的驱动，已正式可用）。它用于连接 Twilio SendGrid 邮件服务读取数据，支持探索、批量同步、增量与表导出，并列出 APIKey 等必需配置项及网络设置。

### 171. Available connectors • SFTP

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sftp/>
- **文档摘要**：本页介绍 SFTP 连接器，用于在 SFTP 服务器文件夹与 Foundry 数据集之间同步（导入/导出）文件。页面涵盖数据模型、性能限制、安装、认证（证书/私钥）、网络、配置项、增量同步与故障排查，并说明在代码中读写 SFTP 文件。

### 172. Available connectors • SharePoint Online

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sharepoint-online/>
- **文档摘要**：本页介绍 SharePoint Online 连接器，用于将指定 SharePoint 文档库中的文件导入 Foundry（并支持导出）。页面涵盖数据模型、性能限制、安装、认证（客户端凭据/用户名密码）、网络、配置项与在代码中摄取 SharePoint Lists。

### 173. Available connectors • ShipStation

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/shipstation/>
- **文档摘要**：本页介绍 ShipStation 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于在 Foundry Data Connection 中连接 ShipStation 电商物流系统。支持 Exploration、批量与增量同步，配置需提供 APIKey 与 APISecret，并开通对 ssapi.shipstation.com 的网络出口策略。

### 174. Available connectors • Shopify

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/shopify/>
- **文档摘要**：本页介绍 Shopify 连接器（Palantir 提供的驱动，已正式可用），用于连接 Shopify 电商系统并将数据同步到 Foundry。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 ShopURL 并通过 AccessToken 或 OAuth 2.0 认证。

### 175. Available connectors • SingleStore

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/singlestore/>
- **文档摘要**：本页介绍 SingleStore 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 SingleStore 分布式数据库。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 Server、Database，并选择 Password 或 OAuth 认证方式。

### 176. Available connectors • Slack

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/slack/>
- **文档摘要**：本页介绍 Slack 连接器（Beta 阶段，通过 CData JDBC 驱动接入），用于将 Slack 工作区数据导入 Foundry 数据集。支持批量导入、Exploration 与增量同步；数据以关系表形式建模，可用标准 SQL 查询。认证采用 bearer token（bot user token）。

### 177. Available connectors • Smartsheet

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/smartsheet/>
- **文档摘要**：本页介绍 Smartsheet 连接器（Palantir 提供的驱动，已正式可用），用于连接 Smartsheet 在线表格协作平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置可选择 PersonalAccessToken 或 OAuth 2.0 认证。

### 178. Available connectors • SMB

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/smb/>
- **文档摘要**：本页介绍 SMB（Server Message Block）连接器，用于连接 SMB 共享（如 Windows File Server、Samba）并在文件夹与 Foundry 数据集间同步文件。支持 SMB 2/3 协议，提供 Exploration、批量导入、增量同步与文件导出，文件格式原样保留。

### 179. Available connectors • Snapchat Ads

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/snapchat-ads/>
- **文档摘要**：本页介绍 Snapchat Ads 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Snapchat 广告平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 AccountId 并通过 OAuth 2.0 认证。

### 180. Available connectors • Snowflake

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/snowflake/>
- **文档摘要**：本页介绍 Snowflake 连接器，用于连接 Snowflake 数据仓库并在二者间读写同步数据，还可对接 Foundry 的 Iceberg catalog 实现虚拟表与计算下推。支持 Exploration、批量/增量导入、Iceberg 同步、虚拟表、计算下推与表导出；配置需填写 Account 标识并选择认证方式。

### 181. Available connectors • Spark SQL

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/spark-sql/>
- **文档摘要**：本页介绍 Spark SQL 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Spark SQL（Hive）数据仓库。支持 Exploration、批量/增量同步与表导出，认证支持 Plain、LDAP、NOSASL、Kerberos，配置需指定 Server、Database 与端口。

### 182. Available connectors • Splunk

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/splunk/>
- **文档摘要**：本页介绍 Splunk 连接器（Palantir 提供的驱动，已正式可用），用于连接 Splunk 日志与指标平台。支持 Exploration、批量/增量同步与表导出，认证支持 Basic、AccessToken 或 HTTPEventCollectorToken，配置需指定 Splunk 的 URL。

### 183. Available connectors • Square

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/square/>
- **文档摘要**：本页介绍 Square 连接器（Palantir 提供的驱动，已正式可用），用于连接 Square 支付与商户平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置可通过 APIKey 或 OAuth 2.0 进行认证。

### 184. Available connectors • Streak

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/streak/>
- **文档摘要**：本页介绍 Streak 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Streak CRM（内嵌于 Gmail 的流水线管理工具）。支持 Exploration、批量与增量同步，配置需提供 ApiKey 并访问 streak.com 域名；暂不支持 OAuth 与表导出。

### 185. Available connectors • Stripe

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/stripe/>
- **文档摘要**：本页介绍 Stripe 连接器（Palantir 提供的驱动，已正式可用），用于连接 Stripe 支付平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，认证默认采用 APIKey，亦可配置 OAuth 2.0 访问实时 API Key。

### 186. Available connectors • SugarCRM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sugarcrm/>
- **文档摘要**：本页介绍 SugarCRM 连接器（Palantir 提供的驱动，已正式可用），用于连接 SugarCRM 客户关系管理系统。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定实例 URL 并通过 OAuth 2.0 认证。

### 187. Available connectors • SuiteCRM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/suitecrm/>
- **文档摘要**：本页介绍 SuiteCRM 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 SuiteCRM 开源 CRM 系统。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 Schema 与实例 URL，并选择 OAuth 2.0 认证。

### 188. Available connectors • SurveyMonkey

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/surveymonkey/>
- **文档摘要**：本页介绍 SurveyMonkey 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 SurveyMonkey 在线问卷平台。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，配置需指定数据中心（Datacenter）并通过 AccessToken 或 OAuth 认证；暂不支持表导出。

### 189. Available connectors • SybaseIQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/sybaseiq/>
- **文档摘要**：本页介绍 SybaseIQ 连接器（Palantir 提供的驱动，已正式可用），用于连接 SybaseIQ / SAP SQL Anywhere 数据库。支持 Exploration、批量/增量同步与表导出，认证支持 Password 或 Kerberos，配置需指定 Server、Database 与端口（2638）。

### 190. Available connectors • Tableau CRM Analytics

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/tableau-crm-analytics/>
- **文档摘要**：本页介绍 Tableau CRM Analytics 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Salesforce Tableau CRM Analytics。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，认证采用 OAuthJWT，可连接沙箱账户；暂不支持表导出。

### 191. Available connectors • Tally

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/tally/>
- **文档摘要**：本页介绍 Tally 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Tally ERP 财务系统。支持 Exploration、批量与增量同步，配置需指定 Tally 实例 URL（默认 9000 端口）；暂不支持 OAuth 与表导出。

### 192. Available connectors • TaxJar

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/taxjar/>
- **文档摘要**：本页介绍 TaxJar 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 TaxJar 销售税计算平台。支持 Exploration、批量与增量同步，配置需提供 APIKey 并可选择是否连接沙箱环境；暂不支持 OAuth 与表导出。

### 193. Available connectors • Trello

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/trello/>
- **文档摘要**：本页介绍 Trello 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Trello 看板协作工具。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，认证支持 Token 或 OAuth，配置需提供 APIKey 与 BoardId。

### 194. Available connectors • TSheets

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/tsheets/>
- **文档摘要**：本页介绍 TSheets（QuickBooks Time）连接器（Palantir 提供的驱动，已正式可用），用于连接 TSheets 工时跟踪系统。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置通过 OAuth 2.0 认证并可选包含自定义字段。

### 195. Available connectors • Twilio

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/twilio/>
- **文档摘要**：本页介绍 Twilio 连接器（Palantir 提供的驱动，已正式可用），用于连接 Twilio 通信平台。支持 Exploration、批量/增量同步与表导出，认证采用 Account SID 与 AuthToken，配置需开通对 api.twilio.com 等域名的访问。

### 196. Available connectors • Twitter Ads

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/twitter-ads/>
- **文档摘要**：本页介绍 Twitter Ads 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Twitter 广告平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需提供 OAuthClientId、OAuthClientSecret 与 AccountId 并通过 OAuth 认证。

### 197. Available connectors • Veeva Vault

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/veeva-vault/>
- **文档摘要**：本页介绍 Veeva Vault 连接器（Palantir 提供的驱动，已正式可用），用于连接 Veeva Vault 生命科学内容管理平台。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，认证支持 Basic、AzureAD、AzureADOpenID 与 OKTA。

### 198. Available connectors • Wave Financial

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/wave-financial/>
- **文档摘要**：本页介绍 Wave Financial 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Wave 中小商户会计平台。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，认证可采用 API Token 或 OAuth 2.0。

### 199. Available connectors • Web Feature Service (WFS)

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/wfs/>
- **文档摘要**：本页介绍 WFS（Web Feature Service）连接器，用于连接 WFS 1.1.0 服务器并将地理空间数据拉取至 Foundry 数据集。属于协议源类型，支持直连或代理方式；认证支持 No Auth 与 GEOAxIS，并可配置大小写请求等选项。

### 200. Available connectors • WooCommerce

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/woocommerce/>
- **文档摘要**：本页介绍 WooCommerce 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 WooCommerce 电商站点。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，认证支持 Basic、OAuth 1.0 或 OAuth 2.0，配置需指定实例 URL。

### 201. Available connectors • WordPress

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/wordpress/>
- **文档摘要**：本页介绍 WordPress 连接器（Palantir 提供的驱动，已正式可用），用于连接 WordPress 站点。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，认证支持 Basic（仅 HTTPS）或 OAuth，配置需指定站点 URL。

### 202. Available connectors • xBase

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/xbase/>
- **文档摘要**：本页介绍 xBase 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 xBase/dBase 文件型数据库。支持 Exploration、批量/增量同步与表导出，配置需指定包含 .dbf 文件的文件夹路径；暂不支持 OAuth 认证。

### 203. Available connectors • Xero

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/xero/>
- **文档摘要**：本页介绍 Xero 连接器（Palantir 提供的驱动，已正式可用），用于连接 Xero 云会计平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，认证采用 OAuth 2.0，可访问 ACCOUNTING 等 API schema。

### 204. Available connectors • Xero WorkflowMax

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/xero-workflowmax/>
- **文档摘要**：本页介绍 Xero WorkflowMax 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Xero WorkflowMax 项目管理平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置通过 OAuth 2.0 认证并需指定 TenantId。

### 205. Available connectors • YouTube Analytics

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/youtube-analytics/>
- **文档摘要**：本页介绍 YouTube Analytics 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 YouTube 数据分析 API。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，认证采用 OAuthJWT，可指定 ChannelId 或 ContentOwnerId；暂不支持表导出。

### 206. Available connectors • Zendesk

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zendesk/>
- **文档摘要**：本页介绍 Zendesk 连接器（Palantir 提供的驱动，已正式可用），用于连接 Zendesk 客服支持平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，认证支持 User/Password/APIToken 或 OAuth，配置需指定 Support URL。

### 207. Available connectors • Zoho Books

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zoho-books/>
- **文档摘要**：本页介绍 Zoho Books 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Zoho Books 会计系统。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 Region（TLD）并通过 OAuth 2.0 认证。

### 208. Available connectors • Zoho Creator

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zoho-creator/>
- **文档摘要**：本页介绍 Zoho Creator 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Zoho Creator 低代码应用平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 Region（TLD）并通过 OAuth 2.0 认证。

### 209. Available connectors • Zoho CRM

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zoho-crm/>
- **文档摘要**：本页介绍 Zoho CRM 连接器（Palantir 提供的驱动，已正式可用），用于连接 Zoho CRM 客户关系管理系统。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置需指定 Schema 版本（v4）与 Region 并通过 OAuth 2.0 认证。

### 210. Available connectors • Zoho Inventory

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zoho-inventory/>
- **文档摘要**：本页介绍 Zoho Inventory 连接器（Palantir 提供的驱动，已正式可用），用于连接 Zoho Inventory 库存管理系统。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，配置可包含自定义字段并需指定 OrganizationId；暂不支持表导出。

### 211. Available connectors • Zoho Projects

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zoho-projects/>
- **文档摘要**：本页介绍 Zoho Projects 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Zoho Projects 项目管理平台。支持 Exploration、批量/增量同步、OAuth 2.0 认证与表导出，配置通过 OAuth 2.0 认证并可用 PortalId 限定范围。

### 212. Available connectors • Zuora

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/zuora/>
- **文档摘要**：本页介绍 Zuora 连接器（Palantir 提供的驱动，处于 Beta 阶段），用于连接 Zuora 订阅计费平台。支持 Exploration、批量/增量同步与 OAuth 2.0 认证，配置需提供 OAuthClientId、OAuthClientSecret 并指定 Tenant 类型；暂不支持表导出。

### 213. Available connectors • Other source types

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/available-connectors/other-source-types/>
- **文档摘要**：本页为行业专用数据源参考，概述 Foundry Data Connection 可对接的非常规企业系统，涵盖 IOT/IIOT、地理空间系统、EHR/EMR 与生产力工具等类别，并列举已集成的代表系统（如 OSI PI、PostGIS、ESRI/ArcGIS 等）。
