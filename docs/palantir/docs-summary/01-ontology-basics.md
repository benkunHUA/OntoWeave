# Ontology 基础概念

> **分类说明**：Ontology 的定义、核心概念、模型、应用场景、最佳实践与设计指导

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `ontology` |
| 文档数量 | 23 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Overview • Ontology

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/overview/>
- **文档摘要**：本页是 Ontology 模块的总览，介绍 Palantir Ontology 作为组织的运营层（operational layer）如何集成数据集、虚拟表和模型等数字资产，并映射到现实世界的实体。页面说明 Ontology 由语义元素（object types、properties、link types、interfaces）和动态元素（action types、functions、动态安全）构成，可作为组织的数字孪生，并支撑实时决策。

### 2. Why create an Ontology?

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/why-ontology/>
- **文档摘要**：本页阐述为何要构建 Ontology，强调它是以决策为中心（decision-centric）的系统，能安全、合规地在企业中落地 AI。Palantir 将每个运营决策拆解为 Data、Logic、Action、Security 四个组成部分，并以一个端到端运营工作流示例说明如何获取可见性、构建仿真、执行决策并从中学习，区别于传统仅关注数据的数据架构。

### 3. Models in the Ontology

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/models/>
- **文档摘要**：本页介绍如何将 AI/ML 模型部署到 Ontology 中用于实时推理。说明了在 Foundry 中运营化模型的端到端步骤：创建模型、配置直接模型部署、发布包装函数、在 Workshop/Vertex 等应用中调用进行实时推理，或基于数据集做批量推理。还列举了将模型映射到 Ontology 的好处，如可解释性和规模经济。

### 4. Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/core-concepts/>
- **文档摘要**：本页概括 Foundry Ontology 的核心概念：Ontology 是组织的数字孪生；object type 定义现实实体或事件，property 描述其特征，link type 定义对象间关系，action type 定义对象修改方式。页面还将数据集与 Ontology 概念做类比（数据集↔object type、行↔object、列↔property、连接↔link type），并介绍 shared property、interfaces、Object Views、functions、roles 等组件。

### 5. Ontology-aware applications

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/applications/>
- **文档摘要**：本页是 Ontology 原生应用的参考指南，列举了可直接运行在 Ontology 之上的对象感知应用（Object Views、Object Explorer、Quiver、Workshop、Slate、Carbon、Map），分别说明其用途，并从使用场景、工作流风格和配置模型三个维度进行对比，帮助用户为不同用例选择合适的应用。

### 6. Ontology design: Best practices

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/ontology-best-practices/>
- **文档摘要**：本页提供 Ontology 设计的最佳实践，包含一份实用设计准则清单（如按现实而非系统建模、有意图地策划属性、跨团队协作、保持 object type 聚焦、选对工具、用 interfaces 抽象等）以及四条核心设计原则：领域驱动设计、不重复自己（rule of three）、对扩展开放对修改封闭、组合优于深层继承，并讨论务实取舍。

### 7. Ontology design: Structural guidance

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/ontology-structural-guidance/>
- **文档摘要**：本页给出 Ontology 结构的设计指导，聚焦 properties、relationships 和访问控制的组织方式。核心建议是「每个事实只存一次」，用 derived properties 提供去规范化的便捷访问而避免数据不一致；并对比预计算值（来自同对象稳定输入、可用 pipeline transform）与动态派生值（依赖链接对象或通过 action 变化、需用 derived property）两种方案的适用场景。

### 8. Ontology design: Anti-patterns

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/ontology-anti-patterns/>
- **文档摘要**：本页列举常见的 Ontology 设计反模式（anti-patterns），如 System Silos、The Kitchen Sink、Department Silos、The God Object、The Golden Hammer、Action Sprawl、The Time Machine、The Misnomer 等，逐一说明其常见成因、示例、问题及对应解决方案，帮助设计者避免随规模增长而产生维护负担的陷阱。

### 9. Ontology design: Validation

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/ontology-design-validation/>
- **文档摘要**：本页讨论 Ontology 设计的验证方法，指出结构化审查只能确认模型语义与技术正确，无法确认可用性。推荐用基于任务（task-based）的验证，即让实际使用的人和 agent 用未见过的业务问题测试 Ontology，并介绍如何获取真实业务问题、设计钻取练习、解读结果、对比人与 agent 的差异以及多种练习形式（测验式、限时下钻）。

### 10. Semantic search • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/overview-semantic-search/>
- **文档摘要**：本页是 Semantic search（语义搜索）的概述，说明语义搜索基于文本的内在含义而非关键词匹配，利用 AI 模型将文本转为向量（embeddings），在 N 维空间中相近的向量语义相近。当嵌入文本关联到某个 Ontology 对象后，即可通过寻找最近邻向量来支持搜索驱动的工作流，并给出相关子主题文档入口。

### 11. Semantic search • Document processing

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/document-processing/>
- **文档摘要**：本页是语义搜索中的文档处理指南，介绍从 PDF 或图片中抽取数据的注意事项，并提供使用 Pipeline Builder 解析 PDF 的基础方法。重点讲解 chunking（分块）策略：将长文本切分为更小、语义更鲜明的块并各自嵌入，每块关联到原对象的 Ontology 对象，从而提升检索效果；给出了分块示例与配置参数。

### 12. Semantic search • Process multimodal and embedding models

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/aip-multimodal-and-embedding-models/>
- **文档摘要**：本页讨论处理多模态（multimodal）与嵌入（embedding）模型的方法，供语义搜索使用。多模态部分推荐 Pix2Struct、Microsoft UDOP 等可处理图表/图像的模型；嵌入部分介绍 MSMARCO、MS MARCO 等面向检索训练的英文模型，并解释非对称嵌入、HyDE、token 长度限制等选型要点与适配建议。

