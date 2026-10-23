# 对象映射（P3）技术设计

- 状态：已确认，待实施
- 日期：2026-10-23
- 阶段文档：[p3-ontology-mapping.md](../phases/p3-ontology-mapping.md)
- 前置阶段：[P1 数据进来](../../checks/2026-10-20-p1-data-ingestion-recheck.md)、[P2 数据整理](../../checks/2026-10-20-p2-data-preparation-smoke.md)

## 1. 目标与边界

### 1.1 目标

把已发布数据集按对象类型映射生成可查询的对象实例与关系实例，并在数据集发布新版本后全量重建、成功后原子切换。

第一版能力：

- 对象类型绑定一个已发布数据集，属性与列一对一映射。
- 外键关系复用本体已有定义，由对象属性推导。
- 多对多关系绑定连接表数据集，两列映射两端主键。
- 对象列表、搜索、AND 筛选、详情、关系双向查看。
- 映射草稿、校验、预览、生成、运行记录、取消、解绑、换源。
- Agent 作为映射助手，可建议、校验、预览、改草稿，关键动作需确认。

### 1.2 非目标

- 不从数据集反向创建对象类型、属性或关系定义。
- 不支持数组、struct、map 属性；`is_array` 属性在 P3 显示为暂不支持。
- 不支持复合主键、关系属性、历史对象快照、OR/嵌套筛选、全文搜索、聚合、保存视图、导出。
- 不引入 Flink 作业做索引。
- 不新增细粒度权限，沿用所有者模型。

## 2. 现状与复用

| 能力 | 现状 | P3 复用方式 |
|---|---|---|
| 本体定义 | Python `object_type` / `property` / `relation` | 只读引用 UUID，不复制定义 |
| P1 数据集 | `data.dataset` + `data.dataset_version` | 作为可选数据源 |
| P2 派生数据集 | `data.pipeline_dataset` + `data.pipeline_dataset_version` | 作为可选数据源 |
| Iceberg 读取 | `HadoopCatalogFactory`、`IcebergGenerics.read` | 固定 snapshot 流式读取 |
| 数据库队列 | P2 `PipelineBuildQueue` 的 `SKIP LOCKED` + 租约模式 | 复用同一套领取和恢复语义 |
| 事件 | Java outbox → RabbitMQ topic | 只发状态通知，不作任务来源 |
| Agent 确认 | `backend/app/agent/tools/**` 的 `interrupt` | 写工具复用 |

两个数据集体系的差异由 Java 的 `OntologyDatasetCatalog` 统一封装：

```text
DatasetRef(kind=SYNC,      datasetId, versionId, catalog, namespace, table, snapshotId, schemaJson, ownerId)
DatasetRef(kind=PIPELINE,  datasetId, versionId, catalog, namespace, table, snapshotId, schemaJson, ownerId)
```

绑定只保存 `dataset_kind + dataset_id`，生成时解析当前已发布版本；用户不能直接选择历史版本。

## 3. 总体架构

```text
Frontend /api/v1/ontology/*
        │
        ▼
Python BFF（鉴权、snake_case、错误映射、Agent 工具）
        │  service token + ownerId
        ▼
Java 数据服务 /internal/v1/ontology/*
        ├── 映射与校验
        ├── 索引运行（DB 队列 + worker）
        ├── 对象/关系存储与查询
        └── outbox → RabbitMQ（状态通知）
        │
        ├── PostgreSQL data schema
        └── Iceberg（P1 / P2 数据集）
```

约束：

- Python 不直接读写 Java 的对象存储。
- Java 不调用 Python 的本体定义接口；提交时由 Python 校验对象类型/关系归属，Java 只存 UUID 并校验 `owner_id`。
- 所有写接口带 `ownerId`，Java 逐条校验归属。

## 4. 数据模型

### 4.1 表清单

| 表 | 职责 |
|---|---|
| `ontology_object_binding` | 对象类型 ↔ 数据集的草稿与生效配置 |
| `ontology_relation_definition` | 关系定义的 Java 侧快照，供生成和自动重建使用 |
| `ontology_relation_binding` | 多对多关系 ↔ 连接表数据集的草稿与生效配置 |
| `ontology_object_version` | 每次成功或进行中的对象索引版本 |
| `ontology_relation_version` | 每次成功或进行中的关系索引版本 |
| `ontology_index_run` | 统一运行记录与队列 |
| `ontology_object_instance` | 当前及待清理的对象实例 |
| `ontology_object_value` | 对象属性的筛选派生值 |
| `ontology_relation_instance` | 关系实例 |
| `ontology_object_stage` | 本次 run 的对象暂存 |
| `ontology_object_value_stage` | 本次 run 的派生值暂存 |
| `ontology_relation_stage` | 本次 run 的关系暂存 |

`pg_trgm` 扩展用于主键/标题/文本属性的包含搜索。

### 4.2 绑定表

