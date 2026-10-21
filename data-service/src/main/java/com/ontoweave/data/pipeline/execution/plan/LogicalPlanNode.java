package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public sealed interface LogicalPlanNode {

    record ScanInput(
            String catalog,
            String namespace,
            String table,
            long snapshotId,
            ScanLimit scanLimit
    ) implements LogicalPlanNode {
    }

    record Filter(JsonNode condition) implements LogicalPlanNode {
    }

    record SelectColumns(String mode, List<String> columns) implements LogicalPlanNode {

        public SelectColumns {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    record RenameColumns(List<ColumnRename> renames) implements LogicalPlanNode {

        public RenameColumns {
            renames = renames == null ? List.of() : List.copyOf(renames);
        }
    }

    record DeriveColumn(String outputField, JsonNode expression) implements LogicalPlanNode {
    }

    record Deduplicate(String mode, List<String> columns) implements LogicalPlanNode {

        public Deduplicate {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    record Join(String joinType, List<JoinKey> keys) implements LogicalPlanNode {

        public Join {
            keys = keys == null ? List.of() : List.copyOf(keys);
        }
    }

    record Union() implements LogicalPlanNode {
    }

    record Aggregate(
            List<String> groupBy,
            List<Aggregation> aggregations
    ) implements LogicalPlanNode {

        public Aggregate {
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
            aggregations = aggregations == null ? List.of() : List.copyOf(aggregations);
        }
    }

    record ColumnRename(String from, String to) {
    }

    record JoinKey(String left, String right) {
    }

    record Aggregation(String op, String field, String outputField) {
    }
}
