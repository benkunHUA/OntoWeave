ALTER TABLE data.sync_run
    ADD COLUMN stop_operation_id UUID;

CREATE INDEX ix_sync_run_stop_operation ON data.sync_run (stop_operation_id);
