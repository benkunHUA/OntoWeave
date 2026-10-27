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

class OntologyQueryStepApiTest extends RelationQueryFixtureSupport {

    @Test
    void objectQueryEndpointAcceptsSortProjectionAndLimit() throws Exception {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("amount", "integer")
        ));
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec amount = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "1", "one",
                Map.of(name.attributeId(), "one", amount.attributeId(), 10));
        insertObject(fixture.versionId(), fixture, "2", "two",
                Map.of(name.attributeId(), "two", amount.attributeId(), 20));

        String body = """
                {
                  "ownerId": "%s",
                  "sort": [{"attributeId": "%s", "direction": "desc"}],
                  "limit": 1,
                  "project": ["%s"]
                }
                """.formatted(ownerId, amount.attributeId(), name.attributeId());

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/objects/query",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.items[0].primaryKey").value("2"))
                .andExpect(jsonPath("$.items[0].properties[0].attributeId")
                        .value(name.attributeId().toString()));
    }

    @Test
    void queryStepEndpointTraversesRelationAndReturnsEvidenceFields() throws Exception {
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
        insertObject(order.versionId(), order, "10", "Order 10", Map.of());
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

        String body = """
                {
                  "ownerId": "%s",
                  "relationId": "%s",
                  "direction": "outgoing",
                  "inputs": [{"objectTypeId": "%s", "primaryKey": "1"}],
                  "limit": 20
                }
                """.formatted(ownerId, relationId, customer.objectTypeId());

        mvc.perform(post("/internal/v1/ontology/query-steps")
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].objectTypeId")
                        .value(order.objectTypeId().toString()))
                .andExpect(jsonPath("$.items[0].primaryKey").value("10"))
                .andExpect(jsonPath("$.indexVersionId").value(order.versionId().toString()));
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }
}
