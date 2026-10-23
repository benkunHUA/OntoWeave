package com.ontoweave.data.ontology.foundation;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.iceberg.ColumnValueReader;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.sync.iceberg.HadoopCatalogFactory;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.encryption.EncryptionUtil;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IcebergSnapshotReaderTest {

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get()),
            Types.NestedField.optional(2, "name", Types.StringType.get()),
            Types.NestedField.optional(3, "amount", Types.DecimalType.of(10, 2)),
            Types.NestedField.optional(4, "day", Types.DateType.get()),
            Types.NestedField.optional(5, "seen_at", Types.TimestampType.withZone())
    );

    @TempDir
    Path warehouse;

    @Test
    void readsTheRequestedSnapshotRatherThanTheLatestOne() throws Exception {
        HadoopCatalogFactory factory = factory();
        IcebergSnapshotReader reader = new IcebergSnapshotReader(factory);
        try (HadoopCatalog catalog = factory.open()) {
            Table table = catalog.createTable(TableIdentifier.of("ns", "customers"), SCHEMA);
            long first = append(table, 1L, "Ada", "12.50",
                    LocalDate.of(2026, 10, 23), Instant.parse("2026-10-23T00:00:00Z"));
            append(table, 2L, "Grace", "20.00",
                    LocalDate.of(2026, 10, 24), Instant.parse("2026-10-24T00:00:00Z"));

            DatasetRef ref = ref("customers");
            List<Record> rows = new ArrayList<>();
            try (IcebergSnapshotReader.SnapshotRead read = reader.open(ref, first)) {
                read.records().forEach(rows::add);
            }

            assertThat(rows).hasSize(1);
            ColumnValueReader values = new ColumnValueReader();
            assertThat(values.read(rows.get(0), "id")).isEqualTo(1L);
            assertThat(values.read(rows.get(0), "name")).isEqualTo("Ada");
            assertThat(values.read(rows.get(0), "amount")).isEqualTo(new BigDecimal("12.50"));
            assertThat(values.read(rows.get(0), "day")).isEqualTo(LocalDate.of(2026, 10, 23));
            assertThat(values.read(rows.get(0), "seen_at"))
                    .isEqualTo(Instant.parse("2026-10-23T00:00:00Z"));
        }
    }

    private DatasetRef ref(String table) {
        return new DatasetRef(
                DatasetKind.SYNC,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ontoweave",
                "ns",
                table,
                1L,
                JsonNodeFactory.instance.objectNode(),
                UUID.randomUUID(),
                1L
        );
    }

    private HadoopCatalogFactory factory() {
        return new HadoopCatalogFactory(new SyncProperties(
                null,
                null,
                null,
                null,
                warehouse.toString(),
                null,
                null,
                0,
                0,
                null,
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                4,
                1
        ));
    }

    private long append(
            Table table,
            long id,
            String name,
            String amount,
            LocalDate day,
            Instant seenAt
    ) throws Exception {
        GenericRecord record = GenericRecord.create(SCHEMA);
        record.setField("id", id);
        record.setField("name", name);
        record.setField("amount", new BigDecimal(amount));
        record.setField("day", day);
        record.setField("seen_at", OffsetDateTime.ofInstant(seenAt, ZoneOffset.UTC));
        OutputFile outputFile = table.io().newOutputFile(
                table.location() + "/data/" + UUID.randomUUID() + ".parquet"
        );
        DataWriter<Record> writer = new GenericAppenderFactory(SCHEMA).newDataWriter(
                EncryptionUtil.plainAsEncryptedOutput(outputFile),
                FileFormat.PARQUET,
                null
        );
        writer.write(record);
        writer.close();
        table.newAppend().appendFile(writer.toDataFile()).commit();
        return table.currentSnapshot().snapshotId();
    }
}
