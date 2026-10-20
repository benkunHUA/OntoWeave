package com.ontoweave.data.pipeline.check;

public final class PipelineIssueCodes {

    public static final String INPUT_MISSING = "INPUT_MISSING";
    public static final String INPUT_VERSION_UNRESOLVED = "INPUT_VERSION_UNRESOLVED";
    public static final String OUTPUT_MISSING = "OUTPUT_MISSING";
    public static final String OUTPUT_NOT_CONNECTED = "OUTPUT_NOT_CONNECTED";
    public static final String NODE_CONFIG_INCOMPLETE = "NODE_CONFIG_INCOMPLETE";
    public static final String CYCLE_DETECTED = "CYCLE_DETECTED";
    public static final String SCHEMA_MISMATCH = "SCHEMA_MISMATCH";
    public static final String FIELD_NOT_FOUND = "FIELD_NOT_FOUND";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String PRIMARY_KEY_INVALID = "PRIMARY_KEY_INVALID";
    public static final String EXPECTATION_INVALID = "EXPECTATION_INVALID";
    public static final String EDGE_PORT_INVALID = "EDGE_PORT_INVALID";
    public static final String PORT_OCCUPIED = "PORT_OCCUPIED";
    public static final String DUPLICATE_FIELD = "DUPLICATE_FIELD";
    public static final String AGGREGATION_INVALID = "AGGREGATION_INVALID";
    public static final String CONFIG_INVALID = "CONFIG_INVALID";
    public static final String NODE_TYPE_UNKNOWN = "NODE_TYPE_UNKNOWN";

    public static final String PHASE_STRUCTURE = "structure";
    public static final String PHASE_GRAPH = "graph";
    public static final String PHASE_SCHEMA = "schema";

    private PipelineIssueCodes() {
    }
}
