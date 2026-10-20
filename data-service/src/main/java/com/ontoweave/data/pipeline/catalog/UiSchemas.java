package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiSchemas {

    public static final String TEXT = "text";
    public static final String NUMBER = "number";
    public static final String BOOLEAN = "boolean";
    public static final String SELECT = "select";
    public static final String MULTI_SELECT = "multi-select";
    public static final String FIELD_SELECTOR = "field-selector";
    public static final String OPERATOR_SELECTOR = "operator-selector";
    public static final String CONDITION_TREE = "condition-tree";
    public static final String RENAME_LIST = "rename-list";
    public static final String JOIN_KEYS = "join-keys";
    public static final String AGGREGATION_LIST = "aggregation-list";
    public static final String EXPRESSION_TREE = "expression-tree";

    private static final List<String> CONTROLS = List.of(
            TEXT, NUMBER, BOOLEAN, SELECT, MULTI_SELECT, FIELD_SELECTOR, OPERATOR_SELECTOR,
            CONDITION_TREE, RENAME_LIST, JOIN_KEYS, AGGREGATION_LIST, EXPRESSION_TREE
    );
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Map<String, String>> FIELDS = createFields();

    private UiSchemas() {
    }

    public static List<String> controls() {
        return CONTROLS;
    }

    static ObjectNode forType(String type) {
        Map<String, String> nodeFields = FIELDS.get(type);
        if (nodeFields == null) {
            throw new IllegalArgumentException("Unknown pipeline node type: " + type);
        }
        ObjectNode schema = JSON.createObjectNode();
        ObjectNode fields = schema.putObject("fields");
        nodeFields.forEach((field, control) -> fields.putObject(field).put("widget", control));
        return schema;
    }

    private static Map<String, Map<String, String>> createFields() {
        Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        fields.put("input", controls(SELECT, "datasetKind", SELECT, "datasetId"));
        fields.put("filter", controls(CONDITION_TREE, "condition"));
        fields.put("select_columns", controls(SELECT, "mode", MULTI_SELECT, "columns"));
        fields.put("rename_columns", controls(RENAME_LIST, "renames"));
        fields.put("derive_column", controls(TEXT, "outputField", EXPRESSION_TREE, "expression"));
        fields.put("deduplicate", controls(SELECT, "mode", MULTI_SELECT, "columns"));
        fields.put("join", controls(SELECT, "joinType", JOIN_KEYS, "keys"));
        fields.put("union", Map.of());
        fields.put("aggregate", controls(FIELD_SELECTOR, "groupBy", AGGREGATION_LIST, "aggregations"));
        fields.put("output", controls(SELECT, "outputId"));
        return Map.copyOf(fields);
    }

    private static Map<String, String> controls(String firstControl, String firstField,
                                                String secondControl, String secondField) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(firstField, firstControl);
        fields.put(secondField, secondControl);
        return Map.copyOf(fields);
    }

    private static Map<String, String> controls(String control, String field) {
        return Map.of(field, control);
    }
}
