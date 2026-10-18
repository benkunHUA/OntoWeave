package com.ontoweave.data.sync.iceberg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class HadoopIcebergCatalogClient implements IcebergCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(HadoopIcebergCatalogClient.class);

    private final HadoopCatalogFactory catalogs;
    private final ObjectMapper objectMapper;

    public HadoopIcebergCatalogClient(HadoopCatalogFactory catalogs, ObjectMapper objectMapper) {
        this.catalogs = catalogs;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<IcebergSnapshotInfo> latestSnapshot(String catalog, String namespace, String table) {
        return catalogs.withTable(namespace, table, current -> {
            current.refresh();
            Snapshot snapshot = current.currentSnapshot();
            return snapshot == null ? null : info(current, snapshot);
        });
    }

    @Override
    public Optional<IcebergSnapshotInfo> createEmptySnapshot(String catalog, String namespace, String table) {
        return catalogs.withTable(namespace, table, current -> {
            current.refresh();
            if (current.currentSnapshot() != null) {
                return info(current, current.currentSnapshot());
            }
            current.newAppend().commit();
            current.refresh();
            return info(current, current.currentSnapshot());
        });
    }

    @Override
    public void createTag(String catalog, String namespace, String table, long snapshotId, String tag) {
        catalogs.withTable(namespace, table, current -> {
            current.refresh();
            try {
                current.manageSnapshots().replaceTag(tag, snapshotId).commit();
            } catch (IllegalArgumentException exception) {
                current.manageSnapshots().createTag(tag, snapshotId).commit();
            }
            return Optional.empty();
        });
    }

    @Override
    public void removeTag(String catalog, String namespace, String table, String tag) {
        catalogs.withTable(namespace, table, current -> {
            current.refresh();
            try {
                current.manageSnapshots().removeTag(tag).commit();
            } catch (RuntimeException ignored) {
                // The tag may not exist; cleanup is idempotent.
            }
            return Optional.empty();
        });
    }

    @Override
    public void dropTable(String catalog, String namespace, String table) {
        try (HadoopCatalog hadoopCatalog = catalogs.open()) {
            hadoopCatalog.dropTable(TableIdentifier.of(namespace, table), true);
        } catch (Exception exception) {
            log.warn("failed to drop iceberg table {}.{}", namespace, table, exception);
        }
    }

    private IcebergSnapshotInfo info(Table table, Snapshot snapshot) {
        Map<String, String> summary = snapshot.summary();
        Schema schema = table.schemas().get((int) snapshot.schemaId());
        if (schema == null) {
            schema = table.schema();
        }
        return new IcebergSnapshotInfo(
                snapshot.snapshotId(),
                Instant.ofEpochMilli(snapshot.timestampMillis()),
                parseLong(summary.get(SnapshotSummary.TOTAL_RECORDS_PROP)),
                parseLong(summary.get(SnapshotSummary.TOTAL_FILE_SIZE_PROP)),
                schemaJson(schema),
                schema.columns().size(),
                table.location()
        );
    }

    private JsonNode schemaJson(Schema schema) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode fields = root.putArray("fields");
        for (Types.NestedField field : schema.columns()) {
            ObjectNode item = fields.addObject();
            item.put("name", field.name());
            item.put("type", field.type().toString());
            item.put("required", field.isRequired());
            item.put("doc", field.doc());
        }
        return root;
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
