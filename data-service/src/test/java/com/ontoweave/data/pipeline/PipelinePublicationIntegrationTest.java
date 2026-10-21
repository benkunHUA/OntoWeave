package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ontoweave.data.pipeline.domain.PipelineBuildStatus;
import com.ontoweave.data.pipeline.domain.PipelineOutputStatus;
import com.ontoweave.data.pipeline.execution.build.PipelinePublisher;
import com.ontoweave.data.pipeline.execution.output.PipelineDatasetWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PipelinePublicationIntegrationTest extends PipelineBuildIntegrationTestSupport {

    @Autowired
    private PipelinePublisher publisher;

    @MockitoBean
    private PipelineDatasetWriter writer;

    @Test
    void successfulPublishSwitchesAllPointersAndWritesOutboxAtomically() {
        PublicationFixture fixture = fixture();

        publisher.publish(fixture.buildId());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, fixture.buildId()
        )).isEqualTo("succeeded");
        assertThat(jdbc.queryForObject(
                "SELECT current_version_id FROM data.pipeline WHERE id = ?",
                UUID.class, fixture.pipelineId()
        )).isEqualTo(fixture.buildVersionId());
        for (PublishedOutput output : fixture.outputs()) {
            UUID current = jdbc.queryForObject(
                    "SELECT current_version_id FROM data.pipeline_dataset WHERE id = ?",
                    UUID.class, output.datasetId()
            );
            assertThat(current).isNotEqualTo(output.previousVersionId());
            assertThat(jdbc.queryForObject(
                    "SELECT version_number FROM data.pipeline_dataset_version WHERE id = ?",
                    Integer.class, current
            )).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT published_at FROM data.pipeline_dataset_version WHERE id = ?",
                    Instant.class, current
            )).isNotNull();
            verify(writer).fastForwardMain(eq(output.datasetId()), anyLong());
            verify(writer).dropBranch(eq(output.datasetId()), eq("build_" + fixture.buildId()));
        }
        assertThat(count("data.outbox_event")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT event_type FROM data.outbox_event", String.class
        )).isEqualTo("pipeline.build.succeeded");
        var event = readEvent();
        assertThat(event.path("conversationId").asText()).isEqualTo(
                jdbc.queryForObject(
                        "SELECT conversation_id::text FROM data.pipeline WHERE id = ?",
                        String.class, fixture.pipelineId()
                )
        );
    }

    @Test
    void successfulPublishAfterArchiveKeepsPipelineArchivedAndDoesNotMoveItsPointer() {
        PublicationFixture fixture = fixture();
        jdbc.update(
                "UPDATE data.pipeline SET status = 'archived', archived_at = now() WHERE id = ?",
                fixture.pipelineId()
        );

        publisher.publish(fixture.buildId());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, fixture.buildId()
        )).isEqualTo("succeeded");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline WHERE id = ?", String.class, fixture.pipelineId()
        )).isEqualTo("archived");
        assertThat(jdbc.queryForObject(
                "SELECT current_version_id FROM data.pipeline WHERE id = ?",
                UUID.class, fixture.pipelineId()
        )).isEqualTo(fixture.previousPipelineVersionId());
        for (PublishedOutput output : fixture.outputs()) {
            assertThat(jdbc.queryForObject(
                    "SELECT current_version_id FROM data.pipeline_dataset WHERE id = ?",
                    UUID.class, output.datasetId()
            )).isNotEqualTo(output.previousVersionId());
        }
        assertThat(readEvent().path("conversationId").asText()).isNotBlank();
    }

    @Test
    void publishRollsBackEveryPointerWhenTheOutboxWriteFails() {
        PublicationFixture fixture = fixture();
        installFailingOutboxTrigger();
        try {
            assertThatThrownBy(() -> publisher.publish(fixture.buildId()))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            dropFailingOutboxTrigger();
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM data.pipeline_build WHERE id = ?", String.class, fixture.buildId()
        )).isEqualTo("running");
        assertThat(jdbc.queryForObject(
                "SELECT current_version_id FROM data.pipeline WHERE id = ?",
                UUID.class, fixture.pipelineId()
        )).isEqualTo(fixture.previousPipelineVersionId());
        for (PublishedOutput output : fixture.outputs()) {
            assertThat(jdbc.queryForObject(
                    "SELECT current_version_id FROM data.pipeline_dataset WHERE id = ?",
                    UUID.class, output.datasetId()
            )).isEqualTo(output.previousVersionId());
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM data.pipeline_dataset_version WHERE dataset_id = ?",
                    Integer.class, output.datasetId()
            )).isEqualTo(1);
        }
        assertThat(count("data.outbox_event")).isZero();
        verifyNoInteractions(writer);
    }

    private PublicationFixture fixture() {
        UUID ownerId = UUID.randomUUID();
        UUID pipelineId = insertPipeline(ownerId);
        ArrayNode empty = mapper.createArrayNode();
        UUID previousPipelineVersion = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), empty
        );
        UUID buildVersion = insertVersion(pipelineId, 2, mapper.createObjectNode(), empty);
        UUID previousBuild = insertBuild(
                pipelineId, previousPipelineVersion, PipelineBuildStatus.SUCCEEDED, UUID.randomUUID()
        );

        List<PublishedOutput> outputs = new ArrayList<>();
        int index = 0;
        for (String name : List.of("orders", "customers")) {
            OutputFixture output = insertOutput(pipelineId, name);
            UUID previousBuildOutput = insertBuildOutput(
                    previousBuild, output.outputId(), PipelineOutputStatus.SUCCEEDED,
                    mapper.createObjectNode()
            );
            UUID previousVersion = insertPreviousVersion(
                    output.datasetId(), previousBuildOutput, index++
            );
            jdbc.update(
                    "UPDATE data.pipeline_dataset SET current_version_id = ? WHERE id = ?",
                    previousVersion, output.datasetId()
            );
            outputs.add(new PublishedOutput(output, previousVersion));
        }
        jdbc.update(
                "UPDATE data.pipeline SET current_version_id = ?, status = 'active' WHERE id = ?",
                previousPipelineVersion, pipelineId
        );

        UUID build = insertBuild(
                pipelineId, buildVersion, PipelineBuildStatus.RUNNING, UUID.randomUUID()
        );
        int snapshot = 100;
        for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
            OutputFixture output = outputs.get(outputIndex).output();
            var result = buildResult(output.datasetId().toString(), snapshot++);
            result.with("snapshot").put("branch", "build_" + build);
            insertBuildOutput(build, output.outputId(), PipelineOutputStatus.SUCCEEDED, result);
        }
        return new PublicationFixture(
                pipelineId, build, buildVersion, previousPipelineVersion, List.copyOf(outputs)
        );
    }

    private UUID insertPreviousVersion(UUID datasetId, UUID buildOutputId, int index) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_dataset_version
                    (id, dataset_id, build_output_id, version_number, iceberg_catalog,
                     iceberg_namespace, iceberg_table, iceberg_snapshot_id, schema_json,
                     row_count, byte_size, committed_at, published_at)
                VALUES (?, ?, ?, 1, 'ontoweave', 'pipeline_outputs', ?, ?,
                        CAST('{}' AS jsonb), 1, 16, now(), now())
                """, id, datasetId, buildOutputId, "previous_" + index, 900L + index);
        return id;
    }

    private void installFailingOutboxTrigger() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION data.test_fail_outbox_insert()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced outbox failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER test_fail_outbox_insert
                BEFORE INSERT ON data.outbox_event
                FOR EACH ROW EXECUTE FUNCTION data.test_fail_outbox_insert()
                """);
    }

    private void dropFailingOutboxTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_outbox_insert ON data.outbox_event");
        jdbc.execute("DROP FUNCTION IF EXISTS data.test_fail_outbox_insert()");
    }

    private com.fasterxml.jackson.databind.JsonNode readEvent() {
        try {
            return mapper.readTree(jdbc.queryForObject(
                    "SELECT payload::text FROM data.outbox_event", String.class
            ));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private record PublicationFixture(
            UUID pipelineId,
            UUID buildId,
            UUID buildVersionId,
            UUID previousPipelineVersionId,
            List<PublishedOutput> outputs
    ) {
    }

    private record PublishedOutput(OutputFixture output, UUID previousVersionId) {

        UUID datasetId() {
            return output.datasetId();
        }
    }
}
