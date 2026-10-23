package com.ontoweave.data.ontology.index.cleanup;

import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Service
public class VersionCleanupService {

    private final JdbcTemplate jdbc;
    private final OntologyIndexProperties properties;

    public VersionCleanupService(JdbcTemplate jdbc, OntologyIndexProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public CleanupResult runOnce(Instant now) {
        Instant cutoff = now.minus(properties.cleanupDelay());
        List<UUID> objectVersions = claimObjectVersions(cutoff);
        List<UUID> relationVersions = claimRelationVersions(cutoff);
        objectVersions.forEach(this::deleteObjectVersion);
        relationVersions.forEach(this::deleteRelationVersion);
        return new CleanupResult(objectVersions.size(), relationVersions.size());
    }

    private List<UUID> claimObjectVersions(Instant cutoff) {
        return jdbc.queryForList("""
                WITH picked AS (
                    SELECT id FROM data.ontology_object_version
                    WHERE status = 'RETIRED' AND retired_at <= ?
                      AND cleanup_completed_at IS NULL
                    ORDER BY retired_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE data.ontology_object_version v
                SET cleanup_started_at = COALESCE(v.cleanup_started_at, now()),
                    cleanup_attempts = v.cleanup_attempts + 1
                FROM picked
                WHERE v.id = picked.id
                RETURNING v.id
                """, UUID.class, Timestamp.from(cutoff), properties.cleanupVersionsPerScan());
    }

    private List<UUID> claimRelationVersions(Instant cutoff) {
        return jdbc.queryForList("""
                WITH picked AS (
                    SELECT id FROM data.ontology_relation_version
                    WHERE status = 'RETIRED' AND retired_at <= ?
                      AND cleanup_completed_at IS NULL
                    ORDER BY retired_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE data.ontology_relation_version v
                SET cleanup_started_at = COALESCE(v.cleanup_started_at, now()),
                    cleanup_attempts = v.cleanup_attempts + 1
                FROM picked
                WHERE v.id = picked.id
                RETURNING v.id
                """, UUID.class, Timestamp.from(cutoff), properties.cleanupVersionsPerScan());
    }

    private void deleteObjectVersion(UUID versionId) {
        deleteBatches("data.ontology_object_value", versionId);
        deleteBatches("data.ontology_object_instance", versionId);
        jdbc.update("""
                UPDATE data.ontology_object_version SET cleanup_completed_at = now()
                WHERE id = ?
                """, versionId);
        jdbc.update("DELETE FROM data.ontology_object_version WHERE id = ?", versionId);
    }

    private void deleteRelationVersion(UUID versionId) {
        deleteBatches("data.ontology_relation_instance", versionId);
        jdbc.update("""
                UPDATE data.ontology_relation_version SET cleanup_completed_at = now()
                WHERE id = ?
                """, versionId);
        jdbc.update("DELETE FROM data.ontology_relation_version WHERE id = ?", versionId);
    }

    private void deleteBatches(String table, UUID versionId) {
        int deleted;
        do {
            deleted = jdbc.update("""
                    DELETE FROM %s
                    WHERE ctid IN (
                        SELECT ctid FROM %s WHERE version_id = ? LIMIT ?
                    )
                    """.formatted(table, table), versionId, properties.cleanupBatchSize());
        } while (deleted >= properties.cleanupBatchSize());
    }

    public record CleanupResult(int objectVersions, int relationVersions) {
    }
}
