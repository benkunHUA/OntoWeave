package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;

import java.util.List;
import java.util.Map;

public interface ExpectationQueryExecutor {

    PrimaryKeyStats primaryKey(OutputSnapshot snapshot, List<String> columns);

    NullStats notNull(OutputSnapshot snapshot, List<String> columns);

    long invalidValueRows(OutputSnapshot snapshot, String column, List<String> allowedValues);

    record PrimaryKeyStats(long nullRows, long duplicateRows) {

        public long failedRows() {
            return nullRows + duplicateRows;
        }
    }

    record NullStats(long failedRows, Map<String, Long> nullRowsByColumn) {

        public NullStats {
            nullRowsByColumn = nullRowsByColumn == null ? Map.of() : Map.copyOf(nullRowsByColumn);
        }
    }
}
