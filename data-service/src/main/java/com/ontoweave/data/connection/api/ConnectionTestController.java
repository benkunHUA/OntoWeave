package com.ontoweave.data.connection.api;

import com.ontoweave.data.connection.api.dto.ConnectionTestResponse;
import com.ontoweave.data.connection.service.ConnectionTestService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/data-connections")
public class ConnectionTestController {

    private final ConnectionTestService service;

    public ConnectionTestController(ConnectionTestService service) {
        this.service = service;
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResponse test(
            @PathVariable UUID id,
            @RequestParam UUID ownerId,
            @RequestHeader(value = "X-Operation-Id", required = false) UUID operationId
    ) {
        return service.test(id, ownerId, operationId);
    }
}
