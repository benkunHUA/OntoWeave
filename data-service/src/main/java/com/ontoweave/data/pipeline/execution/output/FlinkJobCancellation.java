package com.ontoweave.data.pipeline.execution.output;

import com.ontoweave.data.pipeline.execution.build.PipelineBuildConstants;
import com.ontoweave.data.pipeline.execution.build.PipelineBuildException;
import org.apache.flink.api.common.JobStatus;
import org.apache.flink.core.execution.JobClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class FlinkJobCancellation {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private FlinkJobCancellation() {
    }

    static void awaitTermination(JobClient client, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            CompletableFuture<Void> cancellation = client.cancel();
            if (cancellation == null) {
                throw new IllegalStateException("Flink cancel returned no future");
            }
            cancellation.get(remaining(deadline), TimeUnit.NANOSECONDS);
            CompletableFuture<JobStatus> status = client.getJobStatus();
            if (status == null) {
                throw new IllegalStateException("Flink job status returned no future");
            }
            JobStatus jobStatus = status.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (jobStatus == null || !jobStatus.isTerminalState()) {
                throw new IllegalStateException("Flink job has not terminated: " + jobStatus);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw failure("取消 Flink 作业被中断", error);
        } catch (TimeoutException error) {
            throw failure("取消 Flink 作业超时", error);
        } catch (ExecutionException error) {
            throw failure(
                    "取消 Flink 作业失败",
                    error.getCause() == null ? error : error.getCause()
            );
        } catch (RuntimeException error) {
            throw failure("取消 Flink 作业失败", error);
        }
    }

    private static long remaining(long deadline) throws TimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new TimeoutException("Flink cancellation timed out");
        }
        return remaining;
    }

    private static PipelineBuildException failure(String message, Throwable error) {
        return new PipelineBuildException(
                PipelineBuildConstants.CODE_EXECUTION_FAILED,
                message,
                error
        );
    }
}
