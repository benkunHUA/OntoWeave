package com.ontoweave.data.pipeline;

import com.ontoweave.data.pipeline.domain.PipelineScheduleEntity;
import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;
import com.ontoweave.data.pipeline.execution.build.PipelineInputResolver;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.schedule.PipelineScheduleClaimer;
import com.ontoweave.data.pipeline.schedule.PipelineScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

abstract class PipelineScheduleIntegrationTestSupport extends PipelineBuildIntegrationTestSupport {

    @Autowired
    protected PipelineScheduleService schedules;

    @Autowired
    protected PipelineScheduleClaimer claimer;

    @MockitoBean
    protected PipelineInputResolver inputs;

    protected UUID ownerId;

    @BeforeEach
    void initializeScheduleFixtures() {
        ownerId = UUID.randomUUID();
        when(inputs.resolve(any(PipelineVersionEntity.class))).thenReturn(List.of());
    }

    protected PublishedPipeline publishedPipeline() {
        UUID pipelineId = insertPipeline(ownerId);
        String definitionHash = "definition-" + UUID.randomUUID();
        UUID versionId = insertVersion(
                pipelineId, 1, mapper.createObjectNode(), mapper.createArrayNode()
        );
        jdbc.update(
                "UPDATE data.pipeline_version SET definition_hash = ? WHERE id = ?",
                definitionHash, versionId
        );
        jdbc.update("""
                UPDATE data.pipeline
                SET status = 'active', current_version_id = ?
                WHERE id = ?
                """, versionId, pipelineId);
        return new PublishedPipeline(pipelineId, versionId, definitionHash);
    }

    protected UUID insertSchedule(UUID pipelineId, Instant nextRunAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data.pipeline_schedule
                    (id, pipeline_id, name, status, trigger_type, cron_expression, timezone,
                     next_run_at, created_by)
                VALUES (?, ?, 'test schedule', 'active', 'time', '* * * * *', 'UTC', ?, ?)
                """, id, pipelineId, timestamp(nextRunAt), ownerId);
        return id;
    }

    protected void insertRun(
            UUID scheduleId,
            UUID pipelineId,
            String scheduledFor,
            String status
    ) {
        jdbc.update("""
                INSERT INTO data.pipeline_schedule_run
                    (id, schedule_id, pipeline_id, status, trigger_type, scheduled_for, resolved_inputs)
                VALUES (?, ?, ?, ?, 'time', ?, CAST('[]' AS jsonb))
                """, UUID.randomUUID(), scheduleId, pipelineId, status,
                timestamp(Instant.parse(scheduledFor)));
    }

    protected PipelineScheduleEntity schedule(UUID scheduleId) {
        return jdbc.queryForObject(
                "SELECT * FROM data.pipeline_schedule WHERE id = ?",
                (resultSet, rowNum) -> new PipelineScheduleEntity(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("pipeline_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("cron_expression"),
                        resultSet.getString("timezone"),
                        resultSet.getObject("next_run_at", OffsetDateTime.class).toInstant(),
                        resultSet.getObject("created_by", UUID.class)
                ), scheduleId
        );
    }

    protected ResolvedInput resolvedInput() {
        return new ResolvedInput(
                "sync", UUID.randomUUID(), UUID.randomUUID(), 1, "hash",
                "catalog", "namespace", "table", 1L, null
        );
    }

    protected int pendingCount(UUID scheduleId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM data.pipeline_schedule_run "
                        + "WHERE schedule_id = ? AND status = 'pending'",
                Integer.class, scheduleId
        );
        return count == null ? 0 : count;
    }

    protected Instant instant(String sql, UUID id) {
        return jdbc.queryForObject(sql, OffsetDateTime.class, id).toInstant();
    }

    protected <T> List<T> runConcurrently(
            ThrowingSupplier<T> first,
            ThrowingSupplier<T> second
    ) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> left = pool.submit(() -> runAfterBarrier(first, ready, start));
            Future<T> right = pool.submit(() -> runAfterBarrier(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(left.get(30, TimeUnit.SECONDS), right.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private <T> T runAfterBarrier(
            ThrowingSupplier<T> supplier,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent test barrier timed out");
        }
        return supplier.get();
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    protected record PublishedPipeline(UUID pipelineId, UUID versionId, String definitionHash) {
    }
}
