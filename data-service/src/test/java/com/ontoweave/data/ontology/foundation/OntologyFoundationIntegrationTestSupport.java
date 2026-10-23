package com.ontoweave.data.ontology.foundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class OntologyFoundationIntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper mapper;

    @BeforeEach
    void cleanOntologyFoundation() {
        jdbc.execute("""
                TRUNCATE TABLE
                    data.ontology_object_stage,
                    data.ontology_object_value_stage,
                    data.ontology_relation_stage,
                    data.ontology_object_instance,
                    data.ontology_object_value,
                    data.ontology_relation_instance,
                    data.ontology_object_version,
                    data.ontology_relation_version,
                    data.ontology_index_run,
                    data.ontology_object_binding,
                    data.ontology_relation_binding,
                    data.ontology_relation_definition
                CASCADE
                """);
    }
}
