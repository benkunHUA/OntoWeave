# 操作类型（Action Types）

> **分类说明**：Action Type 的参数、规则、副作用、通知、Webhook、权限与监控

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `action-types` |
| 文档数量 | 38 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Action types • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/overview/>
- **文档摘要**：本页是 Action types 的概述，介绍 Foundry Ontology 中 Action 与 Action Type 的核心概念。Action 是改变一个或多个对象属性、属性值与链接的单次事务，而 Action Type 是一组可由用户一次性执行的编辑及附带 Side Effect 行为的定义。页面以「Assign Employee」为例，说明了参数定义、自动建链、通知 Side Effect 与提交校验等能力，并强调 Ontology 将概念映射到真实数据，使同一套逻辑与校验可跨应用保持一致。

### 2. Action types • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/getting-started/>
- **文档摘要**：本页是创建 Action Type 的快速入门教程，指导用户构建一个用于修改工单优先级（Priority）的简单 Action Type。教程依次覆盖先决条件、新建 Action Type、编辑参数、添加 Submission Criteria（限定优先级为 P0/P1/P2 且状态为 Open）、将 Action 添加到 Object View、应用 Action，以及解决用户编辑与数据源更新的冲突。页面还提醒需为 Object Storage v2 启用编辑开关或 v1 创建回写数据集。

### 3. Action types • Explore other action types

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/explore-action-types/>
- **文档摘要**：本页介绍除入门教程中「修改单对象属性」之外、Action Type 可表达的更丰富能力。它逐一概述了创建/删除对象、管理对象间链接、通过 Function 运行自定义逻辑、调用外部系统的 Webhook、提交时通知用户、触发构建（Trigger a build）、合并场景编辑、作用于 Interface、映射 Struct 属性等规则，说明各自适用场景并链接到对应参考文档，帮助用户在统一配置界面中扩展 Action Type 的能力。

### 4. Action types • Use actions in the platform

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/use-actions/>
- **文档摘要**：本页介绍如何在不同 Foundry 应用中配置并应用 Action Type。页面说明了 Action 可无缝集成到 Object views（通过 Actions 区）、Object Explorer（自动出现在 Exploration View 的 Actions 下拉菜单等三处）以及 Workshop（通过 Button group widget，并支持变量、当前用户、时间戳等默认值与多种布局）。文中区分了基于对象引用的 single action type 与基于对象列表参数的 bulk action type，并指出各应用仅展示适用于当前选择对象的 Action。

### 5. Action types • Test run

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/test-run/>
- **文档摘要**：本页介绍 Action Type 的 Test run（测试运行）功能，让用户在 Ontology Manager 中模拟 Action 而无需真正写入数据。页面说明了如何运行测试（需在 form 布局下、保存配置后提交参数值），以及如何解读结果：Proposed changes 标签列出拟创建的增删改对象与链接，Details 标签说明执行过程与外部调用。测试会按当前分支与用户权限执行，强制对象安全与 Submission Criteria，但会跳过实际外部调用并存在一定限制。

### 6. Action types • Rules

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/rules/>
- **文档摘要**：本页是 Action Type 中 Rules 的参考文档，说明规则如何将参数转化为 Ontology 编辑或其他效果。规则分两大类：编辑 Ontology 的规则（Create/Modify/Delete object、Create/Delete link 等）与触发其它 Foundry 效果的规则。页面详述了 Ontology rules 中各类规则的行为、主键与外键链接的处理、值及参数的使用、无效组合，以及 Function rule 等其它规则，是配置 Action 逻辑的核心参考。

### 7. Action types • Parameters • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/parameter-overview/>
- **文档摘要**：本页概述 Action Type 的参数（Parameters）概念。参数是 Action Type 的输入，是 Rules 与 Workshop、Slate、Object Views 等应用之间的接口，作为承载外部值的变量存在。每个参数由类型决定可取值，并可单独配置是否暴露在表单中、是否允许用户修改；参数可在规则、Side Effect、Submission Criteria、覆盖（override）中被引用，也可从宿主应用接收值以捕获动作执行前的属性值。

### 8. Action types • Parameters • Set parameter default value

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/parameters-default-value/>
- **文档摘要**：本页说明如何为 Action Type 参数设置默认值（Default value）以预填表单。默认值在参数级别配置，并支持 Workshop、Object Explorer、Object Views、Quiver 与 Slate，可统一跨应用标准化 Action 逻辑、免去各自配置。页面覆盖了静态默认值、对象属性默认值、Type class 预填三种方式，并强调本地变量（如 Workshop 变量、Object View 环境变量、Slate 默认值）始终优先于全局默认值，迁移时须移除本地覆盖。

