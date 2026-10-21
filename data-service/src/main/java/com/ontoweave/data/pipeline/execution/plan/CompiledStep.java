package com.ontoweave.data.pipeline.execution.plan;

import org.apache.flink.table.api.Table;

import java.util.List;

record CompiledStep(
        Table table,
        List<CompiledTablePlan.HiddenErrorColumn> hiddenColumns
) {

    CompiledStep {
        hiddenColumns = hiddenColumns == null ? List.of() : List.copyOf(hiddenColumns);
    }
}
