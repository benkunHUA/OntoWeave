# 接口（Interfaces）

> **分类说明**：Interface 的创建、实现、编辑与扩展机制

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `interfaces` |
| 文档数量 | 9 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Interfaces • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/interface-overview/>
- **文档摘要**：本页是 Interfaces（接口）模块的概述。Interface 是一种描述 object type 形态与能力的 Ontology 类型，可对共享相同形态的对象类型进行一致建模。页面介绍接口特性（接口属性、link type 约束、action type 约束、可继承扩展）、接口与 object type 在功能与视觉上的差异、接口权限、当前支持程度，以及如何开始使用接口。

### 2. Interfaces • Create an interface

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/create-interface/>
- **文档摘要**：本页是创建 interface 的操作指南。先在 Ontology Manager 中确认目标 Ontology，然后通过 New > Interface 或侧栏 Interfaces 入口新建接口；引导页介绍接口基本信息后进入配置。页面还说明如何可选地为接口添加 interface link types（链接到其他接口或对象类型）和 interface action type constraints（定义实现对象类型应具备的动作能力）。

### 3. Interfaces • Implement an interface

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/implement-interface/>
- **文档摘要**：本页讲解如何让 object type 实现（implement）一个 interface。对象类型须具备满足接口所需属性、link type 约束与 action type 约束的属性与链接。页面分步说明在 Ontology Manager（选择接口与对象类型、映射本地属性/链接/动作约束、保存）与 Pipeline Builder（打开输出类型配置、选择接口、映射属性、查看）中实现的两种方法。

### 4. Interfaces • Edit an interface definition

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/edit-interface-definition/>
- **文档摘要**：本页介绍如何编辑 interface 定义。由于接口暴露 API name，任何改动都可能破坏下游应用及既有实现，新增必需属性/约束时须在同一更新中同步修改所有实现对象类型。页面逐项说明添加/删除属性、添加共享属性、添加/移除或编辑 link type 与 action type 约束，并建议同时更新接口定义与消费者。

### 5. Interfaces • Edit an interface implementation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/edit-interface-implementation/>
- **文档摘要**：本页讲解如何编辑已应用于对象的 interface 实现。在 Ontology Manager 中打开含该实现的对象，可删除整个实现（Remove interface），或修改实现映射（更新用于实现接口的属性或 link type）后保存。页面提供了删除与更新映射两类操作的具体步骤。

### 6. Interfaces • Interface link types

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/interface-link-types-overview/>
- **文档摘要**：本页介绍 Interface link type constraints（接口链接类型约束），它定义了所有实现接口的对象类型之间共有的对象-对象关系。页面说明链接约束的参数（链接目标类型、具体目标、基数、是否必需），并对比两种链接目标——以接口为目标的抽象关系建模与以具体 object type 为目标的具象关系建模，以及相应用法示例。

### 7. Interfaces • Interface action type constraints

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/interface-action-type-constraints/>
- **文档摘要**：本页介绍 Interface action type constraints（接口动作类型约束，目前处于 Beta 阶段）。它定义实现接口的对象类型应具备的通用动作能力（显示名、描述、API name、参数形状），各对象类型再将约束映射到具体 action type。页面说明动作约束、参数约束、实现方式、接口扩展、必需与可选约束的取舍，以及当前支持程度与约束语义等限制。

### 8. Interfaces • Extend an interface

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/extend-interface/>
- **文档摘要**：本页讲解如何扩展（extend）一个 interface，将多个接口组合成更具体的接口，特别适用于构建实现多个能力接口的抽象对象接口。被扩展接口会继承其共享属性、link type 约束与 action type 约束，且可扩展任意数量的接口。页面提供在 Ontology Manager 中通过 Extension 面板添加/移除扩展的操作步骤。

### 9. Interfaces • Metadata reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/interfaces/interface-metadata/>
- **文档摘要**：本页是 Interface 的元数据参考手册，列出接口在 Ontology 中由以下元数据表示：RID（自动生成的唯一标识）、图标、显示名、描述、API name、状态（active/experimental/example/deprecated）、可搜索性（searchable，含实现对象类型数量上限）。这些信息用于平台内标识、展示与错误定位。
