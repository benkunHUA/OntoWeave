# P3 W1-03：Java 对象与关系查询

> 依赖：W0（表、实体、仓储、数据集目录）。
> 写作用域：`com.ontoweave.data.ontology.query` 与对应测试。不要改 `ontology/binding`、`ontology/index`、迁移和 Python/前端。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §7、§8.1

## 目标

交付对象列表、搜索、AND 筛选、详情、关系分组与关系实例查询。所有查询只读当前生效索引版本，不回退历史版本。

## 交付物

- `ontology/query/service/ObjectQueryService`
  - `list(objectTypeId, ownerId, page, pageSize, q, filters)`
  - `detail(objectTypeId, ownerId, primaryKey)`
- `ontology/query/service/RelationQueryService`
  - `groups(objectTypeId, ownerId, primaryKey, direction)`
  - `related(objectTypeId, ownerId, primaryKey, relationId, direction, page, pageSize)`
  - `instances(relationId, ownerId, fromPrimaryKey, toPrimaryKey, page, pageSize)`
- `ontology/query/repository/JdbcObjectQuery`
  - 只读 SQL；从绑定取 `current_index_version_id`。
  - 空指针时返回空页和 `indexVersionId = null`。
- `ontology/query/repository/JdbcRelationQuery`
  - 从关系版本取当前版本；外键关系取最近一个 `ACTIVE` 版本。
- `ontology/query/filter/FilterParser`
  - 只接受设计稿 §7.2 的操作符。
  - 从冻结 mapping 取 `datatype`，类型不匹配返回 422。
  - 所有值走参数绑定，不拼字符串。
- `ontology/query/api/OntologyQueryController`
  - `GET /object-types/{id}/objects`
  - `GET /object-types/{id}/object?primaryKey=`
  - `GET /object-types/{id}/object-relations?primaryKey=`
  - `GET /object-types/{id}/object-relations/{relationId}?primaryKey=`
  - `GET /relation-types/{id}/instances`
- 测试：`data-service/src/test/java/com/ontoweave/data/ontology/query/`
  - `ObjectQuerySqlTest`（Testcontainers）
  - `FilterParserTest`
  - `RelationQueryTest`
  - `ObjectQueryApiTest`（MockMvc）

## 固定行为

### 列表

- `page` 从 1 开始，`pageSize` 默认 20、最大 200。
- 固定按主键类型排序：
  - integer/decimal → `primary_key_num`
  - date → `primary_key_date`
  - timestamp → `primary_key_ts`
  - text → `primary_key_value COLLATE "C"`
- 始终返回 `total`、`page`、`pageSize`、`indexVersionId`。
- `q` 对 `primary_key_value` 和 `title_value` 做 `ILIKE '%q%'`。

### 筛选

```json
[{"attributeId":"...","op":"gte","value":100}]
```

- 多条件 AND，用多个 `EXISTS`。
- `contains` 只用于 text/enum。
- `is_null` / `is_not_null` 不要求 value。
- 布尔只允许 `eq`。
- 属性不在当前 mapping 中 → 422。

### 详情

- 返回 `primaryKey`、`title`、`indexVersionId`、`properties` 数组。
- `properties` 按冻结 mapping 顺序，只含已映射属性；未映射由 Python 补 `null`。
- 不存在返回 404。

### 关系

- `direction=outgoing`：当前对象是 from。
- `incoming`：当前对象是 to。
- `both` 只用于分组接口。
- 分组返回：`relationId`、方向、可读名、总数、前 5 个对端对象（主键 + 标题）。
- 没有当前关系版本时返回空分组，不报错。
- 对端标题 join 对端对象当前版本；对端缺失的行不显示。

## 实施步骤

- [ ] 1. `FilterParser` + 表驱动测试（每种类型、合法/非法操作符）。
- [ ] 2. 对象列表 SQL + 分页 + total + 排序测试。
- [ ] 3. `q` 搜索 + trigram 索引命中测试（允许用 `EXPLAIN` 断言）。
- [ ] 4. 对象详情测试。
- [ ] 5. 关系分组与双向查询测试。
- [ ] 6. 关系实例分页与两端主键过滤测试。
- [ ] 7. 空版本、版本切换、对端缺失场景测试。
- [ ] 8. Controller 集成测试 + 错误码。
- [ ] 9. 全量 `./mvnw -q test` 并更新阶段文档。

## 验收命令

```bash
cd data-service && ./mvnw -q test -Dtest='Ontology*Query*,FilterParser*' && ./mvnw -q test
```

## Definition of Done

- 数字主键按数值排序，不出现 `1, 10, 100, 11`。
- 三条件 AND 查询结果正确，`total` 与 `items` 同源。
- 非法操作符、跨类型值、未知属性返回 422，不落到 SQL。
- 空绑定返回空页而不是 500。
- 双向关系查询各自只返回对应方向。
- 查询不接受历史版本参数，只读当前指针。

## 交接说明

- 不改绑定和版本；只读。
- 前端需要的类型在 W0 已冻结；字段命名以 DTO 为准。
- 性能问题先记录 SQL 和 `EXPLAIN`，不在本流改表结构。
