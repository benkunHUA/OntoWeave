# 数据连接（Data Connection）

> **分类说明**：数据连接（Data Connection）的配置与管理：连接外部数据源、数据同步（Sync）与增量同步、连接权限、健康检查与连接器选项

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `data-connection` |
| 文档数量 | 53 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Data Connection • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/overview/>
- **文档摘要**：本页介绍 Data Connection 应用，它用于将外部系统及其他 Foundry 实例的数据同步进 Foundry 供数据集成、建模与 Ontology 层使用，并支持通过 webhook 和数据导出实现写回。页面概述了 Foundry 标准化数据连接的三大原则：Robustness（自动重试、小批量拉取与数据健康监控）、Extensibility 与 Ease of use，并说明数据应「原样（as-is）」从最原始来源摄取，不做外部预处理。

### 2. Data Connection • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/core-concepts/>
- **文档摘要**：本页说明 Data Connection 贯穿使用的核心概念。Source 表示到外部系统的单一连接，需配置 worker 与 credentials；Worker 决定 capability 的计算位置（Foundry worker 或遗留的 agent worker）。页面还介绍了 networking/egress policies、Agents，以及各类 Capabilities：batch syncs、Iceberg syncs、streaming syncs、CDC syncs、media syncs、HyperAuto、file/table/streaming exports、webhooks 与 virtual tables，帮助用户理解连接与同步的组成要素。

### 3. Data Connection • Architecture

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/architecture/>
- **文档摘要**：本页通过架构示例说明 Data Connection 的三种连接模式。Foundry worker with direct connection policies（直连）是访问接受 Foundry 入站流量的外部系统（如 REST API、云/SaaS）的推荐默认；Foundry worker with agent proxy policy（代理模式）用于访问私有网络系统，agent 仅作网络隧道；Legacy agent worker 为遗留架构。页面总结了各模式差异，并说明凭据以 AES-256-GCM 加密存储、仅在授权用户触发的容器中解密。

### 4. Data Connection • Connection security

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/connection-security/>
- **文档摘要**：本页列出 Data Connection 建立出站 TLS 连接到外部数据源时支持的加密套件。内容提供 TLS 1.3 与 TLS 1.2 下受支持的 cipher suite 清单（含 IANA 与 OpenSSL 名称，如 TLS_AES_256_GCM_SHA384、ECDHE-RSA-AES256-GCM-SHA384 等），并说明具体可用性取决于底层 Java 版本与环境配置。页面还给出如何验证外部系统是否接受受支持套件的建议，以及 agent 主机漏洞扫描发现的说明。

### 5. Data Connection • Troubleshooting

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/troubleshooting/>
- **文档摘要**：本页提供 Data Connection 的故障排查指引，当 source 行为异常时用于诊断与解决。页面给出起步清单（确认连通性、检查 egress 配置与凭据、证书、OAuth 授权），并介绍内置排查工具：Debug with AI FDE、source terminal、network egress logs、source preview、job tracker。还分主题讲解 connectivity/egress、证书与 TLS（PKIX/SSL 异常）、OAuth 及 capability 特定问题，帮助用户定位连接、认证与数据移动故障。

### 6. Data Connection • Foundry worker vs. agent worker

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/foundry-worker-vs-agent-worker/>
- **文档摘要**：本页对比 Foundry worker 与遗留 agent worker，并说明为何推荐将所有 source 迁移到 Foundry worker。Foundry worker 在容器化、可扩展的托管计算环境中运行 capability，具备可扩展性与隔离、稳定发布节奏、可访问全部新 capability、更低运维负担与更清晰凭据生命周期等优势。页面还回答常见疑问（安全性、性能、网络模型、现有 agent worker 源的处理），并提供切换到 Foundry worker 的前提、迁移步骤与回退方法。

### 7. Data Connection • Initial setup overview

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/initial-setup-overview/>
- **文档摘要**：本页是连接组织数据到 Foundry 的初始设置总览，引导完成首次接入。页面强调初始设置本质上是网络概念，最好由熟悉网络工程与拓扑（如防火墙规则）的人员完成。它按序说明三个组件：配置网络连通性、source 配置、capability 配置，并介绍角色与工作流程、高可用（建议多 agent）及后续步骤，为后续创建 source 与 sync 奠定基础。

