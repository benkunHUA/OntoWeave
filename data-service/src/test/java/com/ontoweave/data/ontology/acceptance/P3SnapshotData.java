package com.ontoweave.data.ontology.acceptance;

import com.ontoweave.data.ontology.dataset.DatasetRef;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.types.Types;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.function.LongFunction;

public final class P3SnapshotData {

    public static final String CUSTOMER_TABLE = "p3_customer";
    public static final String ORDER_TABLE = "p3_orders";
    public static final String PRODUCT_TABLE = "p3_product";
    public static final String ORDER_ITEM_TABLE = "p3_order_item";

    public static final long ORDER_V1_SNAPSHOT = 1_001L;
    public static final long ORDER_V2_SNAPSHOT = 1_002L;
    public static final long ORDER_DUPLICATE_SNAPSHOT = 1_003L;

    public static final int CUSTOMER_ROWS = 2_000;
    public static final int PRODUCT_ROWS = 2_000;
    public static final int ORDER_V1_ROWS = 20_000;
    public static final int ORDER_V2_ROWS = 20_001;
    public static final int ORDER_ITEM_ROWS = 20_000;

    public static final Schema ORDER_SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.IntegerType.get()),
            Types.NestedField.required(2, "order_no", Types.StringType.get()),
            Types.NestedField.required(3, "status", Types.StringType.get()),
            Types.NestedField.required(4, "amount", Types.DecimalType.of(12, 2)),
            Types.NestedField.required(5, "created_on", Types.DateType.get()),
            Types.NestedField.required(6, "is_priority", Types.BooleanType.get()),
            Types.NestedField.required(7, "customer_id", Types.IntegerType.get()),
            Types.NestedField.optional(8, "notes", Types.StringType.get())
    );

    public static final Schema CUSTOMER_SCHEMA = new Schema(
            Types.NestedField.required(1, "customer_id", Types.IntegerType.get()),
            Types.NestedField.required(2, "full_name", Types.StringType.get()),
            Types.NestedField.optional(3, "segment", Types.StringType.get())
    );

    public static final Schema PRODUCT_SCHEMA = new Schema(
            Types.NestedField.required(1, "product_id", Types.IntegerType.get()),
            Types.NestedField.required(2, "name", Types.StringType.get())
    );

    public static final Schema ORDER_ITEM_SCHEMA = new Schema(
            Types.NestedField.required(1, "order_id", Types.IntegerType.get()),
            Types.NestedField.required(2, "product_id", Types.IntegerType.get())
    );

    private P3SnapshotData() {
    }

    public static IcebergSnapshotReader.SnapshotRead read(DatasetRef dataset, long snapshotId) {
        return switch (dataset.table()) {
            case ORDER_TABLE -> orderSnapshot(snapshotId);
            case CUSTOMER_TABLE -> new IcebergSnapshotReader.SnapshotRead(
                    CUSTOMER_SCHEMA,
                    customers()
            );
            case PRODUCT_TABLE -> new IcebergSnapshotReader.SnapshotRead(
                    PRODUCT_SCHEMA,
                    products()
            );
            case ORDER_ITEM_TABLE -> new IcebergSnapshotReader.SnapshotRead(
                    ORDER_ITEM_SCHEMA,
                    orderItems()
            );
            default -> throw new IllegalArgumentException("unexpected P3 table: " + dataset.table());
        };
    }

    public static IcebergSnapshotReader.SnapshotRead orderSnapshot(long snapshotId) {
        return switch ((int) snapshotId) {
            case (int) ORDER_V1_SNAPSHOT -> new IcebergSnapshotReader.SnapshotRead(
                    ORDER_SCHEMA,
                    orders(ORDER_V1_ROWS, false)
            );
            case (int) ORDER_V2_SNAPSHOT -> new IcebergSnapshotReader.SnapshotRead(
                    ORDER_SCHEMA,
                    orders(ORDER_V2_ROWS, false)
            );
            case (int) ORDER_DUPLICATE_SNAPSHOT -> new IcebergSnapshotReader.SnapshotRead(
                    ORDER_SCHEMA,
                    orders(ORDER_V2_ROWS, true)
            );
            default -> throw new IllegalArgumentException("unexpected order snapshot: " + snapshotId);
        };
    }

    public static CloseableIterable<Record> orders(long rows, boolean duplicateLast) {
        return lazy(rows, index -> {
            long id = duplicateLast && index == rows - 1 ? rows - 1 : index + 1;
            return orderRecord(id);
        });
    }

    public static CloseableIterable<Record> customers() {
        return lazy(CUSTOMER_ROWS, index -> {
            long id = index + 1;
            Record record = GenericRecord.create(CUSTOMER_SCHEMA);
            record.setField("customer_id", (int) id);
            record.setField("full_name", String.format("Customer %05d", id));
            record.setField("segment", id % 2 == 0 ? "ENTERPRISE" : "SMB");
            return record;
        });
    }

    public static CloseableIterable<Record> products() {
        return lazy(PRODUCT_ROWS, index -> {
            long id = index + 1;
            Record record = GenericRecord.create(PRODUCT_SCHEMA);
            record.setField("product_id", (int) id);
            record.setField("name", String.format("Product %05d", id));
            return record;
        });
    }

    public static CloseableIterable<Record> orderItems() {
        return lazy(ORDER_ITEM_ROWS, index -> {
            long orderId = index % 10_000 + 1;
            long productId = index % 1_000 + 1 + (index / 10_000) * 1_000;
            Record record = GenericRecord.create(ORDER_ITEM_SCHEMA);
            record.setField("order_id", (int) orderId);
            record.setField("product_id", (int) productId);
            return record;
        });
    }

    public static Record orderRecord(long id) {
        Record record = GenericRecord.create(ORDER_SCHEMA);
        record.setField("id", (int) id);
        record.setField("order_no", String.format("Order %05d", id));
        record.setField("status", orderStatus(id));
        record.setField("amount", BigDecimal.valueOf(id % 500).add(new BigDecimal("0.25")));
        record.setField("created_on", LocalDate.of(2026, 1, 1).plusDays(id % 365));
        record.setField("is_priority", id % 3 == 0);
        record.setField("customer_id", (int) ((id - 1) % CUSTOMER_ROWS + 1));
        record.setField("notes", id % 10 == 0 ? null : "note-" + id);
        return record;
    }

    public static String orderStatus(long id) {
        return switch ((int) (id % 4)) {
            case 0 -> "CANCELLED";
            case 2 -> "PENDING";
            default -> "PAID";
        };
    }

    public static boolean matchesThreeConditionFilter(long id) {
        return "PAID".equals(orderStatus(id))
                && id % 500 >= 250
                && id % 3 == 0;
    }

    private static CloseableIterable<Record> lazy(long rows, LongFunction<Record> factory) {
        Iterable<Record> iterable = () -> new Iterator<>() {
            private long cursor;

            @Override
            public boolean hasNext() {
                return cursor < rows;
            }

            @Override
            public Record next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return factory.apply(cursor++);
            }
        };
        return CloseableIterable.withNoopClose(iterable);
    }
}
