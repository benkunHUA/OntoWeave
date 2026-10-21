package com.ontoweave.data.pipeline.execution.flink;

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

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ProcessFlinkTableExecutor implements FlinkTableExecutor {

    private static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    private static final String EXECUTION_PHASE = "execution";
    private static final String WARNING = "warning";
    private static final String TYPE_MISMATCH_HINT = "检查原始值格式或调整目标字段类型";
    private static final String TYPE_MISMATCH_MESSAGE = "字段值无法转换为目标类型，已返回 null";

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
        TableResult result = null;
        try {
            CompiledTablePlan compiled = compiler.compile(plan);
            ScanLimitAssessment scanLimit = scanLimits.evaluate(plan);
            result = compiled.table().limit(0, outputLimit + 1).execute();
            Optional<JobClient> jobClient = result.getJobClient();
            jobClient.ifPresent(client -> jobs.put(executionId, client));
            result.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return collect(
                    executionId,
                    result,
                    compiled.hiddenErrorColumns(),
                    outputLimit + 1,
                    scanLimit
            );
        } catch (InterruptedException error) {
            cancel(executionId);
            Thread.currentThread().interrupt();
            throw PipelineExecutionException.unlocated(error);
        } catch (TimeoutException error) {
            cancel(executionId);
            throw new FlinkPreviewTimeoutException("Flink preview execution timed out");
        } catch (FlinkPreviewTimeoutException error) {
            throw error;
        } catch (PipelineExecutionException error) {
            throw error;
        } catch (ExecutionException error) {
            throw PipelineExecutionException.unlocated(error.getCause());
        } catch (RuntimeException error) {
            throw PipelineExecutionException.unlocated(error);
        } finally {
            jobs.remove(executionId);
            if (result != null) {
                result.getJobClient().ifPresent(client -> {
                    if (!client.getJobStatus().isDone()) {
                        client.cancel();
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
            while (iterator.hasNext() && rows.size() < limit) {
                Row row = iterator.next();
                rowNumber++;
                Map<String, Object> values = new LinkedHashMap<>();
                for (int index = 0; index < names.size(); index++) {
                    String name = names.get(index);
                    if (!isHidden(name)) {
                        values.put(name, normalize(row.getField(index)));
                    }
                }
                rows.add(values);
                issue(row, rowNumber, names, hiddenColumns, errors);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Unable to collect Flink preview rows", error);
        }
        return new FlinkExecutionResult(
                executionId,
                columns,
                rows,
                null,
                scanLimit.scanLimited(),
                errors,
                List.of()
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

    private Object normalize(Object value) {
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof Row row) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int index = 0; index < row.getArity(); index++) {
                values.put("field_" + index, normalize(row.getField(index)));
            }
            return values;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, item) -> values.put(String.valueOf(key), normalize(item)));
            return values;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::normalize).toList();
        }
        return value;
    }
}
