package com.ontoweave.data.sync.api;

import com.ontoweave.data.sync.api.dto.CreateSyncTasksRequest;
import com.ontoweave.data.sync.api.dto.SyncTasksResponse;
import com.ontoweave.data.sync.service.SyncTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/data-connections/{connectionId}/sync-tasks")
public class SyncTaskController {

    private final SyncTaskService service;

    public SyncTaskController(SyncTaskService service) {
        this.service = service;
    }

    @PostMapping
    public SyncTasksResponse create(
            @PathVariable UUID connectionId,
            @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Operation-Id", required = false) UUID operationId,
            @Valid @RequestBody CreateSyncTasksRequest request
    ) {
        return service.create(connectionId, ownerId, request, operationId);
    }

    @GetMapping
    public SyncTasksResponse list(@PathVariable UUID connectionId, @RequestParam UUID ownerId) {
        return service.list(connectionId, ownerId);
    }
}
