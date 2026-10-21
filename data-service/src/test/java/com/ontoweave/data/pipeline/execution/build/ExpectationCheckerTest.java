package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpectationCheckerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExpectationQueryExecutor queries = mock(ExpectationQueryExecutor.class);
    private final ExpectationChecker checker = new ExpectationCheckerImpl(queries);

    @Test
    void checksAllSupportedExpectations() {
        ArrayNode rules = mapper.createArrayNode();
        rules.add(rule("PRIMARY_KEY_UNIQUE").set("columns", mapper.createArrayNode().add("id")));
        rules.add(rule("ROW_COUNT_RANGE").put("min", 1).put("max", 5));
        rules.add(rule("VALUE_SET").put("column", "status")
                .set("values", mapper.createArrayNode().add("active").add("closed")));
        rules.add(rule("NOT_NULL").set("columns", mapper.createArrayNode().add("id")));
        OutputSnapshot snapshot = snapshot(2);
        when(queries.primaryKey(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.PrimaryKeyStats(0L, 0L));
        when(queries.notNull(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.NullStats(0L, Map.of("id", 0L)));
        when(queries.invalidValueRows(any(), eq("status"), eq(List.of("active", "closed"))))
                .thenReturn(0L);

        List<ExpectationResult> results = checker.check(ExpectationSet.from(rules), snapshot);

        assertThat(results).hasSize(4).allMatch(ExpectationResult::passed);
        verify(queries).primaryKey(snapshot, List.of("id"));
        verify(queries).notNull(snapshot, List.of("id"));
    }

    @Test
    void reportsFailedRowsFromAggregateQueriesOnly() {
        ArrayNode rules = mapper.createArrayNode();
        rules.add(rule("PRIMARY_KEY_UNIQUE").set("columns", mapper.createArrayNode().add("id")));
        rules.add(rule("VALUE_SET").put("column", "status")
                .set("values", mapper.createArrayNode().add("active")));
        OutputSnapshot snapshot = snapshot(1_000_000_000L);
        when(queries.primaryKey(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.PrimaryKeyStats(0L, 1L));
        when(queries.invalidValueRows(any(), eq("status"), eq(List.of("active"))))
                .thenReturn(1L);

        List<ExpectationResult> results = checker.check(ExpectationSet.from(rules), snapshot);

        assertThat(results).anySatisfy(result -> {
            assertThat(result.type()).isEqualTo("PRIMARY_KEY_UNIQUE");
            assertThat(result.passed()).isFalse();
            assertThat(result.failedRows()).isEqualTo(1L);
        });
        assertThat(results).anySatisfy(result -> {
            assertThat(result.type()).isEqualTo("VALUE_SET");
            assertThat(result.passed()).isFalse();
            assertThat(result.failedRows()).isEqualTo(1L);
        });
    }

    @Test
    void rowCountUsesSnapshotSummaryWithoutReadingRows() {
        ArrayNode rules = mapper.createArrayNode();
        rules.add(rule("ROW_COUNT_RANGE").put("min", 10).put("max", 20));
        OutputSnapshot snapshot = snapshot(Long.MAX_VALUE);

        List<ExpectationResult> results = checker.check(ExpectationSet.from(rules), snapshot);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.passed()).isFalse();
            assertThat(result.actual()).isEqualTo(Map.of("rowCount", Long.MAX_VALUE));
        });
    }

    private ObjectNode rule(String type) {
        return mapper.createObjectNode().put("type", type);
    }

    private OutputSnapshot snapshot(long rowCount) {
        return new OutputSnapshot(
                UUID.randomUUID(), "ontoweave", "pipeline_outputs", "orders",
                "build_test", 1L, mapper.createObjectNode(), rowCount
        );
    }
}
