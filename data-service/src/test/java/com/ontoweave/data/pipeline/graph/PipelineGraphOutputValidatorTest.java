package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineGraphOutputValidatorTest extends PipelineGraphValidatorTestSupport {

    @Test
    void reportsMissingAndUnconnectedOutputs() {
        assertThat(codes(validate(emptyDraft(), Map.of()))).contains("OUTPUT_MISSING");

        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(OUTPUT, "output", outputConfig(OUTPUT_ID))),
                mapper.createArrayNode()
        );
        assertThat(codes(validate(draft, Map.of(datasetId, schema("id", "long")))))
                .contains("OUTPUT_NOT_CONNECTED");
    }

    @Test
    void validatesPrimaryKeysAndExpectationsAgainstOutputSchema() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = outputDraft(datasetId);
        Map<UUID, InputSchema> schemas = Map.of(datasetId, schema("id", "long"));

        ObjectNode primaryKeyConfig = mapper.createObjectNode();
        primaryKeyConfig.putArray("primaryKeyColumns").add("missing");
        primaryKeyConfig.putArray("expectations");
        List<PipelineIssue> primaryKeyIssues = validator.validate(
                PipelineDraftGraph.from(draft),
                catalog,
                schemas,
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(primaryKeyConfig))
        );
        assertThat(codes(primaryKeyIssues)).contains("PRIMARY_KEY_INVALID");
        assertThat(primaryKeyIssues)
                .filteredOn(issue -> issue.code().equals("PRIMARY_KEY_INVALID"))
                .extracting(PipelineIssue::field)
                .contains("missing");

        ObjectNode expectationConfig = mapper.createObjectNode();
        expectationConfig.putArray("primaryKeyColumns").add("id");
        ObjectNode rowCount = expectationConfig.putArray("expectations").addObject();
        rowCount.put("type", "rowCount");
        rowCount.put("min", 10);
        rowCount.put("max", 1);
        List<PipelineIssue> expectationIssues = validator.validate(
                PipelineDraftGraph.from(draft),
                catalog,
                schemas,
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(expectationConfig))
        );
        assertThat(codes(expectationIssues)).contains("EXPECTATION_INVALID");
    }

    @Test
    void reportsOutputMissingWhenTwoNodesShareOneBinding() {
        UUID datasetId = UUID.randomUUID();
        ObjectNode draft = draft(
                mapper.createArrayNode()
                        .add(node(INPUT_A, "input", inputConfig(datasetId)))
                        .add(node(OUTPUT, "output", outputConfig(OUTPUT_ID)))
                        .add(node(OUTPUT_TWO, "output", outputConfig(OUTPUT_ID))),
                mapper.createArrayNode()
                        .add(edge(INPUT_A, "out", OUTPUT, "in"))
                        .add(edge(INPUT_A, "out", OUTPUT_TWO, "in"))
        );

        List<PipelineIssue> issues = validator.validate(
                PipelineDraftGraph.from(draft),
                catalog,
                Map.of(datasetId, schema("id", "long")),
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(OUTPUT_ID, OUTPUT, true, null, null))
        );

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("OUTPUT_MISSING");
            assertThat(issue.nodeId()).isEqualTo(OUTPUT_TWO.toString());
            assertThat(issue.message()).contains("其他节点");
        });
    }

    @Test
    void reportsOutputMissingWhenStoredNodeIdDoesNotMatch() {
        UUID datasetId = UUID.randomUUID();
        List<PipelineIssue> issues = validator.validate(
                PipelineDraftGraph.from(outputDraft(datasetId)),
                catalog,
                Map.of(datasetId, schema("id", "long")),
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(OUTPUT_ID, OUTPUT_TWO, true, null, null))
        );

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("OUTPUT_MISSING");
            assertThat(issue.nodeId()).isEqualTo(OUTPUT.toString());
            assertThat(issue.message()).contains("其他节点");
        });
    }

    @Test
    void reportsOutputMissingWhenDatasetRowIsAbsent() {
        UUID datasetId = UUID.randomUUID();
        List<PipelineIssue> issues = validator.validate(
                PipelineDraftGraph.from(outputDraft(datasetId)),
                catalog,
                Map.of(datasetId, schema("id", "long")),
                Set.of(OUTPUT_ID),
                Map.of(OUTPUT_ID, new OutputConfiguration(OUTPUT_ID, OUTPUT, false, null, null))
        );

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("OUTPUT_MISSING");
            assertThat(issue.nodeId()).isEqualTo(OUTPUT.toString());
            assertThat(issue.message()).contains("dataset");
        });
    }
}
