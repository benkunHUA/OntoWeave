package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;
import org.apache.flink.core.execution.JobClient;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotRef;
import org.apache.iceberg.Table;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlinkPipelineDatasetWriterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildSnapshotsCarryNoRowsAndWriterHasNoPreviewReaderDependency() throws Exception {
        assertThat(WrittenSnapshot.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("rows");
        assertThat(OutputSnapshot.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("rows");
        assertThat(Arrays.stream(FlinkPipelineDatasetWriter.class.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .noneMatch(type -> type.equals(IcebergPreviewReader.class));
        assertThat(Arrays.stream(FlinkPipelineDatasetWriter.class.getDeclaredFields())
                .map(field -> field.getType()))
                .noneMatch(type -> type.equals(IcebergPreviewReader.class));
    }

    @Test
    void failedJobCancellationRetainsTheClientForRetry() {
        UUID buildId = UUID.randomUUID();
        JobClient client = mock(JobClient.class);
        when(client.cancel())
                .thenThrow(new IllegalStateException("flink unavailable"))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(client.getJobStatus()).thenReturn(CompletableFuture.completedFuture(JobStatus.CANCELED));
        FlinkPipelineDatasetWriter writer = writer();
        ConcurrentMap<UUID, JobClient> jobs = jobs(writer);
        jobs.put(buildId, client);

        assertThatThrownBy(() -> writer.cancel(buildId))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(PipelineBuildConstants.CODE_EXECUTION_FAILED));
        assertThat(jobs).containsEntry(buildId, client);

        writer.cancel(buildId);

        verify(client, times(2)).cancel();
        assertThat(jobs).doesNotContainKey(buildId);
    }

    @Test
    void cancelWaitsForFutureAndTerminalStatusBeforeRemovingTheClient() {
        UUID buildId = UUID.randomUUID();
        JobClient client = mock(JobClient.class);
        when(client.cancel()).thenReturn(CompletableFuture.completedFuture(null));
        when(client.getJobStatus()).thenReturn(CompletableFuture.completedFuture(JobStatus.CANCELED));
        FlinkPipelineDatasetWriter writer = writer();
        ConcurrentMap<UUID, JobClient> jobs = jobs(writer);
        jobs.put(buildId, client);

        writer.cancel(buildId);

        verify(client).getJobStatus();
        assertThat(jobs).doesNotContainKey(buildId);
    }

    @Test
    void cancelRetainsTheClientWhenTheFutureFailsAndCanRetry() {
        UUID buildId = UUID.randomUUID();
        JobClient client = mock(JobClient.class);
        when(client.cancel())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("cancel failed")))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(client.getJobStatus()).thenReturn(CompletableFuture.completedFuture(JobStatus.CANCELED));
        FlinkPipelineDatasetWriter writer = writer();
        ConcurrentMap<UUID, JobClient> jobs = jobs(writer);
        jobs.put(buildId, client);

        assertThatThrownBy(() -> writer.cancel(buildId))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(PipelineBuildConstants.CODE_EXECUTION_FAILED));
        assertThat(jobs).containsEntry(buildId, client);

        writer.cancel(buildId);

        verify(client, times(2)).cancel();
        assertThat(jobs).doesNotContainKey(buildId);
    }

    @Test
    void cancelRetainsTheClientWhenTheFutureTimesOut() {
        UUID buildId = UUID.randomUUID();
        JobClient client = mock(JobClient.class);
        when(client.cancel()).thenReturn(new CompletableFuture<>());
        FlinkPipelineDatasetWriter writer = writer(mock(HadoopCatalogFactory.class), Duration.ofMillis(50));
        ConcurrentMap<UUID, JobClient> jobs = jobs(writer);
        jobs.put(buildId, client);

        assertThatThrownBy(() -> writer.cancel(buildId))
                .isInstanceOfSatisfying(PipelineBuildException.class, error -> {
                    assertThat(error.errorCode())
                            .isEqualTo(PipelineBuildConstants.CODE_EXECUTION_FAILED);
                    assertThat(error).hasMessageContaining("超时");
                });
        assertThat(jobs).containsEntry(buildId, client);
    }

    @Test
    void cancelRetainsTheClientWhenTheJobHasNotTerminated() {
        UUID buildId = UUID.randomUUID();
        JobClient client = mock(JobClient.class);
        when(client.cancel()).thenReturn(CompletableFuture.completedFuture(null));
        when(client.getJobStatus()).thenReturn(CompletableFuture.completedFuture(JobStatus.RUNNING));
        FlinkPipelineDatasetWriter writer = writer();
        ConcurrentMap<UUID, JobClient> jobs = jobs(writer);
        jobs.put(buildId, client);

        assertThatThrownBy(() -> writer.cancel(buildId))
                .isInstanceOf(PipelineBuildException.class);
        assertThat(jobs).containsEntry(buildId, client);
    }

    @Test
    void publishedSchemaUsesThePlannedSchemaWithoutFailedPhysicalColumns() throws Exception {
        UUID datasetId = UUID.randomUUID();
        String tableName = OutputTableNames.table(datasetId);
        String branch = "build_planned_schema";
        HadoopCatalogFactory catalogs = mock(HadoopCatalogFactory.class);
        Table table = mock(Table.class);
        Snapshot icebergSnapshot = mock(Snapshot.class);
        when(catalogs.withTable(eq(OutputTableNames.NAMESPACE), eq(tableName), any()))
                .thenReturn(Optional.of(table));
        when(table.refs()).thenReturn(Map.of(branch, SnapshotRef.branchBuilder(7L).build()));
        when(table.snapshot(7L)).thenReturn(icebergSnapshot);
        when(icebergSnapshot.snapshotId()).thenReturn(7L);
        when(icebergSnapshot.summary()).thenReturn(Map.of("total-records", "0"));
        when(icebergSnapshot.timestampMillis()).thenReturn(1_700_000_000_000L);
        when(table.location()).thenReturn("/tmp/pipeline-table");
        when(table.schema()).thenReturn(new org.apache.iceberg.Schema(
                Types.NestedField.required(1, "id", Types.LongType.get()),
                Types.NestedField.optional(2, "name", Types.StringType.get()),
                Types.NestedField.optional(3, "failed_extra", Types.StringType.get())
        ));
        JsonNode plannedSchema = OutputSchemaJson.from(mapper, ResolvedSchema.physical(
                new String[]{"id", "name"},
                new DataType[]{DataTypes.BIGINT(), DataTypes.STRING()}
        ));

        WrittenSnapshot snapshot = writer(catalogs).snapshot(
                datasetId,
                OutputTableNames.NAMESPACE,
                tableName,
                branch,
                null,
                plannedSchema
        );

        assertThat(fieldNames(snapshot.schema())).containsExactly("id", "name");
        assertThat(snapshot.schema()).isEqualTo(plannedSchema);
        verify(table, org.mockito.Mockito.never()).schema();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<UUID, JobClient> jobs(FlinkPipelineDatasetWriter writer) {
        return (ConcurrentMap<UUID, JobClient>) ReflectionTestUtils.getField(writer, "jobs");
    }

    private FlinkPipelineDatasetWriter writer() {
        return writer(mock(HadoopCatalogFactory.class));
    }

    private FlinkPipelineDatasetWriter writer(HadoopCatalogFactory catalogs) {
        return writer(catalogs, Duration.ofSeconds(1));
    }

    private FlinkPipelineDatasetWriter writer(HadoopCatalogFactory catalogs, Duration cancelTimeout) {
        SyncProperties properties = properties();
        return new FlinkPipelineDatasetWriter(
                mock(PipelineOutputRepository.class),
                mock(PipelineBuildOutputRepository.class),
                mock(PipelineDatasetRepository.class),
                mock(PipelinePlanCompiler.class),
                mock(OutputSchemaBaseline.class),
                catalogs,
                properties,
                mapper,
                cancelTimeout
        );
    }

    private SyncProperties properties() {
        SyncProperties properties = mock(SyncProperties.class);
        when(properties.icebergCatalog()).thenReturn("ontoweave");
        when(properties.icebergWarehouse()).thenReturn("/tmp/warehouse");
        return properties;
    }

    private List<String> fieldNames(JsonNode schema) {
        List<String> names = new ArrayList<>();
        schema.path("fields").forEach(field -> names.add(field.path("name").asText()));
        return names;
    }
}
