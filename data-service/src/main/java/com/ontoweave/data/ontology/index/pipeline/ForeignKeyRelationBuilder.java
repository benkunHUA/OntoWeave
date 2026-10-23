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
        MappingSnapshot.AttributeMapping foreignKey = from.mapping().attributes().stream()
                .filter(attribute -> definition.getForeignKeyPropertyId() != null
                        && definition.getForeignKeyPropertyId().equals(attribute.attributeId()))
                .findFirst()
                .orElseThrow(() -> new RelationSkippedException(
                        "FOREIGN_KEY_NOT_MAPPED",
                        "foreign key property is not mapped"
                ));
        String targetType = normalize(definition.getTargetPrimaryKeyDatatype());
        String sourceColumn = sourceColumn(foreignKey.datatype());
        String targetJoin = targetJoin(targetType);
        if (sourceColumn == null || targetJoin == null) {
            throw new RelationSkippedException(
                    "FOREIGN_KEY_TYPE_INCOMPATIBLE",
                    "foreign key datatype is incompatible with target primary key"
            );
        }
        writer.deleteStage(runId);
        Long sourceCount = jdbc.queryForObject(
                "SELECT count(*) FROM data.ontology_object_instance s "
                        + "JOIN data.ontology_object_value fv ON fv.version_id = s.version_id "
                        + "AND fv.primary_key_value = s.primary_key_value "
                        + "WHERE s.version_id = ? AND fv.attribute_id = ? "
                        + "AND fv." + sourceColumn + " IS NOT NULL",
                Long.class,
                from.versionId(),
                foreignKey.attributeId()
        );
        int rows = jdbc.update("""
                INSERT INTO data.ontology_relation_stage
                    (run_id, relation_id, from_object_type_id, from_primary_key,
                     to_object_type_id, to_primary_key)
                SELECT DISTINCT ?, ?, ?, s.primary_key_value, ?, t.primary_key_value
                FROM data.ontology_object_instance s
                JOIN data.ontology_object_value fv
                  ON fv.version_id = s.version_id
                 AND fv.primary_key_value = s.primary_key_value
                JOIN data.ontology_object_instance t
                  ON t.version_id = ? AND %s
                WHERE s.version_id = ? AND fv.attribute_id = ?
                  AND fv.%s IS NOT NULL
                """.formatted(targetJoin, sourceColumn),
                runId,
                definition.getRelationId(),
                from.objectTypeId(),
                to.objectTypeId(),
                to.versionId(),
                from.versionId(),
                foreignKey.attributeId()
        );
        long skipped = Math.max(0L, (sourceCount == null ? 0 : sourceCount) - rows);
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

    private String sourceColumn(String datatype) {
        String type = normalize(datatype);
        return switch (type) {
            case "text", "enum" -> "text_value";
            case "integer", "decimal" -> "num_value";
            case "date" -> "date_value";
            case "timestamp", "timestamptz" -> "ts_value";
            default -> null;
        };
    }

    private String targetJoin(String targetType) {
        return switch (targetType) {
            case "text" -> "t.primary_key_value = fv.text_value";
            case "integer", "decimal" -> "t.primary_key_num = fv.num_value";
            case "date" -> "t.primary_key_date = fv.date_value";
            case "timestamp", "timestamptz" -> "t.primary_key_ts = fv.ts_value";
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
}
