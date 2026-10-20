package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncRunStatus;
import com.ontoweave.data.sync.flink.FlinkRestClient;
import com.ontoweave.data.sync.repository.SyncRunRepository;
import com.ontoweave.data.sync.repository.SyncTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 用数据库锁和实时状态做 Flink 提交准入，避免多实例并发超卖。 */
@Service
public class SyncRunAdmissionService {

    private static final long ADMISSION_LOCK_KEY = 8_071_120_261_020L;
    private static final List<SyncRunStatus> IN_FLIGHT = List.of(
            SyncRunStatus.PREPARING,
            SyncRunStatus.SUBMITTED,
            SyncRunStatus.RUNNING,
            SyncRunStatus.STOPPING
    );
    private static final List<SyncRunStatus> PENDING_SUBMISSION = List.of(
            SyncRunStatus.PREPARING,
            SyncRunStatus.SUBMITTED
    );

    private final SyncProperties properties;
    private final SyncRunRepository runs;
    private final SyncTaskRepository tasks;
    private final FlinkRestClient flink;
    private final TransactionTemplate transactions;

    public SyncRunAdmissionService(
            SyncProperties properties,
            SyncRunRepository runs,
            SyncTaskRepository tasks,
            FlinkRestClient flink,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.runs = runs;
        this.tasks = tasks;
        this.flink = flink;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public boolean tryAdmit(UUID runId) {
        Boolean admitted = transactions.execute(status -> admit(runId));
        if (!Boolean.TRUE.equals(admitted)) {
            runs.releaseDispatch(runId, SyncRunStatus.QUEUED, Instant.now());
            return false;
        }
        return true;
    }

    private boolean admit(UUID runId) {
        runs.acquireAdmissionLock(ADMISSION_LOCK_KEY);
        SyncRunEntity run = runs.findById(runId).orElse(null);
        if (run == null || run.getStatus() != SyncRunStatus.QUEUED) {
            return false;
        }
        var task = tasks.findById(run.getSyncTaskId()).orElse(null);
        if (task == null) {
            return false;
        }
        if (runs.countByStatusIn(IN_FLIGHT) >= properties.maxConcurrentRuns()) {
            return false;
        }
        if (runs.countByConnectionIdAndStatusIn(task.getConnectionId(), IN_FLIGHT)
                >= properties.maxConcurrentRunsPerConnection()) {
            return false;
        }
        if (!slotsAvailable()) {
            return false;
        }
        return runs.claim(
                runId,
                SyncRunStatus.QUEUED,
                SyncRunStatus.PREPARING,
                Instant.now()
        ) == 1;
    }

    private boolean slotsAvailable() {
        int parallelism = properties.parallelism();
        int available = flink.availableTaskSlots().orElse(parallelism);
        long pendingRuns = runs.countByStatusIn(PENDING_SUBMISSION);
        return available >= parallelism + pendingRuns * parallelism;
    }
}
