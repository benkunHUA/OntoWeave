package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.pipeline.catalog.NodePortRole;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;

import java.util.UUID;

import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.invalid;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requireId;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requireNodeType;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requireOutputNode;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requirePort;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requirePosition;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.requireText;
import static com.ontoweave.data.pipeline.draft.DraftCommandValidation.validateConfig;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_CONFIG;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_EDGES;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_ID;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_NAME;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_NODES;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_OUTPUT_ID;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_POSITION;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_SCHEMA_VERSION;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_SOURCE_NODE_ID;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_SOURCE_PORT;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_TARGET_NODE_ID;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_TARGET_PORT;
import static com.ontoweave.data.pipeline.draft.DraftConstants.FIELD_TYPE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_ADD_NODE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_CONNECT_NODES;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_DELETE_NODE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_DISCONNECT_NODES;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_SET_OUTPUT;
import static com.ontoweave.data.pipeline.draft.DraftConstants.OP_UPDATE_NODE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.SCHEMA_VERSION;
import static com.ontoweave.data.pipeline.draft.DraftConstants.TARGET_EDGE;
import static com.ontoweave.data.pipeline.draft.DraftConstants.TARGET_NODE;

final class DraftCommandApplier {

    private final PipelineNodeCatalog catalog;
    private final ObjectMapper mapper;

