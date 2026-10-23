# P3 W0：契约与地基

> 依赖：无。**必须先合入 main**，W1/W2/W3 只依赖本文件冻结的契约。
> 写作用域：仅下列文件。不要改 `ontology/binding`、`ontology/index`、`ontology/query`、Python 路由和前端功能目录。
> 设计来源：[对象映射技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md)
> 基线：`9114156`

## 目标

一次性建好 P3 的数据库、Java 实体/仓储、数据集目录、Iceberg 读取入口、跨流接口，以及 Python/前端的共享类型。完成后其他流可以互不依赖地并行开发。

## 交付物

### Java

- `data-service/src/main/resources/db/migration/V9__ontology_mapping.sql`
  - 设计稿 §4 的全部表、约束、索引、`pg_trgm` 扩展。
  - 表名统一 `ontology_` 前缀，schema 为 `data`。
- `com.ontoweave.data.ontology.common`
  - `SubjectKind`、`DatasetKind`、`RunStatus`、`RunPhase`、`VersionStatus`、`ValidationState`、`TriggerType`、`RelationKind` 枚举。
  - `OntologyErrorCode` 常量。
  - `MappingSnapshot` 记录：对象 mapping、多对多 mapping、`revision`、`attributes`（含 `datatype/required/isArray/enumValues`）。
  - `PrimaryKeyCodec`：设计稿 §5.3 的编码规则；`canonicalize(value, datatype, scale)`。
- `com.ontoweave.data.ontology.mapping`
  - `MappingValidator`：设计稿 §5.2、§5.4 的纯规则校验，不依赖 Spring、JPA 或 HTTP。
  - `MappingValidationResult`：`blocking[]`、`warnings[]`，每项带 `code/message/attributeId/column`。
  - 输入为 `MappingSnapshot` + `DatasetRef` schema + 本体属性定义，输出可序列化结果。
- `com.ontoweave.data.ontology.dataset`
  - `DatasetRef`：`kind/datasetId/versionId/catalog/namespace/table/snapshotId/schemaJson/ownerId/rowCount`。
  - `DatasetColumn`：`name/type/optional/example`。
  - `OntologyDatasetCatalog`：`listBindable(ownerId, keyword, page, size)`、`resolve(kind, datasetId)`、`requireCurrent(kind, datasetId, ownerId)`、`listColumns(...)`。
  - 统一 P1 `data.dataset` 与 P2 `data.pipeline_dataset`；只返回已发布且有当前版本的数据。
- `com.ontoweave.data.ontology.iceberg`
  - `IcebergSnapshotReader`：`open(DatasetRef, long snapshotId)` 返回 `CloseableIterable<Record>` 和 `Schema`。
  - `ColumnValueReader`：按列名从 `Record` 取值并做类型归一。
  - 复用 `HadoopCatalogFactory`，固定 snapshot，不读 `main` 最新。
- `com.ontoweave.data.ontology.store`
  - `IndexEnqueuer` 接口：`enqueueManual(...)`、`enqueueOnPublish(kind, datasetId, versionId)`、`enqueueRetry(runId)`。
  - `ObjectStoreWriter` / `RelationStoreWriter` 接口：stage 写入、stage 校验、promote、按版本删除。
  - 仅接口，实现由 W1-02 提供。
- `com.ontoweave.data.ontology.domain` + `repository`
  - JPA 实体：`OntologyRelationDefinitionEntity`、`OntologyObjectBindingEntity`、`OntologyRelationBindingEntity`、`OntologyObjectVersionEntity`、`OntologyRelationVersionEntity`、`OntologyIndexRunEntity`。
  - 仓储方法至少覆盖：
    - 绑定：按对象类型/关系读取、按生效数据集查发布命中、`updateDraft(id, baseRevision, ...)` 条件更新、`activateVersion`、`unbind`、`markNeedsRepair`。
    - 关系定义：按 relationId 读取、按 `source_type_id` / `target_type_id` 查命中关系、upsert 快照。
    - 版本：按 id/绑定/run 查询、`markActive`、`markRetired`、可清理列表。
    - run：`upsertQueued`（原生 `ON CONFLICT` 部分唯一索引）、`lockNextQueued`（`FOR UPDATE SKIP LOCKED`）、`markRunning`、`heartbeat`、`markTerminal`、`findExpiredLeases`、`requestCancel`、`cancelQueued`、按 subject 列表。
