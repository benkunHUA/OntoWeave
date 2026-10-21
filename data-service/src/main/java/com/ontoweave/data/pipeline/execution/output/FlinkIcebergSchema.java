package com.ontoweave.data.pipeline.execution.output;

import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.BigIntType;
import org.apache.flink.table.types.logical.BinaryType;
import org.apache.flink.table.types.logical.BooleanType;
import org.apache.flink.table.types.logical.CharType;
import org.apache.flink.table.types.logical.DateType;
import org.apache.flink.table.types.logical.DecimalType;
import org.apache.flink.table.types.logical.DoubleType;
import org.apache.flink.table.types.logical.FloatType;
import org.apache.flink.table.types.logical.IntType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.SmallIntType;
import org.apache.flink.table.types.logical.TimeType;
import org.apache.flink.table.types.logical.TimestampType;
import org.apache.flink.table.types.logical.TinyIntType;
import org.apache.flink.table.types.logical.VarBinaryType;
import org.apache.flink.table.types.logical.VarCharType;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

import java.util.ArrayList;
import java.util.List;

public final class FlinkIcebergSchema {

    private FlinkIcebergSchema() {
    }

    public static Schema icebergSchema(ResolvedSchema schema) {
        List<Types.NestedField> fields = new ArrayList<>();
        List<String> names = schema.getColumnNames();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name.startsWith("__ontoweave_")) {
                continue;
            }
            DataType dataType = schema.getColumnDataTypes().get(index);
            fields.add(field(fields.size() + 1, name, dataType));
        }
        return new Schema(fields);
    }

    public static String canonicalTypeName(LogicalType logical) {
        return type(logical).toString();
    }

    public static void apply(Table table, ResolvedSchema schema) {
        table.refresh();
        org.apache.iceberg.UpdateSchema update = table.updateSchema();
        boolean changed = false;
        List<String> names = schema.getColumnNames();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name.startsWith("__ontoweave_")) {
                continue;
            }
            DataType dataType = schema.getColumnDataTypes().get(index);
            Type.PrimitiveType type = type(dataType.getLogicalType());
            Types.NestedField existing = table.schema().findField(name);
            if (existing == null) {
                update.addColumn(name, type);
                changed = true;
            } else if (!existing.type().equals(type)) {
                update.updateColumn(name, type);
                changed = true;
            }
        }
        if (changed) {
            update.commit();
        }
    }

    private static Types.NestedField field(int id, String name, DataType dataType) {
        Type.PrimitiveType type = type(dataType.getLogicalType());
        return dataType.getLogicalType().isNullable()
                ? Types.NestedField.optional(id, name, type)
                : Types.NestedField.required(id, name, type);
    }

    private static Type.PrimitiveType type(LogicalType logical) {
        if (logical instanceof BooleanType) {
            return Types.BooleanType.get();
        }
        if (logical instanceof TinyIntType || logical instanceof SmallIntType
                || logical instanceof IntType) {
            return Types.IntegerType.get();
        }
        if (logical instanceof BigIntType) {
            return Types.LongType.get();
        }
        if (logical instanceof FloatType) {
            return Types.FloatType.get();
        }
        if (logical instanceof DoubleType) {
            return Types.DoubleType.get();
        }
        if (logical instanceof DecimalType decimal) {
            return Types.DecimalType.of(decimal.getPrecision(), decimal.getScale());
        }
        if (logical instanceof CharType || logical instanceof VarCharType) {
            return Types.StringType.get();
        }
        if (logical instanceof BinaryType || logical instanceof VarBinaryType) {
            return Types.BinaryType.get();
        }
        if (logical instanceof DateType) {
            return Types.DateType.get();
        }
        if (logical instanceof TimeType) {
            return Types.TimeType.get();
        }
        if (logical instanceof TimestampType) {
            return logical.getTypeRoot() == LogicalTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE
                    ? Types.TimestampType.withZone()
                    : Types.TimestampType.withoutZone();
        }
        throw new IllegalArgumentException("Unsupported output type: " + logical);
    }
}
