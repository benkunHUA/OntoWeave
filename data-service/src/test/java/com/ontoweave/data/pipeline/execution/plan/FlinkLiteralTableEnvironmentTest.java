package com.ontoweave.data.pipeline.execution.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FlinkLiteralTableEnvironmentTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FlinkLiteralCompiler literals = new FlinkLiteralCompiler();

    @Test
    void literalsMaterializeWithMatchingJavaTypes() {
        Assumptions.assumeTrue(
                Runtime.version().feature() <= 21,
                "Flink table planner smoke test is skipped on this newer JDK; CI runs it on Java 17"
        );
        TableEnvironment environment = TableEnvironment.create(EnvironmentSettings.inBatchMode());
        Table table = environment.fromValues(Row.of(1)).select(
                literals.literal(mapper.valueToTree((byte) 7), "tinyint").as("tiny"),
                literals.literal(mapper.valueToTree((short) 7), "smallint").as("small"),
                literals.literal(mapper.valueToTree(7), "int").as("integer"),
                literals.literal(mapper.valueToTree(7L), "bigint").as("big"),
                literals.literal(mapper.valueToTree(7.5f), "float").as("single"),
                literals.literal(mapper.valueToTree(7.5d), "double").as("double"),
                literals.literal(mapper.valueToTree(new BigDecimal("7.25")), "decimal").as("decimal"),
                literals.literal(mapper.valueToTree(LocalDate.of(2026, 10, 21)), "date").as("date"),
                literals.literal(mapper.valueToTree(LocalDateTime.of(2026, 10, 21, 9, 30)), "timestamp")
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
}
