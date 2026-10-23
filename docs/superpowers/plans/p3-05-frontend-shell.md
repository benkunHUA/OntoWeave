# P3 W2：前端工作台外壳

> 依赖：W1-04 的 `/api/v1/ontology/**` 接口（未就绪时用类型化 mock）。
> 写作用域：`frontend/src/features/ontology-workbench/` 的外壳与占位、`frontend/src/shared/api/ontology.ts`、`frontend/src/shared/chat/components/ConversationTimeline.tsx`、`frontend/src/features/data-pipeline/components/PipelineConversationPane.tsx`、`frontend/src/styles/ontology-workbench.css`、`ontology-canvas.css`、`ontology-data.css`、`frontend/src/app/App.tsx`、`frontend/src/features/home/views/HomePage.tsx`。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §10

## 目标

交付“对象数据工作台”的可运行外壳：左侧对象类型与关系列表，中间对话，右侧三个标签随资源切换；画布和数据标签先放占位，供 W3 填充。

## 交付物

```text
frontend/src/features/ontology-workbench/
├── views/OntologyWorkbenchView.tsx
├── components/ResourceSidebar.tsx
├── components/OntologyConversationPane.tsx
├── components/tabs/ObjectMappingTab.tsx       占位
├── components/tabs/ObjectDataTab.tsx          占位
├── components/tabs/RunsTab.tsx                占位
├── components/tabs/RelationMappingTab.tsx     占位
├── components/tabs/RelationInstancesTab.tsx   占位
├── components/tabs/RelationOverviewTab.tsx    占位
├── hooks/useOntologyResources.ts
├── hooks/useOntologyConversation.ts
├── hooks/useIndexRun.ts
├── lib/constants.ts
└── lib/format.ts
```

- `shared/api/ontology.ts`：覆盖 P3 全部接口的类型化封装（资源、会话、数据集、绑定、建议、校验、预览、生成、取消、运行、对象、关系），复用 `shared/api/client.ts`。W3 不再改这个文件。
- `shared/chat/components/ConversationTimeline.tsx`：从 `PipelineConversationPane` 抽出共享时间线，接受 `eventFilter` 与图标；`PipelineConversationPane` 改为复用。
- 路由：
  - `/ontology` 进入工作台。
  - 首页新增“对象数据”卡片。
- 标签规则：
  - 对象类型：映射画布 / 对象数据 / 运行记录。
  - 多对多关系：关系映射 / 关系实例 / 运行记录。
  - 外键关系：关系概览 / 关系实例 / 运行记录。
- 每 2 秒轮询当前资源的 active run；终态停止；切换资源时取消旧轮询。
- 左侧列表按对象类型、外键关系、多对多关系分组；选中资源写入 URL `?resource=object_type:<id>`，刷新可恢复。

## 实施步骤

- [ ] 1. `shared/api/ontology.ts` + 类型检查。
- [ ] 2. 抽取 `ConversationTimeline`，改 `PipelineConversationPane`，跑现有前端测试确保无回归。
- [ ] 3. `OntologyWorkbenchView` 三栏布局 + 路由 + 首页入口。
- [ ] 4. `ResourceSidebar` 分组、选中、URL 同步。
- [ ] 5. 标签切换与三个占位组件。
- [ ] 6. `useOntologyConversation` 接 `useConversationAgent`，会话按资源创建/复用。
- [ ] 7. `useIndexRun` 轮询与停止条件。
- [ ] 8. 建 `ontology-workbench.css` / `ontology-canvas.css` / `ontology-data.css`，在 `App.tsx` 按序 import。
- [ ] 9. 跑 `tsc/lint/test/build`。

## 验收命令

```bash
cd frontend && npx tsc -b && npm run lint && npm test && npm run build
```

## Definition of Done

- 首页能进入 `/ontology`，深链接能恢复选中资源。
- 三类资源的标签集合正确，切换不闪烁、不保留旧资源状态。
- 轮询只在有 active run 时进行，离开页面或终态后停止。
- 抽取共享时间线后，数据整理工作台测试与行为不变。
- 占位组件不写业务逻辑，W3 可直接替换。
- 新增文件符合 `features/<feature>` 分层与样式拆分约定。

## 交接说明

- W3-06 只改 `components/canvas/**`、`ObjectMappingTab`、`RelationMappingTab`、`RelationOverviewTab` 和 `styles/ontology-canvas.css`。
- W3-07 只改 `ObjectDataTab`、`RunsTab`、`RelationInstancesTab`、`components/ObjectTable.tsx`、`FilterBar.tsx`、`dialogs/**`、`lib/filters.ts` 和 `styles/ontology-data.css`。
- 不要在外壳里写画布或表格逻辑，避免 W3 冲突。
