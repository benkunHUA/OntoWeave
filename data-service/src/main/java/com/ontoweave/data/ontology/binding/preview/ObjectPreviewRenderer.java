package com.ontoweave.data.ontology.binding.preview;

import com.ontoweave.data.ontology.binding.api.dto.ObjectPreviewResponse;
import com.ontoweave.data.ontology.binding.api.dto.ObjectPreviewRow;
import com.ontoweave.data.ontology.binding.api.dto.PreviewSkip;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.PrimaryKeyCodec;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.iceberg.ColumnValueReader;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.mapping.MappingValidationResult;
import org.apache.iceberg.data.Record;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
final class ObjectPreviewRenderer {

    private final IcebergSnapshotReader reader;
    private final ColumnValueReader values = new ColumnValueReader();

    ObjectPreviewRenderer(IcebergSnapshotReader reader) {
        this.reader = reader;
    }

    ObjectPreviewResponse render(
            DatasetRef dataset,
            MappingSnapshot mapping,
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
        List<ObjectPreviewRow> sample = new ArrayList<>();
        Map<UUID, MappingSnapshot.AttributeMapping> attributes = PreviewSupport.attributes(mapping);
        MappingSnapshot.AttributeMapping primaryKey = attributes.get(mapping.primaryKeyAttributeId());
        Set<String> seenKeys = new HashSet<>();
        try (IcebergSnapshotReader.SnapshotRead read = reader.open(dataset, dataset.snapshotId())) {
            Iterator<Record> records = read.records().iterator();
            int scanned = 0;
            while (records.hasNext() && scanned < limit) {
                Record record = records.next();
                int rowNumber = scanned++;
                Object rawKey = values.read(record, primaryKey.column());
                if (PreviewTypeConverter.isBlank(rawKey)) {
                    PreviewSupport.runtimeBlock(blocking, skipped, PreviewIssueCodes.PRIMARY_KEY_EMPTY,
                            "主键为空", "row-" + rowNumber);
                    continue;
                }
                String primaryKeyValue;
                try {
                    primaryKeyValue = PrimaryKeyCodec.canonicalize(
                            rawKey,
                            primaryKey.datatype(),
                            PreviewTypeConverter.scaleOf(rawKey)
                    );
                } catch (RuntimeException exception) {
                    PreviewSupport.runtimeBlock(blocking, skipped,
                            PreviewIssueCodes.VALUE_CONVERSION_FAILED,
                            "主键值转换失败: " + exception.getMessage(), "row-" + rowNumber);
                    continue;
                }
                if (!seenKeys.add(primaryKeyValue)) {
                    PreviewSupport.runtimeBlock(blocking, skipped,
                            PreviewIssueCodes.PRIMARY_KEY_DUPLICATE, "主键重复", primaryKeyValue);
                    continue;
                }
                Map<String, Object> properties = new LinkedHashMap<>();
                boolean rowValid = true;
                for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
                    Object raw = values.read(record, attribute.column());
                    if (PreviewTypeConverter.isBlank(raw)) {
                        if (attribute.required()) {
                            PreviewSupport.runtimeBlock(blocking, skipped,
                                    PreviewIssueCodes.REQUIRED_VALUE_MISSING,
                                    "必填属性为空", primaryKeyValue);
                            rowValid = false;
                        }
                        continue;
                    }
                    try {
                        Object converted = PreviewTypeConverter.convert(
                                raw, attribute.datatype(), attribute.enumValues()
                        );
                        properties.put(attribute.attributeId().toString(), converted);
                    } catch (RuntimeException exception) {
                        PreviewSupport.runtimeBlock(blocking, skipped,
                                PreviewIssueCodes.VALUE_CONVERSION_FAILED,
                                "属性值转换失败: " + exception.getMessage(), primaryKeyValue);
                        rowValid = false;
                    }
                }
                if (!rowValid) {
                    continue;
                }
                String title = title(record, mapping, properties, primaryKeyValue, warnings);
                sample.add(new ObjectPreviewRow(primaryKeyValue, title, properties));
            }
        }
        return new ObjectPreviewResponse(sample, blocking, warnings, skipped);
    }

    private String title(
            Record record,
            MappingSnapshot mapping,
            Map<String, Object> properties,
            String primaryKey,
            List<MappingValidationResult.MappingIssue> warnings
    ) {
        UUID titleAttributeId = mapping.titleKeyAttributeId();
        if (titleAttributeId == null) {
            return primaryKey;
        }
        MappingSnapshot.AttributeMapping titleAttribute = PreviewSupport.attributes(mapping)
                .get(titleAttributeId);
        if (titleAttribute == null) {
            return primaryKey;
        }
        Object raw = values.read(record, titleAttribute.column());
        if (PreviewTypeConverter.isBlank(raw)) {
            PreviewSupport.warning(warnings, PreviewIssueCodes.TITLE_FALLBACK,
                    "标题为空，回退为主键", primaryKey);
            return primaryKey;
        }
        Object converted = properties.get(titleAttributeId.toString());
        return converted == null ? primaryKey : PreviewTypeConverter.textValue(converted);
    }
}
