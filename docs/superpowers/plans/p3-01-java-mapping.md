# P3 W1-01：Java 映射、校验与预览

> 依赖：W0（迁移、实体、仓储、数据集目录、Iceberg 读取、`IndexEnqueuer` 接口）。
> 写作用域：`com.ontoweave.data.ontology.binding` 与对应测试。不要改 `ontology/index`、`ontology/query`、迁移和 Python/前端。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §5、§6.1、§8.1

## 目标

交付绑定、映射草稿、revision 乐观锁、校验、预览、关系定义同步和生成入口。生成只入队，不执行索引。

## 交付物

- `ontology/binding/service/OntologyDatasetService`
  - 可选数据集列表：P1 + P2，按 owner、已发布、有当前版本过滤；关键字搜索；分页。
  - 字段列表：列名、Iceberg 类型、是否可空、样例值。
- `ontology/binding/service/RelationDefinitionService`
  - `PUT /relation-definitions/{relationId}` upsert 快照，校验 owner、两端类型、基数、外键属性。
- `ontology/binding/service/ObjectBindingService`
  - 读取绑定（草稿 + 生效 + 校验状态 + 最近 run）。
  - 保存草稿：数据集占用检查、`revision` CAS、重新校验。
  - 解绑：`UNBOUND`、清空当前指针、标记旧版本 `RETIRED`、停止自动重建。
- `ontology/binding/service/RelationBindingService`
  - 多对多绑定；外键关系只提供 overview，不创建绑定。
- `ontology/binding/validation/BindingCheckService`
  - 组装本体属性定义、数据集 schema 和 `MappingSnapshot`，调用 W0 的 `MappingValidator`。
  - 对象：列一对一约束、主键/标题在属性上是否唯一。
  - 外键关系：外键属性类型与目标主键兼容；不兼容只告警到关系。
- `ontology/binding/preview/MappingPreviewService`
  - 固定当前已发布 snapshot，最多读 20 行。
  - 对象：样例对象、主键空/重复、必填为空、类型失败。
  - 多对多：样例关系对、目标缺失、重复对。
  - 只读，不写 stage 或实例。
- `ontology/binding/api/OntologyBindingController`、`OntologyRelationController`、`OntologyDatasetController`
  - 设计稿 §8.1 的 `bindable-datasets`、`binding`、`validate`、`preview`、`generate`、`runs`、`relation-definitions`、`overview`。
- 测试：`data-service/src/test/java/com/ontoweave/data/ontology/binding/`
    - `ObjectBindingApiTest`（MockMvc + Testcontainers）
  - `MappingPreviewTest`（Iceberg 夹具）
  - `RelationDefinitionApiTest`

## 固定行为

### 保存草稿

```text
PUT /internal/v1/ontology/object-types/{objectTypeId}/binding
{ownerId, baseRevision, datasetKind, datasetId, mapping}
```

- `baseRevision = 0` 表示首次创建。
- 数据集被其他绑定占用 → 409 `BINDING_CONFLICT`。
- revision 不匹配 → 409 `MAPPING_REVISION_CONFLICT`，返回当前 revision 与草稿。
- 保存成功后返回新 revision 与校验结果；校验阻断不阻止保存，只标记 `INVALID`。

### 生成

```text
POST .../binding/generate {ownerId, mappingRevision}
```

- 校验必须无阻断。
- revision 与当前草稿不一致 → 409。
- 调 `IndexEnqueuer.enqueueManual`，返回 `runId`。
- 首次生成也走这里。

### 解绑

- 置 `UNBOUND`、清空 `current_index_version_id` 和 `active_*`。
- 旧版本 `RETIRED`；对象类型解绑时同时 retire 引用它的关系版本。
- 保留草稿 mapping、数据集、run 记录。
- 物理删除由 W1-02 的清理 worker 完成。

### 预览

- 使用数据集当前已发布版本；不读取草稿里未发布的版本。
- 返回 `sample`、`blocking`、`warnings`、`skipped`，不返回全量。
- 预览不改变 revision，不写运行记录。

## 实施步骤

- [x] 1. 数据集列表/字段接口 + 集成测试（P1/P2 各一例）。
- [x] 2. 关系定义 upsert + 校验 + 测试。
- [x] 3. 对象绑定 get/put/delete + revision CAS + 占用冲突测试。
- [x] 4. 多对多绑定 get/put/delete + 测试。
- [x] 5. `BindingCheckService` 测试：列一对一、主键/标题唯一、外键兼容；类型矩阵用例由 W0 覆盖。
- [x] 6. 预览服务 + 对象/关系预览测试。
- [x] 7. 生成入口接 `IndexEnqueuer` + 409/422 测试。
- [x] 8. runs 列表接口（读 `ontology_index_run`）+ 测试。
- [x] 9. 外键关系 overview 接口（读关系定义 + 对象绑定）+ 测试。
- [x] 10. 全量 `./mvnw -q test` 并更新阶段文档。

## 验收命令

```bash
cd data-service && ./mvnw -q test -Dtest='Ontology*Binding*,Mapping*' && ./mvnw -q test
```

## Definition of Done

- 两类数据集都能出现在可选列表，未发布/无当前版本/他人数据集不出现。
- 保存映射的 revision 冲突、数据集占用冲突都有 409 和明确错误码。
- 类型矩阵每个不允许的组合都有阻断测试；告警不阻塞。
- 预览不产生任何表写入（用 SQL 计数断言）。
- 生成只写一条 queued run，不执行生成。
- 解绑后查询立即为空，旧版本进入可清理状态。

## 交接说明

- W1-02 实现 `IndexEnqueuer`，本流只调用接口；类型矩阵校验复用 W0 的 `MappingValidator`。
- W1-03 提供对象/关系查询；本流的预览使用自己的只读 SQL，不复用 W1-03 的查询类。
- W1-04 按 §8.1 调本流接口；字段名以 DTO 为准，不在 Python 侧重命名业务字段。
