CREATE TABLE data.pipeline (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft',
    current_version_id UUID,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_conversation UNIQUE (conversation_id),
    CONSTRAINT ck_pipeline_status CHECK (status IN ('draft', 'active', 'archived'))
);
CREATE INDEX ix_pipeline_owner_updated
    ON data.pipeline (owner_id, archived_at, updated_at DESC);

CREATE TABLE data.pipeline_draft (
    pipeline_id UUID PRIMARY KEY REFERENCES data.pipeline (id) ON DELETE CASCADE,
    definition JSONB NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    updated_by_type TEXT NOT NULL,
    updated_by_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE data.pipeline_edit_op (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    seq INTEGER NOT NULL,
    op_type TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id UUID,
    before JSONB,
    after JSONB,
    actor_type TEXT NOT NULL,
    actor_id UUID,
    operation_id UUID NOT NULL,
    turn_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_edit_op_revision_seq UNIQUE (pipeline_id, revision, seq)
);
CREATE INDEX ix_pipeline_edit_turn ON data.pipeline_edit_op (pipeline_id, turn_id);

CREATE TABLE data.pipeline_output (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    node_id UUID NOT NULL,
    output_type TEXT NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_output_node UNIQUE (pipeline_id, node_id),
    CONSTRAINT ck_pipeline_output_type CHECK (output_type IN ('dataset'))
);

CREATE TABLE data.pipeline_dataset (
    id UUID PRIMARY KEY,
    output_id UUID NOT NULL UNIQUE REFERENCES data.pipeline_output (id) ON DELETE CASCADE,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    write_mode TEXT NOT NULL DEFAULT 'snapshot',
    primary_key_columns JSONB NOT NULL DEFAULT '[]'::jsonb,
    expectations JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_version_id UUID,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_dataset_name UNIQUE (pipeline_id, name),
    CONSTRAINT ck_pipeline_dataset_write_mode CHECK (write_mode IN ('snapshot'))
);
CREATE INDEX ix_pipeline_dataset_pipeline ON data.pipeline_dataset (pipeline_id);

CREATE TABLE data.pipeline_version (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    definition JSONB NOT NULL,
    outputs JSONB NOT NULL,
    definition_hash TEXT NOT NULL,
    input_refs JSONB NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_version_number UNIQUE (pipeline_id, version_number),
    CONSTRAINT uq_pipeline_version_hash UNIQUE (pipeline_id, definition_hash)
);

CREATE TABLE data.pipeline_build (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    pipeline_version_id UUID NOT NULL REFERENCES data.pipeline_version (id),
    status TEXT NOT NULL,
    stage TEXT,
    trigger_type TEXT NOT NULL,
    operation_id UUID NOT NULL,
    requested_by UUID,
    resolved_inputs JSONB NOT NULL DEFAULT '[]'::jsonb,
    logic_hash TEXT,
    log_uri TEXT,
    error_code TEXT,
    error_message TEXT,
    stats JSONB NOT NULL DEFAULT '{}'::jsonb,
    heartbeat_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_build_operation UNIQUE (pipeline_id, operation_id),
    CONSTRAINT ck_pipeline_build_status
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'canceled')),
    CONSTRAINT ck_pipeline_build_trigger CHECK (trigger_type IN ('manual', 'schedule'))
);
CREATE UNIQUE INDEX uq_pipeline_active_build
    ON data.pipeline_build (pipeline_id) WHERE status IN ('queued', 'running');
CREATE INDEX ix_pipeline_build_created
    ON data.pipeline_build (pipeline_id, created_at DESC);
CREATE INDEX ix_pipeline_build_version_created
    ON data.pipeline_build (pipeline_id, pipeline_version_id, created_at DESC);
CREATE INDEX ix_pipeline_build_queued
    ON data.pipeline_build (created_at, id) WHERE status = 'queued';

CREATE TABLE data.pipeline_build_output (
    id UUID PRIMARY KEY,
    build_id UUID NOT NULL REFERENCES data.pipeline_build (id) ON DELETE CASCADE,
    output_id UUID NOT NULL REFERENCES data.pipeline_output (id) ON DELETE RESTRICT,
    output_type TEXT NOT NULL,
    status TEXT NOT NULL,
    stage TEXT,
    engine_job_id TEXT,
    log_uri TEXT,
    result JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code TEXT,
    error_message TEXT,
    stats JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_build_output UNIQUE (build_id, output_id),
    CONSTRAINT ck_pipeline_build_output_status
        CHECK (status IN ('queued', 'running', 'succeeded', 'failed', 'canceled'))
);

