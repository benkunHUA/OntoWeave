package com.ontoweave.data.connection.config;

import com.fasterxml.jackson.databind.JsonNode;

public record MysqlConfig(
        String host,
        int port,
        String database,
        String username,
        boolean useSsl
) {

    public static MysqlConfig from(JsonNode node) {
        return new MysqlConfig(
                node.path("host").asText(),
                node.path("port").asInt(3306),
                node.path("database").asText(),
                node.path("username").asText(),
                node.path("useSsl").asBoolean(false)
        );
    }
}
