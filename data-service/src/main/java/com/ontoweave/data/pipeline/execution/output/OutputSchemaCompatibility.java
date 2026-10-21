package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class OutputSchemaCompatibility {

    private static final Set<String> INTEGRAL = Set.of("int", "integer", "long", "bigint");
    private static final Set<String> FLOATING = Set.of("float", "double");

    private OutputSchemaCompatibility() {
    }

    public static void assertCompatible(JsonNode previous, JsonNode next) {
        if (previous == null || previous.path("fields").isEmpty()) {
            return;
        }
        Map<String, JsonNode> nextFields = fields(next);
        for (JsonNode field : previous.path("fields")) {
            String name = field.path("name").asText(null);
            JsonNode current = nextFields.get(name);
            if (current == null) {
                throw changed("输出 schema 删除了列或改名: " + name);
            }
            if (!widenable(field.path("type").asText(null), current.path("type").asText(null))) {
                throw changed("输出 schema 类型收窄: " + name);
            }
            if (!field.path("required").asBoolean(false) && current.path("required").asBoolean(false)) {
                throw changed("输出 schema 可空性收窄: " + name);
            }
        }
    }

    private static Map<String, JsonNode> fields(JsonNode schema) {
        Map<String, JsonNode> fields = new HashMap<>();
        if (schema != null && schema.path("fields").isArray()) {
            schema.path("fields").forEach(field -> fields.put(field.path("name").asText(""), field));
        }
        return fields;
    }

    static boolean widenable(String previous, String next) {
        if (previous == null || next == null || previous.equals(next)) {
            return true;
        }
        String left = previous.toLowerCase();
        String right = next.toLowerCase();
        if (INTEGRAL.contains(left) && INTEGRAL.contains(right)) {
            return rank(right) >= rank(left);
        }
        if (FLOATING.contains(left) && FLOATING.contains(right)) {
            return rank(right) >= rank(left);
        }
        if (INTEGRAL.contains(left) && FLOATING.contains(right)) {
            return true;
        }
        if (left.startsWith("decimal") && right.startsWith("decimal")) {
            Decimal previousDecimal = decimal(left);
            Decimal nextDecimal = decimal(right);
            return previousDecimal != null && nextDecimal != null
                    && nextDecimal.precision() >= previousDecimal.precision()
                    && nextDecimal.scale() >= previousDecimal.scale();
        }
        if (left.startsWith("varchar") && right.startsWith("varchar")) {
            return varcharWidth(right) >= varcharWidth(left);
        }
        return false;
    }

    private static int rank(String type) {
        return switch (type) {
            case "int", "integer" -> 1;
            case "long", "bigint" -> 2;
            case "float" -> 3;
            case "double" -> 4;
            default -> 0;
        };
    }

    private static Decimal decimal(String type) {
        int start = type.indexOf('(');
        int close = type.indexOf(')', start + 1);
        if (start < 0 || close < 0) {
            return null;
        }
        try {
            int end = type.indexOf(',', start + 1);
            int precision = Integer.parseInt(type.substring(start + 1, end < 0 ? close : end));
            int scale = end < 0 ? 0 : Integer.parseInt(type.substring(end + 1, close).trim());
            return new Decimal(precision, scale);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static long varcharWidth(String type) {
        int start = type.indexOf('(');
        if (start < 0) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(type.substring(start + 1, type.indexOf(')', start)));
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private static PipelineBuildException changed(String message) {
        return new PipelineBuildException(
                PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED, message
        );
    }

    private record Decimal(int precision, int scale) {
    }
}
