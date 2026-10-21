package com.ontoweave.data.pipeline.execution.plan;

import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.util.List;

public record CompiledTablePlan(
        StreamTableEnvironment tableEnvironment,
        Table table,
        List<HiddenErrorColumn> hiddenErrorColumns
) {

    public CompiledTablePlan {
        hiddenErrorColumns = hiddenErrorColumns == null ? List.of() : List.copyOf(hiddenErrorColumns);
    }

    public record HiddenErrorColumn(
            String nodeId,
            String field,
            String flagColumn,
            String originalColumn
    ) {
    }
}
