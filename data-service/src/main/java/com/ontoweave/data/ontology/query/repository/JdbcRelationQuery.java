package com.ontoweave.data.ontology.query.repository;

import com.ontoweave.data.ontology.query.common.PageRequest;
import com.ontoweave.data.ontology.query.common.QueryDirection;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
import com.ontoweave.data.ontology.edit.query.EffectiveObjectSql;
import com.ontoweave.data.ontology.edit.query.EffectiveRelationSql;
import com.ontoweave.data.ontology.edit.query.EffectiveValueSql;
import com.ontoweave.data.ontology.query.common.CteDefinition;
import com.ontoweave.data.ontology.query.common.EffectiveQuerySql;
import com.ontoweave.data.ontology.query.common.SqlFragment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRelationQuery {

    private static final String CURRENT_VERSION_SQL = """
            SELECT v.id, v.relation_id, v.relation_kind, v.from_object_type_id,
                   v.to_object_type_id, v.from_object_version_id, v.to_object_version_id
            FROM data.ontology_relation_version v
            LEFT JOIN data.ontology_relation_binding b
              ON b.owner_id = v.owner_id
             AND b.relation_id = v.relation_id
             AND b.status = 'ACTIVE'
            WHERE v.owner_id = ? AND v.relation_id = ?
              AND (
                    (v.relation_kind = 'MANY_TO_MANY' AND b.current_index_version_id = v.id)
                    OR (v.relation_kind = 'FK' AND v.status = 'ACTIVE')
                  )
            ORDER BY CASE WHEN v.relation_kind = 'MANY_TO_MANY' THEN 0 ELSE 1 END,
                     COALESCE(v.activated_at, v.created_at) DESC,
                     v.id
            LIMIT 1
            """;

    private static final String DEFINITION_SELECT = """
            SELECT relation_id, source_type_id, target_type_id, name, cardinality,
                   foreign_key_property_id, foreign_key_datatype,
                   source_primary_key_datatype, target_primary_key_datatype
            FROM data.ontology_relation_definition
            """;

    private final JdbcTemplate jdbc;

    public JdbcRelationQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RelationVersionRow> findCurrentVersion(UUID relationId, UUID ownerId) {
        List<RelationVersionRow> rows = jdbc.query(
                CURRENT_VERSION_SQL,
                (resultSet, rowNumber) -> new RelationVersionRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("relation_id", UUID.class),
                        resultSet.getString("relation_kind"),
                        resultSet.getObject("from_object_type_id", UUID.class),
                        resultSet.getObject("to_object_type_id", UUID.class),
                        resultSet.getObject("from_object_version_id", UUID.class),
                        resultSet.getObject("to_object_version_id", UUID.class)
                ),
                ownerId,
                relationId
        );
        return rows.stream().findFirst();
    }

    public Optional<RelationDefinitionRow> findDefinition(UUID relationId, UUID ownerId) {
        List<RelationDefinitionRow> rows = jdbc.query(
                DEFINITION_SELECT + "WHERE relation_id = ? AND owner_id = ?",
                (resultSet, rowNumber) -> definition(resultSet),
                relationId,
                ownerId
        );
        return rows.stream().findFirst();
    }

    public List<RelationDefinitionRow> findDefinitionsForObjectType(
            UUID objectTypeId,
            UUID ownerId
    ) {
        return jdbc.query(DEFINITION_SELECT + """
                WHERE owner_id = ?
                  AND (source_type_id = ? OR target_type_id = ?)
                ORDER BY name COLLATE "C", relation_id
                """,
                (resultSet, rowNumber) -> definition(resultSet),
                ownerId,
                objectTypeId,
                objectTypeId
        );
    }

    public List<Boolean> findRelationInstances(
            UUID versionId,
            UUID relationId,
            UUID fromTypeId,
            String fromPrimaryKey,
            UUID toTypeId,
            String toPrimaryKey
    ) {
        return jdbc.query("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM data.ontology_relation_instance
                            WHERE version_id = ? AND relation_id = ?
                              AND from_object_type_id = ? AND from_primary_key = ?
                              AND to_object_type_id = ? AND to_primary_key = ?
                        ) AS found
                        """,
                (resultSet, rowNumber) -> resultSet.getBoolean("found"),
                versionId, relationId, fromTypeId, fromPrimaryKey, toTypeId, toPrimaryKey
        );
    }

    public long countRelated(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            String primaryKey
    ) {
        RelatedSql sql = relatedSql(
                version,
                ownerId,
                direction,
                objectTypeId,
                List.of(primaryKey)
        );
        Long count = jdbc.queryForObject(
                sql.prefix() + "SELECT count(DISTINCT peer.primary_key_value) " + sql.fromClause(),
                Long.class,
                sql.arguments().toArray()
        );
        return count == null ? 0L : count;
    }

    public List<RelatedRow> previewRelated(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            String primaryKey,
            QueryDatatype peerPrimaryKeyType,
            int limit
    ) {
        RelatedSql sql = relatedSql(
                version,
                ownerId,
                direction,
                objectTypeId,
                List.of(primaryKey)
        );
        List<Object> arguments = new ArrayList<>(sql.arguments());
        arguments.add(limit);
        return jdbc.query(sql.prefix() + """
                        SELECT peer.primary_key_value, peer.title_value
                        """ + sql.fromClause() + """
                        GROUP BY peer.primary_key_value, peer.title_value, peer.%s
                        ORDER BY peer.%s ASC NULLS LAST,
                                 peer.primary_key_value COLLATE "C" ASC
                        LIMIT ?
                        """.formatted(
                        peerPrimaryKeyType.sortColumn(),
                        peerPrimaryKeyType.sortColumn()
                ),
                (resultSet, rowNumber) -> new RelatedRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value")
                ),
                arguments.toArray()
        );
    }

    public RelatedPage relatedPage(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            String primaryKey,
            QueryDatatype peerPrimaryKeyType,
            PageRequest page
    ) {
        return relatedPage(
                version,
                ownerId,
                direction,
                objectTypeId,
                List.of(primaryKey),
                peerPrimaryKeyType,
                page
        );
    }

    public RelatedPage relatedPage(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            List<String> primaryKeys,
            QueryDatatype peerPrimaryKeyType,
            PageRequest page
    ) {
        RelatedSql sql = relatedSql(
                version,
                ownerId,
                direction,
                objectTypeId,
                primaryKeys
        );
        Long total = jdbc.queryForObject(
                sql.prefix() + "SELECT count(DISTINCT peer.primary_key_value) " + sql.fromClause(),
                Long.class,
                sql.arguments().toArray()
        );
        List<Object> arguments = new ArrayList<>(sql.arguments());
        arguments.add(page.pageSize());
        arguments.add(page.offset());
        List<RelatedRow> items = jdbc.query(sql.prefix() + """
                        SELECT peer.primary_key_value, peer.title_value
                        """ + sql.fromClause() + """
                        GROUP BY peer.primary_key_value, peer.title_value, peer.%s
                        ORDER BY peer.%s ASC NULLS LAST,
                                 peer.primary_key_value COLLATE "C" ASC
                        LIMIT ? OFFSET ?
                        """.formatted(
                        peerPrimaryKeyType.sortColumn(),
                        peerPrimaryKeyType.sortColumn()
                ),
                (resultSet, rowNumber) -> new RelatedRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value")
                ),
                arguments.toArray()
        );
        return new RelatedPage(items, total == null ? 0L : total);
    }

    public RelatedPage relatedRefs(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            List<String> primaryKeys,
            QueryDatatype peerPrimaryKeyType,
            int limit
    ) {
        RelatedSql sql = relatedSql(
                version,
                ownerId,
                direction,
                objectTypeId,
                primaryKeys
        );
        Long total = jdbc.queryForObject(
                sql.prefix() + "SELECT count(DISTINCT peer.primary_key_value) " + sql.fromClause(),
                Long.class,
                sql.arguments().toArray()
        );
        List<Object> arguments = new ArrayList<>(sql.arguments());
        arguments.add(limit);
        List<RelatedRow> items = jdbc.query(sql.prefix() + """
                        SELECT peer.primary_key_value, peer.title_value
                        """ + sql.fromClause() + """
                        GROUP BY peer.primary_key_value, peer.title_value, peer.%s
                        ORDER BY peer.%s ASC NULLS LAST,
                                 peer.primary_key_value COLLATE "C" ASC
                        LIMIT ?
                        """.formatted(
                        peerPrimaryKeyType.sortColumn(),
                        peerPrimaryKeyType.sortColumn()
                ),
                (resultSet, rowNumber) -> new RelatedRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value")
                ),
                arguments.toArray()
        );
        return new RelatedPage(items, total == null ? 0L : total);
    }

    public InstancePage instances(
            RelationVersionRow version,
            UUID ownerId,
            String fromPrimaryKey,
            String toPrimaryKey,
            PageRequest page
    ) {
        InstanceSql sql = instanceSql(version, ownerId, fromPrimaryKey, toPrimaryKey);
        if (sql == null) {
            return new InstancePage(List.of(), 0);
        }
        Long total = jdbc.queryForObject(
                sql.prefix() + "SELECT count(*) " + sql.fromClause() + sql.where(),
                Long.class,
                sql.arguments().toArray()
        );
        List<Object> pageArguments = new ArrayList<>(sql.arguments());
        pageArguments.add(page.pageSize());
        pageArguments.add(page.offset());
        List<InstanceRow> items = jdbc.query(sql.prefix() + """
                        SELECT source.primary_key_value AS from_primary_key,
                               target.primary_key_value AS to_primary_key
                        """ + sql.fromClause() + sql.where() + """
                        ORDER BY source.primary_key_value COLLATE "C" ASC,
                                 target.primary_key_value COLLATE "C" ASC
                        LIMIT ? OFFSET ?
                        """,
                (resultSet, rowNumber) -> new InstanceRow(
                        version.fromObjectTypeId(),
                        resultSet.getString("from_primary_key"),
                        version.toObjectTypeId(),
                        resultSet.getString("to_primary_key")
                ),
                pageArguments.toArray()
        );
        return new InstancePage(items, total == null ? 0L : total);
    }

    private RelatedSql relatedSql(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            List<String> primaryKeys
    ) {
        if (primaryKeys.isEmpty()) {
            throw new IllegalArgumentException("primaryKeys must not be empty");
        }
        boolean outgoing = direction == QueryDirection.OUTGOING;
        UUID currentTypeId = outgoing ? version.fromObjectTypeId() : version.toObjectTypeId();
        UUID peerTypeId = outgoing ? version.toObjectTypeId() : version.fromObjectTypeId();
        if (!currentTypeId.equals(objectTypeId)) {
            return RelatedSql.empty();
        }
        UUID currentVersionId = currentObjectVersion(ownerId, currentTypeId);
        UUID peerVersionId = currentObjectVersion(ownerId, peerTypeId);
        if (currentVersionId == null || peerVersionId == null) {
            return RelatedSql.empty();
        }
        CteDefinition currentObject = EffectiveObjectSql.cte(
                "current_object", currentVersionId, ownerId, currentTypeId
        );
        CteDefinition peerObject = EffectiveObjectSql.cte(
                "peer_object", peerVersionId, ownerId, peerTypeId
        );
        RelationDefinitionRow definition = findDefinition(version.relationId(), ownerId)
                .orElse(null);
        if (definition != null && definition.foreignKeyPropertyId() != null) {
            return foreignKeyRelatedSql(
                    definition,
                    outgoing,
                    currentObject,
                    peerObject,
                    ownerId,
                    peerTypeId,
                    primaryKeys
            );
        }
        CteDefinition relation = EffectiveRelationSql.manyToMany(
                EffectiveRelationSql.DEFAULT_NAME,
                version.id(),
                version.relationId(),
                ownerId
        );
        SqlFragment ctes = EffectiveQuerySql.with(currentObject, peerObject, relation);
        String currentTypeColumn = outgoing ? "from_object_type_id" : "to_object_type_id";
        String currentKeyColumn = outgoing ? "from_primary_key" : "to_primary_key";
        String peerKeyColumn = outgoing ? "to_primary_key" : "from_primary_key";
        String fromClause = """
                FROM effective_relation ri
                JOIN current_object current_object
                  ON current_object.object_type_id = ri.%s
                 AND current_object.primary_key_value = ri.%s
                JOIN peer_object peer
                  ON peer.object_type_id = ri.%s
                 AND peer.primary_key_value = ri.%s
                WHERE ri.%s IN (%s)
                """.formatted(
                currentTypeColumn,
                currentKeyColumn,
                outgoing ? "to_object_type_id" : "from_object_type_id",
                peerKeyColumn,
                currentKeyColumn,
                String.join(", ", java.util.Collections.nCopies(primaryKeys.size(), "?"))
        );
        List<Object> arguments = new ArrayList<>(ctes.arguments());
        arguments.addAll(primaryKeys);
        return new RelatedSql(ctes.sql() + "\n", fromClause, arguments);
    }

    private RelatedSql foreignKeyRelatedSql(
            RelationDefinitionRow definition,
            boolean outgoing,
            CteDefinition currentObject,
            CteDefinition peerObject,
            UUID ownerId,
            UUID peerTypeId,
            List<String> primaryKeys
    ) {
        UUID sourceTypeId = outgoing ? currentObjectType(currentObject) : peerTypeId;
        UUID sourceVersionId = outgoing
                ? versionId(currentObject)
                : versionId(peerObject);
        CteDefinition values = EffectiveValueSql.cte(
                "effective_fk_value",
                sourceVersionId,
                ownerId,
                sourceTypeId
        );
        SqlFragment ctes = EffectiveQuerySql.with(currentObject, peerObject, values);
        String join = fkJoinCondition(
                definition, outgoing ? "peer" : "current_object");
        String fromClause;
        List<Object> arguments = new ArrayList<>(ctes.arguments());
        arguments.add(definition.foreignKeyPropertyId());
        if (outgoing) {
            fromClause = """
                    FROM current_object current_object
                    JOIN effective_fk_value fk
                      ON fk.primary_key_value = current_object.primary_key_value
                     AND fk.attribute_id = ?
                    JOIN peer_object peer ON %s
                    WHERE current_object.primary_key_value IN (%s)
                    """.formatted(join, placeholders(primaryKeys.size()));
        } else {
            fromClause = """
                    FROM peer_object peer
                    JOIN effective_fk_value fk
                      ON fk.primary_key_value = peer.primary_key_value
                     AND fk.attribute_id = ?
                    JOIN current_object current_object ON %s
                    WHERE current_object.primary_key_value IN (%s)
                    """.formatted(join, placeholders(primaryKeys.size()));
        }
        arguments.addAll(primaryKeys);
        return new RelatedSql(ctes.sql() + "\n", fromClause, arguments);
    }

    private InstanceSql instanceSql(
            RelationVersionRow version,
            UUID ownerId,
            String fromPrimaryKey,
            String toPrimaryKey
    ) {
        UUID fromVersionId = currentObjectVersion(ownerId, version.fromObjectTypeId());
        UUID toVersionId = currentObjectVersion(ownerId, version.toObjectTypeId());
        if (fromVersionId == null || toVersionId == null) {
            return null;
        }
        CteDefinition source = EffectiveObjectSql.cte(
                "source_object", fromVersionId, ownerId, version.fromObjectTypeId()
        );
        CteDefinition target = EffectiveObjectSql.cte(
                "target_object", toVersionId, ownerId, version.toObjectTypeId()
        );
        RelationDefinitionRow definition = findDefinition(version.relationId(), ownerId)
                .orElse(null);
        List<CteDefinition> ctes = new ArrayList<>(List.of(source, target));
        List<Object> arguments = new ArrayList<>();
        String fromClause;
        String where = where(fromPrimaryKey, toPrimaryKey, arguments);
        if (definition != null && definition.foreignKeyPropertyId() != null) {
            CteDefinition values = EffectiveValueSql.cte(
                    "effective_fk_value",
                    fromVersionId,
                    ownerId,
                    version.fromObjectTypeId()
            );
            ctes.add(values);
            arguments.add(0, definition.foreignKeyPropertyId());
            fromClause = """
                    FROM source_object source
                    JOIN effective_fk_value fk
                      ON fk.primary_key_value = source.primary_key_value
                     AND fk.attribute_id = ?
                    JOIN target_object target ON %s
                    """.formatted(fkJoinCondition(definition, "target"));
            return new InstanceSql(
                    EffectiveQuerySql.with(ctes.toArray(CteDefinition[]::new)).sql() + "\n",
                    fromClause,
                    where,
                    merge(EffectiveQuerySql.with(ctes.toArray(CteDefinition[]::new)).arguments(), arguments)
            );
        }
        CteDefinition relation = EffectiveRelationSql.manyToMany(
                EffectiveRelationSql.DEFAULT_NAME,
                version.id(),
                version.relationId(),
                ownerId
        );
        ctes.add(relation);
        SqlFragment cteFragment = EffectiveQuerySql.with(ctes.toArray(CteDefinition[]::new));
        arguments.addAll(0, cteFragment.arguments());
        fromClause = """
                FROM effective_relation ri
                JOIN source_object source
                  ON source.object_type_id = ri.from_object_type_id
                 AND source.primary_key_value = ri.from_primary_key
                JOIN target_object target
                  ON target.object_type_id = ri.to_object_type_id
                 AND target.primary_key_value = ri.to_primary_key
                """;
        return new InstanceSql(cteFragment.sql() + "\n", fromClause, where, arguments);
    }

    private String where(
            String fromPrimaryKey,
            String toPrimaryKey,
            List<Object> arguments
    ) {
        StringBuilder where = new StringBuilder("WHERE TRUE\n");
        if (fromPrimaryKey != null && !fromPrimaryKey.isBlank()) {
            where.append("  AND source.primary_key_value = ?\n");
            arguments.add(fromPrimaryKey);
        }
        if (toPrimaryKey != null && !toPrimaryKey.isBlank()) {
            where.append("  AND target.primary_key_value = ?\n");
            arguments.add(toPrimaryKey);
        }
        return where.toString();
    }

    private UUID currentObjectVersion(UUID ownerId, UUID objectTypeId) {
        List<UUID> rows = jdbc.query("""
                        SELECT current_index_version_id
                        FROM data.ontology_object_binding
                        WHERE owner_id = ? AND object_type_id = ? AND status = 'ACTIVE'
                          AND current_index_version_id IS NOT NULL
                        """,
                (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class),
                ownerId,
                objectTypeId
        );
        return rows.stream().findFirst().orElse(null);
    }

    private String primaryKeyColumn(QueryDatatype datatype) {
        return datatype.sortColumn();
    }

    private String comparisonColumn(QueryDatatype datatype) {
        return switch (datatype) {
            case INTEGER, DECIMAL -> "num_value";
            case DATE -> "date_value";
            case TIMESTAMP -> "ts_value";
            case BOOLEAN -> "bool_value";
            case TEXT, ENUM -> "text_value";
        };
    }

    private QueryDatatype foreignKeyType(RelationDefinitionRow definition) {
        String datatype = definition.foreignKeyDatatype();
        return datatype == null || datatype.isBlank()
                ? definition.targetPrimaryKeyType()
                : QueryDatatype.from(datatype);
    }

    private String fkJoinCondition(RelationDefinitionRow definition, String targetAlias) {
        QueryDatatype targetType = definition.targetPrimaryKeyType();
        QueryDatatype foreignKeyType = foreignKeyType(definition);
        String foreignKeyColumn = comparisonColumn(foreignKeyType);
        if (targetType == foreignKeyType && foreignKeyType != QueryDatatype.BOOLEAN) {
            return targetAlias + "." + primaryKeyColumn(targetType)
                    + " = fk." + foreignKeyColumn;
        }
        return "CAST(" + targetAlias + ".primary_key_value AS "
                + castType(definition, foreignKeyType) + ") = fk." + foreignKeyColumn;
    }

    private String castType(RelationDefinitionRow definition, QueryDatatype foreignKeyType) {
        String raw = definition.foreignKeyDatatype();
        return switch (foreignKeyType) {
            case INTEGER, DECIMAL -> "numeric";
            case BOOLEAN -> "boolean";
            case DATE -> "date";
            case TIMESTAMP -> raw != null && raw.toLowerCase().contains("tz")
                    ? "timestamptz" : "timestamp";
            default -> "text";
        };
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private List<Object> merge(List<Object> first, List<Object> second) {
        List<Object> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private UUID currentObjectType(CteDefinition cte) {
        return UUID.fromString(cte.arguments().get(2).toString());
    }

    private UUID versionId(CteDefinition cte) {
        return UUID.fromString(cte.arguments().get(1).toString());
    }

    private RelationDefinitionRow definition(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new RelationDefinitionRow(
                resultSet.getObject("relation_id", UUID.class),
                resultSet.getObject("source_type_id", UUID.class),
                resultSet.getObject("target_type_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("cardinality"),
                resultSet.getObject("foreign_key_property_id", UUID.class),
                resultSet.getString("foreign_key_datatype"),
                resultSet.getString("source_primary_key_datatype"),
                resultSet.getString("target_primary_key_datatype")
        );
    }

    private record RelatedSql(String prefix, String fromClause, List<Object> arguments) {

        private static RelatedSql empty() {
            return new RelatedSql("", """
                    FROM (SELECT NULL::text AS primary_key_value,
                                 NULL::text AS title_value,
                                 NULL::numeric AS primary_key_num,
                                 NULL::date AS primary_key_date,
                                 NULL::timestamptz AS primary_key_ts) peer
                    WHERE FALSE
                    """, List.of());
        }
    }

    private record InstanceSql(
            String prefix,
            String fromClause,
            String where,
            List<Object> arguments
    ) {
    }
}