    DraftCommandApplier(PipelineNodeCatalog catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    DraftMutation apply(JsonNode definition, DraftCommand command) {
        if (command instanceof AddNode add) {
            return add(definition, add);
        }
        if (command instanceof UpdateNode update) {
            return update(definition, update);
        }
        if (command instanceof DeleteNode delete) {
            return delete(definition, delete);
        }
        if (command instanceof ConnectNodes connect) {
            return connect(definition, connect);
        }
        if (command instanceof DisconnectNodes disconnect) {
            return disconnect(definition, disconnect);
        }
        throw invalid("SetOutput 由输出配置逻辑处理");
    }

    DraftMutation bindOutput(JsonNode definition, UUID nodeId, UUID outputId) {
        ObjectNode root = root(definition);
        ObjectNode node = requireNode(root, nodeId);
        requireOutputNode(node);
        ObjectNode config = mapper.createObjectNode();
        config.put(FIELD_OUTPUT_ID, outputId.toString());
        node.set(FIELD_CONFIG, config);
        return new DraftMutation(root, OP_SET_OUTPUT, TARGET_NODE, nodeId);
    }

    void validateOutputNode(JsonNode definition, UUID nodeId) {
        ObjectNode root = root(definition);
        requireOutputNode(requireNode(root, nodeId));
    }

    JsonNode moveNode(JsonNode definition, MoveNodeCommand command) {
        if (command == null || command.nodeId() == null) {
            throw invalid("nodeId 不能为空");
        }
        ObjectNode root = root(definition);
        ObjectNode node = requireNode(root, command.nodeId());
        node.set(FIELD_POSITION, requirePosition(command.position()));
        return root;
    }

    JsonNode preservePositions(JsonNode restored, JsonNode current) {
        ObjectNode result = requireDefinition(restored);
        ObjectNode currentRoot = rootOrNull(current);
        if (currentRoot == null) {
            return result;
        }
        java.util.Map<String, JsonNode> positions = new java.util.HashMap<>();
        currentRoot.withArray(FIELD_NODES).forEach(node -> {
            if (node.path(FIELD_ID).isTextual() && node.path(FIELD_POSITION).isObject()) {
                positions.put(node.path(FIELD_ID).asText(), node.path(FIELD_POSITION));
            }
        });
        result.withArray(FIELD_NODES).forEach(node -> {
            JsonNode position = positions.get(node.path(FIELD_ID).asText());
            if (position != null) {
                ((ObjectNode) node).set(FIELD_POSITION, position.deepCopy());
            }
        });
        return result;
    }

    private DraftMutation add(JsonNode definition, AddNode command) {
        ObjectNode root = root(definition);
        requireId(command.nodeId(), "nodeId");
        requireText(command.name(), "name");
        ObjectNode node = mapper.createObjectNode();
        node.put(FIELD_ID, command.nodeId().toString());
        String type = requireNodeType(catalog, command.type());
        node.put(FIELD_TYPE, type);
        node.put(FIELD_NAME, command.name().trim());
        node.set(FIELD_CONFIG, validateConfig(catalog, type, command.config()));
        node.set(FIELD_POSITION, requirePosition(command.position()));
        ArrayNode nodes = root.withArray(FIELD_NODES);
        if (findNode(nodes, command.nodeId()).isPresent()) {
            throw invalid("节点已存在: " + command.nodeId());
        }
        nodes.add(node);
        return new DraftMutation(root, OP_ADD_NODE, TARGET_NODE, command.nodeId());
    }

    private DraftMutation update(JsonNode definition, UpdateNode command) {
        ObjectNode root = root(definition);
        ObjectNode node = requireNode(root, command.nodeId());
        if (command.name() == null && command.config() == null) {
            throw invalid("name 与 config 不能同时为空");
        }
        if (command.name() != null) {
            requireText(command.name(), "name");
            node.put(FIELD_NAME, command.name().trim());
        }
        if (command.config() != null) {
            String type = node.path(FIELD_TYPE).asText();
            node.set(FIELD_CONFIG, validateConfig(catalog, type, command.config()));
        }
        return new DraftMutation(root, OP_UPDATE_NODE, TARGET_NODE, command.nodeId());
    }

    private DraftMutation delete(JsonNode definition, DeleteNode command) {
        ObjectNode root = root(definition);
        requireNode(root, command.nodeId());
        ArrayNode nodes = root.withArray(FIELD_NODES);
        for (int index = nodes.size() - 1; index >= 0; index--) {
            if (command.nodeId().toString().equals(nodes.get(index).path(FIELD_ID).asText())) {
                nodes.remove(index);
            }
        }
        ArrayNode edges = root.withArray(FIELD_EDGES);
        for (int index = edges.size() - 1; index >= 0; index--) {
            JsonNode edge = edges.get(index);
            if (command.nodeId().toString().equals(edge.path(FIELD_SOURCE_NODE_ID).asText())
                    || command.nodeId().toString().equals(edge.path(FIELD_TARGET_NODE_ID).asText())) {
                edges.remove(index);
            }
        }
        return new DraftMutation(root, OP_DELETE_NODE, TARGET_NODE, command.nodeId());
    }

    private DraftMutation connect(JsonNode definition, ConnectNodes command) {
        ObjectNode root = root(definition);
        requireId(command.edgeId(), "edgeId");
        ObjectNode source = requireNode(root, command.sourceNodeId());
        ObjectNode target = requireNode(root, command.targetNodeId());
        requirePort(catalog, source, command.sourcePort(), NodePortRole.OUTPUT);
        requirePort(catalog, target, command.targetPort(), NodePortRole.INPUT);
        ArrayNode edges = root.withArray(FIELD_EDGES);
        if (findEdge(edges, command.edgeId()).isPresent()) {
            throw invalid("连线已存在: " + command.edgeId());
        }
        ObjectNode edge = mapper.createObjectNode();
        edge.put(FIELD_ID, command.edgeId().toString());
        edge.put(FIELD_SOURCE_NODE_ID, command.sourceNodeId().toString());
        edge.put(FIELD_SOURCE_PORT, requireText(command.sourcePort(), "sourcePort"));
        edge.put(FIELD_TARGET_NODE_ID, command.targetNodeId().toString());
        edge.put(FIELD_TARGET_PORT, requireText(command.targetPort(), "targetPort"));
        edges.add(edge);
        return new DraftMutation(root, OP_CONNECT_NODES, TARGET_EDGE, command.edgeId());
    }

    private DraftMutation disconnect(JsonNode definition, DisconnectNodes command) {
        ObjectNode root = root(definition);
        ArrayNode edges = root.withArray(FIELD_EDGES);
        boolean removed = false;
        for (int index = edges.size() - 1; index >= 0; index--) {
            if (command.edgeId() != null
                    && command.edgeId().toString().equals(edges.get(index).path(FIELD_ID).asText())) {
                edges.remove(index);
                removed = true;
            }
        }
        if (!removed) {
            throw invalid("连线不存在: " + command.edgeId());
        }
        return new DraftMutation(root, OP_DISCONNECT_NODES, TARGET_EDGE, command.edgeId());
    }

    private ObjectNode root(JsonNode definition) {
        ObjectNode root = rootOrNull(definition);
        if (root == null) {
            throw invalid("草稿必须是 schemaVersion=1 的对象");
        }
        JsonNode schemaVersion = root.path(FIELD_SCHEMA_VERSION);
        if (!schemaVersion.isIntegralNumber()
                || schemaVersion.asInt() != SCHEMA_VERSION
                || !root.path(FIELD_NODES).isArray()
                || !root.path(FIELD_EDGES).isArray()) {
            throw invalid("草稿必须是 schemaVersion=1 且包含 nodes/edges 数组");
        }
        return root;
    }

    private ObjectNode requireDefinition(JsonNode definition) {
        return root(definition);
    }

    private ObjectNode rootOrNull(JsonNode definition) {
        return definition != null && definition.isObject() ? ((ObjectNode) definition).deepCopy() : null;
    }

    private ObjectNode requireNode(ObjectNode root, UUID nodeId) {
        requireId(nodeId, "nodeId");
        return findNode(root.withArray(FIELD_NODES), nodeId)
                .orElseThrow(() -> invalid("节点不存在: " + nodeId));
    }

    private java.util.Optional<ObjectNode> findNode(ArrayNode nodes, UUID nodeId) {
        for (JsonNode node : nodes) {
            if (nodeId.toString().equals(node.path(FIELD_ID).asText()) && node.isObject()) {
                return java.util.Optional.of((ObjectNode) node);
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<JsonNode> findEdge(ArrayNode edges, UUID edgeId) {
        for (JsonNode edge : edges) {
            if (edgeId.toString().equals(edge.path(FIELD_ID).asText())) {
                return java.util.Optional.of(edge);
            }
        }
        return java.util.Optional.empty();
    }

    record DraftMutation(JsonNode after, String opType, String targetType, UUID targetId) {
    }
}
