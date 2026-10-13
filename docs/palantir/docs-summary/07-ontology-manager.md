# Ontology Manager 本体管理器

> **分类说明**：Ontology Manager 的导航、用量查看、权限迁移与变更管理

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `ontology-manager` |
| 文档数量 | 9 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Ontology Manager • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/overview/>
- **文档摘要**：本页是 Ontology Manager（OMA）的概述。OMA 用于构建与维护组织的 Ontology，涵盖创建对象类型、定义动作类型、连接数据、排查数据更新等。页面说明三种访问方式，并介绍用户界面元素：顶部栏与侧栏导航、Discover 视图，以及 Object type、Property editor、Link type、Action type、Function type 等各类资源视图。

### 2. Ontology Manager • Navigation

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/navigation/>
- **文档摘要**：本页讲解 Ontology Manager 的导航方式。页面介绍位于应用顶部的搜索栏（点击或 Cmd/Ctrl+K 打开）、首页侧栏可进入的 Object types / Link types / Action Types / Shared Properties / Interfaces / Functions 页面及其按可见性、开发状态、索引问题的过滤功能，以及从已打开资源返回首页（Back home）与悬停查看快捷链接的方式。

### 3. Ontology Manager • Viewing usage

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/view-usage/>
- **文档摘要**：本页介绍在 Ontology Manager 中查看 Ontology 使用指标。关键术语包括 Reads（应用加载对象的请求）、Writes（应用对对象的编辑请求）、Interactions（近 30 天读写总量）、Active users（触发读写的不同用户数）。页面说明两个查看位置：Overview 标签的使用量图表与专用 Usage 标签的详细指标，以及如何启用用量统计与共享 Ontology 的用量。

### 4. Ontology Manager • Migrate to project-based permissions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/migrate-to-project-based-permissions/>
- **文档摘要**：本页讲解将 Ontology 资源迁移到基于项目（project-based）的权限模型。迁移后对象/链接实例权限仍依赖底层 datasource，而资源的查看/编辑/管理权限交由 Compass 文件系统管理，取代旧的 ontology roles 与 datasource 派生权限（注意迁移不可逆转）。页面列出限制、迁移方式（迁移助手推荐、直接迁移资源），并说明迁移对 Marketplace 安装的影响。

### 5. Ontology Manager • Ontology roles migration [Legacy]

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/ontology-roles-migration/>
- **文档摘要**：本页为已弃用（Legacy）的 ontology roles 迁移文档，现已非最新权限方案，资源应使用 Compass 文件系统授权。文档保留 Ontology roles（取代 datasource 派生权限的旧授权模型，应用于 object/link/action types）的概念、为何需要迁移（以使用共享属性、接口等新特性），以及通过 Ontology Manager 迁移的前提、通知与常见问题。

### 6. Ontology Manager • Change management • Save changes to the Ontology

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/save-changes/>
- **文档摘要**：本页讲解在 Ontology Manager 中保存对 Ontology 的更改。所有改动起初存储于本地 work-in-progress 状态，需保存后才对他人可见并反映到应用中。页面介绍保存操作、处理错误与警告（错误须先解决、警告不阻止保存）、更新与合并冲突（他人已保存时须 Update）、丢弃更改、响应警告消息，以及保存失败时的排查方法。

### 7. Ontology Manager • Change management • Review and restore changes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/restore-changes/>
- **文档摘要**：本页介绍在 Ontology Manager 中审查与还原更改。可全局查看自己未保存的更改（Unsaved changes），并在 Ontology history 中过滤无权限查看的变更；单个资源可通过 History 标签查看其编辑历史。页面还说明如何将某个 object type 还原到较早版本——还原会撤销该版本之后的更改并加入工作区，需再次保存方可生效。

### 8. Ontology Manager • Export, edit, and import an Ontology

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/export-import/>
- **文档摘要**：本页讲解 Ontology 的导出、编辑与导入。Ontology 模式定义以 JSON 文件存储，可导出后在代码/文本编辑器中直接编辑再导入，适用于偏好代码化编辑或跨 Ontology 复制工作状态的进阶用户。页面说明 Export、Import 操作步骤，以及导入报错 OntologyMetadata:UnreferencedRuleSets 的排查（条件格式规则未定义）。

### 9. Ontology Manager • Ontology cleanup

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/ontology-manager/cleanup/>
- **文档摘要**：本页介绍 Ontology cleanup（本体清理）工具，这是一种安全删除 object types 的方式，可使 Ontology 更易导航、性能更佳并降低存储成本。页面说明访问入口、如何筛选并清理（如未发布的工作流），以及可自定义的清理标记（flags）：过期弃用、datasource 被删除、长期未更新、缺少描述、显示名匹配正则、Phonograph 已去索引等。
