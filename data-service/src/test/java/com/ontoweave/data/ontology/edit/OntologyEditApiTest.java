package com.ontoweave.data.ontology.edit;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.web.InternalTokenFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OntologyEditApiTest extends EditSetFixtureSupport {

    @Test
    void previewAndApplyExposeTheEditSetContract() throws Exception {
        ObjectFixture fixture = customerFixture();
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec level = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "C001", "张三",
                Map.of(name.attributeId(), "张三", level.attributeId(), "普通"));
        ObjectNode request = reviseRequest(fixture, "C001", level.attributeId(), "VIP");
        request.put("idempotencyKey", "api-idem-modify");
        request.put("executionId", UUID.randomUUID().toString());

        mvc.perform(post("/internal/v1/ontology/edit-sets/preview")
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType("application/json")
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.objects[0].kind").value("OBJECT_MODIFY"))
                .andExpect(jsonPath("$.objects[0].after.properties['" + level.attributeId() + "']")
                        .value("VIP"));

        mvc.perform(post("/internal/v1/ontology/edit-sets/apply")
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType("application/json")
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].exists").value(true))
                .andExpect(jsonPath("$.objects[0].title").value("张三"))
                .andExpect(jsonPath("$.objects[0].properties['" + level.attributeId() + "']")
                        .value("VIP"));

        mvc.perform(post("/internal/v1/ontology/edit-sets/apply")
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType("application/json")
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(request.get("executionId").asText()));
    }

    @Test
    void applyReturnsConflictWhenPreviewIsNotApplicable() throws Exception {
        ObjectFixture fixture = customerFixture();
        AttributeSpec level = fixture.attributes().get(2);
        ObjectNode request = reviseRequest(fixture, "missing", level.attributeId(), "VIP");

        mvc.perform(post("/internal/v1/ontology/edit-sets/preview")
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType("application/json")
                        .content(request.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.conflicts[0].code").value("OBJECT_NOT_FOUND"));

        mvc.perform(post("/internal/v1/ontology/edit-sets/apply")
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .contentType("application/json")
                        .content(request.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"));
    }

    private ObjectNode reviseRequest(
            ObjectFixture fixture,
            String primaryKey,
            UUID attributeId,
            String value
    ) {
        ObjectNode request = baseRequest(fixture.ownerId());
        ArrayNode updates = request.putArray("objectUpdates");
        ObjectNode update = updates.addObject();
        update.put("objectTypeId", fixture.objectTypeId().toString());
        update.put("primaryKey", primaryKey);
        update.putObject("set").put(attributeId.toString(), value);
        update.putArray("clear");
        return request;
    }

    private ObjectNode baseRequest(UUID ownerId) {
        ObjectNode request = mapper.createObjectNode();
        request.put("executionId", UUID.randomUUID().toString());
        request.put("ownerId", ownerId.toString());
        request.put("graphId", UUID.randomUUID().toString());
        request.put("actionTypeId", UUID.randomUUID().toString());
        request.put("actionDefinitionHash", "hash");
        request.putObject("actionSnapshot");
        request.putObject("parameters");
        request.put("idempotencyKey", "api-idem-" + UUID.randomUUID());
        ObjectNode expected = request.putObject("expected");
        expected.putArray("objects");
        expected.putArray("relations");
        request.putArray("objectCreates");
        request.putArray("objectUpdates");
        request.putArray("objectDeletes");
        request.putArray("relationCreates");
        request.putArray("relationDeletes");
        return request;
    }

}