### 9. Action types • Parameters • Filter results of a parameter dropdown

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/parameters-filter/>
- **文档摘要**：本页讲解如何为 Action Type 参数下拉菜单添加过滤（Filter），以限定可选值。针对多选参数，可从对象集获取选项并基于链接对象属性派生；针对对象下拉菜单，可配置过滤器与 Search Arounds，使表单仅渲染匹配的、且用户有权查看的对象，并在执行前校验所选值。页面还说明了下拉项展示的显著属性、支持的操作（按属性过滤、更改起始对象集、Search Arounds）以及数据隐私影响。

### 10. Action types • Parameters • Object dropdown security considerations

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/dropdown-security/>
- **文档摘要**：本页提示对象下拉菜单（Object dropdown）验证中使用静态值过滤器的安全风险。这类静态过滤条件对所有可查看 Action Type 的用户可见，可能泄露其无权查看的对象的属性值组合；后端虽会屏蔽敏感信息，但在查看 Action 表单时该验证会转为对象集，用户可从网络请求中读到过滤条件（如 Investigation Name）。文档建议改用对象属性或参数来过滤对象集，避免直接暴露敏感属性值，是配置对象下拉时的安全最佳实践。

### 11. Action types • Parameters • Override parameter configurations

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/parameters-override/>
- **文档摘要**：本页介绍如何通过 Override（覆盖）在特定条件下改变参数的行为与配置，从而免于为细微差异单独创建多个 Action Type、改善用户提交体验。文档说明了添加/编辑覆盖的位置，以及由「If 条件」与「Then 覆盖」组成的 Override block；每个参数可含多个覆盖块，但若多个为真仅执行第一个。覆盖条件仅可使用位于当前参数上方的参数，与 Submission Criteria 的条件配置类似。

### 12. Action types • Parameters • Performance considerations

- **文档类型**：最佳实践
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/parameter-performance-considerations/>
- **文档摘要**：本页讨论 Action Type 参数配置中的性能考量。参数之间的依赖（如默认值、多选选项的定义）会影响表单加载耗时：当后一个参数的默认值或选项引用前一个参数时，系统需迭代依次解析，形成串行延迟。文档建议尽量保持依赖层级扁平，例如让第三个参数的对象集直接引用第一个而非第二个参数，从而并行派生信息、降低从打开表单到可交互的总延迟。

### 13. Action types • Submission criteria

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/submission-criteria/>
- **文档摘要**：本页是 Submission criteria（提交条件，原称 validations）的参考文档，定义决定 Action 能否提交的条件。通过将基于上下文（当前用户、参数、执行上下文）的条件与静态信息组合成逻辑语句，可将业务逻辑编码进数据编辑权限，保障 Ontology 数据质量与治理。页面详述了三类条件模板、取值与各类操作符、逻辑运算符，以及提交失败时的 Failure message，并给出航班—飞机状态校验的示例。

### 14. Action types • Actions on interfaces

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/actions-on-interfaces/>
- **文档摘要**：本页介绍在 Action 中使用 Interface（接口）的能力，可创建适用于某接口下所有对象的通用 Action。主要通过两种方式：Interface action rules（创建/修改/删除/链接接口对象）与 Interface reference parameters（引用接口对象）。文档覆盖了各类接口动作规则的创建、执行、权限控制，以及接口动作类型约束（interface action type constraints）等建模能力，并说明了对所有实现该接口的对象类型统一生效的提交条件及其支持的应用与限制。

### 15. Action types • Actions on structs

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/actions-on-structs/>
- **文档摘要**：本页介绍如何通过 Action 创建与修改 Struct 属性值。Struct 参数（基类 STRUCT）包含具名且各自带类型的嵌套字段，只能用于为 Struct 属性提供值，支持的字段基础类型包括 BOOLEAN、STRING、TIMESTAMP 等。文档说明了 Struct 参数的定义、在 Action 表单中作为分组渲染、对各字段单独设置默认值与约束，以及如何将参数字段映射到对象类型的 Struct 属性字段，并列出相关限制。

