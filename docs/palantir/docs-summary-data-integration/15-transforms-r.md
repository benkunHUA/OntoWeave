# R 转换（Transforms R）

> **分类说明**：R 转换开发指南：R 环境与 @transform_r 用法

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-r` |
| 文档数量 | 2 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. R • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-r/overview/>
- **文档摘要**：本页介绍 Foundry 中的 R transforms。用户可通过 Code Workspace 的 RStudio® Workbench 用 R 语言编写并发布数据转换，借助 Palantir R SDK 读写结构化与非结构化数据集，并从 CRAN、Posit™ Package Manager、Bioconductor 导入 R 包。R transforms 在单节点执行，不借助 Spark 进行数据读写，执行模式与 API 与其他转换语言不同。

### 2. R • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-r/getting-started/>
- **文档摘要**：本页为 R transforms 快速入门，讲解使用 Palantir R SDK 编写数据转换时的项目结构与配置选项。文中说明 transforms spec（YAML 格式，存于 .transforms 目录）的定义方式，并演示读取表格数据集、用 dplyr 过滤并写出结果的简单转换，以及如何管理 R 包与环境。
