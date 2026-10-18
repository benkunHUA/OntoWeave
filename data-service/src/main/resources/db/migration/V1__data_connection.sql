CREATE SCHEMA IF NOT EXISTS data;

CREATE TABLE data.data_connection (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_version INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'draft',
    last_test_status TEXT NOT NULL DEFAULT 'untested',
    last_test_at TIMESTAMPTZ,
    last_test_error TEXT,
    last_test_details JSONB,
    conversation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_data_connection_owner_name UNIQUE (owner_id, name),
    CONSTRAINT uq_data_connection_conversation UNIQUE (conversation_id),
    CONSTRAINT ck_data_connection_kind CHECK (kind IN ('mysql')),
    CONSTRAINT ck_data_connection_status CHECK (status IN ('draft', 'active')),
    CONSTRAINT ck_data_connection_test_status CHECK (last_test_status IN ('untested', 'success', 'failed'))
);

CREATE INDEX ix_data_connection_owner_created
    ON data.data_connection (owner_id, created_at DESC);

CREATE TABLE data.data_connection_secret (
    connection_id UUID PRIMARY KEY REFERENCES data.data_connection (id) ON DELETE CASCADE,
    ciphertext TEXT NOT NULL,
    key_version INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE data.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX ix_outbox_event_pending
    ON data.outbox_event (created_at)
    WHERE published_at IS NULL;

CREATE INDEX ix_outbox_event_aggregate
    ON data.outbox_event (aggregate_type, aggregate_id, created_at DESC);
