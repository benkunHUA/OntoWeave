CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE data.ontology_object_binding (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    object_type_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT NOT NULL DEFAULT 1,
    draft_dataset_kind TEXT NOT NULL,
    draft_dataset_id UUID NOT NULL,
    draft_mapping JSONB NOT NULL,
    active_dataset_kind TEXT,
    active_dataset_id UUID,
    active_dataset_version_id UUID,
    active_mapping_revision BIGINT,
    current_index_version_id UUID,
    validation_state TEXT NOT NULL DEFAULT 'OK',
    validation_error JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_object_binding_type UNIQUE (object_type_id),
    CONSTRAINT ck_object_binding_status CHECK (status IN ('ACTIVE', 'UNBOUND')),
    CONSTRAINT ck_object_binding_kind CHECK (draft_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_object_binding_active_kind
        CHECK (active_dataset_kind IS NULL OR active_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_object_binding_state CHECK (validation_state IN ('OK', 'NEEDS_REPAIR'))
);

CREATE INDEX ix_object_binding_draft_dataset
    ON data.ontology_object_binding (draft_dataset_kind, draft_dataset_id);
CREATE INDEX ix_object_binding_active_dataset
    ON data.ontology_object_binding (active_dataset_kind, active_dataset_id);

CREATE TABLE data.ontology_relation_definition (
    relation_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    source_type_id UUID NOT NULL,
    target_type_id UUID NOT NULL,
    name TEXT NOT NULL,
    cardinality TEXT NOT NULL,
    foreign_key_property_id UUID,
    foreign_key_datatype TEXT,
    source_primary_key_attribute_id UUID NOT NULL,
    source_primary_key_datatype TEXT NOT NULL,
    target_primary_key_attribute_id UUID NOT NULL,
    target_primary_key_datatype TEXT NOT NULL,
    definition_revision BIGINT NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_relation_definition_cardinality
        CHECK (cardinality IN ('one_to_one', 'one_to_many', 'many_to_many'))
);

CREATE INDEX ix_relation_definition_source
    ON data.ontology_relation_definition (owner_id, source_type_id);
CREATE INDEX ix_relation_definition_target
    ON data.ontology_relation_definition (owner_id, target_type_id);

CREATE TABLE data.ontology_relation_binding (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    revision BIGINT NOT NULL DEFAULT 1,
    draft_dataset_kind TEXT NOT NULL,
    draft_dataset_id UUID NOT NULL,
    draft_mapping JSONB NOT NULL,
    active_dataset_kind TEXT,
    active_dataset_id UUID,
    active_dataset_version_id UUID,
    active_mapping_revision BIGINT,
    current_index_version_id UUID,
    validation_state TEXT NOT NULL DEFAULT 'OK',
    validation_error JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_relation_binding_relation UNIQUE (relation_id),
    CONSTRAINT ck_relation_binding_status CHECK (status IN ('ACTIVE', 'UNBOUND')),
    CONSTRAINT ck_relation_binding_kind CHECK (draft_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_relation_binding_active_kind
        CHECK (active_dataset_kind IS NULL OR active_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_relation_binding_state CHECK (validation_state IN ('OK', 'NEEDS_REPAIR'))
);

CREATE INDEX ix_relation_binding_draft_dataset
    ON data.ontology_relation_binding (draft_dataset_kind, draft_dataset_id);
CREATE INDEX ix_relation_binding_active_dataset
    ON data.ontology_relation_binding (active_dataset_kind, active_dataset_id);

CREATE TABLE data.ontology_object_version (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    binding_id UUID NOT NULL REFERENCES data.ontology_object_binding (id) ON DELETE CASCADE,
    object_type_id UUID NOT NULL,
    source_dataset_kind TEXT NOT NULL,
    source_dataset_id UUID NOT NULL,
    source_dataset_version_id UUID NOT NULL,
    source_snapshot_id BIGINT NOT NULL,
    mapping_snapshot JSONB NOT NULL,
    mapping_revision BIGINT NOT NULL,
    run_id UUID NOT NULL,
    status TEXT NOT NULL,
    row_count BIGINT,
    warning_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    cleanup_started_at TIMESTAMPTZ,
    cleanup_completed_at TIMESTAMPTZ,
    cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_object_version_kind
        CHECK (source_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_object_version_status
        CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED', 'DISCARDED'))
);

CREATE INDEX ix_object_version_binding
    ON data.ontology_object_version (binding_id, created_at DESC);
CREATE INDEX ix_object_version_owner
    ON data.ontology_object_version (owner_id, object_type_id, created_at DESC);
CREATE INDEX ix_object_version_cleanup
    ON data.ontology_object_version (status, retired_at)
    WHERE status = 'RETIRED';

CREATE TABLE data.ontology_relation_version (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    binding_id UUID REFERENCES data.ontology_relation_binding (id) ON DELETE CASCADE,
    relation_id UUID NOT NULL,
    relation_kind TEXT NOT NULL,
    from_object_type_id UUID NOT NULL,
    to_object_type_id UUID NOT NULL,
    from_object_version_id UUID NOT NULL,
    to_object_version_id UUID NOT NULL,
    source_dataset_kind TEXT,
    source_dataset_id UUID,
    source_dataset_version_id UUID,
    source_snapshot_id BIGINT,
    mapping_snapshot JSONB,
    mapping_revision BIGINT,
    run_id UUID NOT NULL,
    status TEXT NOT NULL,
    row_count BIGINT,
    skipped_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    cleanup_started_at TIMESTAMPTZ,
    cleanup_completed_at TIMESTAMPTZ,
    cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_relation_version_kind CHECK (relation_kind IN ('FK', 'MANY_TO_MANY')),
    CONSTRAINT ck_relation_version_source_kind
        CHECK (source_dataset_kind IS NULL OR source_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_relation_version_status
        CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED', 'DISCARDED'))
);

CREATE INDEX ix_relation_version_relation
    ON data.ontology_relation_version (owner_id, relation_id, created_at DESC);
CREATE INDEX ix_relation_version_cleanup
    ON data.ontology_relation_version (status, retired_at)
    WHERE status = 'RETIRED';

CREATE TABLE data.ontology_index_run (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    subject_kind TEXT NOT NULL,
    subject_id UUID NOT NULL,
    binding_id UUID,
    target_dataset_kind TEXT NOT NULL,
    target_dataset_version_id UUID NOT NULL,
    mapping_snapshot JSONB NOT NULL,
    mapping_revision BIGINT NOT NULL,
    trigger_type TEXT NOT NULL,
    status TEXT NOT NULL,
    phase TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    cancel_requested_at TIMESTAMPTZ,
    attempt INTEGER NOT NULL DEFAULT 1,
    parent_run_id UUID,
    error_code TEXT,
    error_message TEXT,
    error_details JSONB,
    stats JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_run_subject CHECK (subject_kind IN ('OBJECT_TYPE', 'RELATION')),
    CONSTRAINT ck_run_target_kind
        CHECK (target_dataset_kind IN ('SYNC', 'PIPELINE')),
    CONSTRAINT ck_run_trigger
        CHECK (trigger_type IN ('MANUAL', 'DATASET_PUBLISHED', 'RETRY')),
    CONSTRAINT ck_run_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SUPERSEDED')),
    CONSTRAINT ck_run_phase
        CHECK (phase IS NULL OR phase IN ('READING', 'VALIDATING', 'WRITING', 'SWITCHING'))
);

CREATE UNIQUE INDEX uq_run_queued
    ON data.ontology_index_run (subject_kind, subject_id)
    WHERE status = 'QUEUED';
CREATE UNIQUE INDEX uq_run_running
    ON data.ontology_index_run (subject_kind, subject_id)
    WHERE status = 'RUNNING';
CREATE INDEX ix_run_subject
    ON data.ontology_index_run (subject_kind, subject_id, requested_at DESC);
CREATE INDEX ix_run_lease
    ON data.ontology_index_run (lease_expires_at)
    WHERE status = 'RUNNING';

CREATE TABLE data.ontology_object_instance (
    version_id UUID NOT NULL,
    object_type_id UUID NOT NULL,
    primary_key_value TEXT NOT NULL,
    primary_key_num NUMERIC,
    primary_key_date DATE,
    primary_key_ts TIMESTAMPTZ,
    title_value TEXT,
    properties JSONB NOT NULL,
    PRIMARY KEY (version_id, primary_key_value)
);

CREATE INDEX ix_object_instance_title
    ON data.ontology_object_instance (version_id, title_value);
CREATE INDEX ix_object_instance_title_trgm
    ON data.ontology_object_instance USING gin (title_value gin_trgm_ops);
CREATE INDEX ix_object_instance_pk_trgm
    ON data.ontology_object_instance USING gin (primary_key_value gin_trgm_ops);
CREATE INDEX ix_object_instance_pk_num
    ON data.ontology_object_instance (version_id, primary_key_num);
CREATE INDEX ix_object_instance_pk_date
    ON data.ontology_object_instance (version_id, primary_key_date);
CREATE INDEX ix_object_instance_pk_ts
    ON data.ontology_object_instance (version_id, primary_key_ts);

CREATE TABLE data.ontology_object_value (
    version_id UUID NOT NULL,
    attribute_id UUID NOT NULL,
    primary_key_value TEXT NOT NULL,
    text_value TEXT,
    num_value NUMERIC,
    date_value DATE,
    ts_value TIMESTAMPTZ,
    bool_value BOOLEAN,
    PRIMARY KEY (version_id, attribute_id, primary_key_value)
);

CREATE INDEX ix_object_value_text
    ON data.ontology_object_value (version_id, attribute_id, text_value);
CREATE INDEX ix_object_value_num
    ON data.ontology_object_value (version_id, attribute_id, num_value);
CREATE INDEX ix_object_value_date
    ON data.ontology_object_value (version_id, attribute_id, date_value);
CREATE INDEX ix_object_value_ts
    ON data.ontology_object_value (version_id, attribute_id, ts_value);
CREATE INDEX ix_object_value_bool
    ON data.ontology_object_value (version_id, attribute_id, bool_value);
CREATE INDEX ix_object_value_text_trgm
    ON data.ontology_object_value USING gin (text_value gin_trgm_ops);

CREATE TABLE data.ontology_relation_instance (
    version_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    from_object_type_id UUID NOT NULL,
    from_primary_key TEXT NOT NULL,
    to_object_type_id UUID NOT NULL,
    to_primary_key TEXT NOT NULL,
    PRIMARY KEY (version_id, from_primary_key, to_primary_key)
);

CREATE INDEX ix_relation_instance_from
    ON data.ontology_relation_instance (version_id, from_primary_key);
CREATE INDEX ix_relation_instance_to
    ON data.ontology_relation_instance (version_id, to_primary_key);

CREATE TABLE data.ontology_object_stage (
    run_id UUID NOT NULL,
    object_type_id UUID NOT NULL,
    primary_key_value TEXT,
    primary_key_num NUMERIC,
    primary_key_date DATE,
    primary_key_ts TIMESTAMPTZ,
    title_value TEXT,
    properties JSONB NOT NULL
);
CREATE INDEX ix_object_stage_run ON data.ontology_object_stage (run_id);

CREATE TABLE data.ontology_object_value_stage (
    run_id UUID NOT NULL,
    attribute_id UUID NOT NULL,
    primary_key_value TEXT,
    text_value TEXT,
    num_value NUMERIC,
    date_value DATE,
    ts_value TIMESTAMPTZ,
    bool_value BOOLEAN
);
CREATE INDEX ix_object_value_stage_run
    ON data.ontology_object_value_stage (run_id);

CREATE TABLE data.ontology_relation_stage (
    run_id UUID NOT NULL,
    relation_id UUID NOT NULL,
    from_object_type_id UUID NOT NULL,
    from_primary_key TEXT,
    to_object_type_id UUID NOT NULL,
    to_primary_key TEXT
);
CREATE INDEX ix_relation_stage_run ON data.ontology_relation_stage (run_id);
