# 对象监控（Object Monitors）

> **分类说明**：对象监控的创建、条件配置、评估、通知与错误处理

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `object-monitors` |
| 文档数量 | 11 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Object Monitors [Sunset] • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/overview/>
- **文档摘要**：Object Monitors 已进入 Sunset（淘汰）阶段，未来将弃用，建议迁移至向后兼容的 Automate。它让用户和应用构建者在 Foundry Ontology 数据变化时收到通知或自动提交 Actions。支持监控已保存搜索的新结果、自动发送站内/邮件/SMS/即时消息通知，或对满足条件的数据自动执行 Action，可用于监控与告警工作流。

### 2. Object Monitors [Sunset] • Create a new object monitor

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/create_new_object_monitor/>
- **文档摘要**：本页介绍如何创建 Object Monitor（已被 Automate 取代）。可通过 Object Explorer 保存 Exploration 后点击 Monitor 创建，或在 Object Monitors 应用中点击 Add Monitor 创建；需先将数据接入 Ontology。创建后可查看详情（保存位置、创建/更新时间、过期日期，可延长三个月），并建议多订阅者时存于共享 Project。

### 3. Object Monitors [Sunset] • Core concepts • Monitors

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/monitor/>
- **文档摘要**：本页讲解 Object Monitor 的核心概念——Monitor 资源。Monitor 定义了对一个或多个输入的条件，及条件满足时触发的 Action 或通知。Monitor 保存于 Foundry Project 层级，支持保存、分享、移动、删除与基于角色的访问控制；历史活动保留六个月；始终有过期日期，最长三个月且可由 Editor 角色用户延长。

### 4. Object Monitors [Sunset] • Core concepts • Inputs

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/input/>
- **文档摘要**：本页介绍 Object Monitor 的 Input（输入）概念。Monitor 输入由 Object Set 定义，条件可引用输入的属性；输入可用于计算聚合指标，或监控对象的增删。输入 Object Set 通过在 Object Explorer 中构建已保存 Exploration 创建，可直接在 Object Explorer 或 Object Monitors 应用中作为监控输入添加。

### 5. Object Monitors [Sunset] • Core concepts • Conditions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/condition/>
- **文档摘要**：本页讲解 Object Monitor 的 Condition（条件）概念，定义何时检测到监控活动。Event 条件产生离散事件（如对象增删、指标升降），在活动图中显示为点；Threshold 条件随时间为 true/false 状态，跨越阈值时记录；Function-backed 条件通过返回布尔值的 Function 支持更复杂的判定。条件可含多个子条件与多输入。

### 6. Object Monitors [Sunset] • Core concepts • Evaluation

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/evaluation/>
- **文档摘要**：本页介绍 Object Monitor 的 Evaluation（评估）机制。Monitor 按订阅者分别评估、分别执行 Action，以尊重数据权限。部分输入与条件组合支持实时（realtime）评估（需 Object Storage v2、对象数低于 1000 万、仅精确/范围过滤等）；其余为非实时评估；也支持手动评估。通过 Action 的变更最长可能延迟约七小时才被检测。

### 7. Object Monitors [Sunset] • Core concepts • Activity

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/activity/>
- **文档摘要**：本页是 Object Monitor 的 Activity（活动）参考，说明活动记录与事件类型。活动时间线显示在应用 Overview 页。事件类型包括 Monitor triggered（触发）、Monitor recovered（恢复）、Condition edited、Subscribed/Unsubscribed、Evaluation failed、Muted/Unmuted、Disabled/Enabled 等，分别记录条件状态变化与元数据变更。

### 8. Object Monitors [Sunset] • Core concepts • Notifications

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/notifications/>
- **文档摘要**：本页介绍 Object Monitor 的 Notifications（通知）。订阅者默认收到通知，也可为单个订阅者关闭。Monitor triggered 与 Monitor recovered 的通知内容可自定义：模板渲染（templated rendering）直接填表单并支持 HTML；Function-backed 渲染则由 Function 返回内容，事件条件可传入 ObjectSet<> 提取对象数据。

### 9. Object Monitors [Sunset] • Core concepts • Actions

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/actions/>
- **文档摘要**：本页讲解 Object Monitor 的 Actions（动作）概念。当 Monitor 触发或恢复时可自动运行 Action，由 Monitor 在评估完成后自动提交，多订阅者各自分别运行。事件条件的检测对象集可作为 ObjectSet<> 参数传入 Action；可在 Ontology Manager 禁用某 Action 用于监控；Action 按订阅者用户权限执行，该用户失去权限则停止运行。

### 10. Object Monitors [Sunset] • Monitoring limits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/limits/>
- **文档摘要**：本页是 Object Monitor 的限制（Limits）参考。为保障性能，监控设有多项上限：每小时最多触发 12 次、每天 96 次（超出自动禁用）；对象增删条件的输入最大 100K；单 Monitor 最多 30 个订阅者；实时执行的对象类型最大 1000 万。超限将在保存或运行时报错。

### 11. Object Monitors [Sunset] • Error reference

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/object-monitors/errors/>
- **文档摘要**：本页是 Object Monitor 的错误参考（Error reference），列举常见错误类别。评估错误（如被监控对象类型被删导致失败）会重试；Monitor out of sync 指所引用的已保存 Exploration 变更后未更新；还有通知发送失败、Action 执行失败（逻辑不兼容或提交条件不满足）、以及基于订阅者权限的评估问题等排查要点。
