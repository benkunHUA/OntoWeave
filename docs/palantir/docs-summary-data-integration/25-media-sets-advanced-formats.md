# 媒体集高级格式（Media Sets Advanced Formats）

> **分类说明**：媒体集（Media Sets）高级格式：视频、音频、影像等复杂媒体类型的存储与处理支持

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `media-sets-advanced-formats` |
| 文档数量 | 15 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Media sets (unstructured data) • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/media-overview/>
- **文档摘要**：本页概述 media set（媒体集）的概念与能力。media set 用于存储、转换和处理非结构化媒体文件，支持 audio、DICOM、document、image、spreadsheet、video 等多种 schema，每种 schema 有主格式及可选的附加输入格式（上传后自动转换）。页面还介绍 media reference（媒体引用）类型、从 media set 删除媒体项的方法，并列出集成到平台工作流的延伸阅读。

### 2. Media sets (unstructured data) • Advanced media set settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/media-set-settings/>
- **文档摘要**：本页说明 media set 的高级配置项。事务策略（transaction policy）区分 transactionless 与 transactional：前者上传即读、不可回滚且支持并发写入，后者须通过事务提交且单事务最多写入 10,000 项。保留策略（retention policy）可永久删除过期或被覆盖/删除的媒体项，以节省存储成本，可在 Details 标签页配置。

### 3. Media sets (unstructured data) • Importing media

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/importing-media/>
- **文档摘要**：本页介绍将媒体导入 media set 的多种方式，包括直接上传、连接外部源系统（Data Connection、External transforms）以及通过 transforms 或 Pipeline Builder 导入。页面说明路径去重（同名文件会覆盖且不留警告）与媒体项版本历史（可查看被覆盖项），并指导如何通过拖拽完成直接上传。

### 4. Media sets (unstructured data) • Virtual media sets

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/virtual-media-sets/>
- **文档摘要**：本页概念性地介绍 virtual media set（虚拟媒体集）。它是一类特殊 media set，可直接读取外部源系统（Amazon S3、OneLake、Azure Blob）的文件而无需复制到 Foundry 存储，从而节省成本。页面列出支持源类型、已知限制（不感知源系统更新/删除、转换后媒体仍落入 Foundry 存储、不支持附加输入格式），以及创建方式与用 Python transform 注册媒体项的方法。

### 5. Media sets (unstructured data) • Transforming media

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/transforming-media/>
- **文档摘要**：本页介绍通过 Pipeline Builder 与 Code Repositories 转换 media set 中媒体。Pipeline Builder 适用于 OCR、音频转写、结合 LLM 等常见流程；Code Repositories 适合需要增量管道、非媒体集输入等复杂场景，且独占增量读写与表格数据能力。页面还提及 access patterns（按需预配置转换）及其持久化与优化策略。

### 6. Media sets (unstructured data) • Using media in the Ontology

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/media-in-ontology/>
- **文档摘要**：本页说明如何在 Ontology 中使用媒体。通过 media reference 对象属性可高效展示媒体（Workshop/Object Explorer 快速预览、Map 地理影像分块）。可在函数中对媒体项执行 OCR、文本提取、音频转写等操作，也可借助 OSDK 调用媒体能力，并提示 action 表单上传仅于提交成功后才落库等注意事项。

### 7. Media sets (unstructured data) • Using media in Workshop

- **文档类型**：工具说明
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/media-in-workshop/>
- **文档摘要**：本页介绍在 Workshop 中展示与处理媒体的可用工具。通用组件包括 Media preview、Audio recorder、Image annotation；针对单一 schema 还可使用专用组件，如 PDF Viewer、Spreadsheet Display、Video Display、Audio and Transcription Display 等。页面还说明如何配置 Action 与按钮组组件让用户通过 Workshop 上传媒体。