### 13. Semantic search • Ontology augmented generation

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/ontology-augmented-generation/>
- **文档摘要**：本页介绍 Ontology-augmented generation（本体增强生成，RAG 思路），强调为 LLM 找到相关业务上下文是最关键的一步。概述基础语义搜索流程（分块、生成 embeddings、检索），并列举高级检索技术如 HyDE（假设文档嵌入）、基于关键词的排序搜索、查询增强等，说明应依数据特点组合选用。

### 14. Semantic search • Use Palantir-provided models to create a semantic search workflow

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/using-palantir-provided-models-to-create-a-semantic-search-workflow/>
- **文档摘要**：本页是操作教程，演示如何使用 Palantir 提供的嵌入模型构建端到端语义搜索工作流。前提是 AIP 已启用且具备开发者权限，步骤包括：用 Pipeline Builder 生成 embeddings 并创建带 vector 类型属性的 object type，随后可在 Workshop 中通过 KNN object set（无代码）搭建搜索、启用 AIP Chatbot，或用函数（TypeScript）在 Workshop/AIP Logic 中搜索，并附示例函数。

### 15. Semantic search • Use custom models to create a semantic search workflow

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/using-custom-models-to-create-a-semantic-search-workflow/>
- **文档摘要**：本页是使用自定义（非 Palantir 提供）嵌入模型构建语义搜索工作流的教程，并注明该方式已不再推荐。流程包括：在 Foundry 中用模型生成 embeddings、创建 object type、建立 Live Modeling Deployment、用 Functions on Models 生成嵌入，并提供需要替换的标识符（ObjectApiName、ModelApiName、RID 等）与文件结构示例。

### 16. Search syntax

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/search-syntax/>
- **文档摘要**：本页是 Ontology 搜索语法参考，说明如何从不同入口（Workshop 过滤器、Object Explorer 搜索栏）进行搜索，重点描述正则表达式（regex）搜索的语法与限制：字符串属性需在 Ontology Manager 中开启 regex 索引、模式匹配整值而非子串、不支持 ^/$ 锚点，并列出支持的运算符（.、?、+、*、{} 等）。

### 17. Derived properties

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/derived-properties/>
- **文档摘要**：本页介绍 derived properties（派生属性），其值在查询时根据其他属性或链接对象动态计算，而非存储。派生方式包括跨链接对象聚合、从链接对象选取、算术表达式计算；派生属性只读、不存于对象存储，随底层数据变化自动保持正确。页面还区分 Ontology 定义的与运行时定义的派生属性在配置位置、可见性与能力上的差异。

### 18. Derived properties reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/derived-properties-reference/>
- **文档摘要**：本页是 derived properties 的参考手册，罗列可用的聚合与表达式及其来源/结果类型、使用位置与性能差异。聚合包括 Count、Average、Sum、Min、Max、近似/精确去重、Collect list/set 等；表达式涵盖数值与日期时间戳运算；还说明结果类型拓宽（如 Sum）、集合行为、与原生属性的对比、以及按聚合方式的规模与性能表现。

### 19. Overview • Ontology

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/overview-ontology-scenario/>
- **文档摘要**：本页（Beta）概述 Ontology scenarios（场景），可在不改动实时数据的前提下创建并比较「假设性（what-if）」分析。介绍常见用例（what-if 分析、业务逻辑仿真、agent 评估与预测、对比分析）、默认 31 天存活期、每 10 分钟自动 rebase、与 Global Branching 的区别、按执行上下文约束 action、场景上的函数及其限制。

### 20. Temporary scenarios

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/temporary-scenario/>
- **文档摘要**：本页是 Temporary scenarios（临时场景）的操作教程，介绍仅存在于当前会话、不持久化且不共享的临时场景，适合快速 ad-hoc what-if 分析。提供完整的 Workshop 教程：依次添加 Scenario Manager、Object Table、Scenario Summary、Button Group（配置 action）、Chart、Metric Card 等组件，并说明如何将其元数据持久化。

### 21. Store scenario metadata as objects

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/persisted-scenario/>
- **文档摘要**：本页（Beta）介绍如何将场景元数据作为 Ontology 对象持久化存储（persisted scenario），使场景可共享、可协作并继承 Foundry 的权限与治理。步骤包括在 Ontology Manager 中配置场景 object type（绑定 Ontology Scenario interface）、配置创建/保存 action、将已有临时场景持久化、新建场景对象，以及控制访问与治理。

### 22. Merge scenarios

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/merge-scenario/>
- **文档摘要**：本页说明如何通过 action type 合并（merge）场景，将场景沙箱中暂存的编辑（创建/修改/删除对象、增删链接）以单笔事务提交到主 Ontology。介绍在 Ontology Manager 中创建场景类 action、定义作用范围（相关 object types 与 link types），以及在安全与提交条件页配置可执行合并的用户权限，也可给既有 action 添加 Apply Scenario 规则。

### 23. Use scenarios with OSDK

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology/osdk-scenario/>
- **文档摘要**：本页（实验性）介绍如何在 Ontology SDK（OSDK）中使用场景：场景作用域的客户端行为同常规 OSDK 客户端，但读写与 action 均作用于隔离沙箱。分别给出 TypeScript 与 Python OSDK 的操作方式——创建/挂载场景、查询对象、应用 action、合并场景、列出已编辑实体，并提示 API 仍在演进中可能变化。
