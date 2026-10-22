package com.ontoweave.data.pipeline.execution.plan;

import com.ontoweave.data.common.config.SyncProperties;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.catalog.Catalog;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.iceberg.flink.FlinkCatalogFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
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

    public CompiledTablePlan snapshotScan(
            String catalog,
            String namespace,
            String table,
            long snapshotId
    ) {
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();
        environment.setRuntimeMode(RuntimeExecutionMode.BATCH);
        StreamTableEnvironment tableEnvironment = StreamTableEnvironment.create(environment);
        LogicalPlanNode.ScanInput scan = new LogicalPlanNode.ScanInput(
                catalog, namespace, table, snapshotId, ScanLimit.unlimited()
        );
        return new CompiledTablePlan(tableEnvironment, scan(scan, tableEnvironment), List.of());
    }

    CompiledTablePlan compile(
            LogicalPlan plan,
            StreamTableEnvironment tableEnvironment,
            Function<LogicalPlanNode.ScanInput, Table> scanSource
    ) {
        CompiledStep result = new PlanStepCompiler(scanSource).compile(plan.root());
        return new CompiledTablePlan(tableEnvironment, result.table(), result.hiddenColumns());
    }

    Table scan(LogicalPlanNode.ScanInput scan, StreamTableEnvironment tableEnvironment) {
        registerCatalog(scan, tableEnvironment);
        StringBuilder sql = new StringBuilder("SELECT * FROM ")
                .append(identifier(scan.catalog())).append('.')
                .append(identifier(scan.namespace())).append('.')
                .append(identifier(scan.table()))
                .append(" /*+ OPTIONS('snapshot-id'='")
                .append(scan.snapshotId())
                .append("') */");
        if (scan.scanLimit().limited()) {
            sql.append(" LIMIT ").append(scan.scanLimit().maxRows());
        }
        return tableEnvironment.sqlQuery(sql.toString());
    }

    private String identifier(String value) {
        return '`' + value.replace("`", "``") + '`';
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
