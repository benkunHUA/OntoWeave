package com.ontoweave.data.pipeline.execution.flink;

import com.ontoweave.data.common.PreviewValues;
import com.ontoweave.data.pipeline.execution.plan.CompiledTablePlan;
import com.ontoweave.data.pipeline.execution.plan.LogicalPlan;
import com.ontoweave.data.pipeline.execution.plan.PipelinePlanCompiler;
import com.ontoweave.data.pipeline.execution.plan.ScanLimitAssessment;
import com.ontoweave.data.pipeline.execution.plan.ScanLimitEvaluator;
import com.ontoweave.data.pipeline.execution.preview.PreviewIssue;
import com.ontoweave.data.sync.iceberg.PreviewColumn;
import org.apache.flink.core.execution.JobClient;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ProcessFlinkTableExecutor implements FlinkTableExecutor {

    private static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    private static final String EXECUTION_PHASE = "execution";
    private static final String WARNING = "warning";
    private static final String TYPE_MISMATCH_HINT = "检查原始值格式或调整目标字段类型";
    private static final String TYPE_MISMATCH_MESSAGE = "字段值无法转换为目标类型，已返回 null";
    private static final String SCAN_UNKNOWN_CODE = "SCAN_ROW_LIMIT_UNKNOWN";
    private static final String SCAN_PHASE = "scan";
    private static final String SCAN_UNKNOWN_MESSAGE = "无法确认输入扫描行数，预览可能未覆盖完整数据";
    private static final String SCAN_UNKNOWN_HINT = "检查 Iceberg 快照元数据是否可读";

    private final PipelinePlanCompiler compiler;
    private final ScanLimitEvaluator scanLimits;
    private final ConcurrentMap<UUID, JobClient> jobs = new ConcurrentHashMap<>();

    public ProcessFlinkTableExecutor(PipelinePlanCompiler compiler, ScanLimitEvaluator scanLimits) {
        this.compiler = compiler;
        this.scanLimits = scanLimits;
    }

    @Override
    public FlinkExecutionResult execute(LogicalPlan plan, int outputLimit, Duration timeout) {
        UUID executionId = UUID.randomUUID();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        TableResult result = null;
        try {
            CompiledTablePlan compiled = compiler.compile(plan);
            ScanLimitAssessment scanLimit = scanLimits.evaluate(plan);
            result = compiled.table().limit(0, outputLimit + 1).execute();
            Optional<JobClient> jobClient = result.getJobClient();
            jobClient.ifPresent(client -> jobs.put(executionId, client));
            return collect(
                    executionId,
                    result,
                    compiled.hiddenErrorColumns(),
                    outputLimit + 1,
                    deadlineNanos,
                    scanLimit
            );
        } catch (CancellationException error) {
            cancel(executionId);
            throw error;
        } catch (FlinkPreviewTimeoutException error) {
            throw error;
        } catch (PipelineExecutionException error) {
            throw error;
        } catch (RuntimeException error) {
            throw PipelineExecutionException.unlocated(error);
        } finally {
            jobs.remove(executionId);
            if (result != null) {
                result.getJobClient().ifPresent(client -> {
                    try {
                        if (!client.getJobStatus().isDone()) {
                            client.cancel();
                        }
                    } catch (RuntimeException ignored) {
                        return;
                    }
                });
            }
        }
    }

    @Override
    public void cancel(UUID executionId) {
        JobClient client = jobs.remove(executionId);
        if (client != null) {
            client.cancel();
        }
    }

    private FlinkExecutionResult collect(
            UUID executionId,
            TableResult result,
            List<CompiledTablePlan.HiddenErrorColumn> hiddenColumns,
            int limit,
            long deadlineNanos,
            ScanLimitAssessment scanLimit
    ) {
        ResolvedSchema schema = result.getResolvedSchema();
        List<String> names = schema.getColumnNames();
        List<PreviewColumn> columns = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (!isHidden(name)) {
                columns.add(new PreviewColumn(
                        name,
                        schema.getColumnDataTypes().get(index).toString(),
                        schema.getColumnDataTypes().get(index).getLogicalType().isNullable()
                ));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<PreviewIssue> errors = new ArrayList<>();
        try (CloseableIterator<Row> iterator = result.collect()) {
            int rowNumber = 0;
            while (rows.size() < limit) {
                if (Thread.currentThread().isInterrupted()) {
                    cancel(executionId);
                    throw new CancellationException("Flink preview collection interrupted");
                }
                if (System.nanoTime() >= deadlineNanos) {
                    cancel(executionId);
                    throw new FlinkPreviewTimeoutException("Flink preview collection timed out");
                }
                if (!iterator.hasNext()) {
                    break;
                }
                Row row = iterator.next();
                rowNumber++;
                Map<String, Object> values = new LinkedHashMap<>();
                for (int index = 0; index < names.size(); index++) {
                    String name = names.get(index);
                    if (!isHidden(name)) {
                        values.put(name, PreviewValues.normalize(row.getField(index)));
                    }
                }
                rows.add(values);
                issue(row, rowNumber, names, hiddenColumns, errors);
            }
        } catch (FlinkPreviewTimeoutException | CancellationException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to collect Flink preview rows", error);
        }
        List<PreviewIssue> warnings = scanLimit.metadataAvailable()
                ? List.of()
                : List.of(new PreviewIssue(
                        SCAN_UNKNOWN_CODE,
                        SCAN_PHASE,
                        WARNING,
                        null,
                        null,
                        null,
                        null,
                        SCAN_UNKNOWN_MESSAGE,
                        SCAN_UNKNOWN_HINT,
                        null
                ));
        return new FlinkExecutionResult(
                executionId,
                columns,
                rows,
                null,
                scanLimit.scanLimited(),
                errors,
                warnings
        );
    }

    private void issue(
            Row row,
            int rowNumber,
            List<String> names,
            List<CompiledTablePlan.HiddenErrorColumn> hiddenColumns,
            List<PreviewIssue> errors
    ) {
        for (CompiledTablePlan.HiddenErrorColumn hidden : hiddenColumns) {
            int flagIndex = names.indexOf(hidden.flagColumn());
            if (flagIndex < 0 || !Boolean.TRUE.equals(row.getField(flagIndex))) {
                continue;
            }
            int originalIndex = names.indexOf(hidden.originalColumn());
            Object original = originalIndex < 0 ? null : row.getField(originalIndex);
            errors.add(new PreviewIssue(
                    TYPE_MISMATCH,
                    EXECUTION_PHASE,
                    WARNING,
                    hidden.nodeId(),
                    hidden.portId(),
                    hidden.field(),
                    hidden.rowPrecise() ? (long) rowNumber : null,
                    TYPE_MISMATCH_MESSAGE,
                    TYPE_MISMATCH_HINT,
                    original == null ? null : abbreviate(String.valueOf(original))
            ));
        }
    }

    private boolean isHidden(String name) {
        return name != null && name.startsWith(CompiledTablePlan.HIDDEN_PREFIX);
    }

    private String abbreviate(String value) {
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }

}
