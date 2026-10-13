# Vulcan 可视化工具

> **分类说明**：Vulcan 工具概述、Schema 参考与 Widget

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `vulcan` |
| 文档数量 | 4 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-12 |

---

## 文档列表

### 1. Vulcan • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/vulcan/overview/>
- **文档摘要**：本页是 Vulcan 的总览，Vulcan 是一个在 Palantir 平台中渲染 CAD 模型、工程图纸与点云的 3D 应用。用户可查看 Ontology 中作为 CAD 对象的模型，进行标注（Annotate）、测量（Measure）、剖切（Section）、重新着色等操作，并将视口嵌入 Workshop 模块。标注、测量与剖切面均保存为 Ontology 对象以驱动其他工作流。支持 GLB、STL、OBJ、DXF、LAS 等格式。

### 2. Vulcan • Tools

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/vulcan/tool-overview/>
- **文档摘要**：本页介绍 Vulcan 提供的工具集，用于对 CAD 模型进行标注、测量、剖切、样式切换、着色与重定位。标注可标记缺陷或故障点并保存为 Ontology 对象；测量捕获点间距离；剖切面可隐藏一侧几何以查看内部组件；Mesh style 支持 Normal/X-Ray/Wireframe；Part coloring 与 Part transforms 由函数输出驱动。每个工具在嵌入 Workshop 时可显隐。

### 3. Vulcan • Schema reference

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/vulcan/schema-reference/>
- **文档摘要**：本页是 Vulcan 的 Schema reference，列出运行 Vulcan 所需的 Ontology 对象类型、动作、链接类型与属性类型类。核心是 CAD object（携带 cad_media_reference 类型类指向模型文件），以及 Annotation、Measurement、Section plane 等可保存对象，并给出相应的 Create 动作与 Link types。类型类均位于 vulcan 命名空间，Vulcan 按类型类而非名称查找属性。

### 4. Vulcan • Embed Vulcan in a Workshop module

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/vulcan/widget/>
- **文档摘要**：本页讲解如何将 Vulcan 通过 Vulcan widget 嵌入 Workshop 模块，提供与独立应用相同的 3D 视口与工具，并可对接 Workshop 变量与其他 widget。步骤分为三步：添加 widget、选择 CAD 对象（含 cad_media_reference 类型的 Ontology 对象）、配置 widget。配置区涵盖 Input、Interaction、Styling、Interface、Scene、Tools、Actions、Output 等，可精细控制模型、标注与交互。
