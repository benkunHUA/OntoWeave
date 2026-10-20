package com.ontoweave.data.pipeline;

import com.ontoweave.data.pipeline.domain.PipelineBuildEntity;
import com.ontoweave.data.pipeline.domain.PipelineEntity;
import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.repository.PipelineBuildRepository;
import com.ontoweave.data.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PipelineSchemaIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PipelineRepository pipelineRepository;

    @Autowired
    PipelineBuildRepository pipelineBuildRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void clearPipelineTables() {
        jdbc.execute("TRUNCATE TABLE data.pipeline CASCADE");
    }

    @Test
    void createsPipelineTablesAndViews() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'data'",
                String.class
        );
        assertThat(tables).contains(
                "pipeline", "pipeline_draft", "pipeline_edit_op", "pipeline_output",
                "pipeline_dataset", "pipeline_version", "pipeline_build",
                "pipeline_build_output", "pipeline_dataset_version",
                "pipeline_schedule", "pipeline_schedule_run", "dataset_catalog",
                "dataset_version_catalog"
        );
    }

    @Test
    void keepsOnlyOneActiveBuildAndExposesPublishedVersions() {
        PipelineFixture pipeline = insertPipeline();
        UUID activeBuild = insertBuild(pipeline, "queued", BASE_TIME);

        DataIntegrityViolationException violation = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> insertBuild(pipeline, "running", BASE_TIME.plusSeconds(1))
        );
        assertThat(violation).isNotNull();
        assertThat(sqlState(violation)).isEqualTo("23505");

        jdbc.update("""
                UPDATE data.pipeline_build
                SET status = 'succeeded', finished_at = ?, updated_at = ?
                WHERE id = ?
                """, timestamp(BASE_TIME.plusSeconds(2)), timestamp(BASE_TIME.plusSeconds(2)), activeBuild);
        insertBuild(pipeline, "queued", BASE_TIME.plusSeconds(3));

        Integer activeBuilds = jdbc.queryForObject("""
                SELECT count(*) FROM data.pipeline_build
                WHERE pipeline_id = ? AND status IN ('queued', 'running')
                """, Integer.class, pipeline.pipelineId());
        assertThat(activeBuilds).isEqualTo(1);

        List<String> indexes = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname = 'data' AND tablename = 'pipeline_build'
                """, String.class);
        assertThat(indexes).contains("uq_pipeline_active_build", "ix_pipeline_build_queued");

        List<String> catalogColumns = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'data' AND table_name = 'dataset_version_catalog'
                """, String.class);
        assertThat(catalogColumns).contains("dataset_kind", "version_id", "published_at");

        CatalogFixture catalog = insertPublishedAndUnpublishedVersions();
        List<UUID> visibleVersions = jdbc.queryForList("""
                SELECT version_id FROM data.dataset_version_catalog
                WHERE dataset_kind = 'pipeline' AND dataset_id = ?
                ORDER BY version_number
                """, UUID.class, catalog.datasetId());
        assertThat(visibleVersions)
                .containsExactly(catalog.publishedVersionId())
                .doesNotContain(catalog.unpublishedVersionId());
    }

    @Test
    void locksPipelineAndQueuedBuildsInsideTransaction() {
        PipelineFixture first = insertPipeline();
        PipelineFixture second = insertPipeline();
        UUID firstBuild = insertBuild(first, "queued", BASE_TIME);
        UUID secondBuild = insertBuild(second, "queued", BASE_TIME.plusSeconds(1));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PipelineEntity lockedPipeline = pipelineRepository.lockById(first.pipelineId()).orElseThrow();
            assertThat(lockedPipeline.getId()).isEqualTo(first.pipelineId());

            List<PipelineBuildEntity> lockedBuilds = pipelineBuildRepository.lockQueued(10);
            assertThat(lockedBuilds)
                    .extracting(PipelineBuildEntity::getId)
                    .containsExactly(firstBuild, secondBuild);
        });
    }

    @Test
    void enforcesScheduleNextRunInvariant() {
        PipelineFixture pipeline = insertPipeline();

        DataIntegrityViolationException violation = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO data.pipeline_schedule
                            (id, pipeline_id, name, status, trigger_type,
                             cron_expression, timezone, next_run_at, created_by)
                        VALUES (?, ?, 'active-schedule', 'active', 'time',
                                '* * * * *', 'UTC', NULL, ?)
                        """, UUID.randomUUID(), pipeline.pipelineId(), UUID.randomUUID())
        );
        assertThat(violation).isNotNull();
        assertThat(sqlState(violation)).isEqualTo("23514");

        assertThatThrownBy(() -> new PipelineScheduleEntity(
                UUID.randomUUID(), pipeline.pipelineId(), "active-schedule",
                "* * * * *", "UTC", null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);

        jdbc.update("""
                INSERT INTO data.pipeline_schedule
                    (id, pipeline_id, name, status, trigger_type,
                     cron_expression, timezone, next_run_at, created_by)
                VALUES (?, ?, 'paused-schedule', 'paused', 'time',
                        '* * * * *', 'UTC', NULL, ?)
                """, UUID.randomUUID(), pipeline.pipelineId(), UUID.randomUUID());
        Integer schedules = jdbc.queryForObject(
                "SELECT count(*) FROM data.pipeline_schedule WHERE pipeline_id = ?",
                Integer.class,
                pipeline.pipelineId()
        );
        assertThat(schedules).isEqualTo(1);
    }

    private PipelineFixture insertPipeline() {
        UUID pipelineId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline
                    (id, owner_id, conversation_id, name, status)
                VALUES (?, ?, ?, ?, 'active')
                """, pipelineId, UUID.randomUUID(), UUID.randomUUID(), "pipeline-" + pipelineId);
        jdbc.update("""
                INSERT INTO data.pipeline_version
                    (id, pipeline_id, version_number, definition, outputs, definition_hash,
                     input_refs, source_draft_revision, created_by)
                VALUES (?, ?, 1, '{}'::jsonb, '[]'::jsonb, ?, '[]'::jsonb, 0, ?)
                """, versionId, pipelineId, "hash-" + UUID.randomUUID(), UUID.randomUUID());
        return new PipelineFixture(pipelineId, versionId);
    }

    private UUID insertBuild(PipelineFixture pipeline, String status, Instant createdAt) {
        UUID buildId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_build
                    (id, pipeline_id, pipeline_version_id, status, trigger_type,
                     operation_id, resolved_inputs, stats, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'manual', ?, '[]'::jsonb, '{}'::jsonb, ?, ?)
                """, buildId, pipeline.pipelineId(), pipeline.versionId(), status,
                UUID.randomUUID(), timestamp(createdAt), timestamp(createdAt));
        return buildId;
    }

    private CatalogFixture insertPublishedAndUnpublishedVersions() {
        PipelineFixture pipeline = insertPipeline();
        UUID firstBuild = insertBuild(pipeline, "succeeded", BASE_TIME);
        UUID secondBuild = insertBuild(pipeline, "succeeded", BASE_TIME.plusSeconds(1));
        UUID outputId = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_output
                    (id, pipeline_id, node_id, output_type, name)
                VALUES (?, ?, ?, 'dataset', ?)
                """, outputId, pipeline.pipelineId(), UUID.randomUUID(), "output-" + outputId);
        jdbc.update("""
                INSERT INTO data.pipeline_dataset
                    (id, output_id, pipeline_id, name, write_mode,
                     primary_key_columns, expectations)
                VALUES (?, ?, ?, ?, 'snapshot', '[]'::jsonb, '[]'::jsonb)
                """, datasetId, outputId, pipeline.pipelineId(), "dataset-" + datasetId);

        UUID firstBuildOutput = insertBuildOutput(firstBuild, outputId);
        UUID secondBuildOutput = insertBuildOutput(secondBuild, outputId);
        UUID publishedVersion = insertDatasetVersion(
                datasetId, firstBuildOutput, 1, BASE_TIME.plusSeconds(10)
        );
        UUID unpublishedVersion = insertDatasetVersion(
                datasetId, secondBuildOutput, 2, null
        );
        return new CatalogFixture(datasetId, publishedVersion, unpublishedVersion);
    }

    private UUID insertBuildOutput(UUID buildId, UUID outputId) {
        UUID buildOutputId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_build_output
                    (id, build_id, output_id, output_type, status, result, stats)
                VALUES (?, ?, ?, 'dataset', 'succeeded', '{}'::jsonb, '{}'::jsonb)
                """, buildOutputId, buildId, outputId);
        return buildOutputId;
    }

    private UUID insertDatasetVersion(
            UUID datasetId,
            UUID buildOutputId,
            int versionNumber,
            Instant publishedAt
    ) {
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_dataset_version
                    (id, dataset_id, build_output_id, version_number, iceberg_catalog,
                     iceberg_namespace, iceberg_table, iceberg_snapshot_id, schema_json,
                     row_count, byte_size, committed_at, published_at)
                VALUES (?, ?, ?, ?, 'catalog', 'namespace', ?, ?, '{}'::jsonb,
                        10, 100, ?, ?)
                """, versionId, datasetId, buildOutputId, versionNumber,
                "table-" + datasetId, versionNumber, timestamp(BASE_TIME), timestamp(publishedAt));
        return versionId;
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String sqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private record PipelineFixture(UUID pipelineId, UUID versionId) {
    }

    private record CatalogFixture(
            UUID datasetId,
            UUID publishedVersionId,
            UUID unpublishedVersionId
    ) {
    }
}
