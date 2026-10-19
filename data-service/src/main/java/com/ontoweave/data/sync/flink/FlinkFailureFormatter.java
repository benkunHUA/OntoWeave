package com.ontoweave.data.sync.flink;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 把 Flink `/jobs/{id}/exceptions` 的响应压成 Agent 可读的失败摘要。 */
public final class FlinkFailureFormatter {

    private static final int MAX_LENGTH = 4000;
    private static final int MAX_STACK_LENGTH = 2400;
    private static final int MAX_CAUSES = 5;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|pwd|secret)\\s*[=:]\\s*\\S+"
    );

    private FlinkFailureFormatter() {
    }

    public static String format(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return "Flink 作业失败：Flink 未返回异常详情";
        }
        String root = sanitize(firstLine(text(body, "root-exception")));
        List<String> sections = new ArrayList<>();
        Set<String> causes = new LinkedHashSet<>();
        String taskSection = null;
        String stackSection = null;

        JsonNode all = body.path("all-exceptions");
        if (all.isArray()) {
            for (JsonNode entry : all) {
                String exception = sanitize(text(entry, "exception"));
                if (exception.isBlank()) {
                    continue;
                }
                if (causes.size() < MAX_CAUSES) {
                    causes.add(firstLine(exception));
                }
                if (taskSection == null) {
                    String task = text(entry, "taskName");
                    String location = text(entry, "location");
                    taskSection = "失败任务：" + (task.isBlank() ? "未分配" : task)
                            + (location.isBlank() ? "" : "（" + location + "）");
                    stackSection = "异常堆栈：\n" + truncate(exception.strip(), MAX_STACK_LENGTH);
                }
            }
        }

        if (causes.isEmpty() && !root.isBlank()) {
            causes.add(root);
        }
        sections.add(causes.isEmpty()
                ? "Flink 作业失败：未返回异常详情"
                : "Flink 作业失败：" + String.join("；", causes));
        if (taskSection != null) {
            sections.add(taskSection);
        }
        if (stackSection != null) {
            sections.add(stackSection);
        }
        if (!root.isBlank() && !causes.contains(root)) {
            sections.add("作业级异常：" + root);
        }
        return truncate(String.join("\n", sections), MAX_LENGTH);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : "";
    }

    private static String firstLine(String value) {
        String trimmed = value == null ? "" : value.strip();
        int newline = trimmed.indexOf('\n');
        return newline >= 0 ? trimmed.substring(0, newline).strip() : trimmed;
    }

    private static String sanitize(String value) {
        return SECRET.matcher(value).replaceAll("$1=***");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "\n…（已截断）";
    }
}
