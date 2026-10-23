package com.ontoweave.data.ontology.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.MappingSnapshot;

import java.util.List;
import java.util.UUID;

final class P3FunctionalModels {

    static final UUID ORDER_ID = UUID.randomUUID();
    static final UUID ORDER_NO = UUID.randomUUID();
    static final UUID ORDER_STATUS = UUID.randomUUID();
    static final UUID ORDER_AMOUNT = UUID.randomUUID();
    static final UUID ORDER_CREATED_ON = UUID.randomUUID();
    static final UUID ORDER_PRIORITY = UUID.randomUUID();
    static final UUID ORDER_CUSTOMER_ID = UUID.randomUUID();
    static final UUID ORDER_NOTES = UUID.randomUUID();

    static final UUID CUSTOMER_ID = UUID.randomUUID();
    static final UUID CUSTOMER_NAME = UUID.randomUUID();
    static final UUID CUSTOMER_SEGMENT = UUID.randomUUID();

    static final UUID PRODUCT_ID = UUID.randomUUID();
    static final UUID PRODUCT_NAME = UUID.randomUUID();

    private P3FunctionalModels() {
    }

    static String orderMappingJson(ObjectMapper mapper) {
        List<MappingSnapshot.AttributeMapping> attributes = List.of(
                attribute(ORDER_ID, "id", "integer", true, null),
                attribute(ORDER_NO, "order_no", "text", true, null),
                attribute(ORDER_STATUS, "status", "enum", true,
                        List.of("PAID", "PENDING", "CANCELLED")),
                attribute(ORDER_AMOUNT, "amount", "decimal", true, null),
                attribute(ORDER_CREATED_ON, "created_on", "date", true, null),
                attribute(ORDER_PRIORITY, "is_priority", "boolean", true, null),
                attribute(ORDER_CUSTOMER_ID, "customer_id", "integer", true, null),
                attribute(ORDER_NOTES, "notes", "text", false, null)
        );
        return json(mapper, MappingSnapshot.object(
                ORDER_ID,
                ORDER_NO,
                attributes,
                1,
                attributes.stream().filter(MappingSnapshot.AttributeMapping::required)
                        .map(MappingSnapshot.AttributeMapping::attributeId)
                        .toList()
        ));
    }

    static String customerMappingJson(ObjectMapper mapper) {
        List<MappingSnapshot.AttributeMapping> attributes = List.of(
                attribute(CUSTOMER_ID, "customer_id", "integer", true, null),
                attribute(CUSTOMER_NAME, "full_name", "text", true, null),
                attribute(CUSTOMER_SEGMENT, "segment", "text", false, null)
        );
        return json(mapper, MappingSnapshot.object(
                CUSTOMER_ID, CUSTOMER_NAME, attributes, 1,
                List.of(CUSTOMER_ID, CUSTOMER_NAME)
        ));
    }

    static String productMappingJson(ObjectMapper mapper) {
        List<MappingSnapshot.AttributeMapping> attributes = List.of(
                attribute(PRODUCT_ID, "product_id", "integer", true, null),
                attribute(PRODUCT_NAME, "name", "text", true, null)
        );
        return json(mapper, MappingSnapshot.object(
                PRODUCT_ID, PRODUCT_NAME, attributes, 1,
                List.of(PRODUCT_ID, PRODUCT_NAME)
        ));
    }

    static String relationMappingJson(ObjectMapper mapper) {
        return json(mapper, MappingSnapshot.manyToMany(
                "order_id", "integer", "product_id", "integer", 1));
    }

    private static MappingSnapshot.AttributeMapping attribute(
            UUID id,
            String column,
            String datatype,
            boolean required,
            List<String> enumValues
    ) {
        return new MappingSnapshot.AttributeMapping(
                id, column, datatype, required, false, enumValues);
    }

    private static String json(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
