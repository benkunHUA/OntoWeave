package com.ontoweave.data.ontology.binding;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RelationDefinitionApiTest extends BindingIntegrationTestSupport {

    @Test
    void upsertsAndReplacesARelationDefinitionSnapshot() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID sourceTypeId = UUID.randomUUID();
        UUID targetTypeId = UUID.randomUUID();
        UUID foreignKeyId = UUID.randomUUID();
        UUID sourcePkId = UUID.randomUUID();
        UUID targetPkId = UUID.randomUUID();

        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ownerId, sourceTypeId, targetTypeId, "one_to_many",
                                foreignKeyId, "text", sourcePkId, "text", targetPkId, "text", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationId").value(relationId.toString()))
                .andExpect(jsonPath("$.definitionRevision").value(1))
                .andExpect(jsonPath("$.warnings.length()").value(0));

        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ownerId, sourceTypeId, targetTypeId, "one_to_many",
                                foreignKeyId, "text", sourcePkId, "text", targetPkId, "text", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitionRevision").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT definition_revision FROM data.ontology_relation_definition WHERE relation_id = ?",
                Long.class,
                relationId
        )).isEqualTo(2L);
    }

    @Test
    void rejectsUpsertFromAnotherOwner() throws Exception {
        UUID relationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        upsert(relationId, ownerId, "one_to_one", "text", "text", "text");

        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                "one_to_one", UUID.randomUUID(), "text", UUID.randomUUID(),
                                "text", UUID.randomUUID(), "text", 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BINDING_CONFLICT"));
    }

    @Test
    void rejectsInvalidCardinalityAndMissingForeignKey() throws Exception {
        UUID relationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ownerId, UUID.randomUUID(), UUID.randomUUID(), "many_to_one",
                                UUID.randomUUID(), "text", UUID.randomUUID(), "text",
                                UUID.randomUUID(), "text", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RELATION_DEFINITION"));

        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ownerId, UUID.randomUUID(), UUID.randomUUID(), "one_to_one",
                                null, null, UUID.randomUUID(), "text",
                                UUID.randomUUID(), "text", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RELATION_DEFINITION"));
    }

    @Test
    void foreignKeyDatatypeMismatchIsAWarningNotARejection() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();

        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ownerId, UUID.randomUUID(), UUID.randomUUID(), "one_to_many",
                                UUID.randomUUID(), "text", UUID.randomUUID(), "text",
                                UUID.randomUUID(), "integer", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0].code")
                        .value("FOREIGN_KEY_TYPE_INCOMPATIBLE"))
                .andExpect(jsonPath("$.warnings[0].column").doesNotExist());

        assertThat(jdbc.queryForObject(
                "SELECT foreign_key_datatype FROM data.ontology_relation_definition WHERE relation_id = ?",
                String.class,
                relationId
        )).isEqualTo("text");
    }

    private void upsert(
            UUID relationId,
            UUID ownerId,
            String cardinality,
            String fkType,
            String sourcePkType,
            String targetPkType
    ) throws Exception {
        mvc.perform(put("/internal/v1/ontology/relation-definitions/{id}", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ownerId, UUID.randomUUID(), UUID.randomUUID(), cardinality,
                                UUID.randomUUID(), fkType, UUID.randomUUID(), sourcePkType,
                                UUID.randomUUID(), targetPkType, 1)))
                .andExpect(status().isOk());
    }

    private String body(
            UUID ownerId,
            UUID sourceTypeId,
            UUID targetTypeId,
            String cardinality,
            UUID foreignKeyPropertyId,
            String foreignKeyDatatype,
            UUID sourcePrimaryKeyAttributeId,
            String sourcePrimaryKeyDatatype,
            UUID targetPrimaryKeyAttributeId,
            String targetPrimaryKeyDatatype,
            long definitionRevision
    ) {
        return """
                {"ownerId":"%s","sourceTypeId":"%s","targetTypeId":"%s","name":"orders",
                 "cardinality":"%s","foreignKeyPropertyId":%s,
                 "foreignKeyDatatype":%s,
                 "sourcePrimaryKeyAttributeId":"%s","sourcePrimaryKeyDatatype":"%s",
                 "targetPrimaryKeyAttributeId":"%s","targetPrimaryKeyDatatype":"%s",
                 "definitionRevision":%d}
                """.formatted(
                ownerId,
                sourceTypeId,
                targetTypeId,
                cardinality,
                uuidJson(foreignKeyPropertyId),
                stringJson(foreignKeyDatatype),
                sourcePrimaryKeyAttributeId,
                sourcePrimaryKeyDatatype,
                targetPrimaryKeyAttributeId,
                targetPrimaryKeyDatatype,
                definitionRevision
        );
    }

    private String uuidJson(UUID value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String stringJson(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
