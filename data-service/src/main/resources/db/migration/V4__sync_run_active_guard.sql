CREATE UNIQUE INDEX uq_sync_run_active_task
    ON data.sync_run (sync_task_id)
    WHERE status IN ('queued', 'preparing', 'submitted', 'running', 'stopping');

CREATE INDEX ix_sync_run_task_status ON data.sync_run (sync_task_id, status);