- `data-service/src/test/java/com/ontoweave/data/ontology/foundation`
  - Flyway + Testcontainers 迁移测试。
  - 仓储集成测试：queued 合并、`lockNextQueued` 并发、revision CAS、发布命中查询。
  - `MappingValidatorTest`：类型矩阵、必填、主键、数组、枚举、多对多。

### Python

- `backend/alembic/versions/0021_ontology_workbench.py`
  - `conversation.kind` 增加 `ontology_workbench`，保留原 `modeling/data_connection/data_pipeline`。
- `backend/app/vocabulary.py`
  - `ConversationKind.ONTOLOGY_WORKBENCH = "ontology_workbench"`。
- `backend/app/schemas/ontology.py`
  - 共用请求/响应模型：数据集、字段、映射、校验、预览、生成、运行、对象、关系、筛选。
  - 字段用 snake_case；camelCase ↔ snake_case 在客户端层转换。
- `backend/tests/test_ontology_schemas.py`
  - 枚举与筛选操作符校验。

### 前端

- `frontend/src/shared/types/ontology.ts`
  - 与 Python schema 对齐的类型：`OntologyDataset`、`ObjectMapping`、`RelationMapping`、`MappingValidation`、`IndexRun`、`OntologyObject`、`RelationInstance`、`FilterCondition`。
- `frontend/src/shared/types/ontology.test.ts`
  - 只做类型级断言或构造样例，避免运行时空壳。

## 固定契约

必须与设计稿一致，其他流不再改动：

| 契约 | 位置 |
|---|---|
| 表结构与索引 | 设计稿 §4 |
| mapping JSON | 设计稿 §5.1 |
| 主键编码 | 设计稿 §5.3 |
| run 状态与 phase | 设计稿 §6.2、§6.3 |
| API 路径与错误码 | 设计稿 §8 |
| Agent 工具名 | 设计稿 §9 |

`DatasetRef` 的字段一旦冻结，W1-01/02/03 只能读不能加字段；需要新增时由编排者统一改。

## 实施步骤

- [ ] 1. 写 `V9__ontology_mapping.sql`，本地 `mvn test` 让 Flyway 跑通。
- [ ] 2. 建 Java 枚举、`MappingSnapshot`、`PrimaryKeyCodec`，先写编码单测。
- [ ] 2.1 建 `MappingValidator` 与结果模型，用表驱动用例覆盖设计稿 §5.2/§5.4。
- [ ] 3. 建 `DatasetRef`/`DatasetColumn`/`OntologyDatasetCatalog`，用 P1/P2 测试数据验证两类数据集都能解析。
- [ ] 4. 建 `IcebergSnapshotReader`，用现有预览测试的 Iceberg 夹具验证固定 snapshot 读取。
- [ ] 5. 建 JPA 实体与仓储，写 `lockNextQueued` 并发测试和 `upsertQueued` 合并测试。
- [ ] 6. 建 `IndexEnqueuer` 与 writer 接口，只放接口和空实现占位，不写业务逻辑。
- [ ] 7. 建 Alembic 迁移、`ConversationKind`、Python schema。
- [ ] 8. 建前端类型与样例。
- [ ] 9. 跑全量校验并更新阶段文档进度。

## 验收命令

```bash
cd data-service && ./mvnw -q test
cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app
cd frontend && npx tsc -b && npm run lint
```

## Definition of Done

- V9 在空库和已有 P1/P2 数据的库上都能迁移。
- 两类数据集都能被 `OntologyDatasetCatalog` 解析出当前版本和 schema。
- `PrimaryKeyCodec` 覆盖 text/integer/decimal/date/timestamp 的规范化与往返。
- `lockNextQueued` 两线程并发只领到不同 run。
- `upsertQueued` 对同一 subject 只保留一条 queued，并更新目标版本。
- 新文件通过对应语言的 lint/typecheck。

## 交接说明

- W1-01 在 `ontology/binding` 内实现，依赖 `IndexEnqueuer` 接口、`OntologyDatasetCatalog` 和 W0 的 `MappingValidator`。
- W1-02 实现 `IndexEnqueuer`、两个 writer 和 `ontology/index`；发布钩子复用 W0 的 `MappingValidator`。
- W1-03 只依赖 `ontology/query` 与 `DatasetRef`。
- W1-04 只依赖 Python schema 和设计稿 §8 的路径。
- W2 只依赖 `shared/types/ontology.ts` 与 W1-04 的接口。

**共享文件规则**：仓储接口和实体只由 W0 修改。W1 各流如果缺方法，在交付说明里列出，由编排者在整合时统一补；不要在各自分支改同一个仓储文件。
