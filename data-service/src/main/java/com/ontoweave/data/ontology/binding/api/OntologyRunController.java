package com.ontoweave.data.ontology.binding.api;

import com.ontoweave.data.ontology.binding.api.dto.IndexRunResponse;
import com.ontoweave.data.ontology.binding.service.BindingRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class OntologyRunController {

    private final BindingRunService runs;

    public OntologyRunController(BindingRunService runs) {
        this.runs = runs;
    }

    @GetMapping("/internal/v1/ontology/runs/{runId}")
    public IndexRunResponse get(
            @PathVariable UUID runId,
            @RequestParam UUID ownerId
    ) {
        return runs.run(runId, ownerId);
    }

    @PostMapping("/internal/v1/ontology/runs/{runId}/cancel")
    public IndexRunResponse cancel(
            @PathVariable UUID runId,
            @RequestParam UUID ownerId
    ) {
        return runs.cancel(runId, ownerId);
    }
}
