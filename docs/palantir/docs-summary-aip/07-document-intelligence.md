# Document Intelligence

> **分类说明**：文档智能解析：核心概念、部署到 Python transforms / functions、文档转文本

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `document-intelligence` |
| 文档数量 | 5 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. AIP Document Intelligence • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/document-intelligence/overview/>
- **文档摘要**：AIP Document Intelligence 是 Foundry 文档抽取工作流的统一入口：打开 media set 即可运行多种抽取策略，查看其质量、速度与 token 成本评估，并一键把策略部署为批处理 Python transform。传统方式含 raw text、OCR 和保留版式的 layout-aware OCR；生成式方式用 VLM 配调优 prompt，也可先做预处理再交给 VLM。结果以 Markdown 映射到 PDF bounding box 便于核对。

### 2. AIP Document Intelligence • Core concepts

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/document-intelligence/core-concepts/>
- **文档摘要**：Traditional extraction 指不依赖 LLM 的算法，如 PDF 元数据、OCR 与 layout detection，由 transform media item 端点支撑。Preprocessing 是先对文档跑 OCR，再把结果连同页面图像一并交给 VLM，为模型补充上下文。Evaluations 以所选 VLM 作 judge，对表格、标题等维度打 1 至 5 分，当前需 Claude 4 Sonnet；策略满意后可部署为 Python transform 或按页抽取的 functions，以便对整个数据集批量抽取。

### 3. AIP Document Intelligence • Deploy extraction strategies to Python transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/document-intelligence/deploy-to-python-transforms/>
- **文档摘要**：策略在 Document Intelligence 中验证后可部署为 Python transform，对 media set 内所有文档的全部页面批量抽取，结果与配置端一致。在生成的 my_extraction.py 中用 @transform.using 指定输出数据集并触发 build；模板默认非增量，取消 @incremental 注释后只处理新增文档。prompt 应先在 Document Intelligence 调整验证再重新部署，直接改模板会造成结果不一致。

### 4. AIP Document Intelligence • Deploy extraction strategies to Python functions

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/document-intelligence/deploy-to-python-functions/>
- **文档摘要**：按 Deploy to functions 生成的代码片段可搭建 Python functions 仓库实现按页抽取：安装 foundry-platform-sdk 1.78 以上，需要分块则装 aip-workflows 0.40 以上，需要 embedding 则装 openai，并导入所选模型、把函数超时调到 enrollment 允许的最大值，以便一次处理大量或高页数的 PDF。helper functions 各策略通用，之后再接各策略的抽取逻辑与 chunking、embedding 函数。

### 5. AIP Document Intelligence • Document-to-text media transformations

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/document-intelligence/document-to-text/>
- **文档摘要**：该页列出两个 document-to-text 操作：extractTextV2 返回文档文本的字符串列表，extractLayoutAwareTextV2 返回跨页的 layout-aware 文本块，可保留段落、标题与表格结构，取代旧的 extractLayoutAwareContent 与 ocrOnPage。参数 config.mode 控制解析方式：ELECTRONIC 直接提取内嵌文本，SCAN 按扫描件做 OCR，AUTO 逐页自动判断；config.format 可输出 TEXT、MARKDOWN 或 HTML，另支持 languages 与 pageRange 限定页码范围。
