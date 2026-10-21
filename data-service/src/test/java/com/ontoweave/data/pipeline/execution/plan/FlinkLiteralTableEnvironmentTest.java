package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlinkLiteralTableEnvironmentTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FlinkLiteralCompiler literals = new FlinkLiteralCompiler();

    @Test
    void literalsMaterializeWithMatchingJavaTypes() {
        TableEnvironment environment = TableEnvironment.create(EnvironmentSettings.inBatchMode());
        Table table = environment.fromValues(Row.of(1)).select(
                literals.literal(mapper.valueToTree((byte) 7), "tinyint").as("tiny"),
                literals.literal(mapper.valueToTree((short) 7), "smallint").as("small"),
                literals.literal(mapper.valueToTree(7), "int").as("integer"),
                literals.literal(mapper.valueToTree(7L), "bigint").as("big"),
                literals.literal(mapper.valueToTree(7.5f), "float").as("single"),
                literals.literal(mapper.valueToTree(7.5d), "double").as("double"),
                literals.literal(mapper.valueToTree(new BigDecimal("7.25")), "decimal").as("decimal"),
                literals.literal(mapper.getNodeFactory().textNode("2026-10-21"), "date").as("date"),
                literals.literal(mapper.getNodeFactory().textNode("2026-10-21T09:30:00"), "timestamp")
                        .as("timestamp")
        );

        assertThat(table.getResolvedSchema().getColumnDataTypes())
                .extracting(Object::toString)
                .containsExactly(
                        "TINYINT NOT NULL",
                        "SMALLINT NOT NULL",
                        "INT NOT NULL",
                        "BIGINT NOT NULL",
                        "FLOAT NOT NULL",
                        "DOUBLE NOT NULL",
                        "DECIMAL(38, 18) NOT NULL",
                        "DATE NOT NULL",
                        "TIMESTAMP(3) NOT NULL"
                );
    }

    @Test
    void preservesParameterizedTypesForNullAndNonNullLiterals() {
        TableEnvironment environment = TableEnvironment.create(EnvironmentSettings.inBatchMode());
        Table table = environment.fromValues(Row.of(1)).select(
                literals.literal(mapper.valueToTree(new BigDecimal("7.25")), "decimal(10,2)")
                        .as("amount"),
                literals.literal(mapper.nullNode(), "decimal(10,2)").as("nullable_amount"),
                literals.literal(mapper.getNodeFactory().textNode("2026-10-21T09:30:00.123456"),
                        "timestamp(6)").as("created_at"),
                literals.literal(mapper.nullNode(), "timestamp(6)").as("nullable_created_at"),
                literals.literal(mapper.getNodeFactory().textNode("abc"), "varchar(5)").as("code"),
                literals.literal(mapper.getNodeFactory().textNode("abc"), "char(3)").as("fixed")
        );

        assertThat(table.getResolvedSchema().getColumnDataTypes())
                .extracting(Object::toString)
                .containsExactly(
                        "DECIMAL(10, 2) NOT NULL",
                        "DECIMAL(10, 2)",
                        "TIMESTAMP(6) NOT NULL",
                        "TIMESTAMP(6)",
                        "VARCHAR(5) NOT NULL",
                        "CHAR(3) NOT NULL"
                );
    }

    @Test
    void rejectsUnknownDeclaredLiteralType() {
        assertThatThrownBy(() -> literals.literal(mapper.valueToTree(1), "uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uuid");
    }
}
