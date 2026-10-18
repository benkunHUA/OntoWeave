package com.ontoweave.data.sync.execution;

import com.ontoweave.data.outbox.OutboxWriter;
import com.ontoweave.data.sync.domain.SyncRunEntity;
import com.ontoweave.data.sync.domain.SyncTaskEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 同步运行事件统一出口：保证每条事件都能被 Python 找到所属会话并合并到同一操作卡片。 */
@Component
public class SyncRunEventWriter {

    private final OutboxWriter outbox;

    public SyncRunEventWriter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void write(SyncRunEntity run, SyncTaskEntity task, String eventType, String summary) {
        outbox.write("sync_run", run.getId(), eventType, payload(run, task, summary));
    }

    private Map<String, Object> payload(SyncRunEntity run, SyncTaskEntity task, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getId());
        payload.put("syncTaskId", task.getId());
        payload.put("datasetId", task.getDatasetId());
        payload.put("connectionId", task.getConnectionId());
        payload.put("operationId", run.getOperationId());
        payload.put("runMode", run.getRunMode().value());
        payload.put("flinkJobId", run.getFlinkJobId());
        payload.put("status", run.getStatus().value());
        payload.put("errorCode", run.getErrorCode());
        payload.put("errorMessage", run.getErrorMessage());
        payload.put("summary", task.getSchemaName() + "." + task.getTableName() + "：" + summary);
        return payload;
    }
}
