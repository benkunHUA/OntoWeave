package com.ontoweave.data.ontology.acceptance;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.common.RunStatus;
import com.ontoweave.data.ontology.domain.OntologyIndexRunEntity;
import com.ontoweave.data.ontology.iceberg.IcebergSnapshotReader;
import com.ontoweave.data.ontology.index.OntologyIndexProperties;
import com.ontoweave.data.ontology.index.pipeline.ObjectIndexPipeline;
import com.ontoweave.data.ontology.index.pipeline.ObjectRowMapper;
import com.ontoweave.data.ontology.index.pipeline.ObjectStageValidator;
import com.ontoweave.data.ontology.index.pipeline.RelationIndexBuilder;
import com.ontoweave.data.ontology.index.pipeline.RelationRunPipeline;
import com.ontoweave.data.ontology.index.queue.RunDatasetResolver;
import com.ontoweave.data.ontology.index.run.OntologyRunStateStore;
import com.ontoweave.data.ontology.index.store.JdbcObjectStoreWriter;
import com.ontoweave.data.ontology.index.switching.IndexSwitcher;
import com.ontoweave.data.ontology.index.worker.OntologyIndexExecutor;
import com.ontoweave.data.ontology.query.filter.FilterCondition;
import com.ontoweave.data.ontology.store.ObjectStoreWriter;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.CloseableIterable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "p3.performance", matches = "true")
class P3PerformanceAcceptanceTest extends P3AcceptanceSupport {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ORDER_NO = UUID.randomUUID();
    private static final UUID ORDER_STATUS = UUID.randomUUID();
    private static final UUID ORDER_AMOUNT = UUID.randomUUID();
    private static final UUID ORDER_CREATED_ON = UUID.randomUUID();
    private static final UUID ORDER_PRIORITY = UUID.randomUUID();
    private static final UUID ORDER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_NOTES = UUID.randomUUID();
    private static final List<Integer> BATCH_SIZES = List.of(1_000, 10_000, 50_000);

    @MockitoBean
    private IcebergSnapshotReader snapshots;

    @Autowired
    private RunDatasetResolver datasets;

    @Autowired
    private ObjectRowMapper rowMapper;

    @Autowired
    private ObjectStageValidator stageValidator;

    @Autowired
    private RelationIndexBuilder relations;

    @Autowired
    private RelationRunPipeline relationPipeline;

    @Autowired
    private IndexSwitcher switcher;

    @Autowired
    private OntologyRunStateStore stateStore;

    @Test
    void measuresBatchSizesAndQueryLatency() throws Exception {
        long rows = requestedRows();
        UUID ownerId = UUID.randomUUID();
        UUID objectTypeId = UUID.randomUUID();
        String mapping = mapper.writeValueAsString(orderMapping());
        List<P3PerformanceResult> results = new ArrayList<>();

        for (int batchSize : BATCH_SIZES) {
            truncateOntology();
            DatasetFixture dataset = createDataset(
                    ownerId,
                    "p3_perf_orders_" + batchSize,
                    2_000L + batchSize,
                    Math.toIntExact(rows),
                    8
            );
            ObjectBindingFixture binding = createObjectBinding(
                    ownerId, objectTypeId, dataset, mapping);
            OntologyIndexRunEntity run = insertRunningObjectRun(
                    ownerId, objectTypeId, binding.bindingId(), dataset.versionId(), mapping);

            AtomicLong readNanos = new AtomicLong();
            when(snapshots.open(any(), anyLong())).thenAnswer(invocation ->
                    new IcebergSnapshotReader.SnapshotRead(
                            P3SnapshotData.ORDER_SCHEMA,
                            timedOrders(rows, readNanos)
                    ));

            OntologyIndexProperties properties = properties(batchSize);
            P3TimedObjectStoreWriter timedWriter = new P3TimedObjectStoreWriter(
                    new JdbcObjectStoreWriter(jdbc, properties));
            ObjectIndexPipeline pipeline = new ObjectIndexPipeline(
                    datasets,
                    snapshots,
                    rowMapper,
                    timedWriter,
                    stageValidator,
                    relations,
                    stateStore,
                    properties,
                    mapper,
                    jdbc
            );
            OntologyIndexExecutor perfExecutor = new OntologyIndexExecutor(
                    pipeline, relationPipeline, switcher, stateStore);

            P3PerformanceMetrics.HeapSampler heap = new P3PerformanceMetrics.HeapSampler();
            long start = System.nanoTime();
            heap.start();
            perfExecutor.execute(run, WORKER);
            long totalNanos = System.nanoTime() - start;
            heap.close();

            OntologyIndexRunEntity stored = runs.findById(run.getId()).orElseThrow();
            assertThat(stored.getStatus())
                    .withFailMessage("batch %s failed: %s %s", batchSize,
                            stored.getErrorCode(), stored.getErrorMessage())
                    .isEqualTo(RunStatus.SUCCEEDED);
            UUID versionId = currentObjectVersion(binding.bindingId());
            assertThat(versionId).isNotNull();

            long instanceRows = count("""
                    SELECT count(*) FROM data.ontology_object_instance WHERE version_id = ?
                    """, versionId);
            long valueRows = count("""
                    SELECT count(*) FROM data.ontology_object_value WHERE version_id = ?
                    """, versionId);
            assertThat(instanceRows).isEqualTo(rows);
            assertThat(valueRows).isEqualTo(rows * 8 - rows / 10);
            assertThat(count("""
                    SELECT count(*) FROM data.ontology_object_stage WHERE run_id = ?
                    """, run.getId())).isZero();

            List<Long> listLatencies = new ArrayList<>();
            for (int iteration = 0; iteration < 23; iteration++) {
                long queryStart = System.nanoTime();
                objectQueries.list(objectTypeId, ownerId, 1, 20, null, List.of());
                if (iteration >= 3) {
                    listLatencies.add(System.nanoTime() - queryStart);
                }
            }

            List<FilterCondition> filters = threeConditions();
            long[] filterRows = new long[1];
            List<Long> filterLatencies = new ArrayList<>();
            for (int iteration = 0; iteration < 23; iteration++) {
                long queryStart = System.nanoTime();
                filterRows[0] = objectQueries.list(
                        objectTypeId, ownerId, 1, 20, null, filters).total();
                if (iteration >= 3) {
                    filterLatencies.add(System.nanoTime() - queryStart);
                }
            }
            long expectedFilterRows = LongStream.rangeClosed(1, rows)
                    .filter(P3SnapshotData::matchesThreeConditionFilter)
                    .count();
            assertThat(filterRows[0]).isEqualTo(expectedFilterRows);

            P3PerformanceResult result = new P3PerformanceResult(
                    batchSize,
                    rows,
                    P3PerformanceMetrics.millis(totalNanos),
                    P3PerformanceMetrics.millis(readNanos.get()),
                    P3PerformanceMetrics.millis(timedWriter.writeNanos()),
                    P3PerformanceMetrics.p95Millis(listLatencies),
                    P3PerformanceMetrics.p95Millis(filterLatencies),
                    filterRows[0],
                    instanceRows,
                    valueRows,
                    heap.peakBytes(),
                    run.getId(),
                    versionId
            );
            results.add(result);
            P3PerformanceSummaryWriter.write(mapper, results, rows);
            P3PerformanceEvidence.log(result);
        }

        Path summary = P3PerformanceSummaryWriter.write(mapper, results, rows);
        System.out.println("P3_PERF summary=" + summary.toAbsolutePath());
        assertThat(results).hasSize(BATCH_SIZES.size());
    }

