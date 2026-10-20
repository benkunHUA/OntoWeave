package com.ontoweave.data.pipeline.catalog;

import java.util.List;

public record NodeCatalogResponse(String catalogVersion, List<NodeDefinition> nodes) {

    public NodeCatalogResponse {
        nodes = List.copyOf(nodes);
    }
}
