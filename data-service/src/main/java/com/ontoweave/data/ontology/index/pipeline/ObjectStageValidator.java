package com.ontoweave.data.ontology.index.pipeline;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import com.ontoweave.data.ontology.store.StageValidation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ObjectStageValidator {

    private final JdbcTemplate jdbc;

    public ObjectStageValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StageValidation validate(
            UUID runId,
            MappingSnapshot mapping,
            ObjectStoreWriter writer
    ) {
        StageValidation base = writer.validate(runId);
        long requiredMissing = 0;
        List<String> samples = new ArrayList<>(base.samples());
        for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
            if (!attribute.required()) {
                continue;
            }
            Long missing = jdbc.queryForObject("""
                    SELECT count(*) FROM data.ontology_object_stage
                    WHERE run_id = ? AND NOT jsonb_exists(properties, ?)
                    """, Long.class, runId, attribute.attributeId().toString());
            requiredMissing += missing == null ? 0 : missing;
            if (samples.size() < 20 && missing != null && missing > 0) {
                samples.add("required attribute missing: " + attribute.attributeId());
            }
        }
        long missingKeys = queryLong(
                runId, "primary_key_value IS NULL OR btrim(primary_key_value) = ''");
        boolean valid = base.duplicateKeyCount() == 0
                && base.requiredMissingCount() == 0
                && missingKeys == 0
                && requiredMissing == 0;
        return new StageValidation(
                valid,
                base.rowCount(),
                base.duplicateKeyCount(),
                base.requiredMissingCount() + requiredMissing,
                samples.subList(0, Math.min(samples.size(), 20))
        );
    }

    private long queryLong(UUID runId, String predicate) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_object_stage WHERE run_id = ? AND " + predicate,
                Long.class,
                runId
        );
        return count == null ? 0 : count;
    }
}