```sql
CREATE TABLE data.ontology_object_binding (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    object_type_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT NOT NULL DEFAULT 1,
    draft_dataset_kind TEXT NOT NULL,
    draft_dataset_id UUID NOT NULL,
    draft_mapping JSONB NOT NULL,
    active_dataset_kind TEXT,
    active_dataset_id UUID,
    active_dataset_version_id UUID,
    active_mapping_revision BIGINT,
    current_index_version_id UUID,
    validation_state TEXT NOT NULL DEFAULT 'OK',
    validation_error JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_object_binding_type UNIQUE (object_type_id),
    CONSTRAINT ck_object_binding_status CHECK (status IN ('ACTIVE', 'UNBOUND')),
    CONSTRAINT ck_object_binding_kind CHECK (draft_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_object_binding_state CHECK (validation_state IN ('OK', 'NEEDS_REPAIR'))
);

CREATE INDEX ix_object_binding_draft_dataset
    ON data.ontology_object_binding (draft_dataset_kind, draft_dataset_id);
CREATE INDEX ix_object_binding_active_dataset
    ON data.ontology_object_binding (active_dataset_kind, active_dataset_id);
```

`ontology_relation_binding` 结构相同，把 `object_type_id` 换成 `relation_id`，并限定只用于 `many_to_many`。

### 4.3 关系定义快照

Python 仍是本体定义的事实来源；Java 保存一份只读快照，避免生成时跨服务回查。

```sql
CREATE TABLE data.ontology_relation_definition (
    relation_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    source_type_id UUID NOT NULL,
    target_type_id UUID NOT NULL,
    name TEXT NOT NULL,
    cardinality TEXT NOT NULL,
    foreign_key_property_id UUID,
    foreign_key_datatype TEXT,
    source_primary_key_attribute_id UUID NOT NULL,
    source_primary_key_datatype TEXT NOT NULL,
    target_primary_key_attribute_id UUID NOT NULL,
    target_primary_key_datatype TEXT NOT NULL,
    definition_revision BIGINT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_relation_definition_cardinality
        CHECK (cardinality IN ('one_to_one', 'one_to_many', 'many_to_many'))
);

CREATE INDEX ix_relation_definition_source
    ON data.ontology_relation_definition (owner_id, source_type_id);
CREATE INDEX ix_relation_definition_target
    ON data.ontology_relation_definition (owner_id, target_type_id);
```

同步时机：

- 打开关系工作台、保存多对多绑定、预览、生成前，由 Python 调 `PUT /relation-definitions/{relationId}` upsert。
- `definition_revision` 由 Python 提供；关系定义变化时递增。
- Java 生成时只读快照；快照缺失时该关系跳过并告警，不阻塞对象生成。

### 4.4 版本表

```sql
CREATE TABLE data.ontology_object_version (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    binding_id UUID NOT NULL REFERENCES data.ontology_object_binding (id) ON DELETE CASCADE,
    object_type_id UUID NOT NULL,
    source_dataset_kind TEXT NOT NULL,
    source_dataset_id UUID NOT NULL,
    source_dataset_version_id UUID NOT NULL,
    source_snapshot_id BIGINT NOT NULL,
    mapping_snapshot JSONB NOT NULL,
    mapping_revision BIGINT NOT NULL,
    run_id UUID NOT NULL,
    status TEXT NOT NULL,
    row_count BIGINT,
    warning_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    cleanup_started_at TIMESTAMPTZ,
    cleanup_completed_at TIMESTAMPTZ,
    cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_object_version_status
        CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED', 'DISCARDED'))
);

CREATE INDEX ix_object_version_binding
    ON data.ontology_object_version (binding_id, created_at DESC);
CREATE INDEX ix_object_version_owner
    ON data.ontology_object_version (owner_id, object_type_id, created_at DESC);
CREATE INDEX ix_object_version_cleanup
    ON data.ontology_object_version (status, retired_at)
    WHERE status = 'RETIRED';
```

```sql
CREATE TABLE data.ontology_relation_version (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    binding_id UUID REFERENCES data.ontology_relation_binding (id) ON DELETE CASCADE,
    relation_id UUID NOT NULL,
    relation_kind TEXT NOT NULL,
    from_object_type_id UUID NOT NULL,
    to_object_type_id UUID NOT NULL,
    from_object_version_id UUID NOT NULL,
    to_object_version_id UUID NOT NULL,
    source_dataset_kind TEXT,
    source_dataset_id UUID,
    source_dataset_version_id UUID,
    source_snapshot_id BIGINT,
    mapping_snapshot JSONB,
    mapping_revision BIGINT,
    run_id UUID NOT NULL,
    status TEXT NOT NULL,
    row_count BIGINT,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    cleanup_started_at TIMESTAMPTZ,
    cleanup_completed_at TIMESTAMPTZ,
    cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_relation_version_kind CHECK (relation_kind IN ('FK', 'MANY_TO_MANY')),
    CONSTRAINT ck_relation_version_status
        CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED', 'DISCARDED'))
);

CREATE INDEX ix_relation_version_relation
    ON data.ontology_relation_version (owner_id, relation_id, created_at DESC);
```

外键关系没有绑定行，`binding_id` 为空，版本由对象 run 创建。

### 4.5 运行表

