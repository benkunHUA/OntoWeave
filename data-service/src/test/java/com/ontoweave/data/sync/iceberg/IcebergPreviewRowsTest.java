package com.ontoweave.data.sync.iceberg;

import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcebergPreviewRowsTest {

    private static final Schema SCHEMA = new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get())
    );

    @Test
    void stopsAfterTheCurrentRecordWhenTheThreadIsInterrupted() {
        AtomicInteger nextCalls = new AtomicInteger();
        Iterable<Record> records = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Record next() {
                nextCalls.incrementAndGet();
                Thread.currentThread().interrupt();
                GenericRecord record = GenericRecord.create(SCHEMA);
                record.setField("id", 1L);
                return record;
            }
        };

        try {
            assertThatThrownBy(() -> IcebergPreviewRows.read(
                    records, List.of(new PreviewColumn("id", "long", false)), 100
            )).isInstanceOf(CancellationException.class);
            assertThat(nextCalls).hasValue(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void doesNotStartReadingWhenAlreadyInterrupted() {
        AtomicInteger nextCalls = new AtomicInteger();
        Iterable<Record> records = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Record next() {
                nextCalls.incrementAndGet();
                return GenericRecord.create(SCHEMA);
            }
        };

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> IcebergPreviewRows.read(
                    records, List.of(new PreviewColumn("id", "long", false)), 100
            )).isInstanceOf(CancellationException.class);
            assertThat(nextCalls).hasValue(0);
        } finally {
            Thread.interrupted();
        }
    }
}
