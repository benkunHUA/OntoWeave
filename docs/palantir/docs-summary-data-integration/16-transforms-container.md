# 容器转换（Transforms Container）

> **分类说明**：容器转换（Container Transforms）：将自定义容器镜像作为转换运行的构建与部署

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `transforms-container` |
| 文档数量 | 2 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Containers • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-container/container-overview/>
- **文档摘要**：本页介绍 Foundry 中的 Container transforms 概览。Foundry 通过与推送至平台内的容器镜像交互，支持使用 transforms sidecar decorator 与构建容器化模型资产两种用法，首先需将镜像推入平台内置 Docker registry 并满足镜像要求。要求包括：使用数字 userID（自带容器须为 5001）、构建 linux/amd64 平台、以 digest 或非 latest 标签推送、单层小于约 10GB、端口范围 1024–65535 等。

### 2. Containers • Spark sidecar transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/transforms-container/transforms-sidecar/>
- **文档摘要**：本页说明 Spark sidecar transforms，即在利用 Spark 与 transforms 既有基础设施的同时部署容器化代码。通过 @sidecar 装饰器可让每个 Spark executor 旁启动一个 sidecar 容器，借助共享卷在 executor 与容器间传递输入、输出与执行信号，从而随输入数据横向扩展。页面含架构、构建与推送镜像、编写转换，以及以 server 或 CLI 形式运行的多个示例与资源规格（resource profiles）。
