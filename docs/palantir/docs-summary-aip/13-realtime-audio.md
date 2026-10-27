# Realtime Audio 实时语音

> **分类说明**：实时语音交互：概述与构建语音版 OSDK 应用

| 项目 | 内容 |
| --- | --- |
| 分类标识 | `realtime-audio` |
| 文档数量 | 2 篇 |
| 产品 | Palantir Foundry / AIP |
| 来源 | [Palantir 官方文档](https://www.palantir.com/docs/foundry/) |
| 生成日期 | 2026-10-26 |

---

## 文档列表

### 1. Realtime audio • Overview

- **文档类型**：概述
- **文档链接**：<https://www.palantir.com/docs/foundry/realtime-audio/overview/>
- **文档摘要**：Realtime audio 以音频为模态经 Ontology 与平台交互：对话中从 Ontology 取上下文，实时模型监听、转写并发起 tool call，结果写回 Ontology 供 function、AIP Logic、Workshop 与 action 使用。场景含口述记录、会议结构化、通话辅助与语音控制。

### 2. Realtime audio • Build a voice-enabled OSDK application

- **文档类型**：快速入门
- **文档链接**：<https://www.palantir.com/docs/foundry/realtime-audio/build-a-voice-enabled-osdk-application/>
- **文档摘要**：教程构建 OSDK 浏览器应用：以 OAuth 认证为 Foundry 用户，浏览器直连 WebSocket 代理转发到实时模型，为 agent 注册以用户权限读取 Ontology 的 tool，再流式上传麦克风音频并播放语音回复。需 @osdk/create-app 与 unrestricted 自定义应用。
