package com.ontoweave.data.ontology.binding.support;

import com.ontoweave.data.ontology.common.DatasetKind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BindingOccupancyGuard {

    private final JdbcTemplate jdbc;

    public BindingOccupancyGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireAvailable(
            DatasetKind kind,
            UUID datasetId,
            UUID excludingObjectTypeId,
            UUID excludingRelationId
    ) {
        long objectConflicts = jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_binding
                WHERE status = 'ACTIVE'
                  AND (
                    (draft_dataset_kind = ? AND draft_dataset_id = ?)
                    OR (active_dataset_kind = ? AND active_dataset_id = ?)
                  )
                  AND (?::uuid IS NULL OR object_type_id <> ?::uuid)
                """, Long.class,
                kind.name(), datasetId, kind.name(), datasetId,
                excludingObjectTypeId, excludingObjectTypeId);
        long relationConflicts = jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_relation_binding
                WHERE status = 'ACTIVE'
                  AND (
                    (draft_dataset_kind = ? AND draft_dataset_id = ?)
                    OR (active_dataset_kind = ? AND active_dataset_id = ?)
                  )
                  AND (?::uuid IS NULL OR relation_id <> ?::uuid)
                """, Long.class,
                kind.name(), datasetId, kind.name(), datasetId,
                excludingRelationId, excludingRelationId);
        if (objectConflicts + relationConflicts > 0) {
            throw BindingErrors.bindingConflict(kind, datasetId);
        }
    }
}
