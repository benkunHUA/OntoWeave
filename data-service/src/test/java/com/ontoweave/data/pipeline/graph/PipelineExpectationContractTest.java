package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.execution.build.ExpectationChecker;
import com.ontoweave.data.pipeline.execution.build.ExpectationCheckerImpl;
import com.ontoweave.data.pipeline.execution.build.ExpectationQueryExecutor;
import com.ontoweave.data.pipeline.execution.build.ExpectationResult;
import com.ontoweave.data.pipeline.execution.build.ExpectationSet;
import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipelineExpectationContractTest extends PipelineGraphValidatorTestSupport {

    private final ExpectationQueryExecutor queries = mock(ExpectationQueryExecutor.class);
    private final ExpectationChecker checker = new ExpectationCheckerImpl(queries);

    @Test
    void canonicalExpectationsValidateAndExecuteWithOneContract() {
        ArrayNode expectations = canonicalExpectations();
        List<PipelineIssue> issues = validateConfig(expectations);
        OutputSnapshot snapshot = snapshot(2);
        when(queries.primaryKey(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.PrimaryKeyStats(0L, 0L));
        when(queries.notNull(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.NullStats(0L, Map.of("id", 0L)));
        when(queries.invalidValueRows(any(), eq("status"), eq(List.of("active", "closed"))))
                .thenReturn(0L);

        List<ExpectationResult> results = checker.check(ExpectationSet.from(expectations), snapshot);

        assertThat(issues).noneMatch(issue -> issue.code().equals("EXPECTATION_INVALID"));
        assertThat(results).hasSize(4).allMatch(ExpectationResult::passed);
        assertThat(results).extracting(ExpectationResult::type)
                .containsExactly("PRIMARY_KEY_UNIQUE", "ROW_COUNT_RANGE", "VALUE_SET", "NOT_NULL");
    }

    @Test
    void legacyAliasesNormalizeToTheCanonicalContract() {
        ArrayNode expectations = mapper.createArrayNode();
        expectations.add(mapper.createObjectNode().put("type", "primaryKeyUnique")
                .set("columns", mapper.createArrayNode().add("id")));
        expectations.add(mapper.createObjectNode().put("type", "rowCount").put("min", 1).put("max", 5));
        expectations.add(mapper.createObjectNode().put("type", "acceptedValues").put("column", "status")
                .set("values", mapper.createArrayNode().add("active").add("closed")));
        expectations.add(mapper.createObjectNode().put("type", "notNull")
                .set("columns", mapper.createArrayNode().add("id")));

        when(queries.primaryKey(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.PrimaryKeyStats(0L, 0L));
        when(queries.notNull(any(), eq(List.of("id"))))
                .thenReturn(new ExpectationQueryExecutor.NullStats(0L, Map.of("id", 0L)));
        when(queries.invalidValueRows(any(), eq("status"), eq(List.of("active", "closed"))))
                .thenReturn(0L);
        List<ExpectationResult> results = checker.check(ExpectationSet.from(expectations), snapshot(1));

        assertThat(validateConfig(expectations))
                .noneMatch(issue -> issue.code().equals("EXPECTATION_INVALID"));
        assertThat(results).hasSize(4).allMatch(ExpectationResult::passed);
    }

    @Test
    void primaryKeyUniqueValidatesItsColumnsAgainstTheOutputSchema() {
        ArrayNode expectations = mapper.createArrayNode();
        expectations.add(mapper.createObjectNode().put("type", "PRIMARY_KEY_UNIQUE")
                .set("columns", mapper.createArrayNode().add("missing")));

        assertThat(validateConfig(expectations))
                .filteredOn(issue -> issue.code().equals("EXPECTATION_INVALID"))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.field()).isEqualTo("columns");
                    assertThat(issue.message()).contains("missing");
                });
    }

    private List<PipelineIssue> validateConfig(ArrayNode expectations) {
        UUID datasetId = UUID.randomUUID();
        ObjectNode config = mapper.createObjectNode();
        config.putArray("primaryKeyColumns").add("id");
        config.set("expectations", expectations);
        InputSchema schema = new InputSchema(UUID.randomUUID(), List.of(
                new FieldSchema("id", "long", false),
                new FieldSchema("status", "string", true)
        ));
        return validator.validate(
                PipelineDraftGraph.from(outputDraft(datasetId)),
                catalog,
                Map.of(datasetId, schema),
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(OUTPUT_ID, config.get("primaryKeyColumns"), expectations))
        );
    }

    private ArrayNode canonicalExpectations() {
        ArrayNode expectations = mapper.createArrayNode();
        expectations.add(mapper.createObjectNode().put("type", "PRIMARY_KEY_UNIQUE")
                .set("columns", mapper.createArrayNode().add("id")));
        expectations.add(mapper.createObjectNode().put("type", "ROW_COUNT_RANGE")
                .put("min", 1).put("max", 5));
        expectations.add(mapper.createObjectNode().put("type", "VALUE_SET").put("column", "status")
                .set("values", mapper.createArrayNode().add("active").add("closed")));
        expectations.add(mapper.createObjectNode().put("type", "NOT_NULL")
                .set("columns", mapper.createArrayNode().add("id")));
        return expectations;
    }

    private OutputSnapshot snapshot(long rowCount) {
        return new OutputSnapshot(
                UUID.randomUUID(), "ontoweave", "pipeline_outputs", "orders_out",
                "build_test", 1L, mapper.createObjectNode(), rowCount
        );
    }
}