```sql
CREATE TABLE data.ontology_index_run (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    subject_kind TEXT NOT NULL,
    subject_id UUID NOT NULL,
    binding_id UUID,
    target_dataset_kind TEXT NOT NULL,
    target_dataset_version_id UUID NOT NULL,
    mapping_snapshot JSONB NOT NULL,
    mapping_revision BIGINT NOT NULL,
    trigger_type TEXT NOT NULL,
    status TEXT NOT NULL,
    phase TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    attempt INTEGER NOT NULL DEFAULT 1,
    parent_run_id UUID,
    error_code TEXT,
    error_message TEXT,
    error_details JSONB,
    stats JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_run_subject CHECK (subject_kind IN ('OBJECT_TYPE', 'RELATION')),
    CONSTRAINT ck_run_trigger
        CHECK (trigger_type IN ('MANUAL', 'DATASET_PUBLISHED', 'RETRY')),
    CONSTRAINT ck_run_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SUPERSEDED')),
    CONSTRAINT ck_run_phase
        CHECK (phase IS NULL OR phase IN ('READING', 'VALIDATING', 'WRITING', 'SWITCHING'))
);

CREATE UNIQUE INDEX uq_run_queued
    ON data.ontology_index_run (subject_kind, subject_id)
    WHERE status = 'QUEUED';
CREATE UNIQUE INDEX uq_run_running
    ON data.ontology_index_run (subject_kind, subject_id)
    WHERE status = 'RUNNING';
CREATE INDEX ix_run_subject
    ON data.ontology_index_run (subject_kind, subject_id, requested_at DESC);
CREATE INDEX ix_run_lease
    ON data.ontology_index_run (lease_expires_at)
    WHERE status = 'RUNNING';
```

### 4.6 实例表

```sql
CREATE TABLE data.ontology_object_instance (
    version_id UUID NOT NULL,
    object_type_id UUID NOT NULL,
    primary_key_value TEXT NOT NULL,
    primary_key_num NUMERIC,
    primary_key_date DATE,
    primary_key_ts TIMESTAMPTZ,
    title_value TEXT,
    properties JSONB NOT NULL,
    PRIMARY KEY (version_id, primary_key_value)
);

CREATE INDEX ix_object_instance_title
    ON data.ontology_object_instance (version_id, title_value);
CREATE INDEX ix_object_instance_title_trgm
    ON data.ontology_object_instance USING gin (title_value gin_trgm_ops);
CREATE INDEX ix_object_instance_pk_trgm
    ON data.ontology_object_instance USING gin (primary_key_value gin_trgm_ops);
CREATE INDEX ix_object_instance_pk_num
    ON data.ontology_object_instance (version_id, primary_key_num);
CREATE INDEX ix_object_instance_pk_date
    ON data.ontology_object_instance (version_id, primary_key_date);
CREATE INDEX ix_object_instance_pk_ts
    ON data.ontology_object_instance (version_id, primary_key_ts);
```

`primary_key_num/date/ts` 只按主键属性类型填一个，用于正确排序；文本主键直接用 `primary_key_value`。

```sql
CREATE TABLE data.ontology_object_value (
    version_id UUID NOT NULL,
    attribute_id UUID NOT NULL,
    primary_key_value TEXT NOT NULL,
    text_value TEXT,
    num_value NUMERIC,
    date_value DATE,
    ts_value TIMESTAMPTZ,
    bool_value BOOLEAN,
    PRIMARY KEY (version_id, attribute_id, primary_key_value)
);

CREATE INDEX ix_object_value_text
    ON data.ontology_object_value (version_id, attribute_id, text_value);
CREATE INDEX ix_object_value_num
    ON data.ontology_object_value (version_id, attribute_id, num_value);
CREATE INDEX ix_object_value_date
    ON data.ontology_object_value (version_id, attribute_id, date_value);
CREATE INDEX ix_object_value_ts
    ON data.ontology_object_value (version_id, attribute_id, ts_value);
CREATE INDEX ix_object_value_bool
    ON data.ontology_object_value (version_id, attribute_id, bool_value);
CREATE INDEX ix_object_value_text_trgm
    ON data.ontology_object_value USING gin (text_value gin_trgm_ops);
```

```sql
CREATE TABLE data.ontology_relation_instance (
    version_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    from_object_type_id UUID NOT NULL,
    from_primary_key TEXT NOT NULL,
    to_object_type_id UUID NOT NULL,
    to_primary_key TEXT NOT NULL,
    PRIMARY KEY (version_id, from_primary_key, to_primary_key)
);

CREATE INDEX ix_relation_instance_from
    ON data.ontology_relation_instance (version_id, from_primary_key);
CREATE INDEX ix_relation_instance_to
    ON data.ontology_relation_instance (version_id, to_primary_key);
```

关系实例不存对象行 ID，只存两端类型和主键值；对端对象缺失时该行不写入。

### 4.7 暂存表

暂存表与正式表同形，额外带 `run_id`，主键不含业务约束，便于先落数据再统一校验。示例：

