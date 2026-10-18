package com.ontoweave.data.sync.api;

import com.ontoweave.data.sync.api.dto.DatasetResponse;
import com.ontoweave.data.sync.api.dto.DatasetPreviewResponse;
import com.ontoweave.data.sync.service.DatasetService;
import com.ontoweave.data.sync.service.DatasetPreviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/datasets")
public class DatasetController {

    private final DatasetService service;
    private final DatasetPreviewService previews;

    public DatasetController(DatasetService service, DatasetPreviewService previews) {
        this.service = service;
        this.previews = previews;
    }

    @GetMapping("/{id}")
    public DatasetResponse get(@PathVariable UUID id, @RequestParam UUID ownerId) {
        return service.get(id, ownerId);
    }

    @GetMapping("/{id}/preview")
    public DatasetPreviewResponse preview(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID versionId,
            @RequestParam UUID ownerId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return previews.preview(id, versionId, ownerId, limit);
    }
}
