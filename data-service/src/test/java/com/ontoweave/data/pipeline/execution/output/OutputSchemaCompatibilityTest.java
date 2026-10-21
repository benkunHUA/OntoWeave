package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputSchemaCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allowsAddedColumnsAndTypeWidening() {
        ObjectNode previous = schema(field("id", "integer", true));
        ObjectNode next = schema(field("id", "long", true), field("name", "string", false));

        assertThatCode(() -> OutputSchemaCompatibility.assertCompatible(previous, next))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRemovedColumnsAndNarrowing() {
        ObjectNode previous = schema(field("id", "long", true), field("name", "string", false));
        ObjectNode removed = schema(field("id", "long", true));
        ObjectNode narrowed = schema(field("id", "integer", true), field("name", "string", false));

        assertThatThrownBy(() -> OutputSchemaCompatibility.assertCompatible(previous, removed))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED));
        assertThatThrownBy(() -> OutputSchemaCompatibility.assertCompatible(previous, narrowed))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED));
    }

    @Test
    void allowsNullableRelaxationButRejectsRequiredNarrowing() {
        ObjectNode optional = schema(field("name", "string", false));
        ObjectNode required = schema(field("name", "string", true));

        assertThatCode(() -> OutputSchemaCompatibility.assertCompatible(required, optional))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> OutputSchemaCompatibility.assertCompatible(optional, required))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED));
    }

    @Test
    void requiresDecimalPrecisionAndScaleToWidenTogether() {
        ObjectNode previous = schema(field("amount", "decimal(10, 2)", true));
        ObjectNode wider = schema(field("amount", "decimal(12, 3)", true));
        ObjectNode narrowerScale = schema(field("amount", "decimal(12, 1)", true));

        assertThatCode(() -> OutputSchemaCompatibility.assertCompatible(previous, wider))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> OutputSchemaCompatibility.assertCompatible(previous, narrowerScale))
                .isInstanceOfSatisfying(PipelineBuildException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED));
    }

    private ObjectNode schema(ObjectNode... fields) {
        ObjectNode schema = mapper.createObjectNode();
        ArrayNode array = schema.putArray("fields");
        for (ObjectNode field : fields) {
            array.add(field);
        }
        return schema;
    }

    private ObjectNode field(String name, String type, boolean required) {
        return mapper.createObjectNode().put("name", name).put("type", type).put("required", required);
    }
}