```sql
CREATE TABLE data.ontology_object_stage (
    run_id UUID NOT NULL,
    object_type_id UUID NOT NULL,
    primary_key_value TEXT,
    primary_key_num NUMERIC,
    primary_key_date DATE,
    primary_key_ts TIMESTAMPTZ,
    title_value TEXT,
    properties JSONB NOT NULL
);
CREATE INDEX ix_object_stage_run ON data.ontology_object_stage (run_id);
```

`ontology_object_value_stage`、`ontology_relation_stage` 同理。run 结束或失败后按 `run_id` 删除。

## 5. 映射模型与校验

### 5.1 映射 JSON

对象类型：

```json
{
  "primaryKeyAttributeId": "uuid",
  "titleKeyAttributeId": "uuid",
  "attributes": [
    {"attributeId": "uuid", "column": "customer_id", "datatype": "text",
     "required": true, "isArray": false},
    {"attributeId": "uuid", "column": "name", "datatype": "text",
     "required": false, "isArray": false},
    {"attributeId": "uuid", "column": "status", "datatype": "enum",
     "required": false, "isArray": false,
     "enumValues": ["active", "closed"]}
  ]
}
```

`datatype`、`required`、`isArray`、`enumValues` 由 Python 从本体定义补齐，Java 校验后随 mapping 一起冻结。Java 因此不需要回查 Python 就能做类型、必填和枚举校验，也能为筛选选择正确的值列。

多对多映射同样带两端主键的 `datatype`：

```json
{
  "fromKeyColumn": "customer_id",
  "fromKeyDatatype": "text",
  "toKeyColumn": "order_id",
  "toKeyDatatype": "integer"
}
```

多对多关系：

```json
{
  "fromKeyColumn": "customer_id",
  "toKeyColumn": "order_id"
}
```

数据集身份单独存在绑定的 `draft_dataset_kind + draft_dataset_id`，不写进 mapping。

### 5.2 类型矩阵

| 属性类型 | 允许的数据集类型 | 规则 |
|---|---|---|
| `text` | string、uuid | uuid 按标准字符串写入 |
| `integer` | int、long | long 超出 int32 时报错 |
| `decimal` | decimal、int、long | 按属性精度校验，超范围报错 |
| `boolean` | boolean | 仅 boolean |
| `date` | date | 不做 timestamp 截断 |
| `timestamp` | timestamp、timestamptz | 统一转 UTC 存储 |
| `enum` | string | 所有非空值必须在 `enum_values` 内 |

不允许：float/double → integer/decimal，string → number/date，date → timestamp，任何数组或嵌套类型。`is_array=true` 的属性不可映射。

### 5.3 主键与标题键

- 主键属性必须存在、单值、非数组，类型为 text / integer / decimal / date / timestamp。
- 主键值统一编码为 `primary_key_value`：
  - integer：十进制无前导零。
  - decimal：按属性 scale 规范化的十进制字符串。
  - date：`yyyy-MM-dd`。
  - timestamp：UTC ISO-8601。
  - text：原样。
- 外键属性值、连接表两列值必须用对端主键的同一编码规则，保证可连接。
- 标题键为空时 `title_value` 回退为主键值；标题键非必填。

### 5.4 校验结果

阻断：

- 对象类型/关系未确认（只允许 `confirmed` / `approved`）。
- 数据集不存在、不可见、未发布或无当前版本。
- 数据集已被其他绑定占用。
- 主键缺失、未映射或类型不允许。
- 必填属性未映射。
- 类型矩阵不兼容、数组属性被映射。
- 多对多两列未映射或与两端主键类型不兼容。
- 运行期：主键为空、主键重复、必填属性为空、值转换失败、枚举值越界。

告警：

- 可选属性未映射，生成时取空。
- 标题为空，回退主键。
- 外键属性未映射，对应外键关系不生成。
- 关系目标缺失，跳过并计数。
- 关系对重复，去重并计数。

阻断失败整批不切换；告警不阻塞。

### 5.5 revision 与并发

- 每次保存草稿 `revision + 1`，使用 `UPDATE ... WHERE id = ? AND revision = ?`。
- 冲突返回 409 `MAPPING_REVISION_CONFLICT`，带当前 revision 与草稿。
- 生成请求带 `mappingRevision`；不一致直接 409。
- 生成时把 mapping 与 revision 冻结进版本和 run。
- Agent 写入返回 `previousMapping`，撤销即按当前 revision 回写；不保存映射历史表。

## 6. 生成

### 6.1 触发

| 触发 | 行为 |
|---|---|
| 用户点生成 | 用草稿 mapping + 草稿数据集，入队 `MANUAL` |
| 数据集发布 | 用生效快照 `mapping_snapshot` + `active_dataset_*`，入队 `DATASET_PUBLISHED` |
| 手动重试 | 复制失败 run 的目标与快照，入队 `RETRY` |

数据集发布钩子放在 Java 的发布事务内：

- P1：`DatasetVersionPublisher.publish`。
- P2：`PipelinePublicationTransaction.publish`。