CREATE TABLE data.pipeline_dataset_version (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES data.pipeline_dataset (id) ON DELETE CASCADE,
    build_output_id UUID NOT NULL UNIQUE REFERENCES data.pipeline_build_output (id),
    version_number INTEGER NOT NULL,
    iceberg_catalog TEXT NOT NULL,
    iceberg_namespace TEXT NOT NULL,
    iceberg_table TEXT NOT NULL,
    iceberg_snapshot_id BIGINT NOT NULL,
    schema_json JSONB NOT NULL,
    row_count BIGINT NOT NULL,
    byte_size BIGINT NOT NULL,
    storage_path TEXT,
    committed_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_dataset_version_number UNIQUE (dataset_id, version_number),
    CONSTRAINT uq_pipeline_dataset_version_snapshot
        UNIQUE (dataset_id, iceberg_table, iceberg_snapshot_id)
);
CREATE INDEX ix_pipeline_dataset_version_created
    ON data.pipeline_dataset_version (dataset_id, version_number DESC);

ALTER TABLE data.pipeline_dataset
    ADD CONSTRAINT fk_pipeline_dataset_current_version
    FOREIGN KEY (current_version_id) REFERENCES data.pipeline_dataset_version (id) ON DELETE SET NULL;
ALTER TABLE data.pipeline
    ADD CONSTRAINT fk_pipeline_current_version
    FOREIGN KEY (current_version_id) REFERENCES data.pipeline_version (id) ON DELETE SET NULL;

CREATE TABLE data.pipeline_schedule (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL UNIQUE REFERENCES data.pipeline (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    trigger_type TEXT NOT NULL DEFAULT 'time',
    cron_expression TEXT NOT NULL,
    timezone TEXT NOT NULL,
    next_run_at TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    paused_by UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pipeline_schedule_status CHECK (status IN ('active', 'paused')),
    CONSTRAINT ck_pipeline_schedule_trigger CHECK (trigger_type IN ('time')),
    CONSTRAINT ck_pipeline_schedule_next_run
        CHECK (
            (status = 'active' AND next_run_at IS NOT NULL)
            OR (status = 'paused' AND next_run_at IS NULL)
        )
);
CREATE INDEX ix_pipeline_schedule_due
    ON data.pipeline_schedule (next_run_at) WHERE status = 'active';

CREATE TABLE data.pipeline_schedule_run (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES data.pipeline_schedule (id) ON DELETE CASCADE,
    pipeline_id UUID NOT NULL REFERENCES data.pipeline (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    build_id UUID UNIQUE REFERENCES data.pipeline_build (id) ON DELETE RESTRICT,
    logic_hash TEXT,
    resolved_inputs JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason_code TEXT,
    message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pipeline_schedule_run_time UNIQUE (schedule_id, scheduled_for),
    CONSTRAINT ck_pipeline_schedule_run_status
        CHECK (status IN ('pending', 'dispatched', 'ignored', 'failed'))
);
CREATE UNIQUE INDEX uq_pipeline_schedule_pending
    ON data.pipeline_schedule_run (schedule_id) WHERE status = 'pending';

CREATE VIEW data.dataset_catalog AS
SELECT 'sync'::text AS dataset_kind, d.id AS dataset_id, c.owner_id, d.name,
       d.current_version_id, d.created_at, d.updated_at
FROM data.dataset d
JOIN data.data_connection c ON c.id = d.connection_id
UNION ALL
SELECT 'pipeline'::text AS dataset_kind, d.id AS dataset_id, p.owner_id, d.name,
       d.current_version_id, d.created_at, d.updated_at
FROM data.pipeline_dataset d
JOIN data.pipeline p ON p.id = d.pipeline_id;

CREATE VIEW data.dataset_version_catalog AS
SELECT 'sync'::text AS dataset_kind, v.dataset_id, v.id AS version_id,
       v.version_number, v.row_count, v.schema_json, v.published_at
FROM data.dataset_version v
WHERE v.published_at IS NOT NULL
UNION ALL
SELECT 'pipeline'::text AS dataset_kind, v.dataset_id, v.id AS version_id,
       v.version_number, v.row_count, v.schema_json, v.published_at
FROM data.pipeline_dataset_version v
WHERE v.published_at IS NOT NULL;
