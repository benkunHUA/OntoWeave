package com.ontoweave.data.connection.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.connection.domain.ConnectionKind;

public interface ConnectionConfigValidator {

    ConnectionKind kind();

    JsonNode validate(JsonNode config);
}
