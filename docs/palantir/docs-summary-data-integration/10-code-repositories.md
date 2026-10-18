# 代码仓库（Code Repositories）

> **分类说明**：代码仓库（Code Repositories）工作区：管理转换代码（Python/Java/SQL 等）、分支协作、checks 校验与开发工作流

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `code-repositories` |
| 文档数量 | 35 篇 |
| 产品 | Palantir Foundry |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-17 |

---

## 文档列表

### 1. Code Repositories • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/overview/>
- **文档摘要**：本页概述 Code Repositories：这是一个基于 Web 的集成开发环境（IDE），用于在 Foundry 中编写和协作生产级代码，并通过 Web UI 完成分支、提交、打标签等 Git 版本控制任务。页面还介绍了仓库类型，包括支持 Python/Java/SQL 的 Transforms 仓库、可执行低延迟逻辑的 Functions 仓库，以及模型开发支持，并提及 VS Code workspaces 与 Code Workspaces 等编辑与浏览方式。

### 2. Code Repositories • Python transforms [Legacy]

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/legacy-editing/>
- **文档摘要**：本页说明 Code Repositories 中的 Python transforms 已进入 legacy（遗留）阶段，未来功能开发将聚焦于 VS Code workspaces。VS Code workspace 是处理 Python transforms 的推荐环境，提供 AI 辅助编码、完整数据集预览、优化语言服务器与集成终端。若 enrollment 不支持 VS Code，Code Repositories 仍是编写 Python transforms 的推荐方式，且仍是 Java 与 SQL transforms 等其他仓库类型的推荐编辑器。

### 3. Code Repositories • Navigation

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/navigation/>
- **文档摘要**：本页介绍 Code Repositories 界面的导航方式，顶部提供 Code、Branches、Tags、Pull requests、Checks、Settings 等标签页。Code 标签下涵盖应用内帮助、branch 选项、代码编辑器选项、文件编辑器、辅助面板与状态栏；其中须在 sandbox branch 中编辑代码，并支持 Code Repositories branch 与跨应用的 Global branch。其余标签分别用于管理分支、标签、拉取请求筛选与评审、check 状态及设置。

### 4. Code Repositories • Configuration • Configure Code Repositories settings in Control Panel

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/configure-repositories-in-control-panel/>
- **文档摘要**：本页说明如何在 Control Panel 中配置组织级的 Code Repositories 设置，修改需具备 User Experience Administrator 角色。可用设置包括：使用 Palantir VS Code 扩展进行本地开发、通过扩展进行安全流式预览、使用 Palantir MCP Server 在本地 IDE（如 Claude Code、GitHub Copilot、Cursor）中接入 Foundry 数据与元数据，以及相关的本地预览权限说明与注意事项。

### 5. Code Repositories • FAQ

- **文档类型**：故障排查
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/faq/>
- **文档摘要**：本页汇总 Code Repositories 的常见问题（FAQ），涵盖从分支最新提交发布 Python 包、恢复已删除代码、复制仓库、判断是否需要升级 branch、transform 动态选择输入输出等。同时提供排查指引，例如 Code Preview 中代码可运行但 build 失败、Code Workbook 可运行而 Code Repositories 不行、因缺失 Python 库导致 checks 失败等典型故障的解决方法。

### 6. Code Repositories • Transforms • Create transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/create-transforms/>
- **文档摘要**：本页介绍如何在 Code Repositories 中使用文件模板配置向导创建 transform。向导可先选择 transform 类型（如 Lightweight transforms 与 Spark transforms），再填入所需变量（输入/输出数据集）引导生成最小示例。配置页提供实时预览并自动校验，常见错误包括跨项目引用资源、重复参数名及缺少必填参数，校验通过后即可创建。

### 7. Code Repositories • Transforms • Preview transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/preview-transforms/>
- **文档摘要**：本页说明如何使用 Code Repositories 的 Preview 工具，对输入数据集的有限样本运行代码以快速预览输出，且不会提交变更、运行 check 或物化数据集，从而加速开发循环。Preview 支持含文件与模型的数据集，可对文件类数据集配置样本文件、对 Foundry 训练或预训练文件支持的模型资产直接预览，并适用于 transforms generator 生成的 transform。

