# SAP 集成

> **分类说明**：SAP 数据集成：SAP 连接器配置、数据提取（Extraction）、OData 服务与常见 SAP 场景

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `sap` |
| 文档数量 | 24 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. SAP • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/overview/>
- **文档摘要**：本页介绍将 Foundry 连接至 SAP 的概览。连接分两步：在 SAP 侧安装 ABAP Add-on（Connector 2.0），再在 Foundry 侧配置与 SAP 系统（ERP、S/4HANA、BW、SLT）匹配的数据源。页面还列出支持的能力、对象类型与系统、前置条件及快速上手流程。

### 2. SAP • Architecture

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/architecture/>
- **文档摘要**：本页说明 Foundry 连接 SAP 系统的三种主要架构模式，均遵循标准 Data Connection 架构并使用 Data Connection agents。包括直连 ERP/BW、经 SLT Replication Server 连接、以及通过 gateway 远程连接低版本系统。

### 3. SAP • Download

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/download-sap-addon/>
- **文档摘要**：本页说明如何从 Foundry 下载 Palantir Foundry Connector 2.0 for SAP Applications Add-on 及发布说明。针对新建与已有 SAP 数据源，分别给出在 Data Connection 或 Connection settings 中找到 Download packages 步骤的方法，并说明按 SAP_BASIS 版本选择下载包。

### 4. SAP • SAP Add-on • Installation overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/install-sap/>
- **文档摘要**：本页概述 SAP Add-on（Connector）的安装。Connector 是基于 ABAP 的数据提取接口，运行于 SAP ABAP 平台，经 HTTPS 以 REST API 提供服务，由 PALANTIR、PALCONN、PALAGENT、PALODATA 四个组件构成，并介绍安装场景与支持包/修复包。

### 5. SAP • SAP Add-on • Install the Connector

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/install-sap-connector/>
- **文档摘要**：本页提供在 SAP 侧安装 Palantir Foundry Connector 2.0 for SAP Applications 的操作步骤。说明以 SAINT 安装 SAR 格式包、创建 System 类型技术用户、运行 Post Installation Wizard（含向导步骤、安全授权角色与性能参数）等前置条件与流程。

### 6. SAP • SAP Add-on • Install a Remote Agent

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/install-sap-remote-agent/>
- **文档摘要**：本页说明如何安装 Remote Agent，以访问低于 SAP_BASIS 7.4 SP5 的远程 SAP 系统或将其合并为一处 Foundry Source。介绍在远程系统通过 SAINT 导入 SAR 包、选择 PALANTIR 与 PALAGENT 组件、执行安装及后续 RFC 配置。

### 7. SAP • SAP Add-on • Install a Remote Agent for 4.6C/620/640

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/install-sap-remote-agent-620/>
- **文档摘要**：本页说明为旧版 4.6C/620/640 系统安装 Remote Agent 的步骤，该组件以 SAP Request 形式经 Transport Management System 传输。涵盖下载并提取包、复制 cofiles/data 文件、用 STMS 导入请求、生成授权角色及 RFC 配置与限制。

### 8. SAP • SAP Add-on • Install a Support Package

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/install-sap-support-package/>
- **文档摘要**：本页说明如何通过 SPAM（Support Package Manager）为 Connector 安装支持包。强调安装期间需暂停 housekeeping 作业与所有 Foundry 数据同步以避免数据库锁导致失败，并给出加载包、定义升级队列（组件需同级升级）等步骤。

### 9. SAP • SAP Add-on • Install a Fix Pack

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/install-sap-fixpack/>
- **文档摘要**：本页说明如何为 Connector 安装 Fix Pack，该修复包仅对指定的 SP 级别有效。Fix Pack 以 SAP Request 形式经 STMS 传输，文档给出下载、提取、复制 cofiles/data 文件、STMS 导入请求及检查日志的步骤。

### 10. SAP • SAP Add-on • Configure SAP SLT

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/configure-sap-slt/>
- **文档摘要**：本页说明如何配置 SAP SLT（Landscape Transformation Replication Server）与 Connector 协同实现 CDC 数据复制。涵盖前置条件、ODP 直连与经 OData（SP34+）两种方式、RFC 目标连接、SLT 配置、大表同步性能考量及已知 SAP 问题参数。

### 11. SAP • SAP Add-on • Create an RFC connection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/create-sap-rfc-connection/>
- **文档摘要**：本页说明如何在 SAP 中创建用于从远程系统提取数据的 RFC Destination 连接。通过 SM59 事务建立 ABAP 连接（类型 3），填写目标主机、系统号与客户端登录凭据，并保存及测试连接。

### 12. SAP • SAP Add-on • Uninstall the Connector or Remote Agent

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/uninstall-sap/>
- **文档摘要**：本页说明如何卸载 Connector 或 Remote Agent。卸载前需先运行 SA38 执行 /PALANTIR/UNINSTALL_CORR 修正目录项，再用 SAINT 卸载；须先卸载 PALCONN/PALAGENT/PALODATA，不能单独卸载作为依赖根的 PALANTIR。