在同一事务内查 `active_dataset_kind/id` 命中的绑定并 upsert 排队 run，保证版本发布与索引任务入队原子。
- `validation_state = 'NEEDS_REPAIR'` 的绑定不参与自动重建，修复并重新生成后才恢复。
- 发布钩子先用新版本 schema 校验生效 mapping：字段删除、改名或改类型导致映射失效时，置 `validation_state = 'NEEDS_REPAIR'`、写 `validation_error`，不入队；用户修复并重新生成成功后恢复 `OK`。

### 6.2 合并

同一 subject 同时最多一条 `QUEUED`、一条 `RUNNING`。新任务入队时：

- 有 `QUEUED`：原地更新目标版本、mapping 快照、`requested_at`、`trigger_type`。
- 只有 `RUNNING`：插入新的 `QUEUED`，运行中的任务跑完后继续跑最新版本。
- 被覆盖的中间版本不产生额外运行记录。

### 6.3 领取、租约、恢复

worker 每 1 秒轮询：

```sql
UPDATE data.ontology_index_run
SET status = 'RUNNING', started_at = now(), heartbeat_at = now(),
    lease_owner = :worker, lease_expires_at = now() + interval '60 seconds'
WHERE id = (
    SELECT id FROM data.ontology_index_run
    WHERE status = 'QUEUED'
    ORDER BY requested_at
    LIMIT 1
    FOR UPDATE SKIP LOCKED
)
RETURNING id;
```

- 运行中每 10 秒续租一次。
- 启动时与每 30 秒扫描 `status = 'RUNNING' AND lease_expires_at < now()`，标 `FAILED(WORKER_LOST)`，删除该 run 的 stage 数据。
- 最终切换时校验 `WHERE id = ? AND status = 'RUNNING' AND lease_owner = ?`，防止假死 worker 覆盖结果。
- 取消：`QUEUED` 直接 `CANCELLED`；`RUNNING` 写 `cancel_requested_at`，worker 每批检查后停止、清理 stage、置 `CANCELLED`。
- 最大并发由配置控制，默认 2；同一 subject 由唯一索引保证不并发。

### 6.4 对象生成

1. 固定数据集版本与 `snapshotId`。
2. 用 `IcebergGenerics.read(table).useSnapshot(snapshotId)` 流式读取。
3. 按映射逐行构造：
   - 校验主键非空、类型、必填、枚举。
   - 生成 `primary_key_value` 与 `title_value`。
   - 生成 `properties` JSON。
   - 生成 `ontology_object_value_stage` 行。
4. 每 `batchSize` 行写一次 stage，默认 10000，可配。
5. 全部读完后在 stage 上校验：
   - 主键为空计数。
   - 主键重复：`GROUP BY primary_key_value HAVING count(*) > 1 LIMIT 10`。
   - 必填属性为空计数。
6. 任一阻断错误：run 失败，删除 stage，指针不动。
7. 校验通过：分批 `INSERT INTO 正式表 SELECT ... FROM stage WHERE run_id = ?`。此时版本不可见，允许分批。
8. 写入对象版本 `row_count`、`warning_count` 与统计。

### 6.5 关系生成

**外键关系（FK）**

- 在对象 run 内生成，不单独入队。
- 用源对象实例的外键属性值匹配目标对象 `primary_key_value`。
- 目标对象本轮也重建时 join stage，否则 join 目标当前版本。
- 缺失目标跳过并计数，重复对用 `ON CONFLICT DO NOTHING`。

**多对多关系**

- 有独立绑定和 run；连接表数据集发布时触发。
- 用固定 snapshot 读取连接表，两列按两端主键编码后写入 stage。
- 两端对象本轮重建时 join stage，否则 join 当前版本。
- 缺失端跳过并计数。

**触发范围**

任一对象类型重建时，所有 `from` 或 `to` 指向它的关系都重建：

- 外键关系在对象 run 内生成。
- 多对多关系也在对象 run 内生成，与对象版本同一事务切换。
- 关系范围从 `ontology_relation_definition` 查：`source_type_id` 或 `target_type_id` 命中重建对象类型的关系都要重建；快照缺失的关系跳过并告警。
- 对象 run 领取时，把涉及的、仍在排队的多对多 run 标 `SUPERSEDED`，避免重复劳动。
- 对象 run 重建多对多关系时，使用关系绑定的**生效** mapping 快照与生效连接表版本，忽略关系草稿；快照在与对象版本同一事务里冻结。
- 单独的关系 run（连接表发布触发）只切换关系指针。

关系 run 切换前校验 `from_object_version_id` / `to_object_version_id` 仍是当前版本；多对多还要校验来源连接表版本仍是当前版本；任一不满足则标 `SUPERSEDED` 并丢弃结果，避免旧快照覆盖新结果。

### 6.6 原子切换

一个事务内：

1. 校验 run 仍持有租约。
2. 写入/更新对象版本与关系版本为 `ACTIVE`。
3. 更新绑定 `current_index_version_id`、`active_dataset_*`、`active_mapping_revision`。
4. 旧版本标 `RETIRED`、写 `retired_at`。
5. run 置 `SUCCEEDED`，写 stats。
6. 写 outbox 事件。

