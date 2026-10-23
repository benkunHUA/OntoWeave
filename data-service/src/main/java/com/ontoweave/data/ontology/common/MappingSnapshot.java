package com.ontoweave.data.ontology.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MappingSnapshot(
        @JsonProperty("primaryKeyAttributeId") UUID primaryKeyAttributeId,
        @JsonProperty("titleKeyAttributeId") UUID titleKeyAttributeId,
        @JsonProperty("attributes") List<AttributeMapping> attributes,
        @JsonProperty("fromKeyColumn") String fromKeyColumn,
        @JsonProperty("fromKeyDatatype") String fromKeyDatatype,
        @JsonProperty("toKeyColumn") String toKeyColumn,
        @JsonProperty("toKeyDatatype") String toKeyDatatype,
        @JsonProperty("revision") long revision,
        @JsonProperty("requiredAttributeIds") List<UUID> requiredAttributeIds
) {

    public MappingSnapshot(
            UUID primaryKeyAttributeId,
            UUID titleKeyAttributeId,
            List<AttributeMapping> attributes,
            String fromKeyColumn,
            String fromKeyDatatype,
            String toKeyColumn,
            String toKeyDatatype,
            long revision
    ) {
        this(
                primaryKeyAttributeId,
                titleKeyAttributeId,
                attributes,
                fromKeyColumn,
                fromKeyDatatype,
                toKeyColumn,
                toKeyDatatype,
                revision,
                List.of()
        );
    }

    public MappingSnapshot {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        requiredAttributeIds = requiredAttributeIds == null
                ? List.of()
                : List.copyOf(requiredAttributeIds);
    }

    public static MappingSnapshot object(
            UUID primaryKeyAttributeId,
            UUID titleKeyAttributeId,
            List<AttributeMapping> attributes,
            long revision
    ) {
        return new MappingSnapshot(
                primaryKeyAttributeId,
                titleKeyAttributeId,
                attributes,
                null,
                null,
                null,
                null,
                revision
        );
    }

    public static MappingSnapshot object(
            UUID primaryKeyAttributeId,
            UUID titleKeyAttributeId,
            List<AttributeMapping> attributes,
            long revision,
            List<UUID> requiredAttributeIds
    ) {
        return new MappingSnapshot(
                primaryKeyAttributeId,
                titleKeyAttributeId,
                attributes,
                null,
                null,
                null,
                null,
                revision,
                requiredAttributeIds
        );
    }

    public static MappingSnapshot manyToMany(
            String fromKeyColumn,
            String fromKeyDatatype,
            String toKeyColumn,
            String toKeyDatatype,
            long revision
    ) {
        return new MappingSnapshot(
                null,
                null,
                List.of(),
                fromKeyColumn,
                fromKeyDatatype,
                toKeyColumn,
                toKeyDatatype,
                revision
        );
    }

    public boolean isObjectMapping() {
        return primaryKeyAttributeId != null
                || !attributes.isEmpty()
                || !requiredAttributeIds.isEmpty();
    }

    public boolean isRelationMapping() {
        return fromKeyColumn != null || toKeyColumn != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttributeMapping(
            @JsonProperty("attributeId") UUID attributeId,
            @JsonProperty("column") String column,
            @JsonProperty("datatype") String datatype,
            @JsonProperty("required") boolean required,
            @JsonProperty("isArray") boolean isArray,
            @JsonProperty("enumValues") List<String> enumValues
    ) {

        public AttributeMapping {
            enumValues = enumValues == null ? null : List.copyOf(enumValues);
        }
    }
}
