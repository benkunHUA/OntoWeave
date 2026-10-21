package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.expectation.ExpectationRules;
import com.ontoweave.data.pipeline.execution.output.OutputSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ExpectationCheckerImpl implements ExpectationChecker {

    @Override
    public List<ExpectationResult> check(ExpectationSet expectations, OutputSnapshot snapshot) {
        if (expectations == null || expectations.isEmpty()) {
            return List.of();
        }
        List<ExpectationResult> results = new ArrayList<>();
        for (JsonNode rule : expectations.rules()) {
            results.add(checkOne(rule, snapshot));
        }
        return List.copyOf(results);
    }

    private ExpectationResult checkOne(JsonNode rule, OutputSnapshot snapshot) {
        String rawType = rule.path("type").asText(null);
        Optional<ObjectNode> normalized = ExpectationRules.normalize(rule);
        if (normalized.isEmpty()) {
            return unknown(rawType, rule);
        }
        JsonNode canonical = normalized.get();
        try {
            return switch (ExpectationType.from(canonical.path("type").asText())) {
                case PRIMARY_KEY_UNIQUE -> primaryKey(canonical, snapshot);
                case ROW_COUNT_RANGE -> rowCount(canonical, snapshot);
                case VALUE_SET -> valueSet(canonical, snapshot);
                case NOT_NULL -> notNull(canonical, snapshot);
            };
        } catch (IllegalArgumentException error) {
            return unknown(rawType, rule);
        }
    }

    private ExpectationResult unknown(String rawType, JsonNode rule) {
        return new ExpectationResult(
                rawType, false, null, rule, 0L, "未知的数据期望类型: " + rawType
        );
    }

    private ExpectationResult primaryKey(JsonNode rule, OutputSnapshot snapshot) {
        List<String> columns = strings(rule.get("columns"));
        if (columns.isEmpty()) {
            return failed("PRIMARY_KEY_UNIQUE", List.of(), "至少需要一列主键", 0L);
        }
        Set<List<Object>> seen = new HashSet<>();
        Set<Integer> failed = new HashSet<>();
        for (int index = 0; index < snapshot.rows().size(); index++) {
            Map<String, Object> row = snapshot.rows().get(index);
            List<Object> key = new ArrayList<>();
            boolean nullKey = false;
            for (String column : columns) {
                Object value = row.get(column);
                if (value == null) {
                    nullKey = true;
                }
                key.add(value);
            }
            if (nullKey || !seen.add(key)) {
                failed.add(index);
            }
        }
        long failedRows = failed.size();
        return new ExpectationResult(
                "PRIMARY_KEY_UNIQUE",
                failedRows == 0,
                Map.of("columns", columns, "failedRows", failedRows),
                Map.of("unique", true, "notNull", true),
                failedRows,
                failedRows == 0 ? "主键唯一且非空" : "主键存在重复或空值"
        );
    }

    private ExpectationResult rowCount(JsonNode rule, OutputSnapshot snapshot) {
        Long min = rule.has("min") ? rule.get("min").asLong() : null;
        Long max = rule.has("max") ? rule.get("max").asLong() : null;
        long actual = snapshot.rowCount();
        boolean passed = (min == null || actual >= min) && (max == null || actual <= max);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("min", min);
        expected.put("max", max);
        return new ExpectationResult(
                "ROW_COUNT_RANGE",
                passed,
                Map.of("rowCount", actual),
                expected,
                passed ? 0L : 1L,
                passed ? "行数在期望范围内" : "行数不在期望范围内"
        );
    }

    private ExpectationResult valueSet(JsonNode rule, OutputSnapshot snapshot) {
        String column = rule.path("column").asText(null);
        List<String> expectedValues = strings(rule.get("values"));
        if (column == null || expectedValues.isEmpty()) {
            return failed("VALUE_SET", expectedValues, "列和候选值不能为空", 0L);
        }
        Set<String> allowed = new HashSet<>(expectedValues);
        Map<String, Integer> invalid = new LinkedHashMap<>();
        Set<Integer> failed = new HashSet<>();
        for (int index = 0; index < snapshot.rows().size(); index++) {
            Object value = snapshot.rows().get(index).get(column);
            if (value == null || !allowed.contains(String.valueOf(value))) {
                failed.add(index);
                invalid.merge(String.valueOf(value), 1, Integer::sum);
            }
        }
        long failedRows = failed.size();
        return new ExpectationResult(
                "VALUE_SET",
                failedRows == 0,
                Map.of("column", column, "invalidValues", invalid),
                Map.of("values", expectedValues),
                failedRows,
                failedRows == 0 ? "取值均在候选集合内" : "存在不允许的取值"
        );
    }

    private ExpectationResult notNull(JsonNode rule, OutputSnapshot snapshot) {
        List<String> columns = strings(rule.get("columns"));
        if (columns.isEmpty()) {
            return failed("NOT_NULL", List.of(), "至少需要一列", 0L);
        }
        Map<String, Integer> nulls = new LinkedHashMap<>();
        Set<Integer> failed = new HashSet<>();
        for (String column : columns) {
            nulls.put(column, 0);
        }
        for (int index = 0; index < snapshot.rows().size(); index++) {
            Map<String, Object> row = snapshot.rows().get(index);
            for (String column : columns) {
                if (row.get(column) == null) {
                    nulls.merge(column, 1, Integer::sum);
                    failed.add(index);
                }
            }
        }
        long failedRows = failed.size();
        return new ExpectationResult(
                "NOT_NULL",
                failedRows == 0,
                Map.of("nullRowsByColumn", nulls),
                Map.of("columns", columns),
                failedRows,
                failedRows == 0 ? "指定列均非空" : "指定列存在空值"
        );
    }

    private ExpectationResult failed(
            String type,
            Object expected,
            String message,
            long failedRows
    ) {
        return new ExpectationResult(type, false, Map.of(), expected, failedRows, message);
    }

    private List<String> strings(JsonNode value) {
        List<String> values = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }
}
