package com.ontoweave.data.ontology.query;

import com.ontoweave.data.common.web.InternalTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OntologyRelatedObjectsApiTest extends RelationQueryFixtureSupport {

    @Test
    void relatedObjectsEndpointReturnsBatchPeerReferences() throws Exception {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture customer = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        ObjectFixture order = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        insertObject(customer.versionId(), customer, "1", "Alice", Map.of());
        insertObject(customer.versionId(), customer, "2", "Bob", Map.of());
        insertObject(order.versionId(), order, "10", "Order 10", Map.of());
        insertObject(order.versionId(), order, "20", "Order 20", Map.of());
        UUID relationId = createRelationDefinition(
                ownerId,
                customer,
                order,
                "orders",
                "many_to_many"
        );
        M2mFixture relation = createM2mFixture(
                ownerId,
                relationId,
                customer,
                order,
                customer.versionId(),
                order.versionId(),
                true
        );
        insertInstance(relation.versionId(), relationId, customer, order, "1", "10");
        insertInstance(relation.versionId(), relationId, customer, order, "2", "20");

        String body = """
                {
                  "ownerId": "%s",
                  "direction": "outgoing",
                  "inputs": [
                    {"objectTypeId": "%s", "primaryKey": "1"},
                    {"objectTypeId": "%s", "primaryKey": "2"}
                  ],
                  "limit": 20
                }
                """.formatted(ownerId, customer.objectTypeId(), customer.objectTypeId());

        mvc.perform(post("/internal/v1/ontology/relation-types/{id}/related-objects", relationId)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.indexVersionId").value(relation.versionId().toString()))
                .andExpect(jsonPath("$.items[0].objectTypeId")
                        .value(order.objectTypeId().toString()))
                .andExpect(jsonPath("$.items[0].primaryKey").value("10"))
                .andExpect(jsonPath("$.items[1].primaryKey").value("20"));
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }
}
