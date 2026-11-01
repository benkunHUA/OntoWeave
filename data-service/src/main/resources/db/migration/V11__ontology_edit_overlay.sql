CREATE TABLE data.ontology_edit_execution (
    id                     UUID PRIMARY KEY,
    owner_id               UUID NOT NULL,
    conversation_id        UUID,
    graph_id               UUID NOT NULL,
    action_type_id         UUID NOT NULL,
    action_snapshot        JSONB NOT NULL,
    action_definition_hash TEXT NOT NULL,
    parameters             JSONB NOT NULL,
    status                 TEXT NOT NULL,
    idempotency_key        TEXT NOT NULL,
    base_versions          JSONB NOT NULL DEFAULT '{}'::jsonb,
    diff                   JSONB,
    error                  JSONB,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at             TIMESTAMPTZ,
    CONSTRAINT uq_edit_execution_idempotency UNIQUE (owner_id, idempotency_key),
    CONSTRAINT ck_edit_execution_status CHECK (status IN ('APPLIED', 'REVERTED'))
);

CREATE TABLE data.ontology_object_edit (
    owner_id                    UUID NOT NULL,
    object_type_id              UUID NOT NULL,
    primary_key_value           TEXT NOT NULL,
    origin                      TEXT NOT NULL,
    state                       TEXT NOT NULL,
    title_edited                BOOLEAN NOT NULL DEFAULT FALSE,
    title_value                 TEXT,
    properties_patch            JSONB NOT NULL DEFAULT '{}'::jsonb,
    properties_cleared          TEXT[] NOT NULL DEFAULT '{}',
    primary_key_num             NUMERIC,
    primary_key_date            DATE,
    primary_key_ts              TIMESTAMPTZ,
    source_binding_id           UUID,
    source_dataset_id           UUID,
    source_primary_key_attr_id  UUID,
    source_title_key_attr_id    UUID,
    version                     BIGINT NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID NOT NULL,
    last_execution_id           UUID,
    stale_at                    TIMESTAMPTZ,
    stale_reason                TEXT,
    PRIMARY KEY (owner_id, object_type_id, primary_key_value),
    CONSTRAINT ck_object_edit_origin CHECK (origin IN ('BASE', 'USER')),
    CONSTRAINT ck_object_edit_state CHECK (state IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_object_edit_source CHECK (
        origin = 'USER'
        OR (
            source_binding_id IS NOT NULL
            AND source_dataset_id IS NOT NULL
            AND source_primary_key_attr_id IS NOT NULL
        )
    )
);

CREATE INDEX ix_object_edit_type_state
    ON data.ontology_object_edit (owner_id, object_type_id, state);

CREATE TABLE data.ontology_object_value_edit (
    owner_id            UUID NOT NULL,
    object_type_id      UUID NOT NULL,
    primary_key_value   TEXT NOT NULL,
    attribute_id        UUID NOT NULL,
    value_state         TEXT NOT NULL,
    text_value          TEXT,
    num_value           NUMERIC,
    date_value          DATE,
    ts_value            TIMESTAMPTZ,
    bool_value          BOOLEAN,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID NOT NULL,
    last_execution_id   UUID,
    stale_at            TIMESTAMPTZ,
    stale_reason        TEXT,
    PRIMARY KEY (owner_id, object_type_id, primary_key_value, attribute_id),
    CONSTRAINT ck_object_value_edit_state CHECK (value_state IN ('SET', 'CLEAR')),
    CONSTRAINT ck_object_value_edit_clear CHECK (
        value_state <> 'CLEAR'
        OR (
            text_value IS NULL
            AND num_value IS NULL
            AND date_value IS NULL
            AND ts_value IS NULL
            AND bool_value IS NULL
        )
    )
);

CREATE INDEX ix_object_value_edit_text
    ON data.ontology_object_value_edit (owner_id, object_type_id, attribute_id, text_value);
CREATE INDEX ix_object_value_edit_num
    ON data.ontology_object_value_edit (owner_id, object_type_id, attribute_id, num_value);
CREATE INDEX ix_object_value_edit_date
    ON data.ontology_object_value_edit (owner_id, object_type_id, attribute_id, date_value);
CREATE INDEX ix_object_value_edit_ts
    ON data.ontology_object_value_edit (owner_id, object_type_id, attribute_id, ts_value);
CREATE INDEX ix_object_value_edit_bool
    ON data.ontology_object_value_edit (owner_id, object_type_id, attribute_id, bool_value);

CREATE TABLE data.ontology_relation_edit (
    owner_id             UUID NOT NULL,
    relation_id          UUID NOT NULL,
    from_object_type_id  UUID NOT NULL,
    from_primary_key     TEXT NOT NULL,
    to_object_type_id    UUID NOT NULL,
    to_primary_key       TEXT NOT NULL,
    state                TEXT NOT NULL,
    version              BIGINT NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by           UUID NOT NULL,
    last_execution_id    UUID,
    stale_at             TIMESTAMPTZ,
    stale_reason         TEXT,
    PRIMARY KEY (
        owner_id, relation_id, from_object_type_id, from_primary_key,
        to_object_type_id, to_primary_key
    ),
    CONSTRAINT ck_relation_edit_state CHECK (state IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX ix_relation_edit_from
    ON data.ontology_relation_edit (owner_id, relation_id, from_object_type_id, from_primary_key);
CREATE INDEX ix_relation_edit_to
    ON data.ontology_relation_edit (owner_id, relation_id, to_object_type_id, to_primary_key);

CREATE TABLE data.ontology_edit_change (
    execution_id         UUID NOT NULL REFERENCES data.ontology_edit_execution (id),
    seq                  INTEGER NOT NULL,
    change_kind          TEXT NOT NULL,
    object_type_id       UUID,
    primary_key_value    TEXT,
    attribute_id         UUID,
    relation_id          UUID,
    from_object_type_id  UUID,
    from_primary_key     TEXT,
    to_object_type_id    UUID,
    to_primary_key       TEXT,
    before_value         JSONB,
    after_value          JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (execution_id, seq)
);

CREATE INDEX ix_edit_change_object
    ON data.ontology_edit_change (object_type_id, primary_key_value, created_at DESC);
CREATE INDEX ix_edit_change_relation
    ON data.ontology_edit_change (relation_id, from_primary_key, to_primary_key, created_at DESC);
