package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.ontoweave.data.pipeline.catalog.SchemaNodes.arrayOfObjects;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.enumString;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.properties;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.rootSchema;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.string;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.stringArray;

final class ConfigSchemaDefinitions {

    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
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

    static boolean contains(String type) {
        return SCHEMAS.containsKey(type);
    }

    private static Map<String, ObjectNode> createSchemas() {
        Map<String, ObjectNode> schemas = new LinkedHashMap<>();
        schemas.put("input", input());
        schemas.put("filter", FilterSchemas.schema());
        schemas.put("select_columns", selectColumns());
        schemas.put("rename_columns", renameColumns());
        schemas.put("derive_column", ExpressionSchemas.schema());
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
        properties.set("datasetId", uuidString());
        return schema;
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
        properties(schema).set("outputId", uuidString());
        return schema;
    }

    private static ObjectNode uuidString() {
        ObjectNode schema = string();
        schema.put("format", "uuid");
        schema.put("pattern", UUID_PATTERN);
        return schema;
    }
}
