package com.ontoweave.data.ontology.edit.query;

import com.ontoweave.data.ontology.query.common.CteDefinition;
import com.ontoweave.data.ontology.query.common.SqlFragment;

import java.util.List;
import java.util.UUID;

public final class EffectiveValueSql {

    public static final String DEFAULT_NAME = "effective_value_dedup";

    private EffectiveValueSql() {
    }

    public static CteDefinition cte(
            String name,
            UUID versionId,
            UUID ownerId,
            UUID objectTypeId
    ) {
        return new CteDefinition(name, body(), List.of(
                versionId,
                ownerId,
                objectTypeId,
                versionId,
                ownerId,
                objectTypeId
        ));
    }

    public static SqlFragment forQuery(
            UUID versionId,
            UUID ownerId,
            UUID objectTypeId
    ) {
        return new SqlFragment(body(), List.of(
                versionId,
                ownerId,
                objectTypeId,
                versionId,
                ownerId,
                objectTypeId
        ));
    }

    private static String body() {
        return """
                SELECT DISTINCT ON (attribute_id, primary_key_value)
                       version_id, attribute_id, primary_key_value,
                       text_value, num_value, date_value, ts_value, bool_value
                FROM (
                    SELECT v.version_id, 0 AS source_rank, v.attribute_id,
                           v.primary_key_value, v.text_value, v.num_value,
                           v.date_value, v.ts_value, v.bool_value
                    FROM data.ontology_object_value v
                    WHERE v.version_id = ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM data.ontology_object_edit oe
                          WHERE oe.owner_id = ?
                            AND oe.object_type_id = ?
                            AND oe.primary_key_value = v.primary_key_value
                            AND oe.origin = 'USER'
                            AND oe.stale_at IS NULL
                      )

                    UNION ALL

                    SELECT ?, 1 AS source_rank, e.attribute_id,
                           e.primary_key_value, e.text_value, e.num_value,
                           e.date_value, e.ts_value, e.bool_value
                    FROM data.ontology_object_value_edit e
                    WHERE e.owner_id = ? AND e.object_type_id = ?
                      AND e.stale_at IS NULL
                      AND NOT EXISTS (
                          SELECT 1
                          FROM data.ontology_object_edit oe
                          WHERE oe.owner_id = e.owner_id
                            AND oe.object_type_id = e.object_type_id
                            AND oe.primary_key_value = e.primary_key_value
                            AND oe.stale_at IS NOT NULL
                      )
                ) raw
                ORDER BY attribute_id, primary_key_value, source_rank DESC
                """;
    }
}
