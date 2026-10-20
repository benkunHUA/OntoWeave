package com.ontoweave.data.pipeline.api;

import com.ontoweave.data.pipeline.catalog.NodeCatalogResponse;
import com.ontoweave.data.pipeline.catalog.PipelineNodeCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/pipeline-node-catalog")
public class PipelineNodeCatalogController {

    private final PipelineNodeCatalog catalog;

    public PipelineNodeCatalogController(PipelineNodeCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<NodeCatalogResponse> get() {
        NodeCatalogResponse body = catalog.response();
        return ResponseEntity.ok()
                .eTag("\"" + body.catalogVersion() + "\"")
                .body(body);
    }
}
