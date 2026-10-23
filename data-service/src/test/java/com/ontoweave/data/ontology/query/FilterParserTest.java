package com.ontoweave.data.ontology.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.query.filter.FilterParser;
import com.ontoweave.data.ontology.query.filter.ParsedFilters;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterParserTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final FilterParser parser = new FilterParser();

    private final UUID text = UUID.randomUUID();
    private final UUID integer = UUID.randomUUID();
    private final UUID decimal = UUID.randomUUID();
    private final UUID date = UUID.randomUUID();
    private final UUID timestamp = UUID.randomUUID();
    private final UUID bool = UUID.randomUUID();
    private final UUID enumId = UUID.randomUUID();
    private final MappingSnapshot mapping = MappingSnapshot.object(
            text,
            text,
            List.of(
                    attribute(text, "name", "text"),
                    attribute(integer, "amount", "integer"),
                    attribute(decimal, "ratio", "decimal"),
                    attribute(date, "day", "date"),
                    attribute(timestamp, "created", "timestamp"),
                    attribute(bool, "enabled", "boolean"),
                    attribute(enumId, "status", "enum")
            ),
            1
    );

    @Test
    void parsesEverySupportedOperatorWithTypedValues() {
        ParsedFilters filters = parser.parse(mapping, List.of(
                condition(text, "contains", JSON.textNode("foo")),
                condition(integer, "gte", JSON.numberNode(10)),
                condition(decimal, "lt", JSON.numberNode(new BigDecimal("2.5"))),
                condition(date, "eq", JSON.textNode("2026-10-23")),
                condition(timestamp, "gte", JSON.textNode("2026-10-23T01:02:03Z")),
                condition(bool, "eq", JSON.booleanNode(true)),
                condition(enumId, "is_not_null", null)
        ));

        assertThat(filters.clauses()).hasSize(7);
        assertThat(filters.parameters()).contains(
                "%foo%",
                new BigDecimal("10"),
                new BigDecimal("2.5"),
                LocalDate.parse("2026-10-23"),
                Instant.parse("2026-10-23T01:02:03Z"),
                true
        );
        assertThat(String.join(" ", filters.clauses()))
                .doesNotContain("foo")
                .contains("v.text_value ILIKE ?", "v.num_value >=", "v.bool_value =");
    }

    @Test
    void rejectsOperatorsOutsideTheDatatypeMatrix() {
        assertUnprocessable(condition(text, "gte", JSON.textNode("a")));
        assertUnprocessable(condition(integer, "contains", JSON.textNode("1")));
        assertUnprocessable(condition(bool, "ne", JSON.booleanNode(false)));
        assertUnprocessable(condition(enumId, "gt", JSON.textNode("active")));
    }

    @Test
    void rejectsUnknownAttributesAndCrossTypeValues() {
        assertUnprocessable(condition(UUID.randomUUID(), "eq", JSON.textNode("x")));
        assertUnprocessable(condition(integer, "eq", JSON.textNode("10")));
        assertUnprocessable(condition(decimal, "eq", JSON.booleanNode(true)));
        assertUnprocessable(condition(date, "eq", JSON.textNode("2026/10/23")));
        assertUnprocessable(condition(enumId, "eq", JSON.numberNode(1)));
    }

    @Test
    void rejectsMissingAndIllegalFilterShapes() {
        assertUnprocessable(condition(integer, "gte", null));
        assertUnprocessable(new FilterCondition(null, "eq", JSON.textNode("x")));
        assertUnprocessable(new FilterCondition("not-a-uuid", "eq", JSON.textNode("x")));
        assertUnprocessable(new FilterCondition(integer.toString(), "unknown", JSON.numberNode(1)));
    }

    @Test
    void isNullUsesNotExistsSoMissingValueRowsCountAsNull() {
        ParsedFilters filters = parser.parse(mapping, List.of(
                condition(integer, "is_null", null)
        ));

        assertThat(filters.clauses().get(0)).contains("NOT EXISTS", "IS NOT NULL");
        assertThat(filters.parameters()).containsExactly(integer);
    }

    private void assertUnprocessable(FilterCondition condition) {
        assertThatThrownBy(() -> parser.parse(mapping, List.of(condition)))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.code()).isEqualTo("MAPPING_INVALID");
                });
    }

    private FilterCondition condition(UUID attributeId, String op, JsonNode value) {
        return new FilterCondition(attributeId.toString(), op, value);
    }

    private MappingSnapshot.AttributeMapping attribute(UUID id, String column, String datatype) {
        return new MappingSnapshot.AttributeMapping(id, column, datatype, false, false, null);
    }
}
