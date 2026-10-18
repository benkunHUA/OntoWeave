# 转换通用（Transforms Common）

> **分类说明**：各语言转换共用的通用能力：本地预览、依赖管理与共享工具

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-common` |
| 文档数量 | 3 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Common • Local preview

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-common/local-preview/>
- **文档摘要**：本页介绍在本地开发（VS Code）中预览 transforms 的两种方式。Python 转换可使用 Palantir Visual Studio Code 扩展在本地预览；Java 与 Python 转换则可使用基于 Gradle 的本地预览，该方式在 Foundry 内远程执行。文中说明前提条件与限制，并通过 ./gradlew 命令列出转换与运行数据集预览。

### 2. Common • Transforms versions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-common/transforms-versions/>
- **文档摘要**：本页为 Transforms 版本参考，列出构建时所使用的 Spark、Java、Scala 版本随 transforms 版本的变化。Foundry 会在后台自动升级 transforms 以保障安全与性能，并通过 adjudication 机制避免升级到不兼容版本。页面提供 Python、Java、SQL 各版本的对应表，并介绍 Environment 页面查看、运行时版本选择与作业调试等信息。

### 3. Common • Optimize multi-output transforms

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-common/optimize-multi-output-transforms/>
- **文档摘要**：本页讲解优化多输出转换的两种方案及其取舍，属于最佳实践。方案一是多个单输出 transform（各自独立构建、易维护、可定制 Spark 配置）；方案二是多输出 transform（一次读取、多处写入）。文中对比各自的优势与局限，并给出如何根据输入复用、构建频率与计算成本选择方案的指导。
