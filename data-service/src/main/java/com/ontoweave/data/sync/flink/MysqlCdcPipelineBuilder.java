package com.ontoweave.data.sync.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.connection.config.MysqlConfig;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MysqlCdcPipelineBuilder {

    private final SyncProperties properties;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public MysqlCdcPipelineBuilder(SyncProperties properties) {
        this.properties = properties;
    }

    public PipelineSpec build(
            SyncRunEntity run,
            SyncTaskEntity task,
            MysqlConfig config,
            String password,
            String serverTimeZone
    ) {
        String sourceTable = regex(task.getSchemaName()) + "." + regex(task.getTableName());
        String sinkTable = run.getIcebergNamespace() + "." + run.getIcebergTable();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("source", source(run, config, password, serverTimeZone, sourceTable));
        document.put("sink", sink());
        document.put("route", List.of(Map.of("source-table", sourceTable, "sink-table", sinkTable)));
        document.put("pipeline", pipeline(run));
        try {
            return new PipelineSpec(run.getFlinkJobName(), yaml.writeValueAsString(document));
        } catch (Exception exception) {
            throw new IllegalStateException("生成 Flink CDC pipeline YAML 失败", exception);
        }
    }

    private Map<String, Object> source(
            SyncRunEntity run,
            MysqlConfig config,
            String password,
            String serverTimeZone,
            String sourceTable
    ) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "mysql");
        source.put("hostname", config.host());
        source.put("port", config.port());
        source.put("username", config.username());
        source.put("password", password);
        source.put("tables", sourceTable);
        source.put("server-id", run.getServerIdStart() + "-" + run.getServerIdEnd());
        source.put("server-time-zone", serverTimeZone);
        source.put("scan.startup.mode", run.getRunMode() == SyncMode.SNAPSHOT ? "snapshot" : "initial");
        return source;
    }

    private Map<String, Object> sink() {
        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("type", "iceberg");
        sink.put("catalog.properties.type", "hadoop");
        sink.put("catalog.properties.warehouse", properties.icebergWarehouse());
        sink.put("table.properties.format-version", "2");
        sink.put("table.properties.write.format.default", "parquet");
        sink.put("table.properties.write.parquet.compression-codec", "snappy");
        sink.put("table.properties.history.expire.max-snapshot-age-ms", "2592000000");
        return sink;
    }

    private Map<String, Object> pipeline(SyncRunEntity run) {
        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("name", run.getFlinkJobName());
        pipeline.put("parallelism", properties.parallelism());
        pipeline.put(
                "execution.runtime-mode",
                run.getRunMode() == SyncMode.SNAPSHOT ? "BATCH" : "STREAMING"
        );
        if (run.getRunMode() == SyncMode.CDC) {
            Map<String, Object> flinkConf = new LinkedHashMap<>();
            flinkConf.put("execution.checkpointing.interval", properties.checkpointIntervalSeconds() + "s");
            flinkConf.put("execution.checkpointing.timeout", "10min");
            flinkConf.put("execution.checkpointing.min-pause", "30s");
            flinkConf.put("execution.checkpointing.max-concurrent-checkpoints", "1");
            flinkConf.put("restart-strategy.type", "fixed-delay");
            flinkConf.put("restart-strategy.fixed-delay.attempts", "3");
            flinkConf.put("restart-strategy.fixed-delay.delay", "10s");
            flinkConf.put("state.backend.type", "hashmap");
            flinkConf.put("state.checkpoints.dir", properties.checkpointDir());
            flinkConf.put("state.savepoints.dir", properties.savepointDir());
            pipeline.put("flink-conf", flinkConf);
        }
        return pipeline;
    }

    private String regex(String value) {
        return value.replaceAll("([\\\\.^$|?*+()\\[\\]{}])", "\\\\$1");
    }
}
