package com.ontoweave.data.sync.iceberg;

import com.ontoweave.data.common.config.SyncProperties;
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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HadoopIcebergPreviewReaderTest {

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get()),
            Types.NestedField.optional(2, "name", Types.StringType.get())
    );

    @TempDir
    Path warehouse;

    @Test
    void readsRowsFromTheRequestedSnapshot() throws Exception {
        HadoopCatalogFactory factory = factory();
        try (HadoopCatalog catalog = factory.open()) {
            Table table = catalog.createTable(TableIdentifier.of("ns", "customers"), SCHEMA);
            append(table, 1L, "Ada");
            append(table, 2L, "Grace");

            Optional<IcebergPreview> preview = reader(factory).read(
                    "ontoweave",
                    "ns",
                    "customers",
                    table.currentSnapshot().snapshotId(),
                    10
            );

            assertThat(preview).isPresent();
            assertThat(preview.get().columns()).containsExactly("id", "name");
            assertThat(preview.get().rows()).containsExactlyInAnyOrder(
                    Map.of("id", 1L, "name", "Ada"),
                    Map.of("id", 2L, "name", "Grace")
            );
            assertThat(preview.get().truncated()).isFalse();
        }
    }

    @Test
    void marksPreviewAsTruncatedWhenMoreRowsExist() throws Exception {
        HadoopCatalogFactory factory = factory();
        try (HadoopCatalog catalog = factory.open()) {
            Table table = catalog.createTable(TableIdentifier.of("ns", "customers"), SCHEMA);
            append(table, 1L, "Ada");
            append(table, 2L, "Grace");

            IcebergPreview preview = reader(factory).read(
                    "ontoweave",
                    "ns",
                    "customers",
                    table.currentSnapshot().snapshotId(),
                    1
            ).orElseThrow();

            assertThat(preview.rows()).hasSize(1);
            assertThat(preview.rows().get(0).get("id")).isIn(1L, 2L);
            assertThat(preview.truncated()).isTrue();
        }
    }

    @Test
    void returnsEmptyPreviewForEmptySnapshot() throws Exception {
        HadoopCatalogFactory factory = factory();
        try (HadoopCatalog catalog = factory.open()) {
            Table table = catalog.createTable(TableIdentifier.of("ns", "customers"), SCHEMA);
            table.newAppend().commit();

            IcebergPreview preview = reader(factory).read(
                    "ontoweave",
                    "ns",
                    "customers",
                    table.currentSnapshot().snapshotId(),
                    100
            ).orElseThrow();

            assertThat(preview.rows()).isEmpty();
            assertThat(preview.truncated()).isFalse();
        }
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
                0
        ));
    }

    private HadoopIcebergPreviewReader reader(HadoopCatalogFactory factory) {
        return new HadoopIcebergPreviewReader(factory);
    }

    private void append(Table table, long id, String name) throws Exception {
        GenericRecord record = GenericRecord.create(SCHEMA);
        record.setField("id", id);
        record.setField("name", name);
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
    }
}
