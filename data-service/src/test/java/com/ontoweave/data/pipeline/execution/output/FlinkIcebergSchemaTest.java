package com.ontoweave.data.pipeline.execution.output;

import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.inmemory.InMemoryCatalog;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlinkIcebergSchemaTest {

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
    void rejectsNarrowingPhysicalOptionalColumnsToRequired() throws Exception {
        try (InMemoryCatalog catalog = catalog()) {
            Table table = catalog.createTable(
                    TableIdentifier.of("pipeline", "orders"),
                    new org.apache.iceberg.Schema(
                            Types.NestedField.optional(1, "id", Types.IntegerType.get())
                    )
            );

            assertThatThrownBy(() -> FlinkIcebergSchema.apply(table, schema(
                    new String[]{"id"},
                    new DataType[]{DataTypes.INT().notNull()}
            ))).isInstanceOfSatisfying(PipelineBuildException.class, error ->
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
