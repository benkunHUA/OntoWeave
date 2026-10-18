# 代码检查（Linter）

> **分类说明**：代码检查（Linter）：转换代码的静态检查规则、警告说明与修复建议

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `linter` |
| 文档数量 | 6 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Linter • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/linter/overview/>
- **文档摘要**：本页介绍 Linter 应用。Linter 会定期扫描 Foundry 状态以识别反模式（anti-patterns）并给出改进建议，帮助用户降低成本、提升管道稳定性与弹性；建议按用例目标配置规则模式，并提供了术语表以便理解后续内容。

### 2. Linter • Modes

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/linter/modes/>
- **文档摘要**：本页介绍 Linter 的不同模式（Modes）。每种模式有特定目标与一组规则：Resource Savings 模式给出降低基础设施成本的建议；Pipeline Resilience 模式给出提升管道稳定性与可调试性的建议，推荐用于关键工作流审查。

### 3. Linter • Recommendations

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/linter/recommendations/>
- **文档摘要**：本页说明 Linter 的 Recommendations（建议）的使用方法。建议表示将资源迁移到更优状态的单个动作，可按资源类型、规则、项目筛选，并查看上下文；建议有默认、已暂停、暂停过期等状态，支持暂停（snooze）与指派（assign）操作。

### 4. Linter • Rules

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/linter/rules/>
- **文档摘要**：本页列出 Linter 可用的规则（Rules）参考。每条规则针对资源元数据运行逻辑检测，失败则生成改进建议，并归属某一模式；示例包括代码工作簿跨调度、数据集构建超时、默认分支未保护、调度持续失败等，附判断逻辑与节省估算方法。

### 5. Linter • Sweep schedules

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/linter/sweep-schedules/>
- **文档摘要**：本页说明 Linter 的 Sweep schedules（扫描计划）配置。扫描计划定义在指定 space 内对资源范围按规则范围定期扫描；用户可在 Control Panel 中编辑、暂停或触发，并通过规则预设、具体规则、排除规则三种方式定义规则范围及资源范围。

### 6. Linter • Impact tracking

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/linter/impact-tracking/>
- **文档摘要**：本页说明 Linter 的 Impact tracking（影响追踪）界面。可追踪已处理建议的进度与节省估算，展示建议数、预估影响（计算小时减少）与已验证影响（修复前后使用量差异）三项指标，并提示跨规则共享资源可能导致的估算重复计数问题。