### 8. Code Repositories • Transforms • Debug transforms

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/debug-transforms/>
- **文档摘要**：本页介绍如何使用 Code Repositories 的 debugger 工具在 transform 运行时检查数据转换行为，该工具仅支持 Python。用户可设置断点暂停执行以查看变量与 dataframe，并通过 Preview and debug 启动调试面板；面板提供 Resume、Step over、Step into、Step out、Stop 等控制，并可预览 dataframe、检视 Frames/Variables/Console 及配置内部库调试。

### 9. Code Repositories • Transforms • Use project references

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/use-project-references/>
- **文档摘要**：本页说明 Project references 机制：具备较高权限（通常为数据集或管道所有者）的用户可借此让其他 Project 发现并使用其数据，作为权限边界或发现中心。transform 的输出须位于代码仓库所属 Project 范围内，外部输入数据集需通过灯泡图标或 Projects & files 添加为 Project reference；页面还说明了相关权限要求与 AccessOutsideProjectDenied 错误。

### 10. Code Repositories • Transforms • Analyze the impact of changes

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/analyze-impact/>
- **文档摘要**：本页介绍如何分析代码变更对数据集的影响，建议在合并前保护生产 branch 并要求评审。Pull request 页面会提示受影响数据集是否 stale，并提供 Impact analysis 标签查看受影响数据集及其下游影响；Python 仓库基于 TLLV 生成受影响列表，Java 则依据源文件是否被改动判断。页面还说明了 Pipeline review 等流程。

### 11. Code Repositories • Unit tests

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/unit-tests/>
- **文档摘要**：本页概述 Code Repositories 对单元测试的支持：可通过集成助手发现并运行单元测试用例，覆盖多数语言与仓库类型。页面指向各语言的具体单元测试文档，包括 Python transforms unit tests、Java transforms unit tests 与 TypeScript functions unit tests，供用户按所用语言进一步查阅。

### 12. Code Repositories • Pin Spark modules in-platform

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/module-pinning/>
- **文档摘要**：本页说明如何在 Code Repositories 内 Pin（固定）Spark module，以强制使用特定版本的 Spark，适用于新建与已有 build，但建议仅作为临时措施。操作路径为 Settings 标签下的 Runtime overrides：可对所有或指定 branch 创建 pin 并设定版本与过期日期（最长 90 天），亦支持编辑、归档、恢复 pin，并可将其用于控制 CI 中的 transform 版本。

### 13. Code Repositories • Libraries

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/libraries/>
- **文档摘要**：本页介绍 Code Repositories 中库（libraries）的使用：允许使用本地与外部库，并在 Foundry 环境中共享自有代码。不同语言共享与使用库的步骤不同——Python 可共享并在 Libraries 面板中发现与使用共享库，Java 可共享代码与宏（macros）。页面为各语言提供了对应的详细操作文档入口。

### 14. Code Repositories • Anaconda

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/anaconda/>
- **文档摘要**：本页说明如何在 Foundry 中使用 Anaconda 的 Conda 包：可在 Code Repositories 与 Code Workbooks 中直接调用，获得企业级数据科学能力。Anaconda 在 2027 年 6 月 29 日前于 Foundry 免费提供，之后需客户自行采购许可并遵守其 Embedded EULA。在 Code Repositories 中可通过 Libraries 侧栏搜索并安装 Conda 包，Foundry 会自动配置后端仓库。

### 15. Code Repositories • Documentation

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/readme/>
- **文档摘要**：本页介绍 Code Repositories 中通过 README 文件为项目编写文档的方法，README 支持 Markdown 格式。其特性包括内联图片预览、提及 Foundry 用户（@用户名）、引用 Foundry 资源（粘贴 Resource ID）、使用 repo:// 协议链接仓库内文件、语法高亮、表格与链接等，便于自定义并导航项目文档。

### 16. Code Repositories • AIP features

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/aip-features/>
- **文档摘要**：本页罗列 Code Repositories 中的 AIP 驱动功能，可通过 Ask AIP Assist、编辑器右键菜单或内联选项调用。主要包括 AIP Assist（代码解释、查找 bug、翻译、上下文感知附件、Beta 内联代码辅助）、AI error enhancer（仓库与 build 场景）以及代码自动补全（code autocomplete），帮助用户增强代码理解与开发效率。

