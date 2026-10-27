# Evals 评测

> **分类说明**：AIP Evals 评测套件：创建 suite、定义参数与 metrics、运行实验、分析结果与指标看板

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `aip-evals` |
| 文档数量 | 10 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AIP Evals • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/overview/>
- **文档摘要**：AIP Evals 是用于评估 AIP Logic functions、AIP Chatbot functions 及代码编写 functions 表现的测试环境，专门应对 LLM 的非确定性。你可以创建 test case、定义 evaluation function 度量性能，并把结果与函数的历史版本对比，从而为上线或改动建立信心。核心概念包括 evaluation suite、target function、evaluation function、test case 与 metric，此外也集成在 AI FDE 中，可用对话命令创建并运行 eval suite。

### 2. AIP Evals • Evaluation suites for Logic functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/getting-started/>
- **文档摘要**：本教程演示如何用 AIP Evals 为 Logic function 搭建 evaluation suite，并以一个把餐厅评论分类为正面或负面的函数为例说明流程。保存函数后，可在 Preview 面板选 Add as test case 顺带创建首个 test case，或在 Evals 面板手动 Set up tests manually 建立空 suite；若函数符合条件，可用 Generate evals 让 AIP 自动生成测试用例与 evaluator。之后通过 Edit tests 打开编辑器调整输入并保存。

### 3. AIP Evals • Create an evaluation suite

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/create-suite/>
- **文档摘要**：本页说明如何创建 evaluation suite，即一组用于衡量 target function 表现的 test case 与 evaluation function 集合。运行时会对每个 test case 执行目标函数并套用评估方法。可针对 AIP Logic functions、需先发布为 function 的 AIP Chatbot functions 以及 Code Repositories 中的代码函数创建 suite；还支持添加多个 target function 并行对比，test case 既可手工定义，也可用 object set 批量生成，evaluator 分内置与自定义两类。

### 4. AIP Evals • Use intermediate parameters to evaluate block output

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/intermediate-parameters/>
- **文档摘要**：当 LLM 驱动的逻辑包含多个复杂步骤时，只看最终结果往往无法判断 prompt 的表现，因此 AIP Logic 与 AIP Evals 支持对中间 block 的输出做评估。操作上点击 AIP Logic block 上的烧瓶图标，把该 block 的输出暴露为 intermediate parameter，再在 evaluator 配置面板选中这个中间参数。中间输出值与最终输出一样可用于自动 evaluator 或人工查看，并会写入 evaluation suite 的结果数据集。

### 5. AIP Evals • Evaluate Ontology edits

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/ontology-edits/>
- **文档摘要**：当 test case 涉及创建、修改或删除对象等 Ontology 编辑时，AIP Evals 会在模拟 Ontology 中执行每个 test case，确保真实 Ontology 在测试期间不受影响。这类 Logic function 必须配置 TypeScript 自定义 evaluation function（返回 Boolean 或数值，也可用 struct 一次返回多个指标）或改用 intermediate parameter。对于新建对象，因其只存在于模拟 Ontology，需按可识别属性搜索后再校验其属性。

### 6. AIP Evals • Run an evaluation suite

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/run-suite/>
- **文档摘要**：evaluation suite 既可从 AIP Logic 的 Evals 侧栏运行，也可在 AIP Evals 应用中运行，既支持跑完整 suite，也支持只执行单条 test case 以便调试和快速迭代。运行配置对话框可选被测函数来源与版本（Logic 函数取 last saved 或 published，其他函数只取 published），并可配置 input mapping 把 suite 列映射到函数入参；若配置了多个 target function，还能切换到多目标模式同时评测。

### 7. AIP Evals • Run experiments

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/experiments/>
- **文档摘要**：Experiments 用于系统性测试多个参数取值的所有组合（grid search），帮助在性能与成本之间找到最佳的模型与 prompt 配置。使用前需先把 model 和 prompt 参数化为函数输入，并在各 Use LLM block 中引用；随后在 run configuration 中开启 experiments 开关、为每个参数列出候选值并命名实验，再分多次运行 suite。结果可对比不同 run 与 test case 并逐条调试，定位表现最好的参数组合。

### 8. AIP Evals • Write run results to a dataset

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/results-dataset/>
- **文档摘要**：评估结果有时需要出现在平台其他位置，例如业务专家希望在 Workshop 应用中与其他信息一起查看，而不必进入 AIP Evals。为此 AIP Evals 支持把 run 结果写入 dataset：配置后以 project-scoped 执行模式运行 suite，函数输出、evaluator 结果、用户指定与自动采集的 metadata 以及错误都会自动写入。dataset 必须与 suite 处于同一项目，之后可用 Contour 等工具做进一步分析。

### 9. AIP Evals • Analyze run results

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/analyze-run-results/>
- **文档摘要**：run results 展示函数在 test case 与评估标准下的表现；若 evaluator 设了通过条件，AIP Evals 会自动判定 Passed 或 Failed 并统计整体通过率。针对单条 test case 可打开 debug view，查看 execution trace、输入输出与报错信息：TypeScript/Python 函数可看高亮代码预览，AIP Logic 函数可用原生 Logic debugger 逐步追踪，evaluator 可对比期望与实际值。结果还支持跨 target function 比较。

### 10. AIP Evals • View results in metrics dashboard

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/aip-evals/metrics-dashboard/>
- **文档摘要**：评估运行产生的各项 metric 会被汇总成报告，展示在 AIP Evals 的 metrics dashboard 中，可查看图表与统计，并比较 evaluator 的聚合结果或单条 test case 的结果（仪表盘视图暂不支持 metric objectives）。需要更深入分析时，可从 Run tests 标签双击 test case 进入 LLM trace viewer，查看函数结果的计算过程；若使用自定义 LLM as a judge evaluator，trace 中还会包含该 judge 的决策依据。
