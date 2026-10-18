package com.ontoweave.data.sync.api;

import com.ontoweave.data.sync.api.dto.SyncRunResponse;
import com.ontoweave.data.sync.api.dto.SyncRunsResponse;
import com.ontoweave.data.sync.api.dto.TriggerSyncRequest;
import com.ontoweave.data.sync.service.SyncRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
public class SyncRunController {

    private final SyncRunService service;

    public SyncRunController(SyncRunService service) {
        this.service = service;
    }

    @PostMapping("/data-connections/{connectionId}/sync-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncRunsResponse trigger(
            @PathVariable UUID connectionId,
            @RequestParam UUID ownerId,
            @Valid @RequestBody TriggerSyncRequest request
    ) {
        return service.trigger(connectionId, ownerId, request);
    }

    @GetMapping("/sync-runs/{id}")
    public SyncRunResponse get(@PathVariable UUID id, @RequestParam UUID ownerId) {
        return service.get(id, ownerId);
    }

    @PostMapping("/sync-runs/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncRunResponse stop(@PathVariable UUID id, @RequestParam UUID ownerId) {
        return service.stop(id, ownerId);
    }
}
