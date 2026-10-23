package com.ontoweave.data.ontology.query.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.query.common.PageRequest;
import com.ontoweave.data.ontology.query.filter.ParsedFilters;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcObjectQuery {

    private static final String CURRENT_VERSION_SQL = """
            SELECT v.id, v.mapping_snapshot
            FROM data.ontology_object_binding b
            JOIN data.ontology_object_version v ON v.id = b.current_index_version_id
            WHERE b.owner_id = ? AND b.object_type_id = ? AND b.status = 'ACTIVE'
              AND v.owner_id = b.owner_id AND v.object_type_id = b.object_type_id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcObjectQuery(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<ObjectVersionRow> findCurrentVersion(UUID objectTypeId, UUID ownerId) {
        List<ObjectVersionRow> rows = jdbc.query(
                CURRENT_VERSION_SQL,
                (resultSet, rowNumber) -> new ObjectVersionRow(
                        resultSet.getObject("id", UUID.class),
                        json(resultSet.getString("mapping_snapshot"))
                ),
                ownerId,
                objectTypeId
        );
        return rows.stream().findFirst();
    }

    public ObjectPageRows findPage(
            UUID versionId,
            UUID objectTypeId,
            String q,
            ParsedFilters filters,
            QueryDatatype primaryKeyType,
            PageRequest page
    ) {
        List<Object> arguments = new ArrayList<>();
        String where = pageWhere(versionId, objectTypeId, q, filters, arguments);
        long total = count("""
                SELECT count(*)
                FROM data.ontology_object_instance o
                """ + where, arguments);

        List<Object> pageArguments = new ArrayList<>(arguments);
        pageArguments.add(page.pageSize());
        pageArguments.add(page.offset());
        List<ObjectListItemRow> items = jdbc.query("""
                        SELECT o.primary_key_value, o.title_value
                        FROM data.ontology_object_instance o
                        """ + where + """
                        ORDER BY o.%s ASC NULLS LAST, o.primary_key_value COLLATE "C" ASC
                        LIMIT ? OFFSET ?
                        """.formatted(primaryKeyType.sortColumn()),
                (resultSet, rowNumber) -> new ObjectListItemRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value")
                ),
                pageArguments.toArray()
        );
        return new ObjectPageRows(items, total);
    }

    public Optional<ObjectDetailRow> findDetail(
            UUID versionId,
            UUID objectTypeId,
            String primaryKey
    ) {
        List<ObjectDetailRow> rows = jdbc.query("""
                        SELECT primary_key_value, title_value, properties
                        FROM data.ontology_object_instance
                        WHERE version_id = ? AND object_type_id = ? AND primary_key_value = ?
                        """,
                (resultSet, rowNumber) -> new ObjectDetailRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value"),
                        json(resultSet.getString("properties"))
                ),
                versionId,
                objectTypeId,
                primaryKey
        );
        return rows.stream().findFirst();
    }

    private String pageWhere(
            UUID versionId,
            UUID objectTypeId,
            String q,
            ParsedFilters filters,
            List<Object> arguments
    ) {
        StringBuilder where = new StringBuilder("""
                WHERE o.version_id = ? AND o.object_type_id = ?
                """);
        arguments.add(versionId);
        arguments.add(objectTypeId);
        if (q != null && !q.isBlank()) {
            where.append("""
                      AND (o.primary_key_value ILIKE ? OR COALESCE(o.title_value, '') ILIKE ?)
                    """);
            String pattern = "%" + q.trim() + "%";
            arguments.add(pattern);
            arguments.add(pattern);
        }
        for (String clause : filters.clauses()) {
            where.append("  AND ").append(clause).append('\n');
        }
        arguments.addAll(filters.parameters());
        return where.toString();
    }

    private long count(String sql, List<Object> arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments.toArray());
        return value == null ? 0L : value;
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value == null ? "null" : value);
        } catch (IOException exception) {
            throw new IllegalStateException("invalid ontology JSON value", exception);
        }
    }

    public record ObjectVersionRow(UUID id, JsonNode mappingSnapshot) {
    }

    public record ObjectListItemRow(String primaryKey, String title) {
    }

    public record ObjectPageRows(List<ObjectListItemRow> items, long total) {

        public ObjectPageRows {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record ObjectDetailRow(String primaryKey, String title, JsonNode properties) {
    }
}
