package com.ontoweave.data.ontology.binding;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MappingPreviewTest extends BindingIntegrationTestSupport {

    private static final Schema CUSTOMER_SCHEMA = new Schema(
            Types.NestedField.optional(1, "id", Types.StringType.get()),
            Types.NestedField.optional(2, "name", Types.StringType.get())
    );

    @Test
    void objectPreviewReadsAtMostTwentyRowsAndDoesNotWriteAnyOntologyTable() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String table = "customers-" + UUID.randomUUID();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "customer-" + index);
            row.put("name", "Customer " + index);
            rows.add(row);
        }
        long snapshotId = seedRows("sync_ns", table, CUSTOMER_SCHEMA, rows);
        UUID datasetId = insertSyncDataset(ownerId, table, snapshotId, UUID.randomUUID(),
                customerSchemaJson());
        UUID objectTypeId = UUID.randomUUID();
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();
        saveObjectBinding(ownerId, objectTypeId, datasetId, primaryKey, name);
        Map<String, Long> before = ontologyCounts();

        mvc.perform(post("/internal/v1/ontology/object-types/{id}/binding/preview", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\",\"limit\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sample.length()").value(20))
                .andExpect(jsonPath("$.blocking.length()").value(0))
                .andExpect(jsonPath("$.sample[*].primaryKey")
                        .value(org.hamcrest.Matchers.hasItem("customer-0")));

        MvcResult preview = mvc.perform(
                        post("/internal/v1/ontology/object-types/{id}/binding/preview", objectTypeId)
                                .header("X-Internal-Token", TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ownerId\":\"" + ownerId + "\",\"limit\":20}"))
                .andReturn();
        JsonNode first = mapper.readTree(preview.getResponse().getContentAsString())
                .path("sample").get(0);
        assertThat(first.path("primaryKey").asText()).startsWith("customer-");

        assertThat(ontologyCounts()).isEqualTo(before);
    }

    @Test
    void objectPreviewReportsEmptyDuplicateKeysAndMissingRequiredValues() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String table = "dirty-customers-" + UUID.randomUUID();
        long snapshotId = seedRows("sync_ns", table, CUSTOMER_SCHEMA, List.of(
                row("customer-1", "Ada"),
                row("customer-1", "Duplicate"),
                row("customer-2", null),
                row(null, "No key")
        ));
        UUID datasetId = insertSyncDataset(ownerId, table, snapshotId, UUID.randomUUID(),
                customerSchemaJson());
        UUID objectTypeId = UUID.randomUUID();
        UUID primaryKey = UUID.randomUUID();
        UUID name = UUID.randomUUID();
        saveObjectBinding(ownerId, objectTypeId, datasetId, primaryKey, name);

        MvcResult result = mvc.perform(
                        post("/internal/v1/ontology/object-types/{id}/binding/preview", objectTypeId)
                                .header("X-Internal-Token", TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ownerId\":\"" + ownerId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(codes(body.path("blocking"))).contains(
                "PRIMARY_KEY_DUPLICATE", "PRIMARY_KEY_EMPTY", "REQUIRED_VALUE_MISSING"
        );
        assertThat(body.path("skipped").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void relationPreviewSkipsMissingTargetsAndDuplicatePairsWithoutWriting() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID relationId = UUID.randomUUID();
        UUID sourceTypeId = UUID.randomUUID();
        UUID targetTypeId = UUID.randomUUID();
        UUID sourcePkId = UUID.randomUUID();
        UUID targetPkId = UUID.randomUUID();
        insertDefinition(ownerId, relationId, sourceTypeId, targetTypeId,
                sourcePkId, targetPkId);

        Schema linkSchema = new Schema(
                Types.NestedField.required(1, "customer_id", Types.StringType.get()),
                Types.NestedField.required(2, "order_id", Types.StringType.get())
        );
        String table = "customer-orders-" + UUID.randomUUID();
        long snapshotId = seedRows("sync_ns", table, linkSchema, List.of(
                link("c1", "o1"),
                link("c2", "o2"),
                link("c2", "o2")
        ));
        UUID datasetId = insertSyncDataset(ownerId, table, snapshotId, UUID.randomUUID(), """
                {"fields":[
                  {"name":"customer_id","type":"string","required":true},
                  {"name":"order_id","type":"string","required":true}
                ]}
                """);
        saveRelationBinding(ownerId, relationId, datasetId);

        UUID targetBindingId = UUID.randomUUID();
        UUID targetVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.ontology_object_binding
                    (id, owner_id, object_type_id, draft_dataset_kind, draft_dataset_id,
                     draft_mapping, current_index_version_id)
                VALUES (?, ?, ?, 'SYNC', ?, '{}'::jsonb, ?)
                """, targetBindingId, ownerId, targetTypeId, UUID.randomUUID(), targetVersionId);
        jdbc.update("""
                INSERT INTO data.ontology_object_instance
                    (version_id, object_type_id, primary_key_value, properties)
                VALUES (?, ?, 'o1', '{}'::jsonb)
                """, targetVersionId, targetTypeId);
        Map<String, Long> before = ontologyCounts();

        MvcResult result = mvc.perform(
                        post("/internal/v1/ontology/relation-types/{id}/binding/preview", relationId)
                                .header("X-Internal-Token", TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ownerId\":\"" + ownerId + "\",\"limit\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sample.length()").value(1))
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("warnings").toString()).contains("TARGET_MISSING");
        assertThat(body.path("warnings").toString()).contains("DUPLICATE_RELATION_PAIR");
        assertThat(codes(body.path("skipped"))).contains("TARGET_MISSING", "DUPLICATE_RELATION_PAIR");
        assertThat(ontologyCounts()).isEqualTo(before);
    }

    private void saveObjectBinding(
            UUID ownerId,
            UUID objectTypeId,
            UUID datasetId,
            UUID primaryKey,
            UUID name
    ) throws Exception {
        mvc.perform(put("/internal/v1/ontology/object-types/{id}/binding", objectTypeId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","baseRevision":0,"datasetKind":"SYNC",
                                 "datasetId":"%s","mapping":{"primaryKeyAttributeId":"%s",
                                 "titleKeyAttributeId":"%s","attributes":[
                                   {"attributeId":"%s","column":"id","datatype":"text",
                                    "required":true,"isArray":false},
                                   {"attributeId":"%s","column":"name","datatype":"text",
                                    "required":true,"isArray":false}]}}
                                """.formatted(ownerId, datasetId, primaryKey, name, primaryKey, name)))
                .andExpect(status().isOk());
    }

    private void saveRelationBinding(UUID ownerId, UUID relationId, UUID datasetId)
            throws Exception {
        mvc.perform(put("/internal/v1/ontology/relation-types/{id}/binding", relationId)
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":"%s","baseRevision":0,"datasetKind":"SYNC",
                                 "datasetId":"%s","mapping":{"fromKeyColumn":"customer_id",
                                 "fromKeyDatatype":"text","toKeyColumn":"order_id",
                                 "toKeyDatatype":"text"}}
                                """.formatted(ownerId, datasetId)))
                .andExpect(status().isOk());
    }

    private void insertDefinition(
            UUID ownerId,
            UUID relationId,
            UUID sourceTypeId,
            UUID targetTypeId,
            UUID sourcePkId,
            UUID targetPkId
    ) {
        jdbc.update("""
                INSERT INTO data.ontology_relation_definition
                    (relation_id, owner_id, source_type_id, target_type_id, name, cardinality,
                     source_primary_key_attribute_id, source_primary_key_datatype,
                     target_primary_key_attribute_id, target_primary_key_datatype,
                     definition_revision)
                VALUES (?, ?, ?, ?, 'customer_orders', 'many_to_many', ?, 'text', ?, 'text', 1)
                """, relationId, ownerId, sourceTypeId, targetTypeId, sourcePkId, targetPkId);
    }

    private Map<String, Object> row(String id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private Map<String, Object> link(String from, String to) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customer_id", from);
        row.put("order_id", to);
        return row;
    }

    private String customerSchemaJson() {
        return """
                {"fields":[
                  {"name":"id","type":"string","required":false},
                  {"name":"name","type":"string","required":false}
                ]}
                """;
    }

    private List<String> codes(JsonNode issues) {
        List<String> codes = new ArrayList<>();
        issues.forEach(issue -> codes.add(issue.path("code").asText()));
        return codes;
    }
}
