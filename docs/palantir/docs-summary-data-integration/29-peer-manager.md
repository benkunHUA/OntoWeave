# Peer Manager

> **分类说明**：Peer Manager：Foundry 集群节点管理、资源监控与运维操作

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `peer-manager` |
| 文档数量 | 12 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Peer Manager • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/overview/>
- **文档摘要**：本页介绍 Peer Manager 应用，它用于查看和监控已建立的 peer connection（peer 连接）所关联的作业，从而在不同 Foundry enrollment 之间导入或导出对象。页面列举其核心功能：创建 peer 连接、查看连接概览、配置 Ontology 与 file peering、通过 peer profiles 大规模管理数据共享，以及监控 peering jobs 的健康状态。

### 2. Peer Manager • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/core-concepts/>
- **文档摘要**：本页提供 Peer Manager 相关的 peering 核心概念介绍。内容涵盖 peer connection（控制可 peer 的数据类型、方向与安全标记）、连接安全性（基于 CBAC 分类标记决定是否允许 peer）、远程 peer 如何识别本 enrollment，以及 peering jobs 的工作机制。

### 3. Peer Manager • Create and update a peer connection • Establish a Multipass exchanger connection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/establish-mpx-connection/>
- **文档摘要**：本页说明如何建立 Multipass exchanger (MPX) 连接，作为两个 enrollment 之间创建 peer connection 的先决条件。页面指导 enrollment 管理员在建立网络连接前先联系 Palantir Support 配置网络，并详细说明了如何协调连接方向（双向/Bidirectional、单向 Egress/Ingress）、生成与输入 MPX invite code 的步骤。

### 4. Peer Manager • Create and update a peer connection • Establish a data relay connection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/establish-data-relay-connection/>
- **文档摘要**：本页说明如何建立 data relay 连接，用于在没有直接网络连接时，通过既有的 cross-domain solution (CDS) 在两个 enrollment 间交换数据。页面指导管理员与远端协调 CDS 通道细节（方向、连接类型如 File/HTTP），配置 Export 或 Import 通道，并列出后续步骤。

### 5. Peer Manager • Create and update a peer connection • Create a peer connection

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/create-a-peer-connection/>
- **文档摘要**：本页提供在 Peer Manager 中创建 peer connection 的操作步骤。页面列出前置条件（配置 ingress/egress 策略、建立 MPX 或 data relay 连接、成为 space 管理者、设置平台标题），并分步说明发起连接（复制 invite code）、导入连接以及激活连接的全过程。

### 6. Peer Manager • Create and update a peer connection • Update a peer connection's settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/peer-connection-settings/>
- **文档摘要**：本页说明如何在 Peer Manager 的 Settings 标签页中修改已有 peer connection 的状态与安全设置。可将连接状态设为 Disabled 以停止所有数据 peer；也可在 Security 区修改 Classification Markings 或 Other Markings，但安全变更需经远端 enrollment 审批后方可生效。

### 7. Peer Manager • Peer objects and links between enrollments • Ontology peering

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/ontology-peering-overview/>
- **文档摘要**：本页介绍 Ontology peering，即通过 peer connection 将一个 enrollment 的 object 与 link 类型同步到另一个 enrollment 的 Ontology。内容涵盖 object references 与 mappings（基于 RID 翻译）、source data peering 与 action data peering 等类型、link type peering，以及与 automations 的关系和配置入口。

### 8. Peer Manager • Peer objects and links between enrollments • Configure Ontology peering

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/configure-ontology-peering/>
- **文档摘要**：本页指导如何在 Peer Manager 中配置 Ontology peering。说明先通过 Marketplace 在源 enrollment 创建含待 peer object type 的 product 并部署到远端，再在两端分别将 object type 设为可 peer（Unmapped 标签），随后创建 object type mapping 以同步对象与链接。

### 9. Peer Manager • Configure file peering

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/configure-file-peering/>
- **文档摘要**：本页说明如何在 Peer Manager 中配置 file peering，以在 enrollment 间同步 Gotham files（如 Gaia maps）。需在连接 Settings 中启用文件的 export/import，并在 Files 面板为每种 file type 设置 peer 行为（可按 tag 或时间参数自动 peer 全部或部分文件），且两端都需配置。

### 10. Peer Manager • Peer profiles

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/peer-profiles/>
- **文档摘要**：本页介绍 Peer profiles（测试版），即可复用的数据共享配置模板，用于在一组 enrollment 组成的 mesh 中统一应用 peering 配置。页面解释 mesh、profile pairs、remote strategy 等核心概念，并给出创建配对、发布到 Marketplace、在目标 enrollment 安装与监控的分步说明及常见问题。

### 11. Peer Manager • Peering jobs

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/peering-jobs/>
- **文档摘要**：本页解释 Peer Manager 如何使用 peering jobs 在 space 间传输数据，并说明如何解读连接 Overview 页的作业表。内容涵盖作业工作原理（payload type、direction、sequencing）、concurrent 与 sequential 两类 job、各种 job 状态、空作业、表格筛选与连接健康判读。

### 12. Peer Manager • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/peer-manager/faq/>
- **文档摘要**：本页汇集 Peer Manager 的常见问题解答。首个问题解释为何用户可能无法看到某 peer connection 上的标记（markings）：通常因缺少对该标记的查看权限，此时 Peer Manager 显示 Unknown 标记图标；解决方法是联系 enrollment 管理员授予查看权限。