### 8. Data Connection • Permissions reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/permissions/>
- **文档摘要**：本页是 Data Connection 的权限参考，说明 agents、sources、syncs 等概念在 Foundry 中均作为 resource 管理，可灵活组织到 Project 并应用 markings 等安全原语。页面逐项列出 Agents、Sources、Syncs、Plugins/JDBC Drivers、Webhooks、代码外部连接等资源的默认权限（Owner/Editor/Viewer）与标记传播规则，并给出 agent、source、插件的权限最佳实践，提醒平台所有者勿过度共享连接资源。

### 9. Data Connection • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/faq/>
- **文档摘要**：本页汇总 Data Connection 的常见问题（FAQ）。涵盖计划构建未运行、SNAPSHOT 事务导致重复行、增量加载运行时间、数据库与 dataset 列类型不一致、TIME 类型丢失亚秒精度、查询状态、schema 不匹配导致 sync 失败、重新添加已删除 sync 的权限错误、Bootvisor 状态 Unknown 及常见 TLS/SSL 问题等，针对每项给出排查步骤与解决建议，帮助用户快速定位典型同步与连接故障。

### 10. Data Connection • Agents • Set up an agent

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/set-up-agent/>
- **文档摘要**：本页是设置 agent 的操作指南。agent 是安装在组织网络内、由 Foundry Data Connection 界面管理的可下载程序，用于建立 agent-proxy egress 策略连接私有 source，或作为遗留 agent worker 读取数据并安全摄取到 Foundry。指南依次讲解：创建 agent host（推荐 Linux VM）、配置网络访问、用防火墙加固主机、设自动重启、在 Project 中保存 agent 资源，以及下载安装 agent，最后给出后续步骤。

### 11. Data Connection • Agents • Agent configuration reference

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/agent-configuration-reference/>
- **文档摘要**：本页是 agent 配置参考，描述在网络内创建、监控与管理 agent 时的各项配置。内容包括 agent 权限（在 Control Panel 中通过角色管理创建）、证书（添加信任 Foundry 及系统的证书、TLS  inspecting 环境处理）、代理配置（Agent Manager、Bootstrapper、Source 三级）、指标与健康监控、自动升级窗口（含交错升级与计划 dataset 同步）、重装 agent 及迁移目录/主机等，供管理员全面配置与运维 agent。

### 12. Data Connection • Agents • Agent worker configuration reference

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/agent-worker/>
- **文档摘要**：本页是 agent worker 配置参考（遗留阶段，仍受完整支持但不再新增开发）。页面说明将 agent 作为 worker 运行 capability 时的配置选项：设置 agent、capability 执行（在 agent 主机直接运行 Java 进程）、网络、内存分配与使用、负载均衡、直连 vs. data proxy 上传策略（Direct 模式已弃用）、自定义 JDBC 驱动、凭据、证书、添加私钥及已知限制，并建议新建或更新工作流时改用 Foundry worker。

### 13. Data Connection • Agents • Agent proxy configuration reference

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/agent-proxy/>
- **文档摘要**：本页是 agent proxy 配置参考，说明在 Foundry worker 连接上通过 agent proxy egress policies 将 agent 用作网络代理时的配置选项。agent proxy 为 Foundry 内服务访问独立网络中不可直连的系统提供网络路径（云上访问非公网系统、私网访问其他网络均需代理）。页面讲解如何将 agent 用作 source 代理、agent proxy 特有安全控制（source 配置强制、Foundry/本地 allowlist、主机防火墙）以及连接池配置。

### 14. Data Connection • Agents • Troubleshooting reference

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/agents-troubleshooting/>
- **文档摘要**：本页是 agent 的故障排查参考，介绍如何配置 agent 日志、常见配置问题与调试方法（Agent Manager 在服务器上称为「bootvisor」）。内容包括证书过期后恢复 agent 连通性（更新主机 truststore 或带当前证书重装），以及离线状态、never reported、Unhealthy、无法下载/管理/创建 agent（PERMISSION_DENIED）等常见问题的排查，并建议先检查 ./var/diagnostic/launch.yml 确认 agent 是否成功连接 Foundry。

### 15. Data Connection • Sources • Set up a source

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/set-up-source/>
- **文档摘要**：本页是设置 source 的操作指南。Foundry source 表示 Foundry 与外部系统（如 Postgres、S3、Linux 文件系统、SAP、REST API）之间的连接。指南依次讲解：配置网络访问（同网直连需 egress 策略，异网需 agent 及两条网络路径）、配置 agent 连通性、配置网络策略、配置凭据，以及在 Data Connection 中创建 source（保存到 Project、选择网络策略、配置 source、添加凭据）。建立后可配置 sync 将具体数据引入 Foundry。

