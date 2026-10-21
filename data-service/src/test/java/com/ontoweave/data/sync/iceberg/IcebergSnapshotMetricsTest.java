package com.ontoweave.data.sync.iceberg;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class IcebergSnapshotMetricsTest {

    private final HadoopCatalogFactory catalogs = mock(HadoopCatalogFactory.class);
    private final IcebergSnapshotMetrics metrics = new IcebergSnapshotMetrics(catalogs);

    @Test
    void logsAndReturnsEmptyWhenMetadataCannotBeRead() {
        Logger logger = (Logger) LoggerFactory.getLogger(IcebergSnapshotMetrics.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            doThrow(new IcebergCatalogException("metadata unavailable", new RuntimeException("boom")))
                    .when(catalogs).withTable(anyString(), anyString(), any());

            assertThat(metrics.recordCount("ontoweave", "sales", "orders", 100L)).isEmpty();

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("sales.orders");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
