package com.ontoweave.data.ontology.binding.preview;

import com.ontoweave.data.ontology.binding.api.dto.PreviewSkip;
import com.ontoweave.data.ontology.binding.api.dto.RelationPreviewPair;
import com.ontoweave.data.ontology.binding.api.dto.RelationPreviewResponse;
import com.ontoweave.data.ontology.binding.validation.BindingCheckService;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.PrimaryKeyCodec;
import com.ontoweave.data.ontology.domain.OntologyObjectBindingEntity;
import com.ontoweave.data.ontology.domain.OntologyRelationDefinitionEntity;
import com.ontoweave.data.ontology.iceberg.ColumnValueReader;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import com.ontoweave.data.ontology.repository.OntologyObjectBindingRepository;
import org.apache.iceberg.data.Record;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
final class RelationPreviewRenderer {

    private final IcebergSnapshotReader reader;
    private final OntologyObjectBindingRepository objectBindings;
    private final JdbcTemplate jdbc;
    private final ColumnValueReader values = new ColumnValueReader();

    RelationPreviewRenderer(
            IcebergSnapshotReader reader,
            OntologyObjectBindingRepository objectBindings,
            JdbcTemplate jdbc
    ) {
        this.reader = reader;
        this.objectBindings = objectBindings;
        this.jdbc = jdbc;
    }

    RelationPreviewResponse render(
            BindingCheckService.CheckResult check,
            MappingSnapshot mapping,
            OntologyRelationDefinitionEntity definition,
            UUID ownerId,
            MappingValidationResult validation,
            int limit
    ) {
        List<MappingValidationResult.MappingIssue> blocking = new ArrayList<>(
                validation.blocking()
        );
        List<MappingValidationResult.MappingIssue> warnings = new ArrayList<>(
                validation.warnings()
        );
        List<PreviewSkip> skipped = new ArrayList<>();
        List<RelationPreviewPair> sample = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        UUID targetVersionId = targetVersion(definition.getTargetTypeId(), ownerId);
        boolean targetNotIndexedReported = false;
        try (IcebergSnapshotReader.SnapshotRead read = reader.open(
                check.dataset(), check.dataset().snapshotId()
        )) {
            Iterator<Record> records = read.records().iterator();
            int scanned = 0;
            while (records.hasNext() && scanned < limit) {
                Record record = records.next();
                int rowNumber = scanned++;
                Object rawFrom = values.read(record, mapping.fromKeyColumn());
                Object rawTo = values.read(record, mapping.toKeyColumn());
                if (PreviewTypeConverter.isBlank(rawFrom) || PreviewTypeConverter.isBlank(rawTo)) {
                    PreviewSupport.warning(warnings, PreviewIssueCodes.RELATION_KEY_EMPTY,
                            "关系连接键为空", "row-" + rowNumber);
                    skipped.add(new PreviewSkip(PreviewIssueCodes.RELATION_KEY_EMPTY,
                            "关系连接键为空", "row-" + rowNumber));
                    continue;
                }
                String from;
                String to;
                try {
                    from = PrimaryKeyCodec.canonicalize(
                            rawFrom, mapping.fromKeyDatatype(), PreviewTypeConverter.scaleOf(rawFrom)
                    );
                    to = PrimaryKeyCodec.canonicalize(
                            rawTo, mapping.toKeyDatatype(), PreviewTypeConverter.scaleOf(rawTo)
                    );
                } catch (RuntimeException exception) {
                    PreviewSupport.warning(warnings, PreviewIssueCodes.VALUE_CONVERSION_FAILED,
                            "关系键转换失败: " + exception.getMessage(), "row-" + rowNumber);
                    skipped.add(new PreviewSkip(PreviewIssueCodes.VALUE_CONVERSION_FAILED,
                            "关系键转换失败", "row-" + rowNumber));
                    continue;
                }
                String pairKey = from + "\u0000" + to;
                if (!seenPairs.add(pairKey)) {
                    PreviewSupport.warning(warnings, PreviewIssueCodes.DUPLICATE_RELATION_PAIR,
                            "关系对重复，生成时将去重", from + " -> " + to);
                    skipped.add(new PreviewSkip(PreviewIssueCodes.DUPLICATE_RELATION_PAIR,
                            "关系对重复", from + " -> " + to));
                    continue;
                }
                if (targetVersionId == null) {
                    if (!targetNotIndexedReported) {
                        PreviewSupport.warning(warnings, PreviewIssueCodes.TARGET_NOT_INDEXED,
                                "目标对象尚无生效索引", definition.getTargetTypeId().toString());
                        targetNotIndexedReported = true;
                    }
                    skipped.add(new PreviewSkip(PreviewIssueCodes.TARGET_NOT_INDEXED,
                            "目标对象尚无生效索引", to));
                    continue;
                }
                if (!targetExists(targetVersionId, definition.getTargetTypeId(), to)) {
                    PreviewSupport.warning(warnings, PreviewIssueCodes.TARGET_MISSING,
                            "目标对象不存在，生成时将跳过", to);
                    skipped.add(new PreviewSkip(PreviewIssueCodes.TARGET_MISSING,
                            "目标对象不存在", to));
                    continue;
                }
                sample.add(new RelationPreviewPair(from, to));
            }
        }
        return new RelationPreviewResponse(sample, blocking, warnings, skipped);
    }

    private UUID targetVersion(UUID targetTypeId, UUID ownerId) {
        return objectBindings.findByObjectTypeIdAndOwnerId(targetTypeId, ownerId)
                .filter(binding -> binding.getStatus() == OntologyObjectBindingEntity.BindingStatus.ACTIVE)
                .map(OntologyObjectBindingEntity::getCurrentIndexVersionId)
                .orElse(null);
    }

    private boolean targetExists(UUID versionId, UUID objectTypeId, String primaryKey) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM data.ontology_object_instance
                WHERE version_id = ? AND object_type_id = ? AND primary_key_value = ?
                """, Integer.class, versionId, objectTypeId, primaryKey);
        return count != null && count > 0;
    }
}
