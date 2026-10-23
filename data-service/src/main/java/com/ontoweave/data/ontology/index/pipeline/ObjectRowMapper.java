package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.iceberg.ColumnValueReader;
import com.ontoweave.data.ontology.index.IndexBuildException;
import com.ontoweave.data.ontology.index.IndexFailureCode;
import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import org.apache.iceberg.data.Record;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class ObjectRowMapper {

    private final ColumnValueReader reader = new ColumnValueReader();
    private final ObjectMapper mapper;

    public ObjectRowMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectStoreWriter.ObjectStageRow map(
            Record record,
            UUID objectTypeId,
            MappingSnapshot mapping
    ) {
        MappingSnapshot.AttributeMapping primary = attribute(mapping.primaryKeyAttributeId(), mapping);
        if (primary == null) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "primary key attribute is not mapped"
            );
        }
        Object primaryRaw = read(record, primary.column());
        String primaryKey = canonicalKey(primaryRaw, primary.datatype());
        Object primaryValue = IndexValueCodec.convert(
                primaryRaw, primary.datatype(), primary.enumValues());

        ObjectNode properties = mapper.createObjectNode();
        List<ObjectStoreWriter.ObjectValueStageRow> values = new ArrayList<>();
        Object titleValue = null;
        for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
            Object raw = read(record, attribute.column());
            Object value = IndexValueCodec.convert(
                    raw, attribute.datatype(), attribute.enumValues());
            if (value != null) {
                properties.set(attribute.attributeId().toString(), mapper.valueToTree(value));
                values.add(valueRow(attribute.attributeId(), primaryKey, attribute.datatype(), value));
            }
            if (attribute.attributeId().equals(mapping.titleKeyAttributeId())) {
                titleValue = value;
            }
        }
        String title = IndexValueCodec.title(titleValue);
        if (title == null || title.isBlank()) {
            title = primaryKey;
        }
        return new ObjectStoreWriter.ObjectStageRow(
                objectTypeId,
                primaryKey,
                primaryValue instanceof BigDecimal decimal
                        && IndexValueCodec.isNumeric(primary.datatype()) ? decimal : null,
                primaryValue instanceof LocalDate date ? date : null,
                primaryValue instanceof Instant instant ? instant : null,
                title,
                properties,
                values
        );
    }

    private MappingSnapshot.AttributeMapping attribute(UUID id, MappingSnapshot mapping) {
        if (id == null) {
            return null;
        }
        return mapping.attributes().stream()
                .filter(attribute -> id.equals(attribute.attributeId()))
                .findFirst()
                .orElse(null);
    }

    private String canonicalKey(Object value, String datatype) {
        try {
            return IndexValueCodec.canonicalKey(value, datatype);
        } catch (IndexBuildException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IndexBuildException(
                    IndexFailureCode.TYPE_CONVERSION_FAILED,
                    "primary key does not match datatype " + datatype,
                    java.util.Map.of(),
                    exception
            );
        }
    }

    private Object read(Record record, String column) {
        if (column == null || column.isBlank()) {
            throw new IndexBuildException(
                    IndexFailureCode.MAPPING_INVALID,
                    "mapped column is missing"
            );
        }
        try {
            return reader.read(record, column);
        } catch (RuntimeException exception) {
            throw new IndexBuildException(
                    IndexFailureCode.TYPE_CONVERSION_FAILED,
                    "column is unavailable: " + column,
                    java.util.Map.of("column", column),
                    exception
            );
        }
    }

    private ObjectStoreWriter.ObjectValueStageRow valueRow(
            UUID attributeId,
            String primaryKey,
            String datatype,
            Object value
    ) {
        String type = datatype == null ? "" : datatype.toLowerCase(Locale.ROOT);
        return new ObjectStoreWriter.ObjectValueStageRow(
                attributeId,
                primaryKey,
                IndexValueCodec.isText(type) ? value.toString() : null,
                value instanceof BigDecimal decimal && IndexValueCodec.isNumeric(type) ? decimal : null,
                value instanceof LocalDate date ? date : null,
                value instanceof Instant instant ? instant : null,
                value instanceof Boolean flag ? flag : null
        );
    }
}