### 17. Code Repositories • Add dataset transformation to a Marketplace product

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/marketplace-dataset-transformation/>
- **文档摘要**：本页说明如何使用 Foundry DevOps 将数据集转换（dataset transformation）纳入 Marketplace 产品，供其他用户安装复用。打包时会连同其 Code Repository 一起存储所有依赖，保证转换自包含可运行，支持 Python/Java/SQL 与 Maven、PyPI、Conda 依赖。页面还列出支持特性、添加转换到产品的步骤，以及三种 repository 打包选项。

### 18. Code Repositories • Artifact repositories • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/artifact-repositories-overview/>
- **文档摘要**：本页概述 Artifact repositories：用于发布与管理 Conda、Docker、Maven 等 Artifact，适用于在本地编写、非以库形式创建且无法通过外部 URL 获取的构件。核心功能包括生成 token 并推送发布 Artifact、在界面中搜索 Artifact，以及对 Conda Artifact 执行 recall 以防止下游使用特定版本。用户可通过 Library 搜索面板访问已发布的 Artifact。

### 19. Code Repositories • Artifact repositories • Navigation

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/artifact-repositories-nav/>
- **文档摘要**：本页介绍 Artifact Repository 界面的导航方式。界面顶部包含 Search、Publish、Overview 三个标签页：Search 用于按类型搜索 Artifact 并查看版本历史、大小、创建日期等元数据；Publish 通过生成 token 并按指引发布 Artifact；Overview 汇总仓库内所有 Artifact 及其 RID、创建日期、父文件夹等信息。

### 20. Code Repositories • Artifact repositories • Create an Artifact repository

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/create-artifact-repository/>
- **文档摘要**：本页提供创建 Artifact repository 的操作步骤。在开始发布与管理 Artifact 前，需先创建用于存放 Artifact 的 repository：进入目标 Project，选择 New 并选择 Artifact Repository 即可创建，创建后即可开始发布 Artifact。

### 21. Code Repositories • Artifact repositories • Delete an Artifact repository

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/delete-artifact-repository/>
- **文档摘要**：本页说明如何删除 Artifact repository。删除会移除仓库内全部 Artifact，属于 breaking change 并影响所有下游消费者，操作需谨慎。步骤为：进入所在 Project，右键选择 Move to trash，再到 Trash 标签页选择 Delete permanently 永久删除（不可撤销），也可在 Trash 中 Restore 恢复。

### 22. Code Repositories • Artifact repositories • Publish an Artifact

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/publish-artifact/>
- **文档摘要**：本页介绍向 Artifact repository 发布 Artifact 的流程。需在 Publish 标签页选择类型、生成 token 并按 Instructions 中命令发布，部分类型可添加参数命令。此外还说明如何查找消费某 Conda Artifact 的 repository：在 Search 标签页筛选 Conda 格式，定位版本后在 </> Consumers 中查看消费者列表。

### 23. Code Repositories • Artifact repositories • Recall an Artifact

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/recall-artifact/>
- **文档摘要**：本页说明召回（recall）Conda Artifact 的方法，用于阻止下游消费者用被召回版本编译代码。流程为在版本历史中选择版本并 Recall，需填写原因；也支持 Unrecall 恢复。注意 Artifact repository 内的 Artifact 无法删除，必须删除整个 repository 才能显式删除 Artifact。

### 24. Code Repositories • Artifact repositories • Manage permissions

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/manage-permissions/>
- **文档摘要**：本页列出使用 Artifact repository 的权限要求。使用 Artifact 需对 repository 有 View 权限；发布或召回 Artifact 需有 Edit 权限；Artifact 本身不可删除。权限与 Project 访问相关，需参考 Foundry 权限说明进行配置。

### 25. Code Repositories • Advanced workflows • Create custom checks

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/create-custom-checks/>
- **文档摘要**：本页说明如何在 Code Repositories 中创建自定义 check。自定义 check 以 Gradle 任务形式定义，添加到对应语言的 inner build.gradle 文件中，并通过让项目 check 任务依赖它来在 CI 中执行。还可用 finalizedBy 在发布后执行，建议仅依赖 check、test、publish 等官方 CI 任务，勿修改 ci.yml。

### 26. Code Repositories • Advanced workflows • Prepare datasets for download

- **文档类型**：操作指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/prepare-datasets-download/>
- **文档摘要**：本页提供在 Code Repositories（或 Pipeline Builder）中准备 dataset 以便下载的高级工作流。仅当无法直接从 Foundry 界面导出时采用：需过滤清洗数据、将列转换为字符串、控制 CSV 小于 128 MB，并通过 Python、SQL 或 Pipeline Builder 减少分区。随后可在文件位置处访问并下载。

