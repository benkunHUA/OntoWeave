# P3 W3-06：映射画布

> 依赖：W2 外壳、W1-04 的 binding/suggest/preview 接口。
> 写作用域：`frontend/src/features/ontology-workbench/components/canvas/**`、`components/tabs/ObjectMappingTab.tsx`、`components/tabs/RelationMappingTab.tsx`、`components/tabs/RelationOverviewTab.tsx`、`lib/mapping.ts`、`styles/ontology-canvas.css` 与对应测试。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §5.1、§10

## 目标

交付对象类型和多对多关系的映射画布：左右两列节点、字段级连接桩、一对一约束、类型校验、自动建议、校验/预览和草稿保存。

## 交付物

```text
components/canvas/
├── MappingCanvas.tsx        画布容器、边与节点编排
├── MappingNode.tsx          属性/字段节点，含连接桩与状态徽标
├── MappingEdge.tsx          可删除连线
├── MappingToolbar.tsx       建议、校验、预览、保存、撤销
└── useMappingDraft.ts       本地草稿、revision、undo 栈
components/tabs/ObjectMappingTab.tsx
components/tabs/RelationMappingTab.tsx
lib/mapping.ts               连线约束与类型兼容判断
styles/ontology-canvas.css
```

## 固定交互

### 对象类型

- 左节点：对象类型属性（名称、类型、必填、主键/标题标记）。
- 右节点：数据集字段（名称、Iceberg 类型、可空）。
- 每个字段一个连接桩；一个属性最多一条边，一个列最多一条边。
- 只允许类型矩阵兼容的连接；不兼容时拒绝并提示原因。
- 主键/标题键在左节点顶部选择；切换时弹确认。
- 未映射必填属性显示阻断徽标，生成按钮禁用。

### 多对多

- 左节点只有两个端口：源端主键、目标端主键，显示主键类型。
- 右节点是连接表字段。
- 必须恰好两条边，且来自不同列。
- 两列类型必须分别兼容两端主键。

### 外键关系

- `RelationOverviewTab` 只读展示：源属性 → 目标对象类型，不绑定数据集。
- 不画数据集节点，不提供保存。

### 草稿与撤销

- 本地编辑维护 undo/redo 栈，最多 50 步。
- 保存带 `baseRevision`；409 时提示“已被 Agent 或其他页面修改”，重载草稿并清空栈。
- 保存成功更新 revision；撤销后再次保存是普通写入。
- Agent 修改后通过刷新草稿反映，不自动合并本地未保存改动。

### 建议、校验、预览

- 建议：调 `binding/suggest`，只填充当前为空的兼容连线，不覆盖已有连线。
- 校验：调 `binding/validate`，阻断与告警分别显示在节点和工具栏。
- 预览：调 `binding/preview`，右侧抽屉展示样例对象或关系对、跳过原因；不生成实例。

## 实施步骤

- [ ] 1. `lib/mapping.ts` 约束函数 + 单测（一对一、类型、必填）。
- [ ] 2. `useMappingDraft` + undo/redo/409 测试。
- [ ] 3. `MappingNode`/`MappingEdge` 渲染与删除。
- [ ] 4. `MappingCanvas` 对象模式。
- [ ] 5. 多对多模式与两条边约束。
- [ ] 6. 工具栏建议/校验/预览。
- [ ] 7. 主键/标题切换确认。
- [ ] 8. 只读外键概览。
- [ ] 9. 样式与可访问性（键盘删除、焦点）。
- [ ] 10. 前端全量校验。

## 验收命令

```bash
cd frontend && npx tsc -b && npm run lint && npm test && npm run build
```

## Definition of Done

- 同一属性/列不能出现第二条边。
- 类型不兼容无法连线，有明确提示。
- 必填未连线时不能生成。
- 撤销/重做跨多次编辑正确，保存后 revision 递增。
- 409 后本地未保存内容不覆盖服务端。
- 预览不产生运行记录或对象数据。
- 画布在 1280 宽度下可用，字段多时节点内部滚动。

## 交接说明

- 不改外壳、数据表格和运行记录。
- API 调用统一走 W2 的 `shared/api/ontology.ts`；确实缺方法时在交付说明里列出，由编排者统一补，不在本流改共享客户端。
- 画布状态只在 `useMappingDraft`，不要散落到节点组件。
