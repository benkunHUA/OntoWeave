ALTER TABLE data.dataset_version
    ADD COLUMN source_run_id UUID,
    ADD COLUMN storage_path TEXT;

CREATE INDEX ix_dataset_version_source_run ON data.dataset_version (source_run_id);
