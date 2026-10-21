package com.ontoweave.data.pipeline.execution.plan;

import com.ontoweave.data.common.config.SyncProperties;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.iceberg.flink.FlinkCatalogFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class PipelinePlanCompiler {

    private final String warehouse;

    public PipelinePlanCompiler(SyncProperties properties) {
        this.warehouse = properties.icebergWarehouse();
    }

    public CompiledTablePlan compile(LogicalPlan plan) {
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setRuntimeMode(RuntimeExecutionMode.BATCH);
        StreamTableEnvironment tableEnvironment = StreamTableEnvironment.create(environment);
        return compile(plan, tableEnvironment, scan -> scan(scan, tableEnvironment));
    }

    CompiledTablePlan compile(
            LogicalPlan plan,
            StreamTableEnvironment tableEnvironment,
            Function<LogicalPlanNode.ScanInput, Table> scanSource
    ) {
        CompiledStep result = new PlanStepCompiler(scanSource).compile(plan.root());
        return new CompiledTablePlan(tableEnvironment, result.table(), result.hiddenColumns());
    }

    private Table scan(LogicalPlanNode.ScanInput scan, StreamTableEnvironment tableEnvironment) {
        registerCatalog(scan, tableEnvironment);
        TableDescriptor descriptor = TableDescriptor.forConnector("iceberg")
                .option("catalog-name", scan.catalog())
                .option(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HADOOP)
                .option("catalog-database", scan.namespace())
                .option("catalog-table", scan.table())
                .option("warehouse", warehouse)
                .option("snapshot-id", Long.toString(scan.snapshotId()))
                .option("limit", Integer.toString(scan.scanLimit()))
                .build();
        return tableEnvironment.from(descriptor);
    }

    private void registerCatalog(LogicalPlanNode.ScanInput scan, StreamTableEnvironment tableEnvironment) {
        if (Set.of(tableEnvironment.listCatalogs()).contains(scan.catalog())) {
            return;
        }
        Map<String, String> properties = new HashMap<>();
        properties.put("type", "iceberg");
        properties.put(FlinkCatalogFactory.ICEBERG_CATALOG_TYPE, FlinkCatalogFactory.ICEBERG_CATALOG_TYPE_HADOOP);
        properties.put("warehouse", warehouse);
        Catalog catalog = new FlinkCatalogFactory().createCatalog(scan.catalog(), properties);
        tableEnvironment.registerCatalog(scan.catalog(), catalog);
    }
}