### 13. SAP • SAP Add-on • Add-on Cockpit

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/sap-cockpit/>
- **文档摘要**：本页介绍 Connector Cockpit，一个用于管理 Connector 参数的 SAP GUI 界面（事务码 /n/PALANTIR/COCKPIT）。涵盖参数配置表维护、数据传输监控、日志清理、敏感数据加密与脱敏、预过滤器等管理功能。

### 14. SAP • SAP Add-on • Add-on parameters

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/addon-parameters/>
- **文档摘要**：本页列出 Palantir Foundry Connector 2.0 for SAP Applications 的 Add-on 参数及其默认值。逐项说明各参数（如 BALDAT 分隔符、BEx 引擎与分页、增量等）的 Param Id、名称、可选值与默认含义，供配置参考。

### 15. SAP • SAP Add-on • Add-on housekeeping

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/housekeeping/>
- **文档摘要**：本页说明如何设置与配置 Connector 的 housekeeping 作业。作业用于按时间删除日志表数据、关闭未确认的 SLT 请求、重置未用订阅指针及清理 INIT 请求，并解释各选项（如保留时间窗口、仅删已入 Foundry 数据）的默认行为。

### 16. SAP • SAP Add-on • Add-on authorization roles

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/authorization-roles/>
- **文档摘要**：本页列出 Connector 使用的四类授权角色：Service、Content、Writeback 与监控调试角色，以及 Remote Agent 角色。逐一给出角色名称与用途，例如 Service 角色供 Connector 运行且不可修改，Content 角色可据业务复制调整。

### 17. SAP • SAP Add-on • Add-on backup and restore

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/backup-restore/>
- **文档摘要**：本页说明如何对 Connector 进行备份与恢复（要求 SP13 及以上）。通过 /n/palantir/backup 事务将关键表备份为 zip，并列举所包含的 AGT/CFG/ENC/INC/PAG 等表，以及系统迁移或维护后如何恢复这些表。

### 18. SAP • SAP Data Accelerator

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/data-accelerator/>
- **文档摘要**：本页（Beta）介绍 Foundry 与 SAP Data Accelerator 的集成，该 SAP 托管服务经 Cloud Connector 将流量隧道转发至本地 SAP 系统。说明前置条件、SAP 侧配置、Foundry 源指向（证书、虚拟主机、egress）及用户归因回写配置。

### 19. SAP • How-Tos • Extract long text from SAP

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/extract-long-text/>
- **文档摘要**：本页说明如何从 SAP 提取长文本（SAPscript / 文本对象），其存储于 STXL 表且为压缩格式。介绍前置条件（SP16+）及通过 /n/PALANTIR/DECOMPRESS 在配置表启用解压、创建 STXL 同步并按对象名筛选的实操步骤。

### 20. SAP • How-Tos • Configure custom authorizations and role management

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/configure-custom-authorizations/>
- **文档摘要**：本页说明如何配置自定义授权与角色管理（SP16 起支持），将角色与对象访问定义于透明表而非 PFCG。介绍以 PARAM 参数启用、用 AUTH_01 建角色、AUTH_02 分配用户与上下文，并可按对象类型进行允许/拒绝控制。

### 21. SAP • How-Tos • Configure extractors

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/configure-extractors/>
- **文档摘要**：本页说明如何配置与使用 SAP 提取器（extractors）从 SAP 提取数据至 Foundry。介绍业务内容、客户生成与通用三类提取器，以及 ODP 启用要求、单/多源系统的配置方式与使用要点。

### 22. SAP • How-Tos • Ingest HANA views from SAP

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/ingest-hana-views/>
- **文档摘要**：本页说明如何将 SAP HANA 视图的数据摄取到 Foundry，需先将 HANA 视图发布为外部视图（External View）。介绍前置工具（HANA Studio、ADT）、创建外部视图的步骤及在 Foundry 中摄取视图的方法与数据类型映射。

### 23. SAP • How-Tos • User-attributed SAP writeback with OAuth 2.0

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/oauth2-writeback/>
- **文档摘要**：本页提供使用 OAuth 2.0 实现用户归因回写 SAP 的配置指南（需 Connector SP21+）。分别说明在 SAP 侧配置 OAuth 2.0 服务器（SOAUTH2、OData 服务激活）与在 Foundry 侧配置 OAuth 2.0 客户端的步骤，含 Data Accelerator 与遗留 Webhook 方式。

### 24. SAP • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/sap/faq/>
- **文档摘要**：本页汇总关于 Connector 的常见问题，涵盖连接（系统版本、认证、卸载、性能、许可、安全）、数据提取（表类型、CDC、资源检查、BW 特性、敏感数据）与回写（技术细节、用户归因）等主题。
