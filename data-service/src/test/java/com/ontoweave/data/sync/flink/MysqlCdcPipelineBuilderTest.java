package com.ontoweave.data.sync.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.connection.config.MysqlConfig;
import com.ontoweave.data.sync.domain.SyncMode;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlCdcPipelineBuilderTest {

    private final SyncProperties properties = new SyncProperties(
            null, null, null, null, "/data/iceberg/warehouse", null, null,
            2, 100, null, null, false, false, 5, 86400, 300, 30
    );
    private final MysqlCdcPipelineBuilder builder = new MysqlCdcPipelineBuilder(properties);
    private final MysqlConfig config = new MysqlConfig("mysql", 3306, "demo", "reader", false);

    @Test
    void buildsSnapshotPipelineWithEscapedTableAndAssignedServerIds() {
        SyncRunEntity run = run(SyncMode.SNAPSHOT);
        run.assignServerIds(1000, 1099);

        PipelineSpec spec = builder.build(run, task(), config, "p@ss:word", "UTC");

        assertThat(spec.jobName()).isEqualTo(run.getFlinkJobName());
        assertThat(spec.yaml()).contains(
                "type: \"mysql\"",
                "server-id: \"1000-1099\"",
                "scan.startup.mode: \"snapshot\"",
                "execution.runtime-mode: \"BATCH\"",
                "p@ss:word",
                "type: \"iceberg\"",
                "catalog.properties.warehouse: \"/data/iceberg/warehouse\"",
                "sink-table: \"ontoweave_conn_aaaaaaaaaaaa.ds_bbbbbbbbbbbb_rcccccccccccc\""
        );
        assertThat(spec.yaml()).contains("source-table: \"demo.customers\"");
    }

    @Test
    void buildsCdcPipelineWithCheckpointing() {
        SyncRunEntity run = run(SyncMode.CDC);
        run.assignServerIds(2000, 2099);

        PipelineSpec spec = builder.build(run, task(), config, "secret", "+08:00");

        assertThat(spec.yaml()).contains(
                "scan.startup.mode: \"initial\"",
                "execution.runtime-mode: \"STREAMING\"",
                "execution.checkpointing.interval: \"300s\"",
                "state.checkpoints.dir: \"file:///data/flink/checkpoints\"",
                "state.savepoints.dir: \"file:///data/flink/savepoints\""
        );
        assertThat(spec.yaml()).contains("server-time-zone: \"+08:00\"");
    }

    private SyncTaskEntity task() {
        return new SyncTaskEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "demo",
                "customers",
                SyncMode.SNAPSHOT,
                86400,
                new ObjectMapper().valueToTree(List.of("id"))
        );
    }

    private SyncRunEntity run(SyncMode mode) {
        return new SyncRunEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                mode,
                "ontoweave-run-000000000004",
                "ontoweave",
                "ontoweave_conn_aaaaaaaaaaaa",
                "ds_bbbbbbbbbbbb_rcccccccccccc",
                86400
        );
    }
}
