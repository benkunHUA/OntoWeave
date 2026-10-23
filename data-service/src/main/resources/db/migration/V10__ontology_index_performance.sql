CREATE INDEX ix_relation_stage_pair
    ON data.ontology_relation_stage
        (run_id, relation_id, from_primary_key, to_primary_key);

CREATE INDEX ix_object_value_primary_key
    ON data.ontology_object_value
        (version_id, primary_key_value, attribute_id);
