package com.ontoweave.data.ontology.query.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.query.common.QueryDirection;
import com.ontoweave.data.ontology.query.common.SortSpec;
import com.ontoweave.data.ontology.query.filter.ParsedFilters;
import com.ontoweave.data.ontology.query.filter.QueryDatatype;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcRelationStepQuery {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcRelationStepQuery(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public RelationStepRows query(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID inputTypeId,
            List<String> primaryKeys,
            ParsedFilters filters,
            List<SortSpec> sort,
            QueryDatatype peerPrimaryKeyType,
            int limit
    ) {
        if (primaryKeys.isEmpty()) {
            return new RelationStepRows(List.of(), 0);
        }
        RelatedScope scope = scope(version, ownerId, direction, inputTypeId, primaryKeys);
        List<Object> arguments = new ArrayList<>(scope.arguments());
        String where = where(scope, filters, arguments);
        Long total = jdbc.queryForObject(
                "SELECT count(DISTINCT peer.primary_key_value) " + scope.from() + where,
                Long.class,
                arguments.toArray()
        );
        List<Object> queryArguments = new ArrayList<>(arguments);
        String order = order(sort, peerPrimaryKeyType, queryArguments);
        queryArguments.add(limit);
        List<RelationStepRows.PeerRow> rows = jdbc.query("""
                        SELECT peer.primary_key_value, peer.title_value, peer.properties
                        """ + scope.from() + where + """
                        GROUP BY peer.primary_key_value, peer.title_value,
                                 peer.properties, peer.version_id
                        """ + order + " LIMIT ?",
                (resultSet, rowNumber) -> new RelationStepRows.PeerRow(
                        resultSet.getString("primary_key_value"),
                        resultSet.getString("title_value"),
                        json(resultSet.getString("properties"))
                ),
                queryArguments.toArray()
        );
        return new RelationStepRows(rows, total == null ? 0L : total);
    }

    private RelatedScope scope(
            RelationVersionRow version,
            UUID ownerId,
            QueryDirection direction,
            UUID inputTypeId,
            List<String> primaryKeys
    ) {
        boolean outgoing = direction == QueryDirection.OUTGOING;
        UUID peerTypeId = outgoing ? version.toObjectTypeId() : version.fromObjectTypeId();
        String peerKeyColumn = outgoing ? "to_primary_key" : "from_primary_key";
        String currentTypeColumn = outgoing ? "from_object_type_id" : "to_object_type_id";
        String currentKeyColumn = outgoing ? "from_primary_key" : "to_primary_key";
        String placeholders = String.join(", ", java.util.Collections.nCopies(
                primaryKeys.size(),
                "?"
        ));
        String from = """
                FROM data.ontology_relation_instance ri
                JOIN data.ontology_object_binding peer_binding
                  ON peer_binding.owner_id = ?
                 AND peer_binding.object_type_id = ?
                 AND peer_binding.status = 'ACTIVE'
                JOIN data.ontology_object_instance peer
                  ON peer.version_id = peer_binding.current_index_version_id
                 AND peer.object_type_id = peer_binding.object_type_id
                 AND peer.primary_key_value = ri.%s
                """.formatted(peerKeyColumn);
        List<Object> arguments = new ArrayList<>();
        arguments.add(ownerId);
        arguments.add(peerTypeId);
        arguments.add(version.id());
        arguments.add(version.relationId());
        arguments.add(inputTypeId);
        arguments.addAll(primaryKeys);
        return new RelatedScope(
                from,
                " WHERE ri.version_id = ?\n"
                        + "   AND ri.relation_id = ?\n"
                        + "   AND ri." + currentTypeColumn + " = ?\n"
                        + "   AND ri." + currentKeyColumn + " IN (" + placeholders + ")\n",
                arguments
        );
    }

    private String where(
            RelatedScope scope,
            ParsedFilters filters,
            List<Object> arguments
    ) {
        StringBuilder where = new StringBuilder(scope.where());
        for (String clause : filters.clauses()) {
            where.append("   AND ").append(clause).append('\n');
        }
        arguments.addAll(filters.parameters());
        return where.toString();
    }

    private String order(
            List<SortSpec> sort,
            QueryDatatype primaryKeyType,
            List<Object> arguments
    ) {
        StringBuilder order = new StringBuilder(" ORDER BY ");
        for (SortSpec item : sort) {
            order.append("""
                    (SELECT v.%s FROM data.ontology_object_value v
                     WHERE v.version_id = peer.version_id
                       AND v.primary_key_value = peer.primary_key_value
                       AND v.attribute_id = ? LIMIT 1) %s NULLS LAST,
                    """.formatted(
                    item.datatype().valueColumn(),
                    item.descending() ? "DESC" : "ASC"
            ));
            arguments.add(item.attributeId());
        }
        order.append("peer.%s ASC NULLS LAST, peer.primary_key_value COLLATE \"C\" ASC"
                .formatted(primaryKeyType.sortColumn()));
        return order.toString();
    }

    private JsonNode json(String value) {
        try {
            return mapper.readTree(value == null ? "null" : value);
        } catch (IOException exception) {
            throw new IllegalStateException("invalid ontology JSON value", exception);
        }
    }

    private record RelatedScope(String from, String where, List<Object> arguments) {
    }
}
