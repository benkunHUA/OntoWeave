package com.ontoweave.data.pipeline.execution.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.ProcessFlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.execution.plan.PlanStep;
import com.ontoweave.data.pipeline.execution.plan.ScanLimit;
import com.ontoweave.data.pipeline.execution.plan.ScanLimitEvaluator;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import com.ontoweave.data.sync.iceberg.IcebergSnapshotMetrics;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.encryption.EncryptionUtil;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_FLINK_INTEGRATION", matches = "true")
class PipelinePreviewFlinkIntegrationTest {

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get()),
            Types.NestedField.required(2, "amount", Types.LongType.get())
    );

    @TempDir
    Path warehouse;

    @Test
    void executesFilteredBatchPlanAgainstFixedIcebergSnapshot() throws Exception {
        try (HadoopCatalog catalog = catalog()) {
            Table table = catalog.createTable(TableIdentifier.of("preview", "orders"), SCHEMA);
            append(table, 1L, 10L);
            append(table, 2L, 20L);
            long snapshotId = table.currentSnapshot().snapshotId();
            PipelinePlanCompiler compiler = new PipelinePlanCompiler(properties());
            HadoopCatalogFactory catalogs = new HadoopCatalogFactory(properties());
            ProcessFlinkTableExecutor executor = new ProcessFlinkTableExecutor(
                    compiler, new ScanLimitEvaluator(new IcebergSnapshotMetrics(catalogs))
            );

            FlinkExecutionResult result = executor.execute(
                    filterPlan(snapshotId), 500, Duration.ofSeconds(30)
            );

            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0)).containsEntry("id", 2L).containsEntry("amount", 20L);
        }
    }

    private HadoopCatalog catalog() {
        HadoopCatalog catalog = new HadoopCatalog(new org.apache.hadoop.conf.Configuration(), warehouse.toString());
        catalog.initialize("ontoweave", Map.of("warehouse", warehouse.toString()));
        return catalog;
    }

    private SyncProperties properties() {
        return new SyncProperties(
                null, null, null, "ontoweave", warehouse.toString(), null, null,
                1, 100, null, null, false, false, 5, 86400, 300, 30, 4, 1
        );
    }

    private LogicalPlan filterPlan(long snapshotId) throws Exception {
        PlanStep scan = new PlanStep(
                UUID.randomUUID().toString(),
                "input",
                new LogicalPlanNode.ScanInput(
                        "ontoweave", "preview", "orders", snapshotId, ScanLimit.preview(50_000)
                ),
                List.of()
        );
        PlanStep filter = new PlanStep(
                UUID.randomUUID().toString(),
                "filter",
                new LogicalPlanNode.Filter(new ObjectMapper().createObjectNode()
                        .put("kind", "compare")
                        .put("field", "id")
                        .put("op", "gt")
                        .set("value", new ObjectMapper().createObjectNode()
                                .put("kind", "literal")
                                .put("dataType", "long")
                                .put("value", 1))),
                List.of(scan)
        );
        return LogicalPlan.of(filter, 4L, filter.nodeId(), Map.of());
    }

    private void append(Table table, long id, long amount) throws Exception {
        GenericRecord record = GenericRecord.create(SCHEMA);
        record.setField("id", id);
        record.setField("amount", amount);
        OutputFile outputFile = table.io().newOutputFile(
                table.location() + "/data/" + UUID.randomUUID() + ".parquet"
        );
        DataWriter<Record> writer = new GenericAppenderFactory(SCHEMA).newDataWriter(
                EncryptionUtil.plainAsEncryptedOutput(outputFile),
                FileFormat.PARQUET,
                null
        );
        writer.write(record);
        writer.close();
        table.newAppend().appendFile(writer.toDataFile()).commit();
    }
}
