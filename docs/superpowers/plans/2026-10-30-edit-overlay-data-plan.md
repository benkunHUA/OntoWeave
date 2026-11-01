# 业务行动编辑覆盖层（数据服务）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在数据服务落地“基础索引 + 编辑覆盖层”的存储、effective 查询和 edit-set preview/apply，让 Action 可以安全修改对象、字段和多对多关系，并留下可回放的审计记录。

**Architecture:** 基础索引三张表保持只读；新增对象状态、字段值、关系状态、执行记录和变更历史五组覆盖表。查询通过 effective SQL 合并当前索引版本与覆盖层；写入通过内部 edit-set 接口在单事务内完成，后端只负责 Action 语义和参数解析。

**Tech Stack:** Java 21、Spring Boot、JdbcTemplate、PostgreSQL 16、Flyway、Testcontainers、JUnit 5、AssertJ。

**设计文档：** `docs/superpowers/specs/2026-10-30-edit-overlay-design.md`

**范围：** 数据服务 migration、effective 读、edit-set preview/apply、execution/change 审计。不包含后端 `BusinessAgent` 改名、Agent 工具、前端确认 UI；这些在下一份计划中完成。

**校验命令：**

```bash
cd data-service && ./mvnw -q -Dtest=OntologyObjectQueryApiTest,OntologyObjectQuerySqlTest,OntologyQueryServiceTest,OntologyRelationQueryTest test
cd data-service && ./mvnw -q test
```

## 文件结构

