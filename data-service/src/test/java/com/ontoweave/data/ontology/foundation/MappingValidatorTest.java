package com.ontoweave.data.ontology.foundation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.mapping.MappingValidator;
import com.ontoweave.data.ontology.mapping.OntologyAttributeDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MappingValidatorTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID DATASET = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID PRIMARY_KEY = UUID.randomUUID();
    private static final UUID NAME = UUID.randomUUID();
    private static final UUID REQUIRED = UUID.randomUUID();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MappingValidator validator = new MappingValidator();

    @Test
    void acceptsACompatibleObjectMapping() {
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                NAME,
                List.of(
                        attribute(PRIMARY_KEY, "customer_id", "text", true, false),
                        attribute(NAME, "name", "text", false, false)
                ),
                3
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true},
                          {"name":"name","type":"string","required":false}
                        ]}
                        """),
                definitions(PRIMARY_KEY, NAME),
                "confirmed"
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.blocking()).isEmpty();
    }

    @Test
    void blocksRequiredAttributesThatAreNotMapped() {
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                null,
                List.of(attribute(PRIMARY_KEY, "customer_id", "text", true, false)),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true}
                        ]}
                        """),
                definitions(PRIMARY_KEY, REQUIRED),
                "approved"
        );

        assertThat(codes(result.blocking())).contains("REQUIRED_ATTRIBUTE_UNMAPPED");
    }

    @Test
    void blocksUnsupportedPrimaryKeyTypes() {
        UUID flag = UUID.randomUUID();
        MappingSnapshot mapping = MappingSnapshot.object(
                flag,
                null,
                List.of(attribute(flag, "flag", "boolean", true, false)),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[{"name":"flag","type":"boolean","required":true}]}
                        """),
                List.of(new OntologyAttributeDefinition(
                        flag, "flag", "boolean", true, false, null, null
                )),
                "confirmed"
        );

        assertThat(codes(result.blocking())).contains("PRIMARY_KEY_TYPE_UNSUPPORTED");
    }

    @Test
    void blocksArrayAttributesAndNestedDatasetTypes() {
        UUID tags = UUID.randomUUID();
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                null,
                List.of(
                        attribute(PRIMARY_KEY, "customer_id", "text", true, false),
                        attribute(tags, "tags", "text", false, true)
                ),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true},
                          {"name":"tags","type":"list<string>","required":false}
                        ]}
                        """),
                List.of(
                        new OntologyAttributeDefinition(
                                PRIMARY_KEY, "customer_id", "text", true, false, null, null
                        ),
                        new OntologyAttributeDefinition(
                                tags, "tags", "text", false, true, null, null
                        )
                ),
                "confirmed"
        );

        assertThat(codes(result.blocking())).contains("ARRAY_ATTRIBUTE_UNSUPPORTED");
    }

    @Test
    void blocksIncompatibleDatasetTypes() {
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                null,
                List.of(attribute(PRIMARY_KEY, "customer_id", "integer", true, false)),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[{"name":"customer_id","type":"string","required":true}]}
                        """),
                List.of(new OntologyAttributeDefinition(
                        PRIMARY_KEY, "customer_id", "integer", true, false, null, null
                )),
                "confirmed"
        );

        assertThat(codes(result.blocking())).contains("TYPE_INCOMPATIBLE");
    }

    @Test
    void blocksDuplicateColumns() {
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                null,
                List.of(
                        attribute(PRIMARY_KEY, "customer_id", "text", true, false),
                        attribute(NAME, "customer_id", "text", false, false)
                ),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true},
                          {"name":"name","type":"string","required":false}
                        ]}
                        """),
                definitions(PRIMARY_KEY, NAME),
                "confirmed"
        );

        assertThat(codes(result.blocking())).contains("COLUMN_ALREADY_MAPPED");
    }

    @Test
    void blocksUnconfirmedSubjects() {
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                null,
                List.of(attribute(PRIMARY_KEY, "customer_id", "text", true, false)),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[{"name":"customer_id","type":"string","required":true}]}
                        """),
                definitions(PRIMARY_KEY),
                "draft"
        );

        assertThat(codes(result.blocking())).contains("SUBJECT_NOT_CONFIRMED");
    }

    @Test
    void blocksEnumAttributesWithoutFrozenCandidates() {
        UUID status = UUID.randomUUID();
        MappingSnapshot mapping = MappingSnapshot.object(
                PRIMARY_KEY,
                null,
                List.of(
                        attribute(PRIMARY_KEY, "customer_id", "text", true, false),
                        new MappingSnapshot.AttributeMapping(
                                status, "status", "enum", false, false, List.of()
                        )
                ),
                1
        );

        MappingValidationResult result = validator.validate(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true},
                          {"name":"status","type":"string","required":false}
                        ]}
                        """),
                List.of(
                        new OntologyAttributeDefinition(
                                PRIMARY_KEY, "customer_id", "text", true, false, null, null
                        ),
                        new OntologyAttributeDefinition(
                                status,
                                "status",
                                "enum",
                                false,
                                false,
                                List.of("active", "closed"),
                                null
                        )
                ),
                "confirmed"
        );

        assertThat(codes(result.blocking())).contains("ENUM_VALUES_INVALID");
    }

    @Test
    void validatesManyToManyColumnsAgainstBothPrimaryKeys() {
        MappingSnapshot mapping = MappingSnapshot.manyToMany(
                "customer_id", "text", "order_id", "integer", 5
        );

        MappingValidationResult result = validator.validateRelation(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true},
                          {"name":"order_id","type":"long","required":true}
                        ]}
                        """),
                "approved",
                "text",
                "integer"
        );

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void blocksManyToManyTypeMismatch() {
        MappingSnapshot mapping = MappingSnapshot.manyToMany(
                "customer_id", "text", "order_id", "text", 1
        );

        MappingValidationResult result = validator.validateRelation(
                mapping,
                dataset("""
                        {"fields":[
                          {"name":"customer_id","type":"string","required":true},
                          {"name":"order_id","type":"string","required":true}
                        ]}
                        """),
                "confirmed",
                "text",
                "integer"
        );

        assertThat(codes(result.blocking())).contains("TYPE_INCOMPATIBLE");
    }

    private MappingSnapshot.AttributeMapping attribute(
            UUID attributeId,
            String column,
            String datatype,
            boolean required,
            boolean array
    ) {
        return new MappingSnapshot.AttributeMapping(
                attributeId, column, datatype, required, array, null
        );
    }

    private List<OntologyAttributeDefinition> definitions(UUID... ids) {
        List<OntologyAttributeDefinition> definitions = new ArrayList<>();
        for (UUID id : ids) {
            boolean primary = id.equals(PRIMARY_KEY);
            definitions.add(new OntologyAttributeDefinition(
                    id,
                    primary ? "customer_id" : "name",
                    "text",
                    primary || id.equals(REQUIRED),
                    false,
                    null,
                    null
            ));
        }
        return List.copyOf(definitions);
    }

    private DatasetRef dataset(String schemaJson) {
        try {
            JsonNode schema = MAPPER.readTree(schemaJson);
            return new DatasetRef(
                    DatasetKind.SYNC,
                    DATASET,
                    VERSION,
                    "ontoweave",
                    "ns",
                    "customers",
                    7L,
                    schema,
                    OWNER,
                    100L
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private List<String> codes(List<MappingValidationResult.MappingIssue> issues) {
        return issues.stream().map(MappingValidationResult.MappingIssue::code).toList();
    }
}