对象 run 切换时，把本次生成的所有关系版本一并切换。任一失败整批回滚。

### 6.7 清理

- 旧版本延迟 5 分钟（可配）后清理。
- 每批 5000 行，循环删除：
  1. `ontology_object_value` / `ontology_relation_instance`
  2. `ontology_object_instance`
  3. 版本行
- 用 `cleanup_started_at`、`cleanup_completed_at`、`cleanup_attempts` 记录进度。
- 清理失败只重试，不影响查询。
- 生成失败或取消：删除该 run 的 stage 数据，已写入正式表的半成品按 `version_id` 清理。

### 6.8 解绑与换源

解绑：

- `DELETE /binding` 置 `status = 'UNBOUND'`，清空 `current_index_version_id` 与 `active_*`。
- 当前对象/关系版本标 `RETIRED`，指针先失效，再按 6.7 分批物理删除。
- 停止自动重建；保留 `draft_mapping`、草稿数据集和运行记录，便于重新绑定。
- 解绑对象类型时，同步 retire 所有引用它的关系版本。

换源：

- `rebind_dataset` 只修改草稿 `draft_dataset_kind/id`，`revision + 1`，mapping 需要按新字段重新确认。
- 生效对象不变；只有显式生成成功后才切换 `active_dataset_*` 与 `current_index_version_id`。
- 换源后若未重新生成，数据集发布触发的自动重建仍使用旧生效快照。

## 7. 查询

### 7.1 对象列表

`GET /internal/v1/ontology/object-types/{objectTypeId}/objects`

参数：

| 参数 | 说明 |
|---|---|
| `ownerId` | 必填 |
| `page` | 从 1 开始 |
| `pageSize` | 默认 20，最大 200 |
| `q` | 对主键和标题做包含搜索 |
| `filters` | JSON 数组，AND 组合 |

响应：

```json
{
  "items": [{"primaryKey": "...", "title": "...", "indexVersionId": "..."}],
  "total": 123,
  "page": 1,
  "pageSize": 20,
  "indexVersionId": "..."
}
```

排序按主键属性类型选 `primary_key_num/date/ts/value` 升序，保证 offset 分页稳定。

### 7.2 筛选

```json
[
  {"attributeId": "uuid", "op": "eq", "value": "A"},
  {"attributeId": "uuid", "op": "gte", "value": 100}
]
```

操作符按属性类型限制：

| 类型 | 操作符 |
|---|---|
| text / enum | `eq`、`ne`、`contains`、`is_null`、`is_not_null` |
| integer / decimal / date / timestamp | `eq`、`ne`、`gt`、`gte`、`lt`、`lte`、`is_null`、`is_not_null` |
| boolean | `eq`（true/false） |

SQL 用多个 `EXISTS` 组合：

```sql
SELECT o.*
FROM data.ontology_object_instance o
WHERE o.version_id = :version
  AND EXISTS (SELECT 1 FROM data.ontology_object_value v
              WHERE v.version_id = o.version_id
                AND v.attribute_id = :a1
                AND v.num_value >= :v1)
  AND EXISTS (...)
ORDER BY o.primary_key_num
LIMIT :limit OFFSET :offset;
```

`total` 用同一 `WHERE` 做 `count(*)`。

### 7.3 详情

`GET /object-types/{id}/object?primaryKey=...`

返回主键、标题、已映射属性值、`indexVersionId`。Java 按冻结 mapping 的顺序返回；Python BFF 再与本体定义合并，把未映射属性补成 `null` 并按定义顺序输出。

### 7.4 关系

对象详情的关系入口：

- `GET /object-types/{id}/object-relations?primaryKey=...&direction=both`：按关系类型分组，返回方向、可读名、计数、前 5 个对端对象。
- `GET /object-types/{id}/object-relations/{relationId}?primaryKey=...&direction=outgoing|incoming&page=&pageSize=`：分页返回对端对象。

主键一律用查询参数传递，避免文本主键含 `/` 时路径编码问题。

关系实例页：

- `GET /relation-types/{relationId}/instances?page=&pageSize=`，可按 `fromPrimaryKey` / `toPrimaryKey` 过滤。
- 排序 `(from_primary_key, to_primary_key)`。

对端标题 join 对端对象类型的当前版本；版本已被清理时该行不显示，不报错。

### 7.5 版本变化

列表响应带 `indexVersionId`。前端发现与上次不一致时提示“对象已更新”并回到第 1 页。服务端不做历史版本钉住。

## 8. API

### 8.1 Java internal

所有接口要求 `ownerId`，写接口要求 `X-Operation-Id` 以支持重试幂等。

