package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ontoweave.data.pipeline.catalog.SchemaNodes.anyOf;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.anyValue;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.array;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.arrayOfObjects;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.booleanSchema;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.enumString;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.ifThen;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.kindIs;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.object;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.opIs;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.properties;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.reference;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.required;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.string;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.stringArray;

final class ConfigSchemaDefinitions {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static final List<String> FILTER_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte", "is_null", "is_not_null", "in"
    );
    private static final List<String> VALUE_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte"
    );
    private static final List<String> NULL_OPS = List.of("is_null", "is_not_null");
    private static final List<String> EXPRESSION_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte", "and", "or"
    );
    private static final Map<String, ObjectNode> SCHEMAS = createSchemas();

    private ConfigSchemaDefinitions() {
    }

    static ObjectNode forType(String type) {
        ObjectNode schema = SCHEMAS.get(type);
        if (schema == null) {
            throw new IllegalArgumentException("Unknown pipeline node type: " + type);
        }
        return schema.deepCopy();
    }

    private static Map<String, ObjectNode> createSchemas() {
        Map<String, ObjectNode> schemas = new LinkedHashMap<>();
        schemas.put("input", input());
        schemas.put("filter", filter());
        schemas.put("select_columns", selectColumns());
        schemas.put("rename_columns", renameColumns());
        schemas.put("derive_column", deriveColumn());
        schemas.put("deduplicate", deduplicate());
        schemas.put("join", join());
        schemas.put("union", rootSchema());
        schemas.put("aggregate", aggregate());
        schemas.put("output", output());
        return Collections.unmodifiableMap(schemas);
    }

    private static ObjectNode input() {
        ObjectNode schema = rootSchema();
        ObjectNode properties = properties(schema);
        properties.set("datasetKind", enumString("sync"));
        ObjectNode datasetId = string();
        datasetId.put("format", "uuid");
        datasetId.put("pattern", UUID_PATTERN);
        properties.set("datasetId", datasetId);
        return schema;
    }

    private static ObjectNode filter() {
        ObjectNode schema = rootSchema();
        ObjectNode condition = condition();
        properties(schema).set("condition", condition);
        schema.putObject("$defs").set("condition", condition.deepCopy());
        return schema;
    }

    private static ObjectNode condition() {
        ObjectNode condition = object();
        condition.putArray("required").add("kind");
        ObjectNode properties = properties(condition);
        properties.set("kind", enumString("compare", "and", "or"));
        properties.set("field", string());
        properties.set("op", enumString(FILTER_OPS));
        properties.set("value", anyValue());
        ObjectNode values = array();
        values.put("minItems", 1);
        properties.set("values", values);
        ObjectNode conditions = array();
        conditions.put("minItems", 2);
        conditions.set("items", reference("condition"));
        properties.set("conditions", conditions);

        ArrayNode allOf = condition.putArray("allOf");
        allOf.add(ifThen(kindIs("compare"), required("field", "op").set("not", required("conditions"))));
        allOf.add(ifThen(
                kindIs("and", "or"),
                required("conditions").set("not", anyOf(required("field"), required("op"),
                        required("value"), required("values")))
        ));
        allOf.add(ifThen(opIs(VALUE_OPS), required("value").set("not", required("values"))));
        allOf.add(ifThen(opIs(List.of("in")), required("values").set("not", required("value"))));
        allOf.add(ifThen(opIs(NULL_OPS), object().set("not", anyOf(required("value"), required("values")))));
        return condition;
    }

    private static ObjectNode selectColumns() {
        ObjectNode schema = rootSchema();
        ObjectNode properties = properties(schema);
        properties.set("mode", enumString("include", "exclude"));
        properties.set("columns", stringArray());
        return schema;
    }

    private static ObjectNode renameColumns() {
        ObjectNode schema = rootSchema();
        properties(schema).set("renames", arrayOfObjects("from", "to"));
        return schema;
    }

    private static ObjectNode deriveColumn() {
        ObjectNode schema = rootSchema();
        ObjectNode expression = expression();
        ObjectNode properties = properties(schema);
        properties.set("outputField", string());
        properties.set("expression", expression);
        schema.putObject("$defs").set("expression", expression.deepCopy());
        return schema;
    }

    private static ObjectNode expression() {
        ObjectNode expression = object();
        expression.putArray("required").add("kind");
        ObjectNode properties = properties(expression);
        properties.set("kind", enumString(
                "field", "literal", "cast", "case", "compare", "logical", "isNull"
        ));
        properties.set("field", string());
        properties.set("value", anyValue());
        properties.set("dataType", string());
        properties.set("expression", reference("expression"));
        properties.set("left", reference("expression"));
        properties.set("right", reference("expression"));
        properties.set("op", enumString(EXPRESSION_OPS));
        ObjectNode expressions = array();
        expressions.put("minItems", 2);
        expressions.set("items", reference("expression"));
        properties.set("expressions", expressions);
        properties.set("branches", caseBranches());
        properties.set("otherwise", reference("expression"));
        properties.set("negated", booleanSchema());

        ArrayNode allOf = expression.putArray("allOf");
        allOf.add(ifThen(kindIs("field"), required("field")));
        allOf.add(ifThen(kindIs("literal"), required("value", "dataType")));
        allOf.add(ifThen(kindIs("cast"), required("expression", "dataType")));
        allOf.add(ifThen(kindIs("case"), required("branches", "otherwise")));
        allOf.add(ifThen(kindIs("compare"), required("op", "left", "right")));
        allOf.add(ifThen(kindIs("logical"), required("op", "expressions")));
        allOf.add(ifThen(kindIs("isNull"), required("expression")));
        return expression;
    }

    private static ObjectNode caseBranches() {
        ObjectNode branches = array();
        branches.put("minItems", 1);
        ObjectNode item = object();
        item.putArray("required").add("when").add("then");
        ObjectNode properties = properties(item);
        properties.set("when", reference("expression"));
        properties.set("then", reference("expression"));
        branches.set("items", item);
        return branches;
    }

    private static ObjectNode deduplicate() {
        ObjectNode schema = rootSchema();
        ObjectNode properties = properties(schema);
        properties.set("mode", enumString("all", "subset"));
        properties.set("columns", stringArray());
        return schema;
    }

    private static ObjectNode join() {
        ObjectNode schema = rootSchema();
        ObjectNode properties = properties(schema);
        properties.set("joinType", enumString("left", "right", "inner", "full"));
        properties.set("keys", arrayOfObjects("left", "right"));
        return schema;
    }

    private static ObjectNode aggregate() {
        ObjectNode schema = rootSchema();
        ObjectNode properties = properties(schema);
        properties.set("groupBy", stringArray());
        ObjectNode aggregation = arrayOfObjects("op", "outputField");
        ObjectNode aggregationProperties = properties((ObjectNode) aggregation.path("items"));
        aggregationProperties.set("op", enumString("count", "sum", "min", "max", "avg"));
        aggregationProperties.set("field", string());
        properties.set("aggregations", aggregation);
        return schema;
    }

    private static ObjectNode output() {
        ObjectNode schema = rootSchema();
        ObjectNode outputId = string();
        outputId.put("format", "uuid");
        outputId.put("pattern", UUID_PATTERN);
        properties(schema).set("outputId", outputId);
        return schema;
    }

    private static ObjectNode rootSchema() {
        return object();
    }
}
