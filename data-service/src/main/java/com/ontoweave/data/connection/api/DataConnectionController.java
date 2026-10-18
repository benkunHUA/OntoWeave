package com.ontoweave.data.connection.api;

import com.ontoweave.data.connection.api.dto.CreateDataConnectionRequest;
import com.ontoweave.data.connection.api.dto.DataConnectionPageResponse;
import com.ontoweave.data.connection.api.dto.DataConnectionResponse;
import com.ontoweave.data.connection.api.dto.UpdateDataConnectionRequest;
import com.ontoweave.data.connection.service.DataConnectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/v1/data-connections")
public class DataConnectionController {

    private final DataConnectionService service;

    public DataConnectionController(DataConnectionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataConnectionResponse create(@Valid @RequestBody CreateDataConnectionRequest request) {
        return service.create(request);
    }

    @GetMapping
    public DataConnectionPageResponse list(
            @RequestParam @NotNull UUID ownerId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return service.list(ownerId, limit, offset);
    }

    @GetMapping("/{id}")
    public DataConnectionResponse get(
            @PathVariable UUID id,
            @RequestParam UUID ownerId
    ) {
        return service.get(id, ownerId);
    }

    @PatchMapping("/{id}")
    public DataConnectionResponse update(
            @PathVariable UUID id,
            @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Operation-Id", required = false) UUID operationId,
            @Valid @RequestBody UpdateDataConnectionRequest request
    ) {
        return service.update(id, ownerId, request, operationId);
    }
}
