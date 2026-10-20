package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

final class SchemaNodes {

    static final ObjectMapper JSON = new ObjectMapper();

    private SchemaNodes() {
    }

    static ObjectNode object() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties");
        return schema;
    }

    static ObjectNode string() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "string");
        return schema;
    }

    static ObjectNode booleanSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "boolean");
        return schema;
    }

    static ObjectNode array() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "array");
        return schema;
    }

    static ObjectNode stringArray() {
        ObjectNode schema = array();
        schema.set("items", string());
        return schema;
    }

    static ObjectNode anyValue() {
        return JSON.createObjectNode();
    }

    static ObjectNode enumString(String... values) {
        return enumString(List.of(values));
    }

    static ObjectNode enumString(List<String> values) {
        ObjectNode schema = string();
        ArrayNode options = schema.putArray("enum");
        values.forEach(options::add);
        return schema;
    }

    static ObjectNode arrayOfObjects(String... requiredFields) {
        ObjectNode array = array();
        ObjectNode item = object();
        ArrayNode names = item.putArray("required");
        for (String field : requiredFields) {
            names.add(field);
        }
        ObjectNode itemProperties = properties(item);
        for (String field : requiredFields) {
            itemProperties.set(field, string());
        }
        array.set("items", item);
        return array;
    }

    static ObjectNode properties(ObjectNode schema) {
        return (ObjectNode) schema.path("properties");
    }

    static ObjectNode reference(String definition) {
        ObjectNode reference = JSON.createObjectNode();
        reference.put("$ref", "#/$defs/" + definition);
        return reference;
    }

    static ObjectNode kindIs(String... kinds) {
        ObjectNode condition = JSON.createObjectNode();
        condition.putArray("required").add("kind");
        condition.putObject("properties").set("kind", enumString(kinds));
        return condition;
    }

    static ObjectNode opIs(List<String> operators) {
        ObjectNode condition = JSON.createObjectNode();
        condition.putArray("required").add("op");
        condition.putObject("properties").set("op", enumString(operators));
        return condition;
    }

    static ObjectNode ifThen(ObjectNode condition, ObjectNode consequence) {
        ObjectNode conditional = JSON.createObjectNode();
        conditional.set("if", condition);
        conditional.set("then", consequence);
        return conditional;
    }

    static ObjectNode required(String... fields) {
        ObjectNode requirement = JSON.createObjectNode();
        ArrayNode names = requirement.putArray("required");
        for (String field : fields) {
            names.add(field);
        }
        return requirement;
    }

    static ObjectNode anyOf(ObjectNode... schemas) {
        ObjectNode result = JSON.createObjectNode();
        ArrayNode options = result.putArray("anyOf");
        for (ObjectNode schema : schemas) {
            options.add(schema);
        }
        return result;
    }
}
