package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.pipeline.api.PipelineNodeCatalogController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineNodeCatalogControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsSameVersionInBodyAndEtag() throws Exception {
        PipelineNodeCatalog catalog = PipelineNodeCatalog.defaultCatalog();
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new PipelineNodeCatalogController(catalog))
                .build();

        MvcResult result = mvc.perform(get("/internal/v1/pipeline-node-catalog"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsByteArray());
        String version = body.path("catalogVersion").asText();
        assertThat(version).isEqualTo(catalog.catalogVersion());
        assertThat(body.path("nodes")).hasSize(catalog.nodes().size());
        assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"" + version + "\"");
    }
}