### 27. Code Repositories • Administer repositories • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/admin-overview/>
- **文档摘要**：本页概述 Code Repository 的后台管理。管理需相应权限，多数设置默认由 repository owner 调整，编辑器偏好对所有作者开放。进入 repository 后点击顶部 Settings 可配置分支设置、repository 设置、升级、Spark、Artifact、Project 范围、安全审批、Anaconda 包及 repoSettings.json 等。

### 28. Code Repositories • Administer repositories • Branch settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/branch-settings/>
- **文档摘要**：本页说明 Code Repositories 的分支设置。涵盖：设置默认分支（通常为 master）作为 PR 与提交的基础分支；选择 PR 合并模式（squash and merge、merge、merge with fast-forward）；配置受保护分支（要求 CI/foundry-publish 成功、代码评审、指定评审人、安全审批、限制稳定版本标签）；以及取消保护与分支回退（fallback）。

### 29. Code Repositories • Administer repositories • Repository settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/repository-settings/>
- **文档摘要**：本页介绍 repository settings 的配置项，改动对所有用户生效。包括提交信息设置（可关闭自动生成以强制填写）；升级设置；Dataset aliases（建议用 RID 而非路径以方便迁移，可在编辑器设置自动转换）；以及 External systems（经安全审批后可启用外部 transform 访问外部 API 的高级功能）。

### 30. Code Repositories • Administer repositories • Repository upgrades

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/repository-upgrades/>
- **文档摘要**：本页说明 Foundry 的 repository upgrade 机制。Foundry 会向活跃 repository 生成升级 PR，包含 Transforms 模板与运行时改进；启用自动合并后会在 check 通过后自动合入，否则需人工合并。还介绍文件覆盖、影响分析、模板过旧或用户改动导致需手动干预，以及手动分支升级的情形。

### 31. Code Repositories • Administer repositories • Spark profiles

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/spark-profiles/>
- **文档摘要**：本页介绍 Spark profiles 的配置与使用。在 repository settings 中指定后可在代码中使用，但使用前须先导入 Project，否则发布 transform 时 check 会失败。profile 分 Unrestricted（所有用户可导入）与 Restricted（仅管理员导入）两类，已启用的 profile 在 Settings > Spark 的 Enabled profiles 与 References 面板可见。

### 32. Code Repositories • Administer repositories • Artifact settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/artifact-settings/>
- **文档摘要**：本页说明 Artifact settings（Libraries 标签页）的配置。该标签列出可被引用的 backing repositories（共享代码库或外部/公共库），可添加本地或外部 repository；查看需 artifacts:view-repository、管理需 artifacts:manage-repository 权限。调整顺序或删除可能破坏依赖其包发布的 transform，并介绍 Beta OSDK 版本开关。

### 33. Code Repositories • Administer repositories • Ontology imports

- **文档类型**：概念介绍
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/ontology-imports/>
- **文档摘要**：本页介绍 Code Repositories 中的 Ontology 标签页用途。该标签用于在 Functions on Objects 中导入 object 与 link 类型，以便在代码里使用 Ontology。具体导入与用法详见 Functions 文档中关于 ontology imports 的部分。

### 34. Code Repositories • Administer repositories • Advanced repository settings

- **文档类型**：配置指南
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/advanced-settings/>
- **文档摘要**：本页说明通过根目录 repoSettings.json 配置高级 repository 设置。可设置自定义 tag 名称正则校验、PR 描述模板、新 transform 的输出 dataset 路径前缀（outputPathPrefix）、以及 PR 校验规则（prValidation）。文件不存在时可手动创建，文中给出各项配置示例。

### 35. Code Repositories • Administer repositories • Compute Usage

- **文档类型**：参考手册
- **文档链接**：<https://www.palantir.com/docs/foundry/code-repositories/compute-usage/>
- **文档摘要**：本页详述 Code Repositories 的 Foundry compute 用量。构建 dataset 时消耗以 compute-seconds 计量的计算资源，写代码不计费、仅构建计费；介绍用量计量方式、排查方法、驱动因素与优化手段，并给出标准内存、大内存、动态 executor、GPU 等计费示例及默认费率。