### 16. Data Connection • Sources • Source exploration

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/source-exploration/>
- **文档摘要**：本页介绍 source exploration，即在配置 sync 前通过 Explore 页面预览与探索 source 及其数据。对表类数据，左侧面板搜索并添加表与视图，图表区探索表间关系（外键高亮），右侧面板审核并创建 sync；对文件类数据，左侧选择待同步目录，预览即将同步的文件并可加过滤，右侧审核并创建 sync。该能力帮助用户在数据进入 Foundry 前先了解结构，部分 source 类型支持关系图探索。

### 17. Data Connection • Syncs • Set up a sync

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/set-up-sync/>
- **文档摘要**：本页是设置 batch sync 的操作指南。batch sync 将外部系统数据同步进 Foundry dataset，是最广泛支持的 capability，可用于带 schema 的表数据与无 schema 的原始文件，创建时即生成新 dataset。指南讲解：新建 batch sync、指定输出位置（建议与 connector 同存以便统一授权）、配置同步（含文件与表示例、附加选项、预览输出），以及手动构建或排程触发，最后给出后续步骤。

### 18. Data Connection • Syncs • Set up a streaming sync

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/set-up-streaming-sync/>
- **文档摘要**：本页是设置 streaming sync 的操作指南。streaming sync 与 batch/incremental sync 类似，但持续运行以尽可能低延迟将数据拉入 Foundry，而非周期性执行。教程以 Kafka source 为例，分四部分讲解：定义待同步数据（Explore and create syncs 或 Create sync）、定义 Foundry 中输出位置、配置 streaming sync、运行 sync，并给出后续步骤，帮助从消息源（如 Kafka topic）实时摄取数据。

### 19. Data Connection • Syncs • File-based syncs

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/file-based-syncs/>
- **文档摘要**：本页说明 file-based syncs 的概念性摄取模式与配置。页面列出常见模式：默认 Batch mirror with SNAPSHOT、Incremental mirror with APPEND、Incremental mirror with UPDATE、Trailing window with SNAPSHOT，并标出互相矛盾的配置。还讲解 sync 失败行为，以及文件同步配置项（Filters、Transformers、Completion strategies）与优化方法（如配置增量 APPEND 同步），帮助用户按需求正确设定文件同步的事务类型与过滤策略。

### 20. Data Connection • Syncs • Set up a media set sync

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/media-set-sync/>
- **文档摘要**：本页介绍如何设置 media set sync，将媒体文件经 Data Connection 同步进 Foundry。支持 media syncs 的 source 包括 Amazon S3、OneLake 与 Azure Blob Filesystem（ABFS）等；不支持的文件型 source 可先同步进 dataset 再用 Python transform 转为 media set。页面讲解设置 media set source 与 sync 的步骤，包括创建 virtual media set（直接读取外部源不复制）、选择媒体类型、设定子目录与过滤、配置构建计划，以及支持的媒体 schema。

### 21. Data Connection • Syncs • Optimize JDBC syncs

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/optimize-jdbc-syncs/>
- **文档摘要**：本页提供优化 JDBC syncs 速度与共稳定性的最佳实践。若 sync 已可靠运行则无需调整；新建或运行过慢/不稳定时建议遵循本指南。主要两种方法：先改为 incremental sync（仅摄取新匹配行，通过 APPEND 事务维护状态，需严格递增列），若仍不足再对 SQL 查询并行化（含 WHERE 含 OR 条件时的并行示例）。页面给出配置增量列与初始值等要点，以显著提升大表同步性能。

### 22. Data Connection • Syncs • Troubleshooting reference

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/syncs-troubleshooting/>
- **文档摘要**：本页是 syncs 的故障排查参考，列出常见同步问题及调试步骤。包括 PKIX/SSL 异常（如不支持的 SSL 协议/端口、implicit FTPS 端口 991）、egress proxy 问题（FTP 同步避免负载均衡、仅被动模式；S3 同步遇 403 多为走代理出错）、incremental JDBC 同步问题、间歇性失败或挂起、sync 突然失败、同步了意外数据等，逐一给出根因判断与解决方法，帮助快速恢复同步作业。