### 8. Media sets (unstructured data) • Example media workflows • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/media-workflows-overview/>
- **文档摘要**：本页是示例媒体工作流的总览，列出后续各分步指南的主题：添加 DICOM media set、展示与标注电子表格、音频转写、通过 action 上传媒体、配置媒体项细粒度策略、在 OSDK 应用中使用媒体、用 Pipeline Builder 或 Code Repositories 构建批处理管道，以及在 Data Connection 创建媒体集同步。

### 9. Media sets (unstructured data) • Example media workflows • Add a DICOM media set

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/add-dicom-media-set/>
- **文档摘要**：本指南分步演示将 DICOM (.dcm) 文件作为 media set 导入 Foundry。Part 1 创建 DICOM media set 并添加文件（含 Patient ID、Study ID 等元数据，可调节对比度与曝光）；Part 2 通过管道将 media set 转换为 object type（以 media item RID 为主键）；可选 Part 3 在 Workshop 创建模块自动生成对象表与预览。

### 10. Media sets (unstructured data) • Example media workflows • Display and annotate spreadsheets

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/display-annotate-spreadsheets/>
- **文档摘要**：本指南演示展示与标注电子表格（spreadsheet media set）。Spreadsheet media set 保留每个 .xlsx 原文件，适合结构多变或以原表为真相源的场景。指南包含导入文件、用 Pipeline Builder 创建对象并提取单元格内容、创建标注对象类型与 action，以及在 Workshop 中展示与标注的完整步骤，并列出前置权限。

### 11. Media sets (unstructured data) • Example media workflows • Transcribe an audio media set

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/add-audio-transcription/>
- **文档摘要**：本指南演示构建音频转写交互应用。Part 1 以直接上传或 Data Connection 将音频导入 media set；Part 2 在 Pipeline Builder 中使用 Transcribe audio 转换（可选说话人识别、分段输出等）；Part 3 将转写分段 ontologize 为对象；Part 4 在 Workshop 用转写组件展示结果。

### 12. Media sets (unstructured data) • Example media workflows • Configure granular policies for media items

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/configure-granular-policies-media/>
- **文档摘要**：本指南演示在同一 media set 内为不同媒体项配置差异化访问权限。先创建 media set，再通过 Pipeline Builder 或 media set transforms API 建立含 media reference 的数据集与其受限视图并定义细粒度策略；随后将受限视图 ontologize 为对象类型（可停止继承部分 marking），最终在 Workshop、Object Explorer、Vertex 等通过 Ontology 交互访问。

### 13. Media sets (unstructured data) • Example media workflows • Upload media

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/upload-media/>
- **文档摘要**：本指南演示通过 action 上传媒体到 Ontology。推荐做法是创建带 media reference 参数的 action，并在 Workshop 用 Button group 组件调用。Part 1 在 Ontology Manager 创建 action 类型；Part 2 将媒体引用参数渲染为 File picker（支持拖拽、单/多文件布局）；Part 3 将 action 加入 Workshop 应用完成上传。

### 14. Media sets (unstructured data) • Example media workflows • Use media in OSDK applications

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/use-media-in-osdk/>
- **文档摘要**：本页说明在 OSDK（TypeScript，beta）应用中使用媒体，需在 Developer Console 开启 TypeScript beta 特性。涵盖读取媒体引用属性（fetchMetadata/fetchContents）、通过 uploadMedia 在 Ontology Edit Function 中上传媒体、在 Actions 中使用已上传媒体，以及相关注意事项。媒体支持尚处 beta，可能随开发变更。

### 15. Media sets (unstructured data) • Media usage costs and limits

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/media-sets-advanced-formats/media-usage-limits/>
- **文档摘要**：本页列出 media set 的计算用量与限制。媒体转换（及预览触发、下载/流式传输）按 Foundry compute-seconds 计费，并按数据类型（image、audio、video、document、spreadsheet 等）给出每种转换每 GB 的处理费率。页面还说明大规模使用时的 media set 限制与限流（throttling）策略，企业合同需联系 Palantir 代表核算。
