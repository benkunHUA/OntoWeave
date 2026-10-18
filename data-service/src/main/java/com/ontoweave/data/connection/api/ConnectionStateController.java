package com.ontoweave.data.connection.api;

import com.ontoweave.data.connection.api.dto.DataConnectionStateResponse;
import com.ontoweave.data.connection.service.ConnectionStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/data-connections")
public class ConnectionStateController {

    private final ConnectionStateService service;

    public ConnectionStateController(ConnectionStateService service) {
        this.service = service;
    }

    @GetMapping("/{id}/state")
    public DataConnectionStateResponse state(
            @PathVariable UUID id,
            @RequestParam UUID ownerId
    ) {
        return service.state(id, ownerId);
    }
}
