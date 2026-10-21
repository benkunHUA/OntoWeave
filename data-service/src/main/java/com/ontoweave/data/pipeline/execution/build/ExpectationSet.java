package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record ExpectationSet(List<JsonNode> rules) {

    public ExpectationSet {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static ExpectationSet from(JsonNode expectations) {
        if (expectations == null || !expectations.isArray()) {
            return new ExpectationSet(List.of());
        }
        List<JsonNode> rules = new ArrayList<>();
        expectations.forEach(rule -> rules.add(rule.deepCopy()));
        return new ExpectationSet(rules);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
