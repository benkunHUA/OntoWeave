CREATE SEQUENCE data.sync_server_id_seq START WITH 100 INCREMENT BY 1;

CREATE TABLE data.dataset (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL REFERENCES data.data_connection (id) ON DELETE CASCADE,
    sync_task_id UUID,
    name TEXT NOT NULL,
    active_iceberg_catalog TEXT,
    active_iceberg_namespace TEXT,
    active_iceberg_table TEXT,
    current_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dataset_connection_name UNIQUE (connection_id, name),
    CONSTRAINT uq_dataset_sync_task UNIQUE (sync_task_id)
);

CREATE TABLE data.sync_task (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL REFERENCES data.data_connection (id) ON DELETE CASCADE,
    dataset_id UUID NOT NULL UNIQUE REFERENCES data.dataset (id) ON DELETE CASCADE,
    schema_name TEXT NOT NULL,
    table_name TEXT NOT NULL,
    sync_mode TEXT NOT NULL,
    version_interval_seconds INTEGER NOT NULL DEFAULT 86400,
    primary_key_columns JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'enabled',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_task_source UNIQUE (connection_id, schema_name, table_name),
    CONSTRAINT ck_sync_task_mode CHECK (sync_mode IN ('snapshot', 'cdc')),
    CONSTRAINT ck_sync_task_status CHECK (status IN ('enabled', 'disabled')),
    CONSTRAINT ck_sync_task_interval CHECK (version_interval_seconds >= 60)
);

ALTER TABLE data.dataset
    ADD CONSTRAINT fk_dataset_sync_task
    FOREIGN KEY (sync_task_id) REFERENCES data.sync_task (id) ON DELETE SET NULL;

CREATE TABLE data.sync_run (
    id UUID PRIMARY KEY,
    sync_task_id UUID NOT NULL REFERENCES data.sync_task (id) ON DELETE CASCADE,
    dataset_id UUID NOT NULL REFERENCES data.dataset (id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    run_mode TEXT NOT NULL,
    status TEXT NOT NULL,
    flink_job_id TEXT,
    flink_job_name TEXT NOT NULL UNIQUE,
    flink_job_state TEXT,
    iceberg_catalog TEXT NOT NULL,
    iceberg_namespace TEXT NOT NULL,
    iceberg_table TEXT NOT NULL,
    server_id_start BIGINT,
    server_id_end BIGINT,
    snapshot_completed_at TIMESTAMPTZ,
    last_checkpoint_at TIMESTAMPTZ,
    last_snapshot_id BIGINT,
    last_published_snapshot_id BIGINT,
    version_interval_seconds INTEGER NOT NULL,
    next_version_due_at TIMESTAMPTZ,
    savepoint_path TEXT,
    stop_requested_at TIMESTAMPTZ,
    error_code TEXT,
    error_message TEXT,
    dispatch_attempts INTEGER NOT NULL DEFAULT 0,
    dispatched_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_run_operation UNIQUE (sync_task_id, operation_id),
    CONSTRAINT ck_sync_run_mode CHECK (run_mode IN ('snapshot', 'cdc')),
    CONSTRAINT ck_sync_run_status CHECK (status IN ('queued', 'submitted', 'running', 'stopping', 'succeeded', 'stopped', 'failed'))
);

CREATE INDEX ix_sync_run_status_created ON data.sync_run (status, created_at);
CREATE INDEX ix_sync_run_dataset_created ON data.sync_run (dataset_id, created_at DESC);

CREATE TABLE data.dataset_version (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES data.dataset (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    iceberg_catalog TEXT NOT NULL,
    iceberg_namespace TEXT NOT NULL,
    iceberg_table TEXT NOT NULL,
    iceberg_snapshot_id BIGINT NOT NULL,
    row_count BIGINT NOT NULL,
    row_count_estimated BOOLEAN NOT NULL,
    byte_size BIGINT NOT NULL,
    schema_json JSONB NOT NULL,
    column_count INTEGER NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version_source TEXT NOT NULL,
    CONSTRAINT uq_dataset_version_number UNIQUE (dataset_id, version_number),
    CONSTRAINT uq_dataset_version_snapshot UNIQUE (dataset_id, iceberg_table, iceberg_snapshot_id),
    CONSTRAINT ck_dataset_version_source CHECK (version_source IN ('snapshot_end', 'scheduled', 'stop'))
);

ALTER TABLE data.dataset
    ADD CONSTRAINT fk_dataset_current_version
    FOREIGN KEY (current_version_id) REFERENCES data.dataset_version (id) ON DELETE SET NULL;
