package com.ontoweave.data.pipeline.execution.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.check.PipelineCheckResult;
import com.ontoweave.data.pipeline.check.PipelineIssue;
import com.ontoweave.data.pipeline.check.PipelineStage;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;
import com.ontoweave.data.pipeline.execution.flink.FlinkExecutionResult;
import com.ontoweave.data.pipeline.execution.flink.FlinkTableExecutor;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlanNode;
import com.ontoweave.data.pipeline.execution.plan.PlanStep;
import com.ontoweave.data.pipeline.source.DisplaySnapshot;
import com.ontoweave.data.sync.iceberg.PreviewColumn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

final class PipelinePreviewTestSupport {

    private PipelinePreviewTestSupport() {
    }

    static PipelineCheckResult readyCheck(UUID pipelineId, long revision) {
        return new PipelineCheckResult(
                pipelineId, revision, PipelineStage.READY, List.of(), List.of(), List.of()
        );
    }

    static PipelineCheckResult blockedCheck(UUID pipelineId, long revision, UUID nodeId) {
        return new PipelineCheckResult(
                pipelineId,
                revision,
                PipelineStage.INVALID,
                List.of(new PipelineIssue(
                        "FIELD_NOT_FOUND", "schema", true, nodeId.toString(), null,
                        "missing", "字段不存在", null
                )),
                List.of(),
                List.of()
        );
    }

    static LogicalPlan inputPlan(UUID nodeId, ResolvedInput input, long revision) {
        PlanStep root = new PlanStep(
                nodeId.toString(),
                "input",
                scan(input),
                List.of()
        );
        return LogicalPlan.of(root, revision, nodeId.toString(), Map.of(nodeId.toString(), input));
    }

    static LogicalPlan transformPlan(UUID nodeId, ResolvedInput input, long revision) {
        PlanStep scan = new PlanStep(
                UUID.randomUUID().toString(),
                "input",
                scan(input),
                List.of()
        );
        PlanStep filter = new PlanStep(
                nodeId.toString(),
                "filter",
                new LogicalPlanNode.Filter(new ObjectMapper().createObjectNode()),
                List.of(scan)
        );
        return LogicalPlan.of(filter, revision, nodeId.toString(), Map.of(scan.nodeId(), input));
    }

    static ResolvedInput input(long snapshotId) {
        return new ResolvedInput(
                "sync",
                UUID.randomUUID(),
                UUID.randomUUID(),
                12,
                "schema-hash",
                "ontoweave",
                "sales",
                "orders",
                snapshotId,
                new DisplaySnapshot("orders", 12, UUID.randomUUID(), "prod-mysql", "sales", "orders")
        );
    }

    static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", (long) index);
            rows.add(row);
        }
        return rows;
    }

    private static LogicalPlanNode.ScanInput scan(ResolvedInput input) {
        return new LogicalPlanNode.ScanInput(
                input.icebergCatalog(),
                input.icebergNamespace(),
                input.icebergTable(),
                input.icebergSnapshotId(),
                50_000
        );
    }

    static final class BlockingExecutor implements FlinkTableExecutor {

        private final CountDownLatch started = new CountDownLatch(1);

        boolean awaitStarted(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        @Override
        public FlinkExecutionResult execute(LogicalPlan plan, int outputLimit, Duration timeout) {
            started.countDown();
            try {
                Thread.sleep(10_000);
                throw new AssertionError("preview should have been interrupted");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("preview interrupted", error);
            }
        }

        @Override
        public void cancel(UUID executionId) {
        }
    }

    static final class BlockingOnceExecutor implements FlinkTableExecutor {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch firstStopped = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        boolean awaitStarted(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        @Override
        public FlinkExecutionResult execute(LogicalPlan plan, int outputLimit, Duration timeout) {
            if (calls.getAndIncrement() == 0) {
                started.countDown();
                try {
                    Thread.sleep(10_000);
                    throw new AssertionError("preview should have been interrupted");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("preview interrupted", error);
                } finally {
                    firstStopped.countDown();
                }
            }
            if (firstStopped.getCount() != 0) {
                throw new AssertionError("replacement started before the old task stopped");
            }
            return new FlinkExecutionResult(
                    UUID.randomUUID(),
                    List.of(new PreviewColumn("id", "long", false)),
                    rows(1),
                    1L,
                    false,
                    List.of(),
                    List.of()
            );
        }

        @Override
        public void cancel(UUID executionId) {
        }
    }
}
