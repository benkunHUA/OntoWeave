package com.ontoweave.data.ontology.edit;

import com.ontoweave.data.ontology.query.OntologyQueryIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditOverlayMigrationTest extends OntologyQueryIntegrationTestSupport {

    @Test
    void migrationCreatesOverlayTables() {
        assertThat(count("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'data'
                  AND table_name IN (
                      'ontology_edit_execution', 'ontology_object_edit',
                      'ontology_object_value_edit', 'ontology_relation_edit',
                      'ontology_edit_change'
                  )
                """)).isEqualTo(5);
        assertThat(count("""
                SELECT count(*) FROM pg_constraint
                WHERE conname = 'uq_edit_execution_idempotency'
                """)).isEqualTo(1);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
