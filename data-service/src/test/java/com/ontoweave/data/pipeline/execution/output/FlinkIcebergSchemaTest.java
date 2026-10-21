package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.inmemory.InMemoryCatalog;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlinkIcebergSchemaTest {

    @TempDir
    Path tempDir;

    @Test
    void keepsWiderPhysicalColumnsAndOnlyAddsOrWidens() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.required(1, "id", Types.IntegerType.get())
                    )
            );

            FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id", "failed_extra"},
                    new DataType[]{DataTypes.BIGINT(), DataTypes.STRING()}
            ));
            table.refresh();
            assertThat(table.schema().findType("id").toString()).isEqualTo("long");
            assertThat(table.schema().findField("failed_extra")).isNotNull();

            FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.INT()}
            ));

            table.refresh();
            assertThat(table.schema().findType("id").toString()).isEqualTo("long");
            assertThat(table.schema().findField("failed_extra")).isNotNull();
        }
    }

    @Test
    void rejectsPhysicalTypesThatCannotAcceptThePlannedType() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.required(1, "id", Types.StringType.get())
                    )
            );

            assertThatThrownBy(() -> FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.BIGINT()}
            ))).isInstanceOfSatisfying(PipelineBuildException.class, error ->
                    assertThat(error.errorCode())
                            .isEqualTo(PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED));
        }
    }

    @Test
    void relaxesPublishedRequiredColumnsToOptionalInThePhysicalTable() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.required(1, "id", Types.IntegerType.get())
                    )
            );

            FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.INT()}
            ));

            table.refresh();
            assertThat(table.schema().findField("id").isOptional()).isTrue();
        }
    }

    @Test
    void requiredOptionalRelaxationAcceptsLaterRequiredPlansIdempotently() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.required(1, "id", Types.IntegerType.get())
                    )
            );

            FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.INT()}
            ));
            FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.INT().notNull()}
            ));

            table.refresh();
            assertThat(table.schema().findField("id").isOptional()).isTrue();
        }
    }

    @Test
    void addingRequiredColumnIsIdempotentWhenTableHasNoSnapshots() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.required(1, "id", Types.IntegerType.get())
                    )
            );
            ResolvedSchema planned = schema(
                    new String[]{"id", "name"},
                    new DataType[]{DataTypes.INT(), DataTypes.STRING().notNull()}
            );

            FlinkIcebergSchema.apply(table, planned);
            FlinkIcebergSchema.apply(table, planned);

            table.refresh();
            assertThat(table.schema().findField("name").isRequired()).isTrue();
        }
    }

    @Test
    void addingRequiredColumnToTableWithSnapshotsFallsBackToOptionalIdempotently()
            throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.required(1, "id", Types.IntegerType.get())
                    )
            );
            DataFile dataFile = DataFiles.builder(table.spec())
                    .withPath(tempDir.resolve("data.parquet").toString())
                    .withFormat(FileFormat.PARQUET)
                    .withFileSizeInBytes(1)
                    .withRecordCount(1)
                    .build();
            table.newAppend().appendFile(dataFile).commit();
            ResolvedSchema planned = schema(
                    new String[]{"id", "name"},
                    new DataType[]{DataTypes.INT(), DataTypes.STRING().notNull()}
            );

            FlinkIcebergSchema.apply(table, planned);
            FlinkIcebergSchema.apply(table, planned);

            table.refresh();
            assertThat(table.schema().findField("name").isOptional()).isTrue();
        }
    }

    @Test
    void optionalToRequiredLogicalNarrowingIsRejectedBySchemaCompatibility() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.optional(1, "id", Types.IntegerType.get())
                    )
            );
            ResolvedSchema required = schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.INT().notNull()}
            );
            ObjectMapper mapper = new ObjectMapper();

            FlinkIcebergSchema.apply(table, required);

            assertThatThrownBy(() -> OutputSchemaCompatibility.assertCompatible(
                    OutputSchemaJson.from(mapper, table.schema()),
                    OutputSchemaJson.from(mapper, required)
            )).isInstanceOfSatisfying(PipelineBuildException.class, error ->
                    assertThat(error.errorCode())
                            .isEqualTo(PipelineBuildConstants.CODE_OUTPUT_SCHEMA_CHANGED));
        }
    }

    private ResolvedSchema schema(String[] names, DataType[] types) {
        return ResolvedSchema.physical(names, types);
    }

    private InMemoryCatalog catalog() {
        InMemoryCatalog catalog = new InMemoryCatalog();
        catalog.initialize("test", Map.of());
        catalog.createNamespace(Namespace.of("pipeline"));
        return catalog;
    }
}