### 16. Action types • Function-backed actions • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/function-actions-overview/>
- **文档摘要**：本页是 Function-backed actions（函数支撑的 Action）的概述，说明当简单规则不足以描述所需变更时，可让 Action Type 调用一个函数（Ontology Edit function）来定义对象修改逻辑。典型场景包括批量修改相互关联的对象、依据复杂业务计算写入属性、创建多类对象并建链等。这类 Action 可支持任意复杂度，但受 Action Type 限制与函数执行限制约束，页面并引导读者通过教程上手。

### 17. Action types • Function-backed actions • Getting started

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/function-actions-getting-started/>
- **文档摘要**：本页是创建 Function-backed action 的快速入门教程，讲解如何构建一个由 Ontology Edit function 支撑的 Action Type。教程涵盖先决条件（使用 Demo Ticket 对象类型、基于 functions on objects TypeScript 模板建立仓库、导入对象类型并发布带 @OntologyEditFunction() 注解的函数），以及在 Rules 中添加 Function 类型规则、将输入映射到 Action 参数、切换函数版本与自动升级等步骤，并强调函数规则不可与其他 Ontology 规则混用。

### 18. Action types • Function-backed actions • Batched execution

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/function-actions-batched-execution/>
- **文档摘要**：本页介绍 Function-backed action 的批量执行（Batched execution）。当 Action 在 Workshop 内联编辑或 Automate 等场景中被批量触发时，默认函数为每次请求顺序调用一次、编辑在最后原子提交；为提升性能或解决编辑冲突，可将函数改为接收包含结构体列表（map/dictionary）的单个输入参数，从而在一次执行中处理整批调用。文档通过 updateDestination 与 updateDestinationBatch 的示例签名说明了配置方式。

### 19. Action types • Side effects • Overview

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/side-effects-overview/>
- **文档摘要**：本页是 Action Type 的 Side effects（副作用）概述。为支持组织完整的决策流程，除用规则修改对象外，Action Type 还提供两类 Side effect：Notifications（在 Action 应用时灵活通知用户，包括向平台用户发送邮件）与 Webhooks（以高度灵活方式连接 Foundry 外部系统，如调用 REST API 或 ERP，实现向其它源系统写入或经消息系统发送通知）。文档并引导读者查看通知与 Webhook 的设置指南。

### 20. Action types • Side effects • Notifications

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/notifications/>
- **文档摘要**：介绍 Action Type 中的 Notifications（通知）Side Effect，可通过 Add new rule 添加。支持配置收件人与内容（Content 可选 Template 或 Function，含 Subject、Body、Link），另说明收件人数、长度上限与严格脱敏等要点。

### 21. Action types • Side effects • Set up a notification

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/set-up-notification/>
- **文档摘要**：分步教程，演示为 Action Type 配置 Notification。先决条件是完成入门教程并给 Alert 对象添加 assignee 属性。依次讲解添加通知、配置收件人（来自对象属性的用户）、配置内容与链接，以及发送测试通知。

### 22. Action types • Side effects • Webhooks

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/webhooks/>
- **文档摘要**：讲解 Action Type 中的 Webhooks（Web 钩子）Side Effect，可向外部系统发请求。区分 Writeback（先于其他规则执行，失败则不改 Ontology）与 Side Effect（之后执行）两种配置，并涵盖输入/输出参数及 OAuth 2.0 认证。

### 23. Action types • Side effects • Set up a webhook

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/set-up-webhook/>
- **文档摘要**：配置 Webhook 的简短教程。先决条件是完成入门教程并可能需要管理员协助连接外部系统。步骤为进入 Action 的 Logic 标签页，选 Add new rule 后选 Webhook，即可添加并连接到外部系统。

### 24. Action types • Trigger schedule build

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/trigger-schedule-build/>
- **文档摘要**：介绍在 Action Type 配置 Trigger schedule build（触发计划构建）规则。配置后 Action 被应用即触发所选 schedule 构建（须 project-scoped 模式）。权限由提交标准管理，并可通过 schedule run RID 在 Ontology 编辑规则中追踪进度。

### 25. Action types • Configure sections

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/configure-sections/>
- **文档摘要**：讲解为 Action 表单配置 Sections（分区），可对参数逻辑分组并支持多列、描述与条件覆盖。在 Form 标签页点击 Add section 设置标题与列布局；参数可通过分区配置视图或拖拽加入分区，显示顺序由 Form Content 决定。

### 26. Action types • Upload media

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/upload-media/>
- **文档摘要**：介绍 Action 的 Upload media（上传媒体文件）能力，推荐用 media reference 属性（基于 media set），较 attachment 更具可扩展性与格式支持。用户经表单或表格上传，文件提交成功后持久化到 media set，权限由 Action 提交标准管理。

