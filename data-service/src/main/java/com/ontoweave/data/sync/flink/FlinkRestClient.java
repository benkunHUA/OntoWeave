package com.ontoweave.data.sync.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.common.config.SyncProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Optional;

@Component
public class FlinkRestClient {

    private final RestClient client;

    public FlinkRestClient(SyncProperties properties) {
        this.client = RestClient.builder().baseUrl(properties.flinkRestUrl()).build();
    }

    public Optional<FlinkJobInfo> findJobByName(String name) {
        try {
            JsonNode body = client.get().uri("/jobs/overview").retrieve().body(JsonNode.class);
            if (body == null) {
                return Optional.empty();
            }
            for (JsonNode job : body.path("jobs")) {
                if (name.equals(job.path("name").asText())) {
                    return Optional.of(new FlinkJobInfo(
                            job.path("jid").asText(),
                            job.path("name").asText(),
                            job.path("state").asText()
                    ));
                }
            }
            return Optional.empty();
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    public Optional<FlinkJobInfo> job(String jobId) {
        try {
            JsonNode body = client.get().uri("/jobs/{id}", jobId).retrieve().body(JsonNode.class);
            if (body == null) {
                return Optional.empty();
            }
            return Optional.of(new FlinkJobInfo(
                    body.path("jid").asText(jobId),
                    body.path("name").asText(""),
                    body.path("state").asText()
            ));
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    public Optional<FlinkCheckpointInfo> latestCompletedCheckpoint(String jobId) {
        try {
            JsonNode body = client.get().uri("/jobs/{id}/checkpoints", jobId)
                    .retrieve().body(JsonNode.class);
            JsonNode completed = body == null ? null : body.path("latest").path("completed");
            if (completed == null || completed.isMissingNode() || completed.isNull()) {
                return Optional.empty();
            }
            long id = completed.path("id").asLong();
            long timestamp = completed.path("latest_ack_timestamp").asLong();
            return Optional.of(new FlinkCheckpointInfo(
                    id,
                    Instant.ofEpochMilli(timestamp),
                    completed.path("external_path").asText(null)
            ));
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }

    public void cancel(String jobId) {
        try {
            client.patch().uri("/jobs/{id}?mode=cancel", jobId).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new FlinkSubmitException("FLINK_UNAVAILABLE", "Flink 取消作业失败");
        }
    }

    public Optional<String> stopWithSavepoint(String jobId, String targetDirectory) {
        try {
            JsonNode body = client.post()
                    .uri(uri -> uri.path("/jobs/{id}/stop")
                            .queryParam("target-directory", targetDirectory)
                            .queryParam("drain", false)
                            .build(jobId))
                    .retrieve()
                    .body(JsonNode.class);
            return body == null ? Optional.empty() : Optional.of(body.path("request-id").asText());
        } catch (RestClientException exception) {
            return Optional.empty();
        }
    }
}
