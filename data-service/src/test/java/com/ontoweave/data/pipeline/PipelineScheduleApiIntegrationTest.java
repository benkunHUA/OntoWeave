package com.ontoweave.data.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PipelineScheduleApiIntegrationTest extends PipelineScheduleIntegrationTestSupport {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mvc;

    @Test
    void usesOwnerScopedScheduleRoutesAndValidatesPagination() throws Exception {
        PublishedPipeline published = publishedPipeline();

        mvc.perform(put("/internal/v1/pipelines/{pipelineId}/schedule", published.pipelineId())
                        .header(TOKEN_HEADER, TOKEN)
                        .param("ownerId", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cronExpression\":\"*/5 * * * *\",\"timezone\":\"UTC\",\"name\":\"five\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.triggerType").value("time"))
                .andExpect(jsonPath("$.name").value("five"));

        mvc.perform(get("/internal/v1/pipelines/{pipelineId}/schedule", published.pipelineId())
                        .header(TOKEN_HEADER, TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineId").value(published.pipelineId().toString()));

        mvc.perform(post("/internal/v1/pipelines/{pipelineId}/schedule/pause", published.pipelineId())
                        .header(TOKEN_HEADER, TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"))
                .andExpect(jsonPath("$.nextRunAt").doesNotExist());

        mvc.perform(post("/internal/v1/pipelines/{pipelineId}/schedule/resume", published.pipelineId())
                        .header(TOKEN_HEADER, TOKEN)
                        .param("ownerId", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.nextRunAt").isNotEmpty());

        mvc.perform(get("/internal/v1/pipelines/{pipelineId}/schedule/runs", published.pipelineId())
                        .header(TOKEN_HEADER, TOKEN)
                        .param("ownerId", ownerId.toString())
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/internal/v1/pipelines/{pipelineId}/schedule", published.pipelineId())
                        .header(TOKEN_HEADER, TOKEN)
                        .param("ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }
}