### 23. Data Connection • Exports • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/export-overview/>
- **文档摘要**：本页是 Data Connection 导出（Exports）功能的概述。导出支持将 Foundry 中的 dataset 与 stream 写回外部系统，用于数据平台补给、ML 推理结果外发、Ontology 运营数据外写等。页面介绍导出类型（file/streaming/table exports）、导出前数据准备（控制输出文件结构）、设置导出（为 source 启用、新建导出）、排程、导出历史，以及 Exports 与遗留 export tasks 的区别和迁移建议（2025 年 6 月起无新数据导出标记为成功）。

### 24. Data Connection • Exports • Export tasks [Legacy]

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/export-tasks/>
- **文档摘要**：本页是遗留 export tasks 的参考文档（一般不再推荐使用）。export task 允许将 Foundry 数据导出到外部源，配置由 source（连接定义）与 task 配置（导出类型、输入 dataset、参数）两部分组成，由输出 dataset 上的作业触发。页面列出已知限制（不集成 markings/导出控制、性能未优化、无 UI 需 YAML 配置、仅适用于 agent-worker 源）、入门步骤、支持的导出类型及常用配置（incremental exports、path rewriting），供尚未迁移到新导出工作流的用户参考。

### 25. Data Connection • Webhooks • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/webhooks-overview/>
- **文档摘要**：本页概述 Data Connection 的 webhooks 功能，用于连接 Foundry 与外部系统和工作流（出站 webhook，即 Foundry 向外部发起请求）。每个 webhook 关联一个 source 并存储连接凭据，可按 source 类型使用不同 task（如 REST 可灵活配置 HTTP 调用），并支持设定时间、并发与速率限制。页面还指引进一步文档：设置 webhook、参考手册、actions 配置、external functions 调用，以及发布为 function 供 Workshop、OSDK 等使用。

### 26. Data Connection • Webhooks • Set up a webhook

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/webhooks-setup/>
- **文档摘要**：本页是设置 webhook 的教程，指导如何发送 HTTP 请求到外部系统。教程以创建 REST API source 及关联 webhook 为例，包含前提条件（系统不可从 Foundry 网络直连时推荐 Foundry worker + agent proxy；按 IP 寻址用 agent proxy runtime）、创建 source、创建 webhook、配置选项（相对路径、查询参数、headers、body）、测试 webhook 及后续步骤，帮助将 Foundry 应用按钮等事件连接到外部工作流与源系统。

### 27. Data Connection • Webhooks • Set up a webhook for the Microsoft Graph API

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/webhooks-microsoft-graph-api/>
- **文档摘要**：本页逐步说明如何为 Microsoft Graph API 配置 webhook，以从 Foundry 访问 Microsoft Cloud 服务资源。前提需在 login.microsoftonline.com 登录生成访问令牌，并为 login.microsoftonline.com 与 graph.microsoft.com 配置网络 egress 策略。步骤包括：创建 REST API source（将两域名加入 Domains、添加 TenantId/ClientId/ClientSecret 等密钥与 egress 策略），再配置 webhook 执行两次 POST——先登录取短期 token，再以 bearer token 调用 graph.microsoft.com。

### 28. Data Connection • Webhooks • Configuration reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/webhooks-reference/>
- **文档摘要**：本页提供 Data Connection 中 Webhooks 的详细配置参考。文档说明了如何配置输入参数与输出参数，将外部系统请求与响应映射到 Foundry，并列举了 REST API、Salesforce、SAP 等 Webhook 类型、速率限制、Webhook 函数、权限及 OAuth 2.0 授权方式。

### 29. Data Connection • Listeners • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-overview/>
- **文档摘要**：本页概述了 Listeners 这一让 Foundry 接收外部系统事件的能力。文档介绍了 HTTPS、WebSocket（Beta）和 Email 三类监听器各自的定位与处理目标，并说明当外部系统不支持 OAuth 2.0 或无法自定义时，应使用监听器来接收入站连接。

### 30. Data Connection • Listeners • Listener subdomains

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-subdomains/>
- **文档摘要**：本页介绍如何将 HTTPS 与 WebSocket 监听器挂载到专用子域名，以实现细粒度入口控制与治理隔离。内容涵盖在 Control Panel 创建子域名、配置自定义或继承入口允许列表，以及更改子域名、迁移与端点轮换等运维操作。

