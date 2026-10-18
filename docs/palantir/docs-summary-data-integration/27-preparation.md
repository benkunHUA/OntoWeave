# 数据准备（Preparation）

> **分类说明**：数据准备（Preparation）工作流：构建数据清洗与准备流程的图形化工具说明

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `preparation` |
| 文档数量 | 7 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Preparation [Planned Deprecation] • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/overview/>
- **文档摘要**：本页介绍 Preparation 工具的概览信息。Preparation 是 Foundry 中用于清洗和准备数据（cleaning/preparing）的交互式工具，目前处于计划弃用阶段，将于 2027 年 5 月 4 日弃用，官方建议迁移至 Pipeline Builder。页面还定义了 Preparation、Clean、Prepare、Change、Changelog 等关键术语。

### 2. Preparation [Planned Deprecation] • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/getting-started/>
- **文档摘要**：本页为 Preparation 的快速入门指南。介绍了如何从数据集或 Preparation 界面打开数据集进行清洗和准备、在 Table 与 Column 两种视图下分析数据、在分析时联动 Contour，以及通过“Save as dataset”保存为持续更新或一次性数据集。

### 3. Preparation [Planned Deprecation] • Create a simple preparation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/preparation-tutorial/>
- **文档摘要**：本页提供创建简单 preparation 的操作教程。以 meteorite_landings_raw 样本数据为例，演示了创建 preparation、清洗数据（如去除首尾空格、将年份列转为日期、将无效地理坐标置空）以及保存清洗后数据集的完整流程。

### 4. Preparation [Planned Deprecation] • Project references

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/project-references/>
- **文档摘要**：本页介绍 Preparation 中的 Project 引用机制。Project references 允许高权限用户在其他 Project 中引用并使用数据；自 Contour 9.161.0 起新建 preparation 默认启用 Project scoping，要求输入与输出数据集均处于 Project 范围内，并说明了如何为 preparation 启用项目作用域。

### 5. Preparation [Planned Deprecation] • Basic examples

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/basic-examples/>
- **文档摘要**：本页列举 Preparation 界面中清洗和准备数据的基础转换示例。涵盖去除首尾空格、空字符串置空、批量规范化字符串、解析货币字符串为数值、ZIP 码规范为五位、列名转为 snake case、删除空列与含空值行等多种常见操作。

### 6. Preparation [Planned Deprecation] • Advanced examples

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/advanced-examples/>
- **文档摘要**：本页介绍 Preparation 的高级转换与工作流示例。包括使用 Apply expression 编写表达式、将 preparation 复用到不同数据集、以及如何利用多输出分支（创建分支、保存到不同分支、切换分支、恢复已保存版本）组织数据准备流程。

### 7. Preparation [Planned Deprecation] • FAQ

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/preparation/faq/>
- **文档摘要**：本页汇总 Preparation 的常见问题（FAQ）。解答了“什么是 Preparation”“谁应使用”“能做什么”“能否更改输入数据集”等问题，并说明 Preparation 由 Contour 后端驱动，可通过 Change Log 更改起始数据集或复制文件后复用逻辑。
