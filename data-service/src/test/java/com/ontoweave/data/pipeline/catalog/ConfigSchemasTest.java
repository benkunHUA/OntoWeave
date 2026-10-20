package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ConfigSchemasTest {

    private final PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsStructurallyValidAndEmptyConfigs() throws Exception {
        for (NodeDefinition node : catalog.nodes()) {
            ConfigSchemas.validate(node, mapper.readTree("{}"));
        }
        ConfigSchemas.validate(catalog.require("filter"), mapper.readTree("""
                {"condition":{"kind":"compare","field":"status","op":"eq","value":"active"}}
                """));
        ConfigSchemas.validate(catalog.require("derive_column"), mapper.readTree("""
                {"outputField":"tax","expression":{"kind":"literal","value":0.13,"dataType":"number"}}
                """));
    }

    @Test
    void rejectsInvalidStructureAsConfigInvalid() throws Exception {
        assertInvalid(catalog.require("filter"), """
                {"condition":{"kind":"compare","field":"status","op":"contains","value":"active"}}
                """);
        assertInvalid(catalog.require("union"), """
                {"sql":"select * from users"}
                """);
        assertInvalid(catalog.require("derive_column"), """
                {"outputField":"tax","expression":{"kind":"literal","value":0.13}}
                """);
    }

    private void assertInvalid(NodeDefinition node, String config) throws Exception {
        ApiException exception = catchThrowableOfType(
                ApiException.class,
                () -> ConfigSchemas.validate(node, mapper.readTree(config))
        );
        assertThat(exception).isNotNull();
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.code()).isEqualTo("CONFIG_INVALID");
        assertThat(exception.details()).containsKey("errors");
    }
}
