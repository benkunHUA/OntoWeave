# Recipes 配方

> **分类说明**：Recipes 配方：可复用管道片段的定义、共享与使用

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `recipes` |
| 文档数量 | 6 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Recipes [Sunset] • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/recipes/overview/>
- **文档摘要**：本页介绍 Recipes（条件监控与通知工具）及其已进入 sunset 停更阶段的状态。Recipes 可监控感兴趣的条件并通过 Foundry 或邮件自动发送通知与上下文预览；页面建议用户将工作流迁移至 Automations、Foundry Rules 或 Workshop 等替代方案，并说明了其适用场景与限制。

### 2. Recipes [Sunset] • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/recipes/core-concepts/>
- **文档摘要**：本页介绍 Recipes 的核心概念，包括 expiration（过期）、run history（运行历史）与权限分享。内容说明 recipe 会定期过期但不会删除、仅停止监控，以及基于时间或事件触发的运行会产生 success、skipped、failed 等状态，并概述了隐私与权限模型。

### 3. Recipes [Sunset] • Create a recipe

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/recipes/create-a-recipe/>
- **文档摘要**：本页是创建 recipe 的操作指南，介绍从 Recipes 应用、Dataset Preview 与 Reports 三种入口新建 recipe 的方法。内容涵盖在 Dataset Preview 中定义关注列、消息、接收人与发送条件（最多五列预览），以及将 Report 定时以 PDF 或邮件图片形式自动发送。

### 4. Recipes [Sunset] • View all recipes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/recipes/view-recipes/>
- **文档摘要**：本页介绍如何在 Recipes 应用中查看所有 recipe。用户可从 Foundry 导航侧栏进入 Recipes 应用，在 Recipes Management 页面通过搜索栏查找，并在「Your recipes」标签页中查看自己创建或作为接收人的全部 recipe。

### 5. Recipes [Sunset] • Configure notifications

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/recipes/configure-notifications/>
- **文档摘要**：本页介绍如何配置 recipe 的通知设置。作为 recipe 作者，你会收到运行错误、异常及即将过期提醒；可在 Foundry 用户设置的 Notifications 标签页中找到 Recipes 部分，选择以邮件、站内 web 通知或完全不接收的方式管理通知。

### 6. Recipes [Sunset] • Edit a recipe

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/recipes/edit-a-recipe/>
- **文档摘要**：本页是编辑 recipe 的操作指南，说明基于权限在 Recipe Management 页面可对 recipe 执行的各项操作。内容包括添加接收人（上限 25 人）、静音或暂停通知、延长或恢复过期时间，以及删除 recipe 等具体步骤。
