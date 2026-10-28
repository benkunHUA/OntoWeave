# P4 查询工作台前端设计

- 日期：2026-10-27
- 状态：已实现
- 上游：[只读查询 Agent 设计](2026-10-27-agent-query-design.md)
- 范围：独立查询入口、会话列表、对话区、查询过程与证据展示

## 1. 入口与路由

- 新入口：`/query`，与建模、数据连接、数据整理、对象数据并列。
- 会话路由：`/query/:conversationId`。
- 首页新增「业务问答」卡片；点击后创建或进入最近一次查询会话。
- 查询工作台不复用建模三栏，不展示画布、改动集或视图状态。

## 2. 页面结构

```text
左侧 280px：查询会话列表
右侧自适应：查询对话
```

左侧：

- 品牌区：OntoWeave / 业务问答。
- 「新建问答」按钮。
- 当前用户的 `kind='query'` 会话，按更新时间倒序。
- 当前会话高亮；支持删除，删除当前会话后回到空态。

右侧：

- 空态：未选会话时提示新建或从左侧选择。
- 会话头部：标题、Agent 运行状态。
- 消息区：复用共享 `MessageBubble`，展示正文、思考、工具调用和 `ask_user` 中断卡片。
- 输入区：复用 `Composer`，回车发送，运行中可停止。
- 错误提示：复用 `ErrorToast`。

## 3. 交互规则

- 新建问答后立即进入 `/query/:conversationId`。
- 同一查询会话可以跨问题动态选图，不绑定固定图。
- 主 Agent 的查询工具过程默认折叠，展开后仍显示真实参数与结果。
- `NEEDS_USER` 不进入前端协议；由主 Agent 调 `ask_user`，前端复用现有中断卡片。
- 查询结果正文由主 Agent 输出；证据随 `task` 工具结果返回，前端能解析时显示摘要，解析失败时保留原始结果。
- 查询会话不订阅数据连接事件流，只加载消息历史和 Agent SSE。

## 4. 组件与状态拆分

| 文件 | 职责 |
|---|---|
| `features/query/views/QueryWorkbench.tsx` | 页面编排与两栏布局。 |
| `features/query/components/QueryConversationSidebar.tsx` | 会话列表、新建、删除。 |
| `features/query/components/QueryChatPane.tsx` | 头部、消息、输入与错误提示。 |
| `features/query/hooks/useQueryConversations.ts` | 查询会话列表与增删。 |
| `features/query/hooks/useQueryConversation.ts` | 历史消息、Agent 回合、中断恢复。 |
| `features/query/components/QueryEvidence.tsx` | 从 `task` 结果解析并展示证据摘要。 |
| `shared/api/query.ts` | 创建查询会话等查询域接口。 |
| `styles/query.css` | 查询工作台布局与局部样式。 |

## 5. 后端配套

- `POST /api/v1/query/conversations` 创建 `kind='query'` 会话，`resource_id` 使用随机 UUID。
- 会话列表继续复用 `GET /api/v1/conversations?kind=query`。
- 消息、停止、`ask_user` 恢复继续复用现有一轮 Agent 接口。
- 查询会话不产生 `conversation_event`，前端也不连接事件流。

## 6. 建模侧发布入口

- 位置：建模页右侧画布顶部，布局选择器右侧。
- 状态：未发布、已发布 + 本地时间；读取失败显示「发布状态未知」。
- 按钮：未发布为「发布图」，已发布为「重新发布」，提交中为「发布中…」。
- 禁用条件：发布状态读取中、发布提交中、存在待确认的本体改动、存在草稿对象/属性/关系、图内没有对象类型；空改动集和纯视图改动不拦截。
- 发布成功直接更新 React Query 缓存；失败使用页面错误提示展示后端消息。
- 发布后业务问答才能通过图向量召回该图。

## 7. 验收

- 首页可进入业务问答，新建后左侧出现会话。
- 左侧切换会话，右侧加载对应历史消息。
- 发问后能看到流式正文和查询工具过程。
- 遇到需要追问时出现中断卡片，回答后同一会话继续。
- 刷新页面后消息和会话状态仍能恢复。