### 31. Data Connection • HTTPS listeners • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-https/>
- **文档摘要**：本页概述 HTTPS 监听器，它通过 HTTPS 端点接收外部系统的入站 webhook 请求并将事件写入 stream。文档说明其适用场景、配置步骤、速率与负载限制（约 100 请求/秒、1MB），并列出受支持的外部系统（如 GitHub、Jira、Pub/Sub 等）。

### 32. Data Connection • HTTPS listeners • Security

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-https-security/>
- **文档摘要**：本页说明 HTTPS 监听器与传统 Foundry 数据摄取不同的安全范式。文档强调请求授权依赖外部系统自身协议、需对监听器及输出 stream 做权限隔离与标记，并介绍了数据脱敏、子域名与端点轮换等关键安全要点。

### 33. Data Connection • HTTPS listeners • Event processing

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-event-processing/>
- **文档摘要**：本页介绍 HTTPS 监听器事件的处理方式：事件被写入 stream 后，可通过 Automate 流处理、streaming pipelines 或 batch pipelines 进行消费。文档比较了三种方式在吞吐、状态、延迟与处理语义上的差异及各自适用场景。

### 34. Data Connection • HTTPS listeners • Set up a Google Pub/Sub listener

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-google-pub-sub/>
- **文档摘要**：本指南逐步演示如何为 Google Cloud Pub/Sub 配置监听器，将主题事件实时推送至 Foundry streaming dataset。文档列出前置的入口策略配置要求，并分步说明创建主题、监听器、推送订阅及认证所需的服务账号与共享密钥。

### 35. Data Connection • HTTPS listeners • Set up a Jira listener

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-jira/>
- **文档摘要**：本指南逐步演示如何为 Jira Cloud 配置监听器，将 Jira 事件实时送入 Foundry streaming dataset。文档列出入口策略与管理员权限等前置条件，并说明在 Jira 创建 WebHook、复制监听器 URL、配置消息签名密钥及启动测试的步骤。

### 36. Data Connection • HTTPS listeners • Set up a Slack listener

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-slack/>
- **文档摘要**：本指南逐步演示如何为 Slack 配置监听器，将 Slack 事件实时送入 Foundry streaming dataset。文档说明创建 Slack 应用、记录签名密钥、在 Foundry 创建监听器与端点 URL，并在 Slack 中完成事件订阅与验证的完整流程。

### 37. Data Connection • HTTPS listeners • Create an AI-powered Slack bot

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-slack-bot/>
- **文档摘要**：本指南演示如何利用 Foundry 监听器构建一个 AI 驱动的 Slack 机器人（示例 Tennis Bot）。文档按步骤说明设置 Slack 监听器与 source、AIP Chatbot、TypeScript 函数、自动化及函数支持的动作，实现聊天事件的接收与回复。

### 38. Data Connection • WebSocket listeners • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-websocket/>
- **文档摘要**：本页概述 WebSocket 监听器（Beta），专为实时音频与电话工作流设计，支持 Foundry 与外部服务间的双向通信。文档介绍其适用场景，以及创建监听器并配置端点、安全与 compute module 的步骤，并列出受支持的外部连接器。

### 39. Data Connection • WebSocket listeners • Security

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-websocket-security/>
- **文档摘要**：本页说明 WebSocket 监听器的安全范式，包括请求授权、可导出行标记校验、子域名与端点轮换。文档强调 Foundry 认证的监听器会校验令牌与权限，其他类型依赖外部系统协议，并说明如何防止带安全标记的数据外泄。

### 40. Data Connection • WebSocket listeners • Set up Foundry authentication

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-websocket-foundry-auth/>
- **文档摘要**：本指南说明如何使用 Foundry 认证将客户端连接到 compute module 中的 WebSocket 服务器。文档涵盖创建并启用监听器、服务器地址绑定（localhost:5000）以及客户端携带 Foundry token 进行升级请求并建立双向通信的接入方式。

### 41. Data Connection • Email listeners • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-email/>
- **文档摘要**：本页概述 Email 监听器，它通过 Foundry 托管的专用邮箱地址接收入站邮件并解析、转发至 media set。文档说明配置流程（创建、连接、激活、测试）、监听器生命周期状态（Provisioning/Active/Inactive/Rejected）及使用限制。

### 42. Data Connection • Email listeners • Security

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/listeners-email-security/>
- **文档摘要**：本页说明 Email 监听器在接收邮件前施加的多层安全校验。文档列举必须通过的 SPF、DKIM、DMARC、反垃圾与病毒扫描，以及发件人允许列表、附件 MIME 类型限制和单封 40MB 的大小上限等安全控制。

