ALTER TABLE data.sync_run
    ADD COLUMN snapshot_expected_rows BIGINT;

ALTER TABLE data.sync_run
    DROP CONSTRAINT ck_sync_run_status;

ALTER TABLE data.sync_run
    ADD CONSTRAINT ck_sync_run_status
        CHECK (status IN ('queued', 'preparing', 'submitted', 'running', 'stopping', 'succeeded', 'stopped', 'failed'));
