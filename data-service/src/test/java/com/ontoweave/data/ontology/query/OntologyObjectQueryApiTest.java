package com.ontoweave.data.ontology.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.web.InternalTokenFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OntologyObjectQueryApiTest extends RelationQueryFixtureSupport {

    @Test
    void returnsObjectPagesAndDetails() throws Exception {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text"),
                attribute("amount", "integer")
        ));
        AttributeSpec name = fixture.attributes().get(1);
        AttributeSpec amount = fixture.attributes().get(2);
        insertObject(fixture.versionId(), fixture, "1", "alpha", Map.of(
                name.attributeId(), "alpha", amount.attributeId(), 10
        ));

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("q", "alp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.indexVersionId").value(fixture.versionId().toString()))
                .andExpect(jsonPath("$.items[0].primaryKey").value("1"))
                .andExpect(jsonPath("$.items[0].title").value("alpha"));

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/object",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("primaryKey", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.properties.length()").value(3))
                .andExpect(jsonPath("$.properties[0].attributeId")
                        .value(fixture.attributes().get(0).attributeId().toString()))
                .andExpect(jsonPath("$.properties[2].value").value(10));
    }

    @Test
    void emptyBindingReturnsAnEmptyPageAndMissingDetailIs404() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects", objectTypeId)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.indexVersionId").doesNotExist());

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/object", objectTypeId)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("primaryKey", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidFiltersAndPaginationAreRejectedBeforeSql() throws Exception {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        AttributeSpec name = fixture.attributes().get(1);

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("filters", "[{\"attributeId\":\"%s\",\"op\":\"gte\",\"value\":\"a\"}]"
                                .formatted(name.attributeId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAPPING_INVALID"));

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("filters", "[{\"attributeId\":\"%s\",\"op\":\"eq\",\"value\":10}]"
                                .formatted(name.attributeId())))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("pageSize", "201"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("filters", "not-json"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ownerScopingHidesObjectsAndDetails() throws Exception {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture fixture = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        insertObject(fixture.versionId(), fixture, "1", "owned", Map.of());

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/objects",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/object",
                                fixture.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", UUID.randomUUID().toString())
                        .param("primaryKey", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesRelationGroupsAndInstances() throws Exception {
        UUID ownerId = UUID.randomUUID();
        ObjectFixture source = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        ObjectFixture target = createObjectFixture(ownerId, List.of(
                attribute("id", "integer"),
                attribute("name", "text")
        ));
        insertObject(source.versionId(), source, "1", "source", Map.of());
        insertObject(target.versionId(), target, "1", "target", Map.of());
        UUID relationId = createRelationDefinition(
                ownerId, source, target, "links", "one_to_many"
        );
        UUID relationVersion = addFkVersion(
                ownerId,
                relationId,
                source,
                target,
                source.versionId(),
                target.versionId()
        );
        insertInstance(relationVersion, relationId, source, target, "1", "1");

        String groups = mvc.perform(get(
                                "/internal/v1/ontology/object-types/{id}/object-relations",
                                source.objectTypeId())
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("primaryKey", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].relationId").value(relationId.toString()))
                .andExpect(jsonPath("$.groups[0].direction").value("outgoing"))
                .andExpect(jsonPath("$.groups[0].total").value(1))
                .andExpect(jsonPath("$.groups[0].relatedObjects[0].primaryKey").value("1"))
                .andReturn().getResponse().getContentAsString();
        JsonNode groupJson = mapper.readTree(groups);
        assertThat(groupJson.path("groups").get(0).path("relatedObjects").get(0).path("title").asText())
                .isEqualTo("target");

        mvc.perform(get("/internal/v1/ontology/object-types/{id}/object-relations/{relationId}",
                                source.objectTypeId(), relationId)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("primaryKey", "1")
                        .param("direction", "outgoing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.indexVersionId")
                        .value(relationVersion.toString()));

        mvc.perform(get("/internal/v1/ontology/relation-types/{id}/instances", relationId)
                        .header(InternalTokenFilter.HEADER, INTERNAL_TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("fromPrimaryKey", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].toPrimaryKey").value("1"));
    }

    private AttributeSpec attribute(String column, String datatype) {
        return new AttributeSpec(UUID.randomUUID(), column, datatype);
    }
}
