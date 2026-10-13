# 对象与链接类型

> **分类说明**：Object Type、Property、Link Type、Struct、Value Type 等本体建模要素

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-link-types` |
| 文档数量 | 45 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Object and link types • Types reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/type-reference/>
- **文档摘要**：本页是 Foundry Ontology 的类型参考手册，系统列举了定义 Ontology 时可用的各类类型。它区分 Ontology types（如 Object type、Shared property、Link type、Action type、Object type groups、Interfaces）与 Value types（用于表示数据值，灵感来自 RDF/OWL/XSD），并解释了 object type 与 object 实例的区别，是理解 Ontology 建模基础的权威索引。

### 2. Object and link types • Object types • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/object-types-overview/>
- **文档摘要**：本页概述了 Object type 的核心概念：object type 是对真实世界实体或事件的 schema 定义，object（对象实例）是单个实体，object set（对象集）是多个实例的集合。页面以 Employee、Flight 为例说明，并指出 Ontology 中的 object type、object、object set 分别对应于数据集的 schema、行、过滤行集合，强调 Ontology 将概念映射到实际数据。

### 3. Object and link types • Object types • Create an object type

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/create-object-type/>
- **文档摘要**：本页是创建 Object type 的操作指南。主要方式是通过引导式分步助手（guided helper）完成，涵盖选择 backing datasource、创建无 backing datasource 的对象类型、填写元数据、创建 property、配置 primary key 与 title key、生成 actions、保存位置、配置安全策略与保存变更等步骤；若中途退出也可手动补全。还提及可用 SuperRepo 以 Ontology-as-code 方式编程创建。

### 4. Object and link types • Object types • Edit object types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/edit-object-type/>
- **文档摘要**：本页讲解如何编辑 Object type 及其 property，并强调编辑可能带来破坏用户应用的后果。它分“无 writeback”和“有 writeback”两种情况说明潜在 breaking changes（如更改 backing datasource、primary key、删除对象类型会触发重索引），并介绍导航、删除、增删/更改 backing datasource、编辑元数据与故障排查等操作步骤。

### 5. Object and link types • Object types • Enable Gotham integration

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/enable-gotham-integration/>
- **文档摘要**：本页说明如何通过对 Foundry 与 Gotham 进行 type mapping 来启用 Gotham 集成。若 enrollment 已包含 Map Rendering Service（MRS）则无需 type mapping。页面介绍如何检查 MRS、何时对 object type 做 type mapping、在 Ontology Manager 中开启该开关，以及配置 object type 的父类别与 Gotham property types，实现跨 Foundry/Gotham 的统一表示。

### 6. Object and link types • Object types • Create Ontology objects from Gaia

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/create-ontology-objects-from-gaia/>
- **文档摘要**：本页介绍如何从 Gaia 地图（绘制的形状、落点、战术图形）创建 Ontology 对象。需先安装 Marketplace 产品并让 object type 实现 Creatable Geoshape、Creatable Geopoint 或 MILSTD 2525C Symbol 接口（替代即将弃用的旧接口），并创建支持对象创建的 action type。还说明如何用 interface action type constraints 自动发现支持对象创建的对象类型。

### 7. Object and link types • Object types • Metadata reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/object-type-metadata/>
- **文档摘要**：本页是 Object type 的元数据参考手册，列出其在 Ontology 中的各项元数据：ID、RID、Icon、Display name、Plural display name、Description、Groups、API name、Visibility、Status 等，逐一解释其用途与示例，帮助用户在配置应用与 Ontology 时理解并引用对象类型的元数据字段。

### 8. Object and link types • Properties • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/properties-overview/>
- **文档摘要**：本页概述 Property 概念：property 是 object type 对真实世界实体特征的 schema 定义，property value 是单个对象上该特征的值。以 Employee、Flight 为例说明，并指出 property 与 property value 分别对应于数据集的列与字段。同时列出支持的 property types，以及通过 backing datasource 或 actions 创建属性值的方式。

### 9. Object and link types • Properties • Edit object type properties

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/edit-properties/>
- **文档摘要**：本页讲解如何在属性编辑器中编辑 object type 的 property，包括删除 property（active 状态不可删）、更换 backing 列（Unlink/Map to a column）、编辑 property type 元数据（Type classes、Render hints、Visibility）以及批量编辑多个 property。提醒删除或改动需保存后才生效，并可能影响引用该属性视图与应用的契约。

### 10. Object and link types • Properties • Add value formatting

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/value-formatting/>
- **文档摘要**：本页说明如何为 property 添加 value formatting（值格式化），将原始值转换为更易读的形式。支持的类型包括数值格式化（货币/单位/紧凑/科学计数）、日期时间格式化、User ID、Resource RID、Artifact GID 格式化。页面给出在属性编辑器中的配置步骤、时区选项，并附 FAQ 说明其与 type classes、Object Views 可编辑属性的兼容性。

### 11. Object and link types • Properties • Add conditional formatting

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/conditional-formatting/>
- **文档摘要**：本页介绍 conditional formatting（条件格式化）：为任意 property 配置规则以决定其值在用户应用中的渲染方式（如颜色、对齐）。配置在 Ontology Manager 中完成，规则会作用于 Object Explorer、Object Views、Quiver、Workshop。页面涵盖用 Rule editor 编辑规则、复制规则，以及条件格式化与 type classes、可编辑属性兼容性的 FAQ。

### 12. Object and link types • Properties • Metadata reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/property-metadata/>
- **文档摘要**：本页是 Property 的元数据参考手册，列出 property 在 Ontology 中的元数据：ID、Display name、Description、RID、Status、API name、Keys（title key 与 primary key 说明）等，并解释其用途与示例。还提示部分 property base types 支持有限，帮助用户理解并正确引用属性字段。

### 13. Object and link types • Properties • Edit-only properties

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/edit-only-properties/>
- **文档摘要**：本页介绍 edit-only properties（仅编辑属性）：可定义不直接映射到对象类型 backing dataset 列的 Ontology 属性，便于在不修改底层数据集的情况下存储附加信息。要点包括不映射列、必须权限归属于某一 backing dataset、仅限 Object Storage v2，并说明如何创建及后续将属性映射到数据集新增列。

### 14. Object and link types • Properties • Required properties

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/required-properties/>
- **文档摘要**：本页介绍 required properties（必填属性）：object type 的某些 property 必须取值，用于校验不存在 null 或空数组。校验在索引时进行（Object Storage v2 专属），通过 actions 写入空值会在应用时报错。页面说明如何创建必填属性、允许空数组、以及在多 backing datasource 对象类型上的注意事项。

### 15. Object and link types • Properties • Mandatory control properties

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/mandatory-control-properties/>
- **文档摘要**：本页介绍 mandatory control properties（强制性控制属性），用于对对象中数据做细粒度访问控制，可限制同一 datasource 内其他 property 仅对满足控制条件的用户可见（仅 Object Storage v2）。类型包括 Markings、Organizations、Classifications，并涵盖 datasource 级权限、校验、在 actions 中的使用及 Marketplace 用法。

### 16. Object and link types • Properties • Base types

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/base-types/>
- **文档摘要**：本页是 Property base types（属性基础类型）参考，说明 base type 定义 property 可存储的数据种类及可用操作。除 Map、Binary 外所有字段类型均可作 base type，并列出 Vector、Geopoint、Geoshape、Attachment、Time series、Struct、Media reference、Cipher text 等高级类型，及可数组化的限制；还介绍 Media references 与 Structs 等复杂类型。

### 17. Object and link types • Properties • Property reducers

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/property-reducers/>
- **文档摘要**：本页（Beta）介绍 property reducers：将数组 property 规约为数组中的单个值，用于显示与接口实现，而不改变底层 property 类型或数据。适用于数值、时间、字符串、布尔及 struct 数组（按支持的基础类型字段）。页面涵盖适用场景、支持/不支持的 base types、单/多 reducer 配置、在应用中的呈现及与 interfaces 配合。

### 18. Object and link types • Properties • Derived properties

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/derived-properties/>
- **文档摘要**：本页（Beta）介绍 ontology 定义的 derived properties（派生属性）：在运行时基于 linked objects 的值计算，通过 link type 拉取关联对象信息并可应用聚合（平均、计数、收集为列表）。给出在 Ontology Manager 中配置步骤（选择 Linked objects 源、link type、聚合、派生属性、集合上限），并说明多跳派生、识别方式与限制。

### 19. Object and link types • Structs • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/structs-overview/>
- **文档摘要**：本页概述 Structs：一种 Ontology property base type，让单个 property 容纳多个具名、具类型的字段（如 Full Name 含 firstName、lastName）。说明 struct 由单一 struct 类型列 backing，以及配置约束（不可嵌套 struct、字段不可为数组、至少一字段、支持的字段类型）、查询语义（按字段独立求值）与当前支持程度。

### 20. Object and link types • Structs • Create a struct type

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/create-struct-type/>
- **文档摘要**：本页是创建 struct property type 的操作指南：在 Ontology Manager 的对象类型详情页打开 Properties 标签，点击 Create property，在 Base type 下拉中选择 Struct，于 Data 区选择 Backing column，并在 Struct fields 区 Add field、New field 命名后映射数据源列，即可完成 struct 属性创建。

### 21. Object and link types • Structs • Edit struct types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/edit-struct-type/>
- **文档摘要**：本页讲解如何编辑 struct property type：在 Ontology Manager 中选择对象类型下的相关 struct property，于 Struct fields 区选择要编辑的字段，顶部会显示编辑次数。特别提醒：更改 struct field 的 API name 会生成新的 struct field RID 并覆盖已有索引（类似改动 property ID），需同步更新引用该字段的应用。

### 22. Object and link types • Structs • Automapping struct types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/struct-automapping/>
- **文档摘要**：本页介绍 struct properties 的自动映射（automapping）。已创建的对象可在 Ontology Manager 的 Properties 标签下用 Automap all 自动映射所有列；尚未创建的对象可在 Pipeline Builder 中于新建对象类型向导的 Properties 步骤添加属性后完成自动映射（通过 All Actions > Create object type），免去手动逐列映射。

### 23. Object and link types • Structs • Structs and shared properties

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/struct-shared-properties/>
- **文档摘要**：本页说明 Struct property 与 shared property 的关系：struct 可用于本地与共享属性类型；将本地属性提升为共享属性时需重新映射 struct fields，本地属性会继承共享属性的字段元数据（display name/description/aliases）但保留原 RID。页面给出创建 struct type 共享属性、附加共享属性、及转换为共享属性的操作步骤。

### 24. Object and link types • Structs • Designate struct main fields

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/struct-main-fields/>
- **文档摘要**：本页介绍 Struct main fields（Beta），允许为 Struct 指定核心值与补充元数据（如 Address 中 streetName/postalCode 为主字段）。涵盖何时使用、在 Ontology Manager 配置主字段、在 Workshop 的紧凑展示、与 interfaces 配合及结合 property reducers，并给出限制与常见问题。

### 25. Object and link types • Shared properties • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/shared-property-overview/>
- **文档摘要**：本页概述 Shared properties（共享属性）：可在多个 object type 上复用、保持一致数据建模并集中管理元数据，但底层数据不共享。说明其可直接创建或转换已有属性，显示为带地球图标，例如 Employee 与 Contractor 共用 start date 属性。

### 26. Object and link types • Shared properties • Create shared properties

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/create-shared-property/>
- **文档摘要**：本页讲解在 Ontology Manager 的 Shared properties 页面创建共享属性：通过 New shared property 弹窗配置 Name、Description、Base type、Value formatting、Type classes、Render hints 等；Base type 决定操作，Render hints 影响重建索引性能。

### 27. Object and link types • Shared properties • Edit shared properties

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/edit-shared-property/>
- **文档摘要**：本页说明编辑与删除共享属性：在 Shared properties 页面选中目标属性即可编辑元数据；删除时勾选一个或多个属性后选择 Delete property，删除后所有使用该共享属性的 object type 会回退为普通属性。

### 28. Object and link types • Shared properties • Use shared properties on object types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/use-shared-property/>
- **文档摘要**：本页讲解在 object type 上使用共享属性：在 Ontology Manager 进入对象类型，选中属性并在 Shared Property 配置区块将其更新为共享属性；也可通过同一面板选择 Detach 解除属性与共享属性的关联。

### 29. Object and link types • Shared properties • Metadata reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/shared-property-metadata/>
- **文档摘要**：本页是 Shared properties 的元数据参考，列出其在 Ontology 中的元数据：Name、Description、RID、Base type、Value formatting、Type classes、Render hints、Visibility 等，并说明各项（如 RID 用于错误提示）的含义。

### 30. Object and link types • Link types • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/link-types-overview/>
- **文档摘要**：本页概述 Link types（链接类型）：它定义两 object type 间关系的 schema，link 是单实例关系。说明链接可存在于同类对象间但不支持跨 Ontology，并重点介绍 directionality——链接类型双向，每侧有独立 display name 与 API name 可分别遍历。

### 31. Object and link types • Link types • Create a link type

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/create-link-type/>
- **文档摘要**：本页讲解创建 link type，推荐用 Ontology Manager 引导式助手，含选择关系类型（foreign key、join table、backing object type）、定义链接资源、命名与保存，并涵盖 object-backed links 的前提与转换及 DatasetAndBranchAlreadyRegistered 等排查。

### 32. Object and link types • Link types • Edit link types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/edit-link-types/>
- **文档摘要**：本页说明编辑 link type 的注意事项与操作。列出无/有 writeback 两类链接类型的潜在 breaking changes（如改 cardinality、外键或删除会触发 Phonograph 注销重建），并介绍如何导航、删除链接类型、变更底层数据源及编辑元数据。

### 33. Object and link types • Link types • Metadata reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/link-type-metadata/>
- **文档摘要**：本页是 Link types 的元数据参考，列出其在 Foundry Ontology 中的元数据：ID、RID、Status、Object types、Cardinality、Key、遍历方向等，并用 Employee→Employer 等示例说明主键/外键如何定义链接及各字段含义。

### 34. Object and link types • Allow users to edit objects and links

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/allow-editing/>
- **文档摘要**：本页说明如何让用户在 Workshop、Object Views 等应用中编辑对象与链接，包括改属性、增删链接、创建删除对象，外部应用可通过 Objects API 写入。核心配置是在 Ontology Manager 创建 action types 并生成 writeback dataset 保存编辑。

### 35. Object and link types • Value types • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/value-types-overview/>
- **文档摘要**：本页概述 Value types（值类型）：围绕 field type 的语义封装，含元数据与约束，可增强类型安全、集中数据校验。值类型与 space 关联仅限该 space 复用，不适用于 Default ontology，相比基元类型能捕获数据语义（如正则约束 email 类型）。

### 36. Object and link types • Value types • Create a value type

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/create-value-type/>
- **文档摘要**：本页讲解创建 value type：在 Value Types Manager 选 space 后 Create New Value Type，填写名称、描述、API name，选 Base type，可定义约束（正则/枚举/范围等）与失败提示及示例预览，最后保存；约束方法因基类型而异。

### 37. Object and link types • Value types • Use value types

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/use-value-type/>
- **文档摘要**：本页说明 value type 的使用场景：赋给 object type 属性、shared property、Pipeline Builder 逻辑类型，或作 action parameter 约束。详述属性配置时选值类型（校验失败致索引失败）及对 String/Integer/Decimal/Date 等参数施加约束的方法。

### 38. Object and link types • Value types • Value type versions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/value-types-versions/>
- **文档摘要**：本页介绍 Value type 版本机制：版本含 metadata 与 constraints 两部分。名称等 metadata 可随时改；Base type 与约束不可变，改约束会建新版本；无消费者可自由改，有消费者的破坏性改动建议弃用旧类型新建，非破坏性改动自动传播到 Ontology。

### 39. Object and link types • Value types • Value type permissions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/value-types-permissions/>
- **文档摘要**：本页说明 Value type 权限：通过 platform space 管理，space 内值类型自动导入供关联 ontology 使用，其他消费者可在项目范围导入。有 View 权限的用户可赋值类型，Editor/Owner 可创建、编辑或删除值类型。

### 40. Object and link types • Value types • Value type constraints

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/value-type-constraints/>
- **文档摘要**：本页是 Value type 约束参考，列出各值类型可定义的约束及适用 Base type：Enum、Range、String 的 Regex/RID/UUID、Array 的 Uniqueness/Nested、Struct 的 Element constraints 等，指导在确保校验下配置类型约束。

### 41. Object and link types • Metadata • Type classes

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/metadata-typeclasses/>
- **文档摘要**：本页介绍 Metadata 的 Type classes，可应用于 properties、link types 与 action types，作被用户应用解释的附加元数据。以表格列出已知类型类及弃用状态、适用类型与在 Object Views、Quiver 等行为，部分已迁移至 Capabilities 页。

### 42. Object and link types • Metadata • Render hints

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/metadata-render-hints/>
- **文档摘要**：本页介绍 Metadata 的 Render hints（渲染提示），向 Object Storage v1 与用户应用传达属性使用信息，常影响重建索引性能。以表格列出各提示名称、描述及是否增加原始索引、是否需要重建索引等技术细节，并说明如何在属性面板勾选优化。

### 43. Object and link types • Metadata • Statuses

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/metadata-statuses/>
- **文档摘要**：本页说明 Ontology 资源的 Statuses 元数据：可选 active、experimental、deprecated、example，object type 另有 promoted。列出各状态含义、禁止操作、编辑与批量编辑方法，并提供属性/链接类型状态冲突等排查建议。

### 44. Object and link types • Object type groups

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/type-groups/>
- **文档摘要**：本页介绍 Object type groups（对象类型分组）分类原语，帮助搜索探索 ontology。涵盖在 Ontology Manager 创建管理分组、在搜索与 Object Explorer 发现、分组权限（需项目 viewer 权限），以及 2024 年 5 月从旧标签分组迁移的可见性与兼容性处理。

### 45. Object and link types • Add Ontology types to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-link-types/marketplace-ontology-types/>
- **文档摘要**：本页讲解用 Foundry DevOps 将 object type、link type、shared property、interface type 打包进 Marketplace 产品供复用。列出不支持的特性（Cipher、Geo time、Vector、流式数据源对象等），并分别说明添加各类 Ontology 实体的方法。
