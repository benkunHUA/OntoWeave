# 对象权限（Object Permissioning）

> **分类说明**：本体资源与对象数据的细粒度权限、安全策略与访问控制

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-permissioning` |
| 文档数量 | 7 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Object permissioning • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/overview/>
- **文档摘要**：本页是 Object permissioning 的总览，介绍 Foundry Ontology 对全部本体实体（object types、link types、action types 以及对象与链接数据本身）提供的细粒度安全控制。文档将授权结构分为两个层面：Ontology resources（定义 schema 的资源，如对象类型、链接类型）与 objects and links（实际主键值与属性值的数据），并分别指向各自的权限说明。

### 2. Object permissioning • Ontology permissions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/ontology-permissions/>
- **文档摘要**：本页讲解 Ontology permissions 的基于项目（project-based）的权限模型。查看、编辑与管理本体资源的权限通过 Compass 文件系统管理，资源保存在项目（project）中，由项目决定谁能访问。该模型取代了旧的 ontology roles 与 datasource-derived permissions，带来统一权限模型、批量管理、权限可解释性等好处，并支持 CBAC（classification-based access controls）。页面还涵盖项目权限示例、查看权限、链接/Action 编辑权限及打包安装等内容。

### 3. Object permissioning • Legacy ontology permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/ontology-permissions-legacy/>
- **文档摘要**：本页是 Ontology permissions 的遗留（Legacy）文档，说明已被 Compass 文件系统管理取代的旧权限模型。内容包括 Ontology roles（直接对本体资源授予角色，如 Ontology Owner、Ontology Editor，独立于底层 datasource）与 datasource-derived permissions（依赖底层 datasource 的权限，要求 1:1 绑定 backing dataset）两种方式，并分别列出各自下的查看权限、类型级编辑权限、只读视图及删除 backing dataset 等细节。

### 4. Object permissioning • Managing object security

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/managing-object-security/>
- **文档摘要**：本页介绍如何在 Ontology 中管理对象安全（object security）。Ontology 数据可通过 object and property policies 或 data source policies 来保护，二者均支持 cell-level security（行级与列级安全的结合，即特定行特定列）。页面对比了 object/property policies 与 data source policies（基于 restricted views 和 MDOs 配置）的差异，并说明如何通过 Check access 面板检查访问权限，以及附属资源的可见性注意事项。

### 5. Object permissioning • Configuring restricted-view-backed object types

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/configuring-rv-access-controls/>
- **文档摘要**：本页讲解如何配置基于 restricted views (RVs) 的 object types 以实现行级访问控制。RVs 在 dataset 级别配置，使 ontology 对象继承其中的精细权限，从而比直接授权整个 dataset 或某类型全部对象更细粒度。页面涵盖使用 RVs 作为 object type backing、Ontology edits（编辑可能影响安全属性从而影响可见性）、以及 OSv1 与 OSv2 在查看受限对象时的不同安全配置要求。

### 6. Object permissioning • Multi-datasource object types (MDOs)

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/multi-datasource-objects/>
- **文档摘要**：本页介绍 multi-datasource object types (MDOs)，即由多个 datasource 支撑的对象类型，仅适用于 Object Storage v2。目前仅支持 Foundry datasets 或 restricted views，不支持 streaming。MDO 分为 column-wise（类似 join，支持列级访问控制）与 row-wise（类似 union，支持行级控制，但 Foundry 仅支持 column-wise）。页面提供配置步骤并附带大量 FAQ，涵盖可用性、编辑与 materialization 支持、属性 multiplicity 等问题。

### 7. Object permissioning • Object security policies

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-permissioning/object-security-policies/>
- **文档摘要**：本页详解 object and property security policies 的配置与行为。Object security policies 在对象类型上配置视图权限以实现行级安全，property security policies 保护特定属性实现列级安全，二者结合即为 cell-level security，且仅在读取时强制。页面涵盖配置权限、下载权限、粒度策略、强制控制、带策略的 materializations、策略测试，以及从 restricted views 迁移到 object security policies 的方法及限制。
