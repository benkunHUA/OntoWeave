package com.ontoweave.data.ontology.index.pipeline;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.RelationKind;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.store.RelationStoreWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class ForeignKeyRelationBuilder {

    private final JdbcTemplate jdbc;
    private final RelationStoreWriter writer;
    private final RelationMaterializer materializer;

    public ForeignKeyRelationBuilder(
            JdbcTemplate jdbc,
            RelationStoreWriter writer,
            RelationMaterializer materializer
    ) {
        this.jdbc = jdbc;
        this.writer = writer;
        this.materializer = materializer;
    }

    public RelationBuildResult build(
            UUID runId,
            UUID ownerId,
            OntologyRelationDefinitionEntity definition,
            EndpointVersion from,
            EndpointVersion to
    ) {
        ForeignKeyColumn foreignKey = resolveForeignKey(definition, from, to);
        String foreignKeyColumn = valueColumn(foreignKey.attribute().datatype());
        String referencedType = normalize(foreignKey.onTarget()
                ? definition.getSourcePrimaryKeyDatatype()
                : definition.getTargetPrimaryKeyDatatype());
        String foreignKeyAlias = foreignKey.onTarget() ? "t" : "s";
        String referencedAlias = foreignKey.onTarget() ? "s" : "t";
        String referencedJoin = foreignKeyColumn == null
                ? null
                : primaryKeyJoin(referencedAlias, referencedType, "fv." + foreignKeyColumn);
        if (referencedJoin == null) {
            throw new RelationSkippedException(
                    "FOREIGN_KEY_TYPE_INCOMPATIBLE",
                    "foreign key datatype is incompatible with target primary key"
            );
        }
        EndpointVersion foreignKeyEndpoint = foreignKey.onTarget() ? to : from;
        writer.deleteStage(runId);
        Long foreignKeyCount = jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_object_instance k "
                        + "JOIN data.ontology_object_value fv ON fv.version_id = k.version_id "
                        + "AND fv.primary_key_value = k.primary_key_value "
                        + "WHERE k.version_id = ? AND fv.attribute_id = ? "
                        + "AND fv." + foreignKeyColumn + " IS NOT NULL",
                Long.class,
                foreignKeyEndpoint.versionId(),
                foreignKey.attribute().attributeId()
        );
        int rows = jdbc.update("""
                INSERT INTO data.ontology_relation_stage
                    (run_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key)
                SELECT DISTINCT ?, ?, ?, s.primary_key_value, ?, t.primary_key_value
                FROM data.ontology_object_instance s
                JOIN data.ontology_object_instance t ON t.version_id = ?
                JOIN data.ontology_object_value fv
                  ON fv.version_id = %s.version_id
                 AND fv.primary_key_value = %s.primary_key_value
                WHERE s.version_id = ? AND fv.attribute_id = ?
                  AND fv.%s IS NOT NULL
                  AND %s
                """.formatted(foreignKeyAlias, foreignKeyAlias, foreignKeyColumn, referencedJoin),
                runId,
                definition.getRelationId(),
                from.objectTypeId(),
                to.objectTypeId(),
                to.versionId(),
                from.versionId(),
                foreignKey.attribute().attributeId()
        );
        long skipped = Math.max(0L, (foreignKeyCount == null ? 0 : foreignKeyCount) - rows);
        return materializer.materialize(
                runId,
                ownerId,
                null,
                definition.getRelationId(),
                RelationKind.FK,
                from,
                to,
                from.sourceDatasetKind(),
                from.sourceDatasetId(),
                from.sourceDatasetVersionId(),
                from.sourceSnapshotId(),
                from.mappingSnapshot(),
                from.mappingRevision(),
                skipped
        );
    }

    private ForeignKeyColumn resolveForeignKey(
            OntologyRelationDefinitionEntity definition,
            EndpointVersion from,
            EndpointVersion to
    ) {
        UUID foreignKeyId = definition.getForeignKeyPropertyId();
        if (foreignKeyId != null) {
            MappingSnapshot.AttributeMapping sourceAttribute = findAttribute(from.mapping(), foreignKeyId);
            if (sourceAttribute != null) {
                return new ForeignKeyColumn(sourceAttribute, false);
            }
            MappingSnapshot.AttributeMapping targetAttribute = findAttribute(to.mapping(), foreignKeyId);
            if (targetAttribute != null) {
                return new ForeignKeyColumn(targetAttribute, true);
            }
        }
        throw new RelationSkippedException(
                "FOREIGN_KEY_NOT_MAPPED",
                "foreign key property is not mapped"
        );
    }

    private MappingSnapshot.AttributeMapping findAttribute(MappingSnapshot mapping, UUID attributeId) {
        if (mapping == null) {
            return null;
        }
        return mapping.attributes().stream()
                .filter(attribute -> attributeId.equals(attribute.attributeId()))
                .findFirst()
                .orElse(null);
    }

    private String valueColumn(String datatype) {
        String type = normalize(datatype);
        return switch (type) {
            case "text", "enum" -> "text_value";
            case "integer", "decimal" -> "num_value";
            case "date" -> "date_value";
            case "timestamp", "timestamptz" -> "ts_value";
            default -> null;
        };
    }

    private String primaryKeyJoin(String alias, String datatype, String valueColumn) {
        return switch (normalize(datatype)) {
            case "text" -> alias + ".primary_key_value = " + valueColumn;
            case "integer", "decimal" -> alias + ".primary_key_num = " + valueColumn;
            case "date" -> alias + ".primary_key_date = " + valueColumn;
            case "timestamp", "timestamptz" -> alias + ".primary_key_ts = " + valueColumn;
            default -> null;
        };
    }

    private String normalize(String datatype) {
        if (datatype == null) {
            return "";
        }
        String value = datatype.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "timestamp with time zone", "timestamp_tz" -> "timestamptz";
            case "timestamp without time zone", "timestamp_ntz" -> "timestamp";
            default -> value;
        };
    }

    private record ForeignKeyColumn(
            MappingSnapshot.AttributeMapping attribute,
            boolean onTarget
    ) {
    }
}
