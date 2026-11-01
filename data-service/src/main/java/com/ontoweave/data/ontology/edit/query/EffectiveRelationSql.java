package com.ontoweave.data.ontology.edit.query;

import com.ontoweave.data.ontology.query.common.CteDefinition;

import java.util.List;
import java.util.UUID;

public final class EffectiveRelationSql {

    public static final String DEFAULT_NAME = "effective_relation";

    private EffectiveRelationSql() {
    }

    public static CteDefinition manyToMany(
            String name,
            UUID relationVersionId,
            UUID relationId,
            UUID ownerId
    ) {
        return new CteDefinition(name, body(), List.of(
                relationVersionId,
                relationId,
                ownerId,
                relationId,
                relationVersionId,
                ownerId,
                relationId
        ));
    }

    private static String body() {
        return """
                SELECT ri.version_id, ri.relation_id, ri.from_object_type_id,
                       ri.from_primary_key, ri.to_object_type_id, ri.to_primary_key
                FROM data.ontology_relation_instance ri
                WHERE ri.version_id = ? AND ri.relation_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM data.ontology_relation_edit e
                      WHERE e.owner_id = ?
                        AND e.relation_id = ?
                        AND e.from_object_type_id = ri.from_object_type_id
                        AND e.from_primary_key = ri.from_primary_key
                        AND e.to_object_type_id = ri.to_object_type_id
                        AND e.to_primary_key = ri.to_primary_key
                        AND e.stale_at IS NULL
                  )

                UNION ALL

                SELECT ?, e.relation_id, e.from_object_type_id, e.from_primary_key,
                       e.to_object_type_id, e.to_primary_key
                FROM data.ontology_relation_edit e
                WHERE e.owner_id = ? AND e.relation_id = ?
                  AND e.stale_at IS NULL AND e.state = 'ACTIVE'
                """;
    }
}