| 文件 | 职责 |
|---|---|
| `data-service/src/main/resources/db/migration/V11__ontology_edit_overlay.sql` | 覆盖层、execution、change 表与索引。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/model/*.java` | edit-set 请求/响应、对象/字段/关系变更、preview 结果。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveObjectSql.java` | 生成 effective object CTE。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveValueSql.java` | 生成 effective value CTE。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveRelationSql.java` | 生成 effective 多对多/FK 关系 SQL。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcObjectEditStore.java` | 对象/字段覆盖读写。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcRelationEditStore.java` | 关系覆盖读写。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcEditAuditStore.java` | execution/change 写入与回放。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetPreviewService.java` | 只读 preview：计算 before/after、冲突、stale。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetApplyService.java` | 单事务 apply、幂等、冲突检测、readback。 |
| `data-service/src/main/java/com/ontoweave/data/ontology/edit/api/OntologyEditController.java` | 内部 preview/apply 接口。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditOverlayFixtureSupport.java` | 测试用覆盖层插入与断言工具。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/OntologyEditOverlayMigrationTest.java` | migration 表/列/索引存在性。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveObjectReadTest.java` | 对象覆盖合并、删除、用户创建、stale、标题回退。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveValueReadTest.java` | 覆盖后筛选、排序、分页、total。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveRelationReadTest.java` | 多对多覆盖、FK 派生、端点不可见。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetPreviewTest.java` | preview before/after、冲突、stale。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetApplyTest.java` | modify/create/delete/link、幂等、回滚、审计。 |
| `data-service/src/test/java/com/ontoweave/data/ontology/edit/OntologyEditApiTest.java` | 内部 API 契约。 |

## Task 1: 覆盖层 migration

**Files:**
- Create: `data-service/src/main/resources/db/migration/V11__ontology_edit_overlay.sql`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/OntologyEditOverlayMigrationTest.java`

- [ ] **Step 1: 写失败的 migration 测试**

```java
package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.ontology.query.OntologyQueryIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyEditOverlayMigrationTest extends OntologyQueryIntegrationTestSupport {

    @Test
    void createsOverlayAndAuditTables() {
        List<String> tables = List.of(
                "ontology_object_edit",
                "ontology_object_value_edit",
                "ontology_relation_edit",
                "ontology_edit_execution",
                "ontology_edit_change"
        );

        for (String table : tables) {
            assertThat(jdbc.queryForObject(
                    "SELECT to_regclass(?)",
                    String.class,
                    "data." + table
            )).as(table).isNotNull();
        }
    }

    @Test
    void editTablesCarryStaleAndVersionColumns() {
        assertThat(columnExists("ontology_object_edit", "stale_at")).isTrue();
        assertThat(columnExists("ontology_object_value_edit", "stale_reason")).isTrue();
        assertThat(columnExists("ontology_relation_edit", "version")).isTrue();
        assertThat(columnExists("ontology_edit_execution", "idempotency_key")).isTrue();
        assertThat(columnExists("ontology_edit_change", "after_value")).isTrue();
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'data' AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd data-service && ./mvnw -q -Dtest=OntologyEditOverlayMigrationTest test`

Expected: FAIL，`to_regclass` 返回 null 或表不存在。

- [ ] **Step 3: 创建 migration**

把设计稿 §4.2–§4.6 的 DDL 落到 `V11__ontology_edit_overlay.sql`，并确保：

- `ontology_object_edit`、`ontology_object_value_edit`、`ontology_relation_edit` 都有 `stale_at TIMESTAMPTZ` 和 `stale_reason TEXT`。
- `ontology_object_value_edit` 不包含 `json_value`，v1 不支持数组。
- `ontology_edit_execution.status` 只允许 `APPLIED`、`REVERTED`。
- `ontology_edit_execution(owner_id, idempotency_key)` 唯一约束存在。
- 覆盖表按 `owner_id + object_type_id` 建索引；关系表按 `owner_id + relation_id` 建索引。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd data-service && ./mvnw -q -Dtest=OntologyEditOverlayMigrationTest test`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add data-service/src/main/resources/db/migration/V11__ontology_edit_overlay.sql \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/OntologyEditOverlayMigrationTest.java
git commit -m "feat(data): add ontology edit overlay schema"
```

## Task 2: 测试夹具与清理

**Files:**
- Create: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditOverlayFixtureSupport.java`
- Modify: `data-service/src/test/java/com/ontoweave/data/ontology/query/OntologyQueryIntegrationTestSupport.java`

- [ ] **Step 1: 扩展测试清理表**

在 `cleanQueryTables()` 的 `TRUNCATE TABLE` 列表中加入：

```sql
data.ontology_edit_change,
data.ontology_edit_execution,
data.ontology_relation_edit,
data.ontology_object_value_edit,
data.ontology_object_edit,
```

放在基础表之后、`CASCADE` 之前。

- [ ] **Step 2: 创建覆盖层测试夹具**

```java
package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.ontology.query.ObjectQueryFixtureSupport;

import java.util.Map;
import java.util.UUID;

abstract class EditOverlayFixtureSupport extends ObjectQueryFixtureSupport {

    protected void insertObjectEdit(
            ObjectFixture fixture,
            String primaryKey,
            String origin,
            String state,
            String title,
            Map<UUID, Object> patch
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_object_edit
                    (owner_id, object_type_id, primary_key_value, origin, state,
                     title_edited, title_value, properties_patch,
                     source_binding_id, source_dataset_id, source_primary_key_attr_id,
                     updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                ON CONFLICT (owner_id, object_type_id, primary_key_value)
                DO UPDATE SET origin = EXCLUDED.origin, state = EXCLUDED.state,
                              title_edited = EXCLUDED.title_edited,
                              title_value = EXCLUDED.title_value,
                              properties_patch = EXCLUDED.properties_patch,
                              version = data.ontology_object_edit.version + 1,
                              updated_at = now()
                """,
                fixture.ownerId(), fixture.objectTypeId(), primaryKey, origin, state,
                title != null, title, json(patch),
                fixture.bindingId(), UUID.randomUUID(), fixture.primaryKeyAttributeId(),
                UUID.randomUUID()
        );
    }

    protected void insertValueEdit(
            ObjectFixture fixture,
            String primaryKey,
            UUID attributeId,
            Object value
    ) {
        String datatype = fixture.attribute(attributeId).datatype();
        String text = null;
        java.math.BigDecimal number = null;
        java.time.LocalDate date = null;
        java.sql.Timestamp timestamp = null;
        Boolean bool = null;
        if (value != null) {
            switch (datatype) {
                case "integer", "decimal" -> number = new java.math.BigDecimal(value.toString());
                case "date" -> date = java.time.LocalDate.parse(value.toString());
                case "timestamp", "timestamptz" -> timestamp = java.sql.Timestamp.from(
                        java.time.Instant.parse(value.toString())
                );
                case "boolean" -> bool = (Boolean) value;
                default -> text = value.toString();
            }
        }
        jdbc.update("""
                INSERT INTO data.ontology_object_value_edit
                    (owner_id, object_type_id, primary_key_value, attribute_id, value_state,
                     text_value, num_value, date_value, ts_value, bool_value, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, object_type_id, primary_key_value, attribute_id)
                DO UPDATE SET value_state = EXCLUDED.value_state,
                              text_value = EXCLUDED.text_value,
                              num_value = EXCLUDED.num_value,
                              date_value = EXCLUDED.date_value,
                              ts_value = EXCLUDED.ts_value,
                              bool_value = EXCLUDED.bool_value,
                              stale_at = NULL,
                              stale_reason = NULL,
                              updated_at = now()
                """,
                fixture.ownerId(), fixture.objectTypeId(), primaryKey, attributeId,
                value == null ? "CLEAR" : "SET", text, number, date, timestamp, bool,
                UUID.randomUUID()
        );
    }

    protected void markObjectStale(ObjectFixture fixture, String primaryKey, String reason) {
        jdbc.update("""
                UPDATE data.ontology_object_edit
                SET stale_at = now(), stale_reason = ?
                WHERE owner_id = ? AND object_type_id = ? AND primary_key_value = ?
                """, reason, fixture.ownerId(), fixture.objectTypeId(), primaryKey);
    }

    private String json(Map<UUID, Object> values) {
        var node = mapper.createObjectNode();
        values.forEach((id, value) -> node.set(id.toString(), mapper.valueToTree(value)));
        return node.toString();
    }
}
```

- [ ] **Step 3: 跑现有查询测试**

Run: `cd data-service && ./mvnw -q -Dtest=OntologyQueryServiceTest,OntologyRelationQueryTest test`

Expected: PASS；确认清理列表没有破坏现有夹具。

- [ ] **Step 4: Commit**

```bash
git add data-service/src/test/java/com/ontoweave/data/ontology/query/OntologyQueryIntegrationTestSupport.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EditOverlayFixtureSupport.java
git commit -m "test(data): add edit overlay fixtures"
```

## Task 3: effective 对象读取

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveObjectSql.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/query/common/SqlFragment.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcObjectQuery.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveObjectReadTest.java`

- [ ] **Step 1: 写失败测试**

```java
class EffectiveObjectReadTest extends EditOverlayFixtureSupport {

    @Test
    void baseObjectUsesEditedFieldAndKeepsDatasourceField() {
        // 建 Customer(id, name, level)，基础行 name=张三，level=普通
        // 插入 object_edit：origin=BASE, state=ACTIVE, patch={level: VIP}
        // 断言 query 返回 name=张三、level=VIP
    }

    @Test
    void deletedObjectIsInvisible() {
        // 基础行存在，object_edit state=DELETED
        // 断言 total=0
    }

    @Test
    void userCreatedObjectIsVisibleWithoutBaseRow() {
        // object_edit origin=USER, state=ACTIVE, patch={name:李四}
        // 基础行不存在；断言对象可见
    }

    @Test
    void staleEditIsIgnored() {
        // object_edit 有 patch，但 stale_at 非空
        // 断言返回基础值
    }

    @Test
    void clearedTitleFallsBackToPrimaryKey() {
        // title_edited=true, title_value=null
        // 断言 title == primary_key
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveObjectReadTest test`

Expected: FAIL，覆盖层不参与查询。

- [ ] **Step 3: 创建 SQL 片段类型**

```java
package com.ontoweave.data.ontology.query.common;

import java.util.List;

public record SqlFragment(String sql, List<Object> arguments) {
    public SqlFragment {
        arguments = List.copyOf(arguments);
    }
}
```

- [ ] **Step 4: 创建 effective object CTE**

```java
package com.ontoweave.data.ontology.edit.query;

import com.ontoweave.data.ontology.query.common.SqlFragment;

import java.util.List;
import java.util.UUID;

public final class EffectiveObjectSql {
    private EffectiveObjectSql() {
    }

    public static SqlFragment forQuery(UUID versionId, UUID ownerId, UUID objectTypeId) {
        return new SqlFragment("""
                WITH effective_object AS (
                    SELECT b.version_id, b.object_type_id, b.primary_key_value,
                           b.primary_key_num, b.primary_key_date, b.primary_key_ts,
                           CASE
                               WHEN e.title_edited AND COALESCE(e.title_value, '') = ''
                                   THEN b.primary_key_value
                               WHEN e.title_edited THEN e.title_value
                               ELSE b.title_value
                           END AS title_value,
                           (b.properties - COALESCE(e.properties_cleared, '{}'))
                               || COALESCE(e.properties_patch, '{}') AS properties
                    FROM data.ontology_object_instance b
                    LEFT JOIN data.ontology_object_edit e
                      ON e.owner_id = ?
                     AND e.object_type_id = b.object_type_id
                     AND e.primary_key_value = b.primary_key_value
                     AND e.stale_at IS NULL
                    WHERE b.version_id = ?
                      AND COALESCE(e.state, 'ACTIVE') <> 'DELETED'
                      AND COALESCE(e.origin, 'BASE') <> 'USER'

                    UNION ALL

                    SELECT ?, ?, e.primary_key_value,
                           e.primary_key_num, e.primary_key_date, e.primary_key_ts,
                           CASE WHEN COALESCE(e.title_value, '') = ''
                                THEN e.primary_key_value ELSE e.title_value END,
                           e.properties_patch
                    FROM data.ontology_object_edit e
                    WHERE e.owner_id = ? AND e.object_type_id = ?
                      AND e.origin = 'USER' AND e.state = 'ACTIVE'
                      AND e.stale_at IS NULL
                )
                """, List.of(ownerId, versionId, versionId, objectTypeId, ownerId, objectTypeId));
    }
}
```

- [ ] **Step 5: 接入 `JdbcObjectQuery`**

在 `findPage`、`query`、`findDetail` 前拼上 `EffectiveObjectSql.forQuery(...)`，并把 `FROM data.ontology_object_instance o` 改成 `FROM effective_object o`。参数顺序保持：CTE 参数在前，原有查询参数在后。

- [ ] **Step 6: 运行测试确认通过**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveObjectReadTest test`

Expected: PASS。

- [ ] **Step 7: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveObjectSql.java \
        data-service/src/main/java/com/ontoweave/data/ontology/query/common/SqlFragment.java \
        data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcObjectQuery.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveObjectReadTest.java
git commit -m "feat(data): merge object edit overlay into queries"
```

## Task 4: effective 字段值、筛选与排序

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveValueSql.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/filter/FilterParser.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcObjectQuery.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveValueReadTest.java`

- [ ] **Step 1: 写失败测试**

```java
class EffectiveValueReadTest extends EditOverlayFixtureSupport {

    @Test
    void filterUsesEditedValue() {
        // 基础 level=普通，覆盖 level=VIP
        // filters=[level eq VIP] 命中，[level eq 普通] 不命中
    }

    @Test
    void sortUsesEditedValue() {
        // A 基础 amount=10、覆盖 100；B 基础 amount=50
        // desc 排序应为 A、B
    }

    @Test
    void clearedValueSortsAsNull() {
        // 覆盖 CLEAR，排序时排在非空之后
    }

    @Test
    void totalAndPaginationUseEffectiveRows() {
        // 覆盖层新增用户对象后，total 增加，分页不漏不重
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveValueReadTest test`

Expected: FAIL，筛选/排序仍读基础值。

- [ ] **Step 3: 创建 effective value CTE**

```java
public final class EffectiveValueSql {
    private EffectiveValueSql() {
    }

    public static SqlFragment forQuery(UUID versionId, UUID ownerId, UUID objectTypeId) {
        return new SqlFragment("""
                WITH effective_value AS (
                    SELECT 0 AS source_rank, v.attribute_id, v.primary_key_value,
                           v.text_value, v.num_value, v.date_value, v.ts_value, v.bool_value
                    FROM data.ontology_object_value v
                    WHERE v.version_id = ?

                    UNION ALL

                    SELECT 1 AS source_rank, e.attribute_id, e.primary_key_value,
                           e.text_value, e.num_value, e.date_value, e.ts_value, e.bool_value
                    FROM data.ontology_object_value_edit e
                    WHERE e.owner_id = ? AND e.object_type_id = ?
                      AND e.stale_at IS NULL
                ),
                effective_value_dedup AS (
                    SELECT DISTINCT ON (attribute_id, primary_key_value) *
                    FROM effective_value
                    ORDER BY attribute_id, primary_key_value, source_rank DESC
                )
                """, List.of(versionId, ownerId, objectTypeId));
    }
}
```

注意：`value_state='CLEAR'` 的覆盖行所有类型列为空，`DISTINCT ON` 会选到覆盖行，因此基础值被空值遮蔽。

- [ ] **Step 4: 让 `FilterParser` 可替换值表**

新增重载：

```java
public ParsedFilters parse(
        MappingSnapshot mapping,
        List<FilterCondition> conditions,
        String tableAlias,
        String valueTable
) {
    // 现有逻辑不变，把 notExistsNonNull / existsNonNull / comparison
    // 里的 FROM data.ontology_object_value 改成 FROM + valueTable
}
```

原有 `parse(mapping, conditions, tableAlias)` 委托到 `parse(..., valueTable = "data.ontology_object_value")`，保持现有测试行为。

- [ ] **Step 5: 接入 `JdbcObjectQuery`**

- `pageWhere` 使用 `filters.parse(..., "effective_value_dedup")`。
- `queryOrder` 的子查询从 `data.ontology_object_value v` 改成 `effective_value_dedup v`。
- `findPage` / `query` 先拼 `EffectiveValueSql.forQuery(...)`，再拼对象 CTE 或共享 CTE。
- 参数顺序：value CTE 参数 → object CTE 参数 → where 参数。

- [ ] **Step 6: 运行测试**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveObjectReadTest,EffectiveValueReadTest,OntologyQueryServiceTest test`

Expected: PASS。

- [ ] **Step 7: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveValueSql.java \
        data-service/src/main/java/com/ontoweave/data/ontology/query/filter/FilterParser.java \
        data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcObjectQuery.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveValueReadTest.java
git commit -m "feat(data): use effective values for filters and sorting"
```

## Task 5: effective 多对多关系

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveRelationSql.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcRelationQuery.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveRelationReadTest.java`

- [ ] **Step 1: 写失败测试**

```java
class EffectiveRelationReadTest extends EditOverlayFixtureSupport {

    @Test
    void relationCreateOverlayIsVisible() {
        // 基础关系为空；插入 ontology_relation_edit state=ACTIVE
        // relatedBatch 返回对端对象
    }

    @Test
    void relationDeleteOverlayHidesBaseRelation() {
        // 基础关系存在；插入 state=DELETED
        // relatedBatch 返回空
    }

    @Test
    void relationToDeletedObjectIsHidden() {
        // 对端对象 object_edit state=DELETED
        // 关系不返回
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveRelationReadTest test`

Expected: FAIL，关系仍只读基础表。

- [ ] **Step 3: 创建 effective 多对多关系 CTE**

```java
public final class EffectiveRelationSql {
    private EffectiveRelationSql() {
    }

    public static SqlFragment forMultiToMany(UUID versionId, UUID ownerId, UUID relationId) {
        return new SqlFragment("""
                WITH effective_relation AS (
                    SELECT ri.version_id, ri.relation_id, ri.from_object_type_id,
                           ri.from_primary_key, ri.to_object_type_id, ri.to_primary_key
                    FROM data.ontology_relation_instance ri
                    WHERE ri.version_id = ? AND ri.relation_id = ?
                      AND NOT EXISTS (
                          SELECT 1 FROM data.ontology_relation_edit e
                          WHERE e.owner_id = ? AND e.relation_id = ri.relation_id
                            AND e.from_object_type_id = ri.from_object_type_id
                            AND e.from_primary_key = ri.from_primary_key
                            AND e.to_object_type_id = ri.to_object_type_id
                            AND e.to_primary_key = ri.to_primary_key
                            AND e.stale_at IS NULL
                            AND e.state = 'DELETED'
                      )

                    UNION ALL

                    SELECT ?, e.relation_id, e.from_object_type_id, e.from_primary_key,
                           e.to_object_type_id, e.to_primary_key
                    FROM data.ontology_relation_edit e
                    WHERE e.owner_id = ? AND e.relation_id = ?
                      AND e.stale_at IS NULL AND e.state = 'ACTIVE'
                )
                """, List.of(versionId, relationId, ownerId, versionId, ownerId, relationId));
    }
}
```

- [ ] **Step 4: 接入 `JdbcRelationQuery`**

- `relatedSql` 的 `FROM data.ontology_relation_instance ri` 改为 `FROM effective_relation ri`。
- 对端对象 join 改为 effective object CTE，确保对端标题/删除状态正确。
- `instances` 也改为 effective 关系；`total` 和分页一起改。
- FK 关系分支留在 Task 6，不在本任务混入。

- [ ] **Step 5: 运行测试**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveRelationReadTest,OntologyRelationQueryTest test`

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/query/EffectiveRelationSql.java \
        data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcRelationQuery.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveRelationReadTest.java
git commit -m "feat(data): merge relation overlays into traversal"
```

## Task 6: FK 关系从 effective 外键派生

**Files:**
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcRelationQuery.java`
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/query/repository/RelationDefinitionRow.java`（如当前不在单独文件，改 `JdbcRelationQuery` 内部 record）
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveRelationReadTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void fkRelationUsesEditedForeignKeyValue() {
    // Order.customer_id 基础指向 C001；覆盖为 C002
    // 从 Order 查 Customer 关系应返回 C002，不是 C001
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveRelationReadTest#fkRelationUsesEditedForeignKeyValue test`

Expected: FAIL，仍返回 C001。

- [ ] **Step 3: 扩展关系定义读取**

在 `DEFINITION_SELECT` 中加入 `foreign_key_property_id`，并在 `RelationDefinitionRow` 增加对应字段。若字段为空，FK 关系视为不可派生，返回结构化 `NOT_INDEXED` 或空结果。

- [ ] **Step 4: 增加 FK 分支**

`JdbcRelationQuery.relatedSql` 按 `version.relationKind()` 分支：

- `MANY_TO_MANY` 使用 Task 5 的 effective 关系 CTE。
- `FK` 使用 effective object value：

```sql
FROM data.ontology_object_binding peer_binding
JOIN effective_object peer
  ON peer.version_id = peer_binding.current_index_version_id
 AND peer.primary_key_value = <effective_fk_value>
WHERE <current_object>.primary_key_value IN (...)
```

外键属性值必须走 `effective_value_dedup`，不能直接读 `ontology_object_value`。

- [ ] **Step 5: 运行测试**

Run: `cd data-service && ./mvnw -q -Dtest=EffectiveRelationReadTest,OntologyRelationQueryTest test`

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/query/repository/JdbcRelationQuery.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EffectiveRelationReadTest.java
git commit -m "feat(data): derive FK relations from effective values"
```

## Task 7: edit-set 模型与 preview

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/model/EditSetRequest.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/model/EditSetPreview.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetPreviewService.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetPreviewTest.java`

- [ ] **Step 1: 定义请求/响应 record**

```java
public record EditSetRequest(
        UUID executionId,
        UUID ownerId,
        UUID graphId,
        UUID actionTypeId,
        String idempotencyKey,
        Expectations expected,
        List<ObjectCreate> objectCreates,
        List<ObjectUpdate> objectUpdates,
        List<ObjectDelete> objectDeletes,
        List<RelationCreate> relationCreates,
        List<RelationDelete> relationDeletes
) {
}

public record Expectations(List<ObjectExpectation> objects, List<RelationExpectation> relations) {
}

public record ObjectExpectation(UUID objectTypeId, String primaryKey, long version) {
}

public record ObjectUpdate(
        UUID objectTypeId,
        String primaryKey,
        Map<UUID, JsonNode> set,
        List<UUID> clear
) {
}
```

其余 `ObjectCreate`、`ObjectDelete`、`RelationCreate`、`RelationDelete` 按设计稿 §7.1 的 JSON 字段一一映射，不放额外占位字段。

- [ ] **Step 2: preview service**

```java
@Service
public class EditSetPreviewService {
    public EditSetPreview preview(EditSetRequest request) {
        // 1. 解析 current_index_version_id；
        // 2. 校验对象/关系存在与可见；
        // 3. 校验 expected.version；
        // 4. 返回 before/after 与 conflicts；
        // 5. 不写任何覆盖表。
    }
}
```

`EditSetPreview` 至少包含：

```java
public record EditSetPreview(
        UUID executionId,
        List<ObjectChangePreview> objects,
        List<RelationChangePreview> relations,
        List<PreviewConflict> conflicts,
        boolean applicable
) {
}
```

- [ ] **Step 3: 写 preview 测试**

覆盖：

- modify 对象字段的 before/after；
- 删除对象的 Modify 被拒绝；
- 对已删除对象 Create 被允许；
- expected.version 不匹配返回 conflict；
- stale 覆盖不参与 before 值。

- [ ] **Step 4: 运行测试**

Run: `cd data-service && ./mvnw -q -Dtest=EditSetPreviewTest test`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/model \
        data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetPreviewService.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetPreviewTest.java
git commit -m "feat(data): add edit-set preview"
```

## Task 8: apply 对象修改并写审计

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcObjectEditStore.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcEditAuditStore.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetApplyService.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetApplyTest.java`

- [ ] **Step 1: 写失败的 apply 测试**

```java
class EditSetApplyTest extends EditOverlayFixtureSupport {

    @Test
    void applyModifyWritesOverlayAndAudit() {
        // 基础 Customer C001 level=普通
        // edit set: set level=VIP
        // apply 后：
        //   ontology_object_edit 有 patch
        //   ontology_object_value_edit 有 VIP
        //   ontology_edit_execution 一条 APPLIED
        //   ontology_edit_change 有 OBJECT_MODIFY + VALUE_SET
    }

    @Test
    void idempotentRetryDoesNotWriteTwice() {
        // 同一 idempotency_key 调用两次
        // 第二次返回同一结果，change 行数不变
    }

    @Test
    void failedApplyRollsBackEverything() {
        // 第二个对象改失败
        // 断言第一个对象、execution、change 都没有写入
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd data-service && ./mvnw -q -Dtest=EditSetApplyTest test`

Expected: FAIL，apply service 不存在。

- [ ] **Step 3: 实现对象覆盖写入**

`JdbcObjectEditStore` 至少提供：

```java
public void upsertObjectPatch(
        UUID ownerId,
        UUID objectTypeId,
        String primaryKey,
        String origin,
        String state,
        String title,
        Map<UUID, JsonNode> patch,
        Set<UUID> cleared,
        UUID bindingId,
        UUID datasetId,
        UUID primaryKeyAttributeId,
        UUID executionId
) {
    // INSERT ... ON CONFLICT (...) DO UPDATE
    // version = version + 1
}

public void upsertValue(
        UUID ownerId,
        UUID objectTypeId,
        String primaryKey,
        UUID attributeId,
        String datatype,
        JsonNode value,
        UUID executionId
) {
    // value_state=SET/CLEAR，写入对应类型列
}
```

JSONB patch 以属性 ID 字符串为 key；`cleared` 写入 `properties_cleared`。对象级和字段级必须在同一个 service 事务里写。

- [ ] **Step 4: 实现审计写入**

`JdbcEditAuditStore` 至少提供：

```java
public void insertExecution(
        EditSetRequest request,
        JsonNode actionSnapshot,
        JsonNode parameters,
        Map<String, String> baseVersions,
        JsonNode diff
) {
    // INSERT INTO data.ontology_edit_execution
    // status='APPLIED', applied_at=now()
}

public void insertChange(
        UUID executionId,
        int seq,
        String changeKind,
        UUID objectTypeId,
        String primaryKey,
        UUID attributeId,
        UUID relationId,
        JsonNode beforeValue,
        JsonNode afterValue
) {
    // INSERT INTO data.ontology_edit_change
}

public Optional<EditSetResult> findApplied(UUID ownerId, String idempotencyKey) {
    // 用于幂等重试
}
```

- [ ] **Step 5: apply 事务**

```java
@Service
public class EditSetApplyService {

    @Transactional
    public EditSetResult apply(EditSetRequest request) {
        var existing = audit.findApplied(request.ownerId(), request.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        // 1. 读 current version / mapping / binding
        // 2. 校验 expected.version
        // 3. 写对象/字段覆盖
        // 4. 写 execution + change
        // 5. 返回 effective readback
        return result;
    }
}
```

方法必须是 `@Transactional`；任何异常让 Spring 回滚全部写入。

- [ ] **Step 6: 运行测试**

Run: `cd data-service && ./mvnw -q -Dtest=EditSetApplyTest test`

Expected: PASS。

- [ ] **Step 7: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/store \
        data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetApplyService.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetApplyTest.java
git commit -m "feat(data): apply object edits transactionally"
```

## Task 9: create/delete、多对多关系与 stale/conflict

**Files:**
- Modify: `data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetApplyService.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcRelationEditStore.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetApplyTest.java`

- [ ] **Step 1: 扩测试**

```java
@Test
void createObjectBecomesUserOwnedAndIgnoresDatasource() {
    // create C002，之后基础行出现 C002
    // effective 仍是 create 时提供的值
}

@Test
void deleteObjectHidesItAndClearsFieldEdits() {
    // delete C001，之后 modify 失败
}

@Test
void deleteThenCreateStartsWithFreshValues() {
    // delete C001，再 create C001
    // 只保留新 create 的字段
}

@Test
void multiToManyLinkCreateAndDeleteOverlay() {
    // create link 可见，delete link 隐藏基础关系
}

@Test
void concurrentObjectEditReturnsConflict() {
    // expected.version 不匹配 -> EDIT_CONFLICT
}

@Test
void staleOverlayIsIgnoredAndCanBeReplaced() {
    // stale 覆盖不参与查询；新 apply 清除 stale 并写新值
}
```

- [ ] **Step 2: 实现 create**

- 校验 effective 对象不存在（Deleted 墓碑允许创建）。
- 写 `origin=USER`、`state=ACTIVE`、完整 `properties_patch`。
- 写每个非空属性的 `ontology_object_value_edit`。
- 未提供属性视为 null，不继承任何旧覆盖。

- [ ] **Step 3: 实现 delete**

- 写 `state=DELETED`，清空 `properties_patch`、`properties_cleared`、`title_edited`。
- 删除该对象的 `ontology_object_value_edit` 行。
- 删除该对象相关的 `ontology_relation_edit` 行。
- 写 `OBJECT_DELETE` 审计；历史保留在 `ontology_edit_change`。

- [ ] **Step 4: 实现多对多关系**

`JdbcRelationEditStore` 提供：

```java
void upsertRelation(
        UUID ownerId,
        UUID relationId,
        UUID fromObjectTypeId,
        String fromPrimaryKey,
        UUID toObjectTypeId,
        String toPrimaryKey,
        String state,
        UUID executionId
);
```

- `create_link` 写 `ACTIVE`。
- `delete_link` 写 `DELETED`。
- apply 前校验两端 effective 可见，且关系端点与定义一致。

- [ ] **Step 5: 实现冲突与 stale**

- `expected.version` 与当前覆盖行 `version` 不同 → 抛 `EDIT_CONFLICT`，整个事务回滚。
- 新 apply 命中 stale 行时，更新该行并清空 `stale_at/stale_reason`；不要新增重复行。
- 数据源更新不触发冲突。

- [ ] **Step 6: 运行测试**

Run: `cd data-service && ./mvnw -q -Dtest=EditSetApplyTest test`

Expected: PASS。

- [ ] **Step 7: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/store/JdbcRelationEditStore.java \
        data-service/src/main/java/com/ontoweave/data/ontology/edit/service/EditSetApplyService.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/EditSetApplyTest.java
git commit -m "feat(data): support object lifecycle and relation edits"
```

## Task 10: 内部 API 与全量验证

**Files:**
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/api/OntologyEditController.java`
- Create: `data-service/src/main/java/com/ontoweave/data/ontology/edit/model/EditSetResult.java`
- Test: `data-service/src/test/java/com/ontoweave/data/ontology/edit/OntologyEditApiTest.java`

- [ ] **Step 1: 写 API 契约测试**

```java
@Test
void previewReturnsBeforeAfterAndApplicable() throws Exception {
    mvc.perform(post("/internal/v1/ontology/edit-sets/preview")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applicable").value(true))
            .andExpect(jsonPath("$.objects[0].after").exists());
}

@Test
void applyReturnsEffectiveReadback() throws Exception {
    mvc.perform(post("/internal/v1/ontology/edit-sets/apply")
                    .header("X-Internal-Token", INTERNAL_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").exists())
            .andExpect(jsonPath("$.objects[0].properties").exists());
}
```

- [ ] **Step 2: 实现 controller**

```java
@RestController
@RequestMapping("/internal/v1/ontology/edit-sets")
public class OntologyEditController {

    @PostMapping("/preview")
    public EditSetPreview preview(@RequestBody EditSetRequest request) {
        return previewService.preview(request);
    }

    @PostMapping("/apply")
    public EditSetResult apply(@RequestBody EditSetRequest request) {
        return applyService.apply(request);
    }
}
```

接口只做参数校验和转发；业务规则在 service 层。

- [ ] **Step 3: 运行定向测试**

Run: `cd data-service && ./mvnw -q -Dtest=OntologyEditOverlayMigrationTest,EffectiveObjectReadTest,EffectiveValueReadTest,EffectiveRelationReadTest,EditSetPreviewTest,EditSetApplyTest,OntologyEditApiTest test`

Expected: PASS。

- [ ] **Step 4: 跑现有查询回归**

Run: `cd data-service && ./mvnw -q -Dtest=OntologyObjectQueryApiTest,OntologyObjectQuerySqlTest,OntologyQueryServiceTest,OntologyRelationQueryTest test`

Expected: PASS。

- [ ] **Step 5: 跑全量 Java 测试**

Run: `cd data-service && ./mvnw -q test`

Expected: 新增用例全绿；已知 Iceberg/Spark 环境失败单独记录，不作为本计划回归。

- [ ] **Step 6: Commit**

```bash
git add data-service/src/main/java/com/ontoweave/data/ontology/edit/api/OntologyEditController.java \
        data-service/src/main/java/com/ontoweave/data/ontology/edit/model/EditSetResult.java \
        data-service/src/test/java/com/ontoweave/data/ontology/edit/OntologyEditApiTest.java
git commit -m "feat(data): expose internal edit-set preview and apply"
```

## 完成标准

- 基础索引三张表在运行 Action 后保持原值不变。
- 对象覆盖支持 BASE/USER、ACTIVE/DELETED、stale、标题回退主键。
- 字段覆盖参与筛选、排序、分页和 total。
- 多对多关系支持新增/删除覆盖；FK 关系由 effective 外键派生。
- `ontology_edit_execution` 与 `ontology_edit_change` 在成功 apply 时同事务写入。
- 同一 idempotency key 重试不重复写。
- 并发 Action 改同一对象时返回 `EDIT_CONFLICT`。
- 定向测试和现有查询回归全部通过。

## 后续计划

数据服务层完成后，另起一份计划处理：

1. 后端 `ActionExecutionService`：动作定义读取、参数校验、提交条件、规则赋值、edit-set 组装。
2. 现有 Query Agent 改名与工具接入：`execute_graph_action` 的 preview → ask_user → apply → readback。
3. 前端确认卡片与执行结果展示。

Agent 改名和工具接入不在本计划内，避免数据层与 Agent 层同时改动。
