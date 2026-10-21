package com.ontoweave.data.pipeline.api.dto;

import com.ontoweave.data.pipeline.service.PipelineResponse;

import java.util.List;

public record PipelinePageResponse(
        List<PipelineResponse> items,
        long total,
        int limit,
        int offset
) {

    public PipelinePageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
