package com.ontoweave.data.ontology.binding.api;

import com.ontoweave.data.ontology.binding.api.dto.BindableDatasetPageResponse;
import com.ontoweave.data.ontology.binding.api.dto.DatasetColumnResponse;
import com.ontoweave.data.ontology.binding.service.OntologyDatasetService;
import com.ontoweave.data.ontology.common.DatasetKind;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class OntologyDatasetController {

    private final OntologyDatasetService datasets;

    public OntologyDatasetController(OntologyDatasetService datasets) {
        this.datasets = datasets;
    }

    @GetMapping("/internal/v1/ontology/bindable-datasets")
    public BindableDatasetPageResponse list(
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return datasets.list(ownerId, keyword, page, size);
    }

    @GetMapping("/internal/v1/ontology/bindable-datasets/{kind}/{datasetId}/columns")
    public List<DatasetColumnResponse> columns(
            @PathVariable DatasetKind kind,
            @PathVariable UUID datasetId,
            @RequestParam UUID ownerId
    ) {
        return datasets.columns(kind, datasetId, ownerId);
    }
}
