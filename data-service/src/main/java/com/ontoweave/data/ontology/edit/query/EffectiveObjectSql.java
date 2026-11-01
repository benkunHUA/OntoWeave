package com.ontoweave.data.ontology.edit.query;

import com.ontoweave.data.ontology.query.common.CteDefinition;
import com.ontoweave.data.ontology.query.common.SqlFragment;

import java.util.List;
import java.util.UUID;

public final class EffectiveObjectSql {

    public static final String DEFAULT_NAME = "effective_object";

    private EffectiveObjectSql() {
    }

    public static CteDefinition cte(
            String name,
            UUID versionId,
            UUID ownerId,
            UUID objectTypeId
    ) {
        return new CteDefinition(name, body(), List.of(
                ownerId,
                versionId,
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
                ownerId,
                versionId,
                objectTypeId,
                versionId,
                ownerId,
                objectTypeId
        ));
    }

    private static String body() {
        return """
                SELECT b.version_id, b.object_type_id, b.primary_key_value,
                       b.primary_key_num, b.primary_key_date, b.primary_key_ts,
                       CASE
                           WHEN e.title_edited AND COALESCE(e.title_value, '') = ''
                               THEN b.primary_key_value
                           WHEN e.title_edited THEN e.title_value
                           ELSE b.title_value
                       END AS title_value,
                       (b.properties - COALESCE(e.properties_cleared, ARRAY[]::text[]))
                           || COALESCE(e.properties_patch, '{}'::jsonb) AS properties
                FROM data.ontology_object_instance b
                LEFT JOIN data.ontology_object_edit e
                  ON e.owner_id = ?
                 AND e.object_type_id = b.object_type_id
                 AND e.primary_key_value = b.primary_key_value
                 AND e.stale_at IS NULL
                WHERE b.version_id = ? AND b.object_type_id = ?
                  AND (e.state IS NULL OR e.state <> 'DELETED')
                  AND (e.origin IS NULL OR e.origin <> 'USER')

                UNION ALL

                SELECT ?, e.object_type_id, e.primary_key_value,
                       e.primary_key_num, e.primary_key_date, e.primary_key_ts,
                       COALESCE(NULLIF(e.title_value, ''), e.primary_key_value) AS title_value,
                       e.properties_patch AS properties
                FROM data.ontology_object_edit e
                WHERE e.owner_id = ? AND e.object_type_id = ?
                  AND e.origin = 'USER' AND e.state = 'ACTIVE'
                  AND e.stale_at IS NULL
                """;
    }
}
