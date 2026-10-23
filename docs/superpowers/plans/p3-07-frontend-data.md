# P3 W3-07：对象数据、关系实例与运行记录

> 依赖：W2 外壳、W1-04 查询与运行接口。
> 写作用域：`frontend/src/features/ontology-workbench/components/tabs/ObjectDataTab.tsx`、`RunsTab.tsx`、`RelationInstancesTab.tsx`、`components/ObjectTable.tsx`、`components/FilterBar.tsx`、`components/ObjectDetailDrawer.tsx`、`components/dialogs/**`、`lib/filters.ts`、`styles/ontology-data.css` 与对应测试。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §7、§10

## 目标

交付对象数据页、关系实例页、运行记录页，以及生成/换源/解绑/取消的确认弹窗。所有列表走 BFF 的分页接口，不做前端聚合。

## 交付物

```text
components/ObjectTable.tsx          分页表格、版本变化提示
components/FilterBar.tsx            多条件 AND 筛选
components/ObjectDetailDrawer.tsx   属性、关系分组、前 5 个对端
components/dialogs/GenerateDialog.tsx
components/dialogs/RebindDialog.tsx
components/dialogs/UnbindDialog.tsx
components/dialogs/CancelRunDialog.tsx
components/tabs/ObjectDataTab.tsx
components/tabs/RunsTab.tsx
components/tabs/RelationInstancesTab.tsx
lib/filters.ts
```

## 固定交互

### 对象数据

- 默认 `pageSize=20`，支持页码跳转，始终显示 `total`。
- 搜索框 300ms 防抖，按主键/标题包含搜索。
- 筛选条件可增删，条件间 AND；操作符按属性类型收窄。
- 行点击打开详情抽屉：属性按定义顺序，未映射显示 `—`。
- 详情内关系分组显示方向、计数、前 5 个对端；点击进入关系分页。
- 响应 `indexVersionId` 变化时，顶部提示“对象已更新”，重置到第 1 页。

### 关系实例

- 分页表格：from 主键/标题、to 主键/标题。
- 可按 `fromPrimaryKey` / `toPrimaryKey` 过滤。
- 空 state 与对象表一致。

### 运行记录

- 列表：状态、触发方式、来源版本、开始/结束、写入数、告警数/跳过数。
- 详情抽屉：阶段、错误码/消息、最多 20 条行级样例。
- 运行中显示取消按钮，确认后调 cancel；终态禁用。
- 失败显示“重试”入口，走生成确认。

### 确认弹窗

- 生成：展示数据集、版本、revision、预计全量重建。
- 换源：展示旧/新数据集，说明“生成成功后才切换”。
- 解绑：展示将删除的对象/关系实例数量，要求二次确认。
- 取消：说明排队与运行中的不同行为。

## 实施步骤

- [ ] 1. `lib/filters.ts` 操作符映射 + 单测。
- [ ] 2. `ObjectTable` 分页/total/空态/版本提示。
- [ ] 3. `FilterBar` 增删条件与类型联动。
- [ ] 4. 对象数据页接列表与搜索。
- [ ] 5. 详情抽屉 + 关系分组 + 关系分页。
- [ ] 6. 关系实例页。
- [ ] 7. 运行记录页 + 详情 + 取消。
- [ ] 8. 四个确认弹窗。
- [ ] 9. 轮询与终态停止、切页清理。
- [ ] 10. 前端全量校验。

## 验收命令

```bash
cd frontend && npx tsc -b && npm run lint && npm test && npm run build
```

## Definition of Done

- 分页、跳页、总数与后端一致。
- 筛选操作符随属性类型变化，非法组合不能提交。
- 版本变化提示只在版本 id 真的变化时出现。
- 详情未映射属性显示 `—`，顺序与本体定义一致。
- 运行中的取消和失败重试都有确认，不重复提交。
- 所有列表在空数据、加载中、错误三种状态下都有明确 UI。

## 交接说明

- 不改画布、外壳和共享 API 封装；确实缺方法时在交付说明里列出，由编排者统一补。
- 格式化工具复用 W2 的 `lib/format.ts`，样式写 `ontology-data.css`，不在本流重复实现。
- 不做前端本地筛选或排序，全部下沉到后端。
- 运行状态轮询复用 W2 的 `useIndexRun`。