    private long requestedRows() {
        String configured = System.getProperty("p3.performance.rows", "500000");
        long rows = Long.parseLong(configured);
        assertThat(rows).isPositive();
        return rows;
    }

    private OntologyIndexProperties properties(int batchSize) {
        return new OntologyIndexProperties(
                false,
                1_000L,
                0L,
                Duration.ofSeconds(10),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                1,
                batchSize,
                false,
                0L,
                30_000L,
                Duration.ofMinutes(5),
                5_000,
                20
        );
    }

    private CloseableIterable<Record> timedOrders(long rows, AtomicLong readNanos) {
        Iterable<Record> iterable = () -> new Iterator<>() {
            private final Iterator<Record> delegate = P3SnapshotData.orders(rows, false).iterator();

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public Record next() {
                long start = System.nanoTime();
                try {
                    return delegate.next();
                } finally {
                    readNanos.addAndGet(System.nanoTime() - start);
                }
            }
        };
        return CloseableIterable.withNoopClose(iterable);
    }

    private MappingSnapshot orderMapping() {
        List<MappingSnapshot.AttributeMapping> attributes = List.of(
                attribute(ORDER_ID, "id", "integer", true, null),
                attribute(ORDER_NO, "order_no", "text", true, null),
                attribute(ORDER_STATUS, "status", "enum", true,
                        List.of("PAID", "PENDING", "CANCELLED")),
                attribute(ORDER_AMOUNT, "amount", "decimal", true, null),
                attribute(ORDER_CREATED_ON, "created_on", "date", true, null),
                attribute(ORDER_PRIORITY, "is_priority", "boolean", true, null),
                attribute(ORDER_CUSTOMER_ID, "customer_id", "integer", true, null),
                attribute(ORDER_NOTES, "notes", "text", false, null)
        );
        return MappingSnapshot.object(
                ORDER_ID,
                ORDER_NO,
                attributes,
                1,
                attributes.stream().filter(MappingSnapshot.AttributeMapping::required)
                        .map(MappingSnapshot.AttributeMapping::attributeId)
                        .toList()
        );
    }

    private MappingSnapshot.AttributeMapping attribute(
            UUID id,
            String column,
            String datatype,
            boolean required,
            List<String> enumValues
    ) {
        return new MappingSnapshot.AttributeMapping(
                id, column, datatype, required, false, enumValues);
    }

    private List<FilterCondition> threeConditions() {
        return List.of(
                filter("eq", ORDER_STATUS, "PAID"),
                filter("gte", ORDER_AMOUNT, new BigDecimal("250")),
                filter("eq", ORDER_PRIORITY, true)
        );
    }

    private FilterCondition filter(String operation, UUID attributeId, Object value) {
        return new FilterCondition(attributeId.toString(), operation, mapper.valueToTree(value));
    }
}
