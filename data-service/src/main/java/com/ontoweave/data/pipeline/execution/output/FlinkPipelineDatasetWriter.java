package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.pipeline.domain.PipelineDatasetEntity;
import com.ontoweave.data.pipeline.domain.PipelineOutputEntity;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import com.ontoweave.data.pipeline.execution.plan.CompiledTablePlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.repository.PipelineBuildOutputRepository;
import com.ontoweave.data.pipeline.repository.PipelineDatasetRepository;
import com.ontoweave.data.pipeline.repository.PipelineOutputRepository;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.FlinkCatalogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class FlinkPipelineDatasetWriter implements PipelineDatasetWriter {

    private final PipelineOutputRepository outputs;
    private final PipelineBuildOutputRepository buildOutputs;
    private final PipelineDatasetRepository datasets;
    private final PipelinePlanCompiler compiler;
    private final OutputSchemaBaseline schemaBaseline;
    private final HadoopCatalogFactory catalogs;
    private final SyncProperties properties;
    private final ObjectMapper mapper;
    private final Duration cancelTimeout;
    private final ConcurrentMap<UUID, JobClient> jobs = new ConcurrentHashMap<>();

    @Autowired
    public FlinkPipelineDatasetWriter(
            PipelineOutputRepository outputs,
            PipelineBuildOutputRepository buildOutputs,
            PipelineDatasetRepository datasets,
            PipelinePlanCompiler compiler,
            OutputSchemaBaseline schemaBaseline,
            HadoopCatalogFactory catalogs,
            SyncProperties properties,
            ObjectMapper mapper
    ) {
        this(
                outputs,
                buildOutputs,
                datasets,
                compiler,
                schemaBaseline,
                catalogs,
                properties,
                mapper,
                FlinkJobCancellation.DEFAULT_TIMEOUT
        );
    }

    FlinkPipelineDatasetWriter(
            PipelineOutputRepository outputs,
            PipelineBuildOutputRepository buildOutputs,
            PipelineDatasetRepository datasets,
            PipelinePlanCompiler compiler,
            OutputSchemaBaseline schemaBaseline,
            HadoopCatalogFactory catalogs,
            SyncProperties properties,
            ObjectMapper mapper,
            Duration cancelTimeout
    ) {
        this.outputs = outputs;
        this.buildOutputs = buildOutputs;
        this.datasets = datasets;
        this.compiler = compiler;
        this.schemaBaseline = schemaBaseline;
        this.catalogs = catalogs;
        this.properties = properties;
        this.mapper = mapper;
        this.cancelTimeout = cancelTimeout;
    }

    @Override
    public WrittenSnapshot write(UUID buildId, UUID outputId, LogicalPlan plan, String branch) {
        PipelineOutputEntity output = outputs.findById(outputId)
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出不存在"
                ));
        PipelineDatasetEntity dataset = datasets.findByOutputId(output.getId())
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出数据集不存在"
                ));
        String namespace = OutputTableNames.NAMESPACE;
        String tableName = OutputTableNames.table(dataset.getId());
        JsonNode publishedSchema = schemaBaseline.resolve(dataset);
        CompiledTablePlan compiled = compiler.compile(plan);
        JsonNode plannedSchema = OutputSchemaJson.from(mapper, compiled.table().getResolvedSchema());
        if (publishedSchema != null) {
            OutputSchemaCompatibility.assertCompatible(publishedSchema, plannedSchema);
        }
        ensureTable(namespace, tableName, compiled.table().getResolvedSchema());
        TableDescriptor sink = TableDescriptor.forConnector("iceberg")
                .option("catalog-name", properties.icebergCatalog())
                .option(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE,
                        FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HADOOP)
                .option("warehouse", properties.icebergWarehouse())
                .option("catalog-database", namespace)
                .option("catalog-table", tableName)
                .option("branch", branch)
                .build();
        TableResult result = compiled.table().executeInsert(sink, true);
        JobClient client = result.getJobClient().orElse(null);
        String engineJobId = null;
        String logUri = null;
        if (client != null) {
            engineJobId = client.getJobID().toString();
            logUri = FlinkJobLogUri.forJob(properties.flinkRestUrl(), engineJobId);
            jobs.put(buildId, client);
            try {
                recordEngineJob(buildId, outputId, engineJobId, logUri);
            } catch (RuntimeException error) {
                jobs.remove(buildId);
                try {
                    client.cancel();
                } catch (RuntimeException ignored) {
                    // The original persistence failure remains the actionable error.
                }
                throw new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED,
                        "记录 Flink 作业标识失败",
                        error
                );
            }
        }
        try {
            result.await(7, TimeUnit.DAYS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出作业被中断", error
            );
        } catch (Exception error) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED, "输出作业执行失败", error
            );
        } finally {
            jobs.remove(buildId);
        }
        return snapshot(dataset.getId(), namespace, tableName, branch, engineJobId, plannedSchema);
    }

    @Override
    public void fastForwardMain(UUID datasetId, long snapshotId) {
        Table table = requireTable(OutputTableNames.NAMESPACE, OutputTableNames.table(datasetId));
        table.refresh();
        if (table.currentSnapshot() != null && table.currentSnapshot().snapshotId() == snapshotId) {
            return;
        }
        table.manageSnapshots().setCurrentSnapshot(snapshotId).commit();
    }

    @Override
    public void dropBranch(UUID datasetId, String branch) {
        if (branch == null || branch.isBlank()) {
            return;
        }
        Table table = requireTable(OutputTableNames.NAMESPACE, OutputTableNames.table(datasetId));
        table.refresh();
        try {
            table.manageSnapshots().removeBranch(branch).commit();
        } catch (RuntimeException error) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED,
                    "删除 Iceberg 临时分支失败: " + branch,
                    error
            );
        }
    }

    @Override
    public void cancel(UUID buildId) {
        JobClient client = jobs.get(buildId);
        if (client == null) {
            return;
        }
        FlinkJobCancellation.awaitTermination(client, cancelTimeout);
        jobs.remove(buildId, client);
    }

    private void recordEngineJob(UUID buildId, UUID outputId, String engineJobId, String logUri) {
        buildOutputs.updateEngineJobAndLogUri(buildId, outputId, engineJobId, logUri);
    }

    WrittenSnapshot snapshot(
            UUID datasetId,
            String namespace,
            String tableName,
            String branch,
            String engineJobId,
            JsonNode plannedSchema
    ) {
        Table table = requireTable(namespace, tableName);
        table.refresh();
        Snapshot snapshot = branchSnapshot(table, branch);
        if (snapshot == null) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED, "临时分支没有可用快照"
            );
        }
        Map<String, String> summary = snapshot.summary();
        long rowCount = longValue(summary.get("total-records"));
        return new WrittenSnapshot(
                datasetId,
                properties.icebergCatalog(),
                namespace,
                tableName,
                branch,
                snapshot.snapshotId(),
                plannedSchema.deepCopy(),
                rowCount,
                longValue(summary.get("total-file-size-bytes")),
                table.location(),
                Instant.ofEpochMilli(snapshot.timestampMillis()),
                engineJobId,
                FlinkJobLogUri.forJob(properties.flinkRestUrl(), engineJobId)
        );
    }

    private void ensureTable(String namespace, String tableName, ResolvedSchema schema) {
        try (org.apache.iceberg.hadoop.HadoopCatalog catalog = catalogs.open()) {
            org.apache.iceberg.catalog.TableIdentifier identifier =
                    org.apache.iceberg.catalog.TableIdentifier.of(namespace, tableName);
            if (!catalog.tableExists(identifier)) {
                catalog.createTable(identifier, FlinkIcebergSchema.icebergSchema(schema));
                return;
            }
            Table table = catalog.loadTable(identifier);
            FlinkIcebergSchema.apply(table, schema);
        } catch (PipelineBuildException error) {
            throw error;
        } catch (Exception error) {
            throw new PipelineBuildException(
                    PipelineBuildConstants.CODE_EXECUTION_FAILED,
                    "创建或更新输出表失败: " + namespace + "." + tableName,
                    error
            );
        }
    }

    private Table requireTable(String namespace, String tableName) {
        return catalogs.withTable(namespace, tableName, table -> table)
                .orElseThrow(() -> new PipelineBuildException(
                        PipelineBuildConstants.CODE_EXECUTION_FAILED,
                        "Iceberg 表不存在: " + namespace + "." + tableName
                ));
    }

    private Snapshot branchSnapshot(Table table, String branch) {
        if (branch == null) {
            return table.currentSnapshot();
        }
        org.apache.iceberg.SnapshotRef ref = table.refs().get(branch);
        return ref == null ? null : table.snapshot(ref.snapshotId());
    }

    private long longValue(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException error) {
            return 0L;
        }
    }
}