```text
GET    /internal/v1/ontology/bindable-datasets
GET    /internal/v1/ontology/bindable-datasets/{kind}/{datasetId}/columns
PUT    /internal/v1/ontology/relation-definitions/{relationId}

GET    /internal/v1/ontology/object-types/{objectTypeId}/binding
PUT    /internal/v1/ontology/object-types/{objectTypeId}/binding
DELETE /internal/v1/ontology/object-types/{objectTypeId}/binding
POST   /internal/v1/ontology/object-types/{objectTypeId}/binding/validate
POST   /internal/v1/ontology/object-types/{objectTypeId}/binding/preview
POST   /internal/v1/ontology/object-types/{objectTypeId}/binding/generate
GET    /internal/v1/ontology/object-types/{objectTypeId}/binding/runs

GET    /internal/v1/ontology/relation-types/{relationId}/binding
PUT    /internal/v1/ontology/relation-types/{relationId}/binding
DELETE /internal/v1/ontology/relation-types/{relationId}/binding
POST   /internal/v1/ontology/relation-types/{relationId}/binding/preview
POST   /internal/v1/ontology/relation-types/{relationId}/binding/generate
GET    /internal/v1/ontology/relation-types/{relationId}/binding/runs

GET    /internal/v1/ontology/runs/{runId}
POST   /internal/v1/ontology/runs/{runId}/cancel

GET    /internal/v1/ontology/object-types/{objectTypeId}/objects
GET    /internal/v1/ontology/object-types/{objectTypeId}/object?primaryKey=
GET    /internal/v1/ontology/object-types/{objectTypeId}/object-relations?primaryKey=
GET    /internal/v1/ontology/object-types/{objectTypeId}/object-relations/{relationId}?primaryKey=
GET    /internal/v1/ontology/relation-types/{relationId}/overview
GET    /internal/v1/ontology/relation-types/{relationId}/runs
GET    /internal/v1/ontology/relation-types/{relationId}/instances
```

外键关系没有独立绑定：`overview` 返回两端定义、外键属性和最近一次生成统计；`runs` 返回产生过该关系版本的父对象 run。

错误码：

| 码 | 场景 |
|---|---|
| `MAPPING_REVISION_CONFLICT` | 保存/生成 revision 不匹配 |
| `MAPPING_INVALID` | 校验阻断 |
| `DATASET_NOT_FOUND` / `DATASET_NOT_BOUND` | 数据集不可用 |
| `BINDING_CONFLICT` | 数据集已被占用 |
| `RUN_NOT_FOUND` | 运行记录不存在 |
| `RUN_NOT_CANCELLABLE` | 终态任务不能取消 |

### 8.2 Python public

路径为 `/api/v1/ontology/...`，参数 snake_case，错误结构沿用现有 `app/errors.py`。Python 还提供三个只在 BFF 实现的接口：

```text
GET  /api/v1/ontology/resources
POST /api/v1/ontology/object-types/{objectTypeId}/binding/suggest
POST /api/v1/ontology/relation-types/{relationId}/binding/suggest
GET  /api/v1/ontology/conversations?resourceKind=&resourceId=
```

- `resources` 汇总对象类型、外键关系、多对多关系和绑定摘要，供左侧栏一次加载。
- `suggest` 在 Python 侧按名称相似度和类型兼容度计算，不写 Java。
- `conversations` 按 `(owner, resourceKind, resourceId)` 确定性生成会话并确保存在。

Python 负责：

- 从登录态取 `owner_id`。
- 调 Java 前补 `ownerId` 与 service token。
- camelCase ↔ snake_case。
- 把 Java 409/422 映射成现有错误类型。

## 9. Agent 工具

读：

| 工具 | 说明 |
|---|---|
| `list_bindable_datasets(keyword?, limit=20)` | 可选数据集与当前版本摘要 |
| `get_dataset_schema(dataset_id, dataset_kind)` | 字段名、类型、可空、样例 |
| `get_subject_definition(subject_kind, subject_id)` | 属性或关系两端定义 |
| `get_mapping(subject_kind, subject_id)` | 草稿、revision、生效状态、最近运行 |
| `suggest_mapping(subject_kind, subject_id, dataset_id?)` | 建议连线与理由 |
| `validate_mapping(subject_kind, subject_id, mapping?)` | 阻断与告警 |
| `preview_mapping(subject_kind, subject_id, mapping?, limit=20)` | 样例对象或关系对 |
| `list_index_runs(subject_kind, subject_id, limit=20)` | 运行列表 |
| `get_index_run(run_id)` | 运行详情与错误样例 |

写：

| 工具 | 说明 | 确认 |
|---|---|---|
| `update_mapping(subject_kind, subject_id, base_revision, mapping)` | 保存草稿，返回 previousMapping | 改主键/标题键时 |
| `start_generation(subject_kind, subject_id, mapping_revision)` | 提交全量生成，返回 runId | 是 |
| `rebind_dataset(subject_kind, subject_id, dataset_kind, dataset_id, base_revision)` | 换源，旧对象保留到成功 | 是 |
| `unbind(subject_kind, subject_id)` | 解绑并删除实例 | 是 |
| `cancel_index_run(run_id)` | 取消排队或运行中任务 | 是 |

约束：

- 写工具遇到 409 返回冲突与当前 revision，不自动重试。
- Agent 不能创建/修改本体定义，不能直接写对象实例。
- Agent 不在对话轮次内等待生成完成。

## 10. 前端

新增独立路由 `/ontology`，目录：