### 43. Data Connection • External connections from code • External transforms

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/external-transforms/>
- **文档摘要**：本页介绍 external transforms，即允许从 Python transforms 仓库连接外部系统的能力，是替代旧版凭据式方案的首选。文档提供配置 Data Connection source、在代码中使用 @external_systems 装饰器及内置 HTTP 客户端的指南，并涵盖权限、能力对比与迁移。

### 44. Data Connection • External connections from code • External functions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/external-functions/>
- **文档摘要**：本页介绍 External Functions，允许从 Function 调用 webhook 以与 Workshop、Actions、Functions 等应用中的外部系统交互。文档说明其概念、需先配置支持 webhooks 的 Data Connection source（如 REST API），并给出创建 source 与 webhook 的接入步骤。

### 45. Data Connection • External connections from code • Sources in Python environments

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/sources-in-python/>
- **文档摘要**：本页介绍在 Foundry 各 Python 环境中连接外部系统的通用能力，涵盖 external transforms、external functions 与 compute modules。文档说明预配置的 HTTPS 客户端、secret 与会话凭据、agent-proxy 出口策略以及 source 属性等常见用法。

### 46. Data Connection • External connections from code • Legacy external transforms

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/external-transforms-legacy/>
- **文档摘要**：本页为旧版（Legacy）external transforms 文档，记录直接在代码中引用出口策略、凭据与导出标记的 transform 写法。文档说明如何授权代码仓库、配置出口与凭据、编写逻辑并加入 dataset 输入与 media set 输出，建议迁移至 source-based 方案。

### 47. Data Connection • Push data into a stream

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/push-based-ingestion/>
- **文档摘要**：本指南演示如何将数据以推送（push-based）方式写入 Foundry stream，以支持事件驱动工作流。文档分初始设置、推送记录、共享与测试几个部分，讲解创建 stream、定义 schema/吞吐/键，以及通过 REST 端点认证并写入数据。

### 48. Data Connection • OpenID Connect (OIDC) authentication

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/oidc/>
- **文档摘要**：本页介绍 OpenID Connect (OIDC) 认证，允许在不使用静态凭据的情况下向外部数据源认证。文档说明 Foundry 作为 OIDC 身份提供者签发短期令牌，并列举支持 OIDC 的源（如 S3、Snowflake、BigQuery）以及如何在代码中使用身份令牌。

### 49. Data Connection • Add sync to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/marketplace-data-connection/>
- **文档摘要**：本页介绍如何利用 Foundry DevOps 将 Data Connection 的同步与导出打包进 Marketplace 产品，供他人安装复用。文档说明标准同步、streaming 同步与导出均可纳入产品，并给出添加 source 与配置产品输出的操作步骤。

### 50. Data Connection • Add virtual tables to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/marketplace-virtual-tables/>
- **文档摘要**：本页介绍如何利用 Foundry DevOps 将 virtual table 打包进 Marketplace 产品。文档说明所有 virtual table 均可被打包同步，但暂不支持单独打包 source 或含自动注册的 source，并指出安装方需保证目标 source 同名同结构。

### 51. Data Connection • Legacy documentation • Set up a direct connection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/set-up-direct-connection/>
- **文档摘要**：本页为旧版（Legacy）文档，说明如何为可直接访问的数据源（如 REST API、SFTP）配置 direct connection 以避免部署 agent。文档涵盖配置网络出口策略、配置凭据、在 Data Connection 创建 source 及网络策略与驱动设置等步骤。

### 52. Data Connection • Agents • Agent worker configuration reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/agent-worker-runtime/>
- **文档摘要**：本页为 Agent worker 配置参考，记录将 agent 作为 worker 执行同步与导出能力时的各项配置选项。文档涵盖能力执行、网络、内存分配、负载均衡、上传策略（data proxy/direct）、自定义 JDBC 驱动、凭据、证书及已知限制。

### 53. Data Connection • Agents • Agent proxy configuration reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/data-connection/agent-proxy-runtime/>
- **文档摘要**：本页为 Agent proxy 配置参考，记录将 agent 作为网络代理以访问 Foundry 网络外系统时的配置选项。文档说明如何在 source 上通过 agent proxy 出口策略使用代理，以及源配置强制、双向允许列表、主机防火墙与连接池等安全控制。
