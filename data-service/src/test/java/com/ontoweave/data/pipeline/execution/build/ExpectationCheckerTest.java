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

class ExpectationCheckerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExpectationChecker checker = new ExpectationCheckerImpl();

    @Test
    void checksAllSupportedExpectations() {
        ArrayNode rules = mapper.createArrayNode();
        rules.add(rule("PRIMARY_KEY_UNIQUE").set("columns", mapper.createArrayNode().add("id")));
        rules.add(rule("ROW_COUNT_RANGE").put("min", 1).put("max", 5));
        rules.add(rule("VALUE_SET").put("column", "status")
                .set("values", mapper.createArrayNode().add("active").add("closed")));
        rules.add(rule("NOT_NULL").set("columns", mapper.createArrayNode().add("id")));
        OutputSnapshot snapshot = snapshot(2, List.of(
                Map.of("id", 1, "status", "active"),
                Map.of("id", 2, "status", "closed")
        ));

        List<ExpectationResult> results = checker.check(ExpectationSet.from(rules), snapshot);

        assertThat(results).hasSize(4).allMatch(ExpectationResult::passed);
    }

    @Test
    void reportsFailedRowsForDuplicateAndInvalidValues() {
        ArrayNode rules = mapper.createArrayNode();
        rules.add(rule("PRIMARY_KEY_UNIQUE").set("columns", mapper.createArrayNode().add("id")));
        rules.add(rule("VALUE_SET").put("column", "status")
                .set("values", mapper.createArrayNode().add("active")));
        OutputSnapshot snapshot = snapshot(2, List.of(
                Map.of("id", 1, "status", "active"),
                Map.of("id", 1, "status", "deleted")
        ));

        List<ExpectationResult> results = checker.check(ExpectationSet.from(rules), snapshot);

        assertThat(results).anySatisfy(result -> {
            assertThat(result.type()).isEqualTo("PRIMARY_KEY_UNIQUE");
            assertThat(result.passed()).isFalse();
            assertThat(result.failedRows()).isEqualTo(1);
        });
        assertThat(results).anySatisfy(result -> {
            assertThat(result.type()).isEqualTo("VALUE_SET");
            assertThat(result.passed()).isFalse();
            assertThat(result.failedRows()).isEqualTo(1);
        });
    }

    private ObjectNode rule(String type) {
        return mapper.createObjectNode().put("type", type);
    }

    private OutputSnapshot snapshot(long rowCount, List<Map<String, Object>> rows) {
        return new OutputSnapshot(
                UUID.randomUUID(), "build_test", 1L, mapper.createObjectNode(), rowCount, rows
        );
    }
}
