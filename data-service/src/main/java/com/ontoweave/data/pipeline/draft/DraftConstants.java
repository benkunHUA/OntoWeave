package com.ontoweave.data.pipeline.draft;

public final class DraftConstants {

    public static final int SCHEMA_VERSION = 1;
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_NODES = "nodes";
    public static final String FIELD_EDGES = "edges";
    public static final String FIELD_ID = "id";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_CONFIG = "config";
    public static final String FIELD_POSITION = "position";
    public static final String FIELD_X = "x";
    public static final String FIELD_Y = "y";
    public static final String FIELD_SOURCE_NODE_ID = "sourceNodeId";
    public static final String FIELD_SOURCE_PORT = "sourcePort";
    public static final String FIELD_TARGET_NODE_ID = "targetNodeId";
    public static final String FIELD_TARGET_PORT = "targetPort";
    public static final String FIELD_OUTPUT_ID = "outputId";

    public static final double DEFAULT_NODE_POSITION_X = 0;
    public static final double DEFAULT_NODE_POSITION_Y = 0;
    public static final double NODE_HORIZONTAL_SPACING = 240;

    public static final String WRITE_MODE_SNAPSHOT = "snapshot";

    public static final String EVENT_AGGREGATE_TYPE = "pipeline";
    public static final String EVENT_DRAFT_CHANGED = "pipeline.draft.changed";

    public static final String OP_ADD_NODE = "add_node";
    public static final String OP_UPDATE_NODE = "update_node";
    public static final String OP_DELETE_NODE = "delete_node";
    public static final String OP_CONNECT_NODES = "connect_nodes";
    public static final String OP_DISCONNECT_NODES = "disconnect_nodes";
    public static final String OP_SET_OUTPUT = "set_output";
    public static final String OP_UNDO_TURN = "undo_turn";

    public static final String TARGET_NODE = "node";
    public static final String TARGET_EDGE = "edge";
    public static final String TARGET_OUTPUT = "output";
    public static final String TARGET_TURN = "turn";

    public static final String CODE_CONFIG_INVALID = "CONFIG_INVALID";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String CODE_OUTPUT_NAME_CONFLICT = "OUTPUT_NAME_CONFLICT";
    public static final String CODE_UNDO_NOT_LATEST = "UNDO_NOT_LATEST";

    private DraftConstants() {
    }
}
