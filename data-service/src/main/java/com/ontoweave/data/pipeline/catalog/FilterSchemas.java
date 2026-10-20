package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import static com.ontoweave.data.pipeline.catalog.SchemaNodes.JSON;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.anyOf;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.array;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.enumString;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.ifThen;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.kindIs;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.object;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.opIs;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.properties;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.reference;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.required;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.rootSchema;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.scalar;
import static com.ontoweave.data.pipeline.catalog.SchemaNodes.string;

final class FilterSchemas {

    private static final List<String> COMPARISON_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte"
    );
    private static final List<String> FILTER_OPS = List.of(
            "eq", "ne", "gt", "gte", "lt", "lte", "is_null", "is_not_null", "in"
    );
    private static final List<String> NULL_OPS = List.of("is_null", "is_not_null");

    private FilterSchemas() {
    }

    static ObjectNode schema() {
        ObjectNode schema = rootSchema();
        ObjectNode condition = condition();
        ObjectNode operand = operand();
        properties(schema).set("condition", condition);
        ObjectNode definitions = schema.putObject("$defs");
        definitions.set("condition", condition.deepCopy());
        definitions.set("operand", operand);
        return schema;
    }

    private static ObjectNode condition() {
        ObjectNode condition = object();
        condition.putArray("required").add("kind");
        ObjectNode properties = properties(condition);
        properties.set("kind", enumString("compare", "and", "or"));
        properties.set("field", string());
        properties.set("op", enumString(FILTER_OPS));
        properties.set("value", reference("operand"));
        ObjectNode values = array();
        values.put("minItems", 1);
        values.set("items", reference("operand"));
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
        allOf.add(ifThen(opIs(COMPARISON_OPS), required("value").set("not", required("values"))));
        allOf.add(ifThen(opIs(List.of("in")), required("values").set("not", required("value"))));
        allOf.add(ifThen(opIs(NULL_OPS), forbidFields("value", "values")));
        return condition;
    }

    private static ObjectNode operand() {
        ObjectNode operand = object();
        ArrayNode required = operand.putArray("required");
        required.add("kind").add("dataType").add("value");
        ObjectNode properties = properties(operand);
        properties.set("kind", enumString("literal"));
        properties.set("dataType", string());
        properties.set("value", scalar());
        return operand;
    }

    private static ObjectNode forbidFields(String... fields) {
        ObjectNode consequence = JSON.createObjectNode();
        ObjectNode alternatives = JSON.createObjectNode();
        ArrayNode anyOf = alternatives.putArray("anyOf");
        for (String field : fields) {
            anyOf.add(required(field));
        }
        consequence.set("not", alternatives);
        return consequence;
    }
}