```text
frontend/src/features/ontology-workbench/
├── views/OntologyWorkbenchView.tsx          编排：选中资源、标签、轮询
├── components/ResourceSidebar.tsx           对象类型、外键关系、多对多关系
├── components/tabs/ObjectMappingTab.tsx
├── components/tabs/ObjectDataTab.tsx
├── components/tabs/RunsTab.tsx
├── components/tabs/RelationMappingTab.tsx
├── components/tabs/RelationInstancesTab.tsx
├── components/tabs/RelationOverviewTab.tsx
├── components/canvas/MappingCanvas.tsx
├── components/canvas/MappingNode.tsx
├── components/canvas/MappingEdge.tsx
├── components/ObjectTable.tsx
├── components/FilterBar.tsx
├── components/dialogs/UnbindDialog.tsx
├── components/dialogs/RebindDialog.tsx
└── lib/constants.ts / filters.ts / format.ts
```

- 画布复用现有 React Flow 依赖，节点显示完整字段和连接桩。
- 映射草稿状态由 `revision` 驱动；保存 409 时提示并重载。
- 运行中有 active run 时每 2 秒轮询，终态停止。
- 样式拆到 `styles/ontology-workbench.css`、`styles/ontology-canvas.css`，在 `App.tsx` 按序 import。

## 11. 事件

Java outbox 发布：

- `ontology.index.run.started`
- `ontology.index.run.succeeded`
- `ontology.index.run.failed`
- `ontology.index.run.cancelled`

负载含 `runId`、`subjectKind`、`subjectId`、`status`、计数、错误码。Python 消费者可写对话事件或唤醒轮询；事件丢失不影响正确性。

## 12. 权限

- 所有表带 `owner_id`。
- Java 每个读写按 `owner_id` 过滤；跨用户返回 404。
- Python 只从登录态取 owner，不接受前端传入的 owner。
- 数据集可见性：P1 走 connection.owner_id，P2 走 pipeline.owner_id。

## 13. 可观测性

运行记录字段：

- 来源数据集、版本、snapshot、mapping revision。
- 阶段：`QUEUED`、`READING`、`VALIDATING`、`WRITING`、`SWITCHING`、终态。
- 计数：读取行数、写入对象数、跳过关系数、告警数。
- 错误：错误码、消息、最多 20 条行级样例（主键或行号）。

日志只记录 runId 与计数，不打印属性值。

## 14. 测试与验收

- Java 单元：类型矩阵、主键编码、校验规则、revision 冲突、合并语义。
- Java 集成（Testcontainers PostgreSQL）：`SKIP LOCKED` 领取、租约恢复、取消、原子切换、旧版本清理、筛选 SQL、双向关系查询。
- Python：BFF 转发与错误码、Agent 工具确认路径、只写草稿约束。
- 前端：画布连线约束、筛选操作符、轮询停止、版本变化提示。
- e2e：2 万行三表场景，覆盖绑定、生成、查询、重建失败、字段改名、解绑。
- 性能：50 万行 × 8 属性，批大小 1k / 10k / 50k 对比，列表首屏 P95，三条件筛选 P95。

## 15. 实施拆分

| 波次 | 文档 | 写作用域 |
|---|---|---|
| W0 | `p3-00-foundation` | Flyway V9、实体/仓储、数据集目录、API DTO 骨架、Python/前端类型 |
| W1 | `p3-01-java-mapping` | `ontology/binding`、校验、预览、revision |
| W1 | `p3-02-java-indexing` | `ontology/index`、run、worker、生成、切换、清理、事件 |
| W1 | `p3-03-java-query` | `ontology/query`、列表、详情、筛选、关系查询 |
| W1 | `p3-04-python-bff-agent` | `backend/app/api/ontology_*`、`services/ontology_*`、`agent/tools/ontology/*` |
| W2 | `p3-05-frontend-shell` | 工作台外壳、侧栏、标签骨架、路由与样式入口 |
| W3 | `p3-06-frontend-canvas` | 映射画布、节点、连线、校验展示 |
| W3 | `p3-07-frontend-data` | 对象表格、筛选、详情、关系、运行记录 |
| W3 | `p3-08-acceptance` | e2e、性能脚本、验收报告 |

W0 必须先合入；W1 各流只依赖 W0 的契约；W2 依赖 W1 的 API；W3 三流写作用域互不重叠。

## 16. 风险与后置

| 风险 | 处理 |
|---|---|
| 50 万行派生值表写放大 | 批大小可调，验收实测；必要时只索引被筛选过的属性 |
| 全局 trigram 索引偏大 | 先按 50 万行验收，超阈值再按对象类型分区 |
| 同一对象类型频繁发版 | 排队合并，运行中不打断 |
| 对象 run 与关系 run 竞争 | 关系切换校验对象版本，过期结果标 `SUPERSEDED` |
| 解绑大版本删除慢 | 指针先置空，物理删除分批异步 |
| Java/Python 跨库无外键 | Python 提交前校验归属，Java 存 UUID 并校验 owner |

后置：数组/JSON 属性、复合主键、关系属性、历史快照、字段级权限、跨对象类型联合查询。
