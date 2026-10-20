package com.ontoweave.data.pipeline.catalog;

public record NodePort(
        String id,
        NodePortRole role,
        String dataType,
        boolean required,
        boolean multiple
) {
}
