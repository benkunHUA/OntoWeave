# Logic 逻辑编排

> **分类说明**：AIP Logic 无代码 LLM 函数构建：核心概念、Blocks、工具调用、分阶段写入、执行模式与指标

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-logic` |
| 文档数量 | 11 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AIP Logic • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/overview/>
- **文档摘要**：AIP Logic 是无需编码的 LLM 函数开发环境，可构建、测试并发布由 LLM 驱动的 function，无需自行搭建开发环境和 API 调用即可利用 Ontology。Logic function 接收 Ontology 对象或文本等输入，返回字符串、对象，或直接对 Ontology 做出编辑，并可接入 Automate 自动执行或暂存待人工审核。它还支持用 Foundry Branching 隔离开发、用 metrics 监控成功率与执行时长，并沿用平台的权限与安全模型。

### 2. AIP Logic • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/core-concepts/>
- **文档摘要**：本页解释使用 AIP Logic 必须掌握的五个核心概念。Logic function 接收 Ontology 对象或文本输入，输出值、对象或对 Ontology 的编辑，可像普通函数一样在 Workshop 等场景调用，而修改 Ontology 时须先发布并由 action 调用。Blocks 是函数的基本构件，可读写 Ontology、做计算聚合、调用函数、循环、判断条件或与 LLM 交互，通过串联传递输出；此外还涵盖 Evaluations、Debugging 与 Execution modes。

### 3. AIP Logic • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/getting-started/>
- **文档摘要**：本指南说明如何进入 AIP Logic、认识其界面并用 LLM block 搭建一个基础 Logic function，同时在 Debugger 中查看模型的 chain of thought。界面由三部分组成：左侧的 inputs、blocks 与 outputs 配置区，中间的 Debugger，以及右侧的 Run 面板。典型流程是先配好输入、block 与输出，在 Run 面板生成样例结果，再用 Debugger 观察推理步骤；还涵盖命令行运行、Ontology 编辑与 Branching 等后续操作。

### 4. AIP Logic • Blocks

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/blocks/>
- **文档摘要**：Blocks 是 AIP Logic 函数的基本组成单元，各自接收输入、返回输出，完成一次与数据的离散交互，可读写 Ontology、执行计算、聚合数据、调用函数或与 LLM 交互，且输出可传给后续 block 以串联出复杂逻辑。Use LLM 是核心 block，由 prompt、tools 与 output 三部分构成，支持平台内任意可用模型。其他常见 block 还包括 Apply action、Execute function、Conditionals、Loops 与 Create variable。

### 5. AIP Logic • Automate AIP Logic

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/aip-logic-integration-automate/>
- **文档摘要**：AIP Logic 可接入 Automate：对象变更时调用 Logic function 并把编辑写回 Ontology。启用 staged writes 时须先用 Create action 生成 action type、再以 action effect 触发，未启用则直接用 Logic effect。

### 6. AIP Logic • Staged writes

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/staged-writes/>
- **文档摘要**：Staged writes 是 AIP Logic 编辑 Ontology 的新执行模型（Beta，需申请开通）：编辑先暂存于临时存储，执行结束统一提交。它支持嵌套 function 与 action 调用中的编辑及全部 object set 过滤器，函数输出不再是 Ontology edits 列表。

### 7. AIP Logic • Compute usage

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/compute-usage/>
- **文档摘要**：AIP Logic 的 compute 以 block 为计费单元：基础 LLM block 执行最低计 4 compute-seconds，调用工具时最低 8，token 另按底层模型口径统计。block 向 Ontology 或 function 分发计算还会产生额外 Foundry compute。

### 8. AIP Logic • AIP Logic metrics

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/logic-metrics/>
- **文档摘要**：AIP Logic resource 由 function 支撑，执行时暴露底层 function 的成功与失败次数及近 30 天 P95 时长，可在 Ontology Manager 或 Workflow Lineage 查看。指标由 FTS 近实时更新，run history 可回看近七天。

### 9. AIP Logic • Execution mode settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/execution-mode-settings/>
- **文档摘要**：Logic function 支持两种执行模式：user-scoped（默认）以运行者权限执行，仅本人可见日志且保留 24 小时；project-scoped 以 project 权限执行，日志对成员可见，且要求引用资源全部导入同一 project。后者可配 dataset 记录运行历史，保留最近 10000 次。

### 10. AIP Logic • Branching AIP Logic

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/branching-logic/>
- **文档摘要**：AIP Logic 接入 Global Branching，支持在隔离分支上开发 Logic function：保存即为分支资源，发布后带 Branched 标签且仅本分支可用。合并前须与 main 同步、已在分支发布、无错误且无待审批；文档还覆盖 rebase 冲突处理与已知限制。

### 11. AIP Logic • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/logic/faq/>
- **文档摘要**：本页解答 AIP Logic 常见问题：如何与平台其他组件配合、如何降低 token（只取所需属性、拆分 Use LLM block、改用 32k 模型、多用确定性 block）、单 block 与多 block 的取舍、block 性能优化、调 temperature、语义检索、输出校验与用历史执行构造测试用例。
