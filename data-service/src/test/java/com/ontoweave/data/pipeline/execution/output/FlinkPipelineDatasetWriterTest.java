package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import com.ontoweave.data.sync.iceberg.IcebergPreviewReader;
import org.apache.flink.core.execution.JobClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlinkPipelineDatasetWriterTest {

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
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
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

    @SuppressWarnings("unchecked")
    private ConcurrentMap<UUID, JobClient> jobs(FlinkPipelineDatasetWriter writer) {
        return (ConcurrentMap<UUID, JobClient>) ReflectionTestUtils.getField(writer, "jobs");
    }

    private FlinkPipelineDatasetWriter writer() {
        SyncProperties properties = mock(SyncProperties.class);
        when(properties.icebergCatalog()).thenReturn("ontoweave");
        when(properties.icebergWarehouse()).thenReturn("/tmp/warehouse");
        return new FlinkPipelineDatasetWriter(
                mock(PipelineOutputRepository.class),
                mock(PipelineBuildOutputRepository.class),
                mock(PipelineDatasetRepository.class),
                mock(PipelinePlanCompiler.class),
                mock(OutputSchemaBaseline.class),
                mock(HadoopCatalogFactory.class),
                properties,
                new ObjectMapper()
        );
    }
}