### 27. Action types • Upload attachments

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/upload-attachments/>
- **文档摘要**：介绍 Action 的 Upload attachments（上传附件），支持从 Workshop、Object Explorer 等多处上传。需在 Action Type 与 Object Type 两侧配置 Attachment 类型，多文件需 Allow multiple。附件加入表单即上传，提交失败则自动删除。

### 28. Action types • Scale and property limits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/scale-property-limits/>
- **文档摘要**：汇总 Action Type 的 Scale and property limits（规模与属性限制），超限将导致失败。涵盖配置限制（列表元素上限）、编辑限制（单次最多 50 个对象类型、10000 个对象）、批量调用限制（最多 10000 次）及支持的属性类型（单值/数组，含 OSv1/OSv2 差异）。

### 29. Action types • Inline edits

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/inline-edits/>
- **文档摘要**：介绍 Action-backed inline edits（内联编辑），每个参数可选并默认取对象当前值，可逐项修改属性。分别说明 Object Explorer（仅改单一对象、启用默认值）与 Workshop 中的配置、批量校验提交机制及有效/无效情形。

### 30. Action types • Permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/permissions/>
- **文档摘要**：系统说明 Action Type 的 Permissions（权限）：谁能查看、编辑、应用 Action。应用权限取决于提交标准（Submission criteria）、读写授权与 Action log 权限；对象编辑权限视是否仅限 Action 编辑或需 dataset 写权限，另提及行列访问控制仅读取时强制。

### 31. Action types • Read and write authorizations

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/read-write-authorizations/>
- **文档摘要**：介绍 Beta 阶段的 Read and write authorizations（读写授权），为 Action Type 增加安全边界。Read authorization 限制可读取的带标记数据上限；Write authorization 设定创建或修改数据须满足的最低安全要求。二者补充而非替代既有权限与提交标准。

### 32. Action types • Consistency and isolation

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/consistency-guarantees/>
- **文档摘要**：讲解 Action Type 的 Consistency and isolation（一致性与隔离）。Action 以单事务编辑 Ontology，提供 ACID 保证（Side Effect 与函数外部调用不在原子范围）。详述写入机制、隔离级别（Legacy、Snapshot、Read committed）与重试。

### 33. Action types • Monitoring

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/monitoring/>
- **文档摘要**：介绍 Action 的 Monitoring（监控）能力，支持两类规则：Action duration p95 与窗口内失败次数超阈告警。说明通过监控视图与规则设置阈值和严重级别，动态作用域（Workflow Lineage、Workshop、OSDK 应用）可自动追踪相关 Action。

### 34. Action types • Undo or revert Actions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/action-reverts/>
- **文档摘要**：介绍 Action 的 Undo or revert（撤销/回滚）能力。新 Action 默认可回滚，仅限 Object Storage v2 且只能由操作者本人回滚。说明如何在 Form 标签页开启 Allow revert、在成功提示中点击 Undo，以及对象已有后续编辑则无法回滚等注意事项。

### 35. Action types • Branching action types

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/branching-action-types/>
- **文档摘要**：介绍 Branching action types（分支 Action 类型），与 Global Branching 集成，可在分支测试不影响生产。说明在分支运行 Action 的先决条件（对象类型须建索引）、function-backed action 的差异，以及分支上 Side Effect 的管理与已知限制。

### 36. Action types • Action metrics

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/action-metrics/>
- **文档摘要**：介绍 Action metrics（Action 指标），展示近 30 天使用情况，可在 Ontology Manager 或 Workflow Lineage 查看。含成功/失败计数与 P95 执行时长，并提供失败类型分类（无效参数、规模超限、认证、Side Effect、函数等失败）及权限说明。

### 37. Action types • Action log

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/action-log/>
- **文档摘要**：介绍 Action log（动作日志），将成功的 Action 提交建模为对象类型，用于决策分析与监控 Ontology 变更。说明其适用场景（区别于 edit history、metrics、撤销）、配置位置、日志 Schema 与限制，并强调仅记录成功提交不记录失败。

### 38. Action types • Add action types to a Marketplace product

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/action-types/marketplace-action-types/>
- **文档摘要**：说明如何将 Action Type 加入 Marketplace 产品（借助 Foundry DevOps）供其他用户复用。多数功能受支持，但引用含不支持特性的对象类型除外；打包前须将 Security & Submission criteria 中的用户引用改为组，并给出添加步骤。
