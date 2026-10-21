package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.iceberg.types.Types;

import java.util.List;

final class OutputSchemaJson {

    private OutputSchemaJson() {
    }

    static JsonNode from(ObjectMapper mapper, org.apache.iceberg.Schema schema) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode fields = root.putArray("fields");
        for (Types.NestedField field : schema.columns()) {
            ObjectNode item = fields.addObject();
            item.put("name", field.name());
            item.put("type", field.type().toString());
            item.put("required", field.isRequired());
        }
        return root;
    }

    static JsonNode from(ObjectMapper mapper, ResolvedSchema schema) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode fields = root.putArray("fields");
        List<String> names = schema.getColumnNames();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name.startsWith("__ontoweave_")) {
                continue;
            }
            ObjectNode item = fields.addObject();
            item.put("name", name);
            item.put("type", FlinkIcebergSchema.canonicalTypeName(
                    schema.getColumnDataTypes().get(index).getLogicalType()
            ));
            item.put("required", !schema.getColumnDataTypes().get(index).getLogicalType().isNullable());
        }
        return root;
    }
}
