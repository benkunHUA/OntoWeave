package com.ontoweave.data.pipeline.execution.plan;

import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.util.List;

public record CompiledTablePlan(
        StreamTableEnvironment tableEnvironment,
        Table table,
        List<HiddenErrorColumn> hiddenErrorColumns
) {

    public static final String HIDDEN_PREFIX = "__ontoweave_preview_";

    public CompiledTablePlan {
        hiddenErrorColumns = hiddenErrorColumns == null ? List.of() : List.copyOf(hiddenErrorColumns);
    }

    public record HiddenErrorColumn(
            String nodeId,
            String portId,
            String field,
            String flagColumn,
            String originalColumn,
            boolean rowPrecise
    ) {

        public HiddenErrorColumn withNames(String flagColumn, String originalColumn) {
            return new HiddenErrorColumn(
                    nodeId, portId, field, flagColumn, originalColumn, rowPrecise
            );
        }

        public HiddenErrorColumn withField(String outputField) {
            return new HiddenErrorColumn(
                    nodeId, portId, outputField, flagColumn, originalColumn, rowPrecise
            );
        }

        public HiddenErrorColumn withRowPrecise(boolean precise) {
            return new HiddenErrorColumn(
                    nodeId, portId, field, flagColumn, originalColumn, precise
            );
        }
    }
}
