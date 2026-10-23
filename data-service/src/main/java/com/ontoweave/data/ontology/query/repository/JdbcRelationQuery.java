package com.ontoweave.data.ontology.query.repository;

import com.ontoweave.data.ontology.query.common.PageRequest;
import com.ontoweave.data.ontology.query.common.QueryDirection;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
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
            SELECT relation_id, source_type_id, target_type_id, name,
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

    public long countRelated(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID objectTypeId,
            String primaryKey
    ) {
        RelatedSql sql = relatedSql(version, ownerId, direction, objectTypeId, primaryKey);
        Long count = jdbc.queryForObject(
                "SELECT count(*) " + sql.fromClause(),
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
        RelatedSql sql = relatedSql(version, ownerId, direction, objectTypeId, primaryKey);
        List<Object> arguments = new ArrayList<>(sql.arguments());
        arguments.add(limit);
        return jdbc.query("""
                        SELECT peer.primary_key_value, peer.title_value
                        """ + sql.fromClause() + """
                        ORDER BY peer.%s ASC NULLS LAST,
                                 peer.primary_key_value COLLATE "C" ASC
                        LIMIT ?
                        """.formatted(peerPrimaryKeyType.sortColumn()),
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
        RelatedSql sql = relatedSql(version, ownerId, direction, objectTypeId, primaryKey);
        Long total = jdbc.queryForObject(
                "SELECT count(*) " + sql.fromClause(),
                Long.class,
                sql.arguments().toArray()
        );
        List<Object> arguments = new ArrayList<>(sql.arguments());
        arguments.add(page.pageSize());
        arguments.add(page.offset());
        List<RelatedRow> items = jdbc.query("""
                        SELECT peer.primary_key_value, peer.title_value
                        """ + sql.fromClause() + """
                        ORDER BY peer.%s ASC NULLS LAST,
                                 peer.primary_key_value COLLATE "C" ASC
                        LIMIT ? OFFSET ?
                        """.formatted(peerPrimaryKeyType.sortColumn()),
                (resultSet, rowNumber) -> new RelatedRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value")
                ),
                arguments.toArray()
        );
        return new RelatedPage(items, total == null ? 0L : total);
    }

    public InstancePage instances(
            UUID versionId,
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey,
            PageRequest page
    ) {
        List<Object> arguments = new ArrayList<>();
        String where = instancesWhere(
                versionId,
                relationId,
                fromPrimaryKey,
                toPrimaryKey,
                arguments
        );
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_relation_instance ri " + where,
                Long.class,
                arguments.toArray()
        );
        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(page.pageSize());
        pageArguments.add(page.offset());
        List<InstanceRow> items = jdbc.query("""
                        SELECT ri.from_object_type_id, ri.from_primary_key,
                               ri.to_object_type_id, ri.to_primary_key
                        FROM data.ontology_relation_instance ri
                        """ + where + """
                        ORDER BY ri.from_primary_key COLLATE "C" ASC,
                                 ri.to_primary_key COLLATE "C" ASC
                        LIMIT ? OFFSET ?
                        """,
                (resultSet, rowNumber) -> new InstanceRow(
                        resultSet.getObject("from_object_type_id", UUID.class),
                        resultSet.getString("from_primary_key"),
                        resultSet.getObject("to_object_type_id", UUID.class),
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
            String primaryKey
    ) {
        boolean outgoing = direction == QueryDirection.OUTGOING;
        UUID peerTypeId = outgoing ? version.toObjectTypeId() : version.fromObjectTypeId();
        String peerKeyColumn = outgoing ? "to_primary_key" : "from_primary_key";
        String currentTypeColumn = outgoing ? "from_object_type_id" : "to_object_type_id";
        String currentKeyColumn = outgoing ? "from_primary_key" : "to_primary_key";
        String fromClause = """
                FROM data.ontology_relation_instance ri
                JOIN data.ontology_object_binding peer_binding
                  ON peer_binding.owner_id = ?
                 AND peer_binding.object_type_id = ?
                 AND peer_binding.status = 'ACTIVE'
                JOIN data.ontology_object_instance peer
                  ON peer.version_id = peer_binding.current_index_version_id
                 AND peer.object_type_id = peer_binding.object_type_id
                 AND peer.primary_key_value = ri.%s
                WHERE ri.version_id = ?
                  AND ri.relation_id = ?
                  AND ri.%s = ?
                  AND ri.%s = ?
                """.formatted(peerKeyColumn, currentTypeColumn, currentKeyColumn);
        return new RelatedSql(
                fromClause,
                List.of(ownerId, peerTypeId, version.id(), version.relationId(),
                        objectTypeId, primaryKey)
        );
    }

    private String instancesWhere(
            UUID versionId,
            UUID relationId,
            String fromPrimaryKey,
            String toPrimaryKey,
            List<Object> arguments
    ) {
        StringBuilder where = new StringBuilder("""
                WHERE ri.version_id = ? AND ri.relation_id = ?
                """);
        arguments.add(versionId);
        arguments.add(relationId);
        if (fromPrimaryKey != null && !fromPrimaryKey.isBlank()) {
            where.append("  AND ri.from_primary_key = ?\n");
            arguments.add(fromPrimaryKey);
        }
        if (toPrimaryKey != null && !toPrimaryKey.isBlank()) {
            where.append("  AND ri.to_primary_key = ?\n");
            arguments.add(toPrimaryKey);
        }
        return where.toString();
    }

    private RelationDefinitionRow definition(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new RelationDefinitionRow(
                resultSet.getObject("relation_id", UUID.class),
                resultSet.getObject("source_type_id", UUID.class),
                resultSet.getObject("target_type_id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("source_primary_key_datatype"),
                resultSet.getString("target_primary_key_datatype")
        );
    }

    private record RelatedSql(String fromClause, List<Object> arguments) {
    }
}
