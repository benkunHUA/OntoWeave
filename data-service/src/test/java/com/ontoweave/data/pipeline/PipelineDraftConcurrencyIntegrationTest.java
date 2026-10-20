package com.ontoweave.data.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.draft.MoveNodeCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineDraftConcurrencyIntegrationTest extends PipelineDraftIntegrationTestSupport {

    @Test
    void concurrentMovesPreserveBothNodePositions() throws Exception {
        UUID firstNode = UUID.randomUUID();
        UUID secondNode = UUID.randomUUID();
        insertDraft(draftWithNodes(firstNode, secondNode), 4);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(moveAfterStart(
                    ready, start, firstNode, mapper.valueToTree(Map.of("x", 100, "y", 110))
            ));
            Future<?> second = executor.submit(moveAfterStart(
                    ready, start, secondNode, mapper.valueToTree(Map.of("x", 200, "y", 210))
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        JsonNode definition = drafts.findById(pipelineId).orElseThrow().getDefinition();
        assertThat(position(definition, firstNode).path("x").asInt()).isEqualTo(100);
        assertThat(position(definition, firstNode).path("y").asInt()).isEqualTo(110);
        assertThat(position(definition, secondNode).path("x").asInt()).isEqualTo(200);
        assertThat(position(definition, secondNode).path("y").asInt()).isEqualTo(210);
        assertThat(revision()).isEqualTo(4);
    }

    private java.util.concurrent.Callable<Void> moveAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            UUID nodeId,
            JsonNode position
    ) {
        return () -> {
            ready.countDown();
            start.await();
            service.moveNode(pipelineId, ownerId, new MoveNodeCommand(nodeId, position));
            return null;
        };
    }

    private ObjectNode draftWithNodes(UUID firstNode, UUID secondNode) {
        ObjectNode definition = emptyDraft();
        definition.withArray("nodes").add(node(firstNode));
        definition.withArray("nodes").add(node(secondNode));
        return definition;
    }

    private ObjectNode node(UUID nodeId) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", nodeId.toString());
        node.put("type", "filter");
        node.put("name", "Filter");
        node.set("config", mapper.createObjectNode());
        node.set("position", mapper.valueToTree(Map.of("x", 1, "y", 2)));
        return node;
    }

    private JsonNode position(JsonNode definition, UUID nodeId) {
        for (JsonNode node : definition.path("nodes")) {
            if (nodeId.toString().equals(node.path("id").asText())) {
                return node.path("position");
            }
        }
        throw new AssertionError("missing node " + nodeId);
    }
}
