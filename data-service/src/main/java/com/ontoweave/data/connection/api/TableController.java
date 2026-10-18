package com.ontoweave.data.connection.api;

import com.ontoweave.data.connection.api.dto.TableDetailResponse;
import com.ontoweave.data.connection.api.dto.TableListResponse;
import com.ontoweave.data.connection.service.ConnectionMaterialService;
import com.ontoweave.data.connector.mysql.MysqlTableDiscovery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/data-connections")
public class TableController {

    private final ConnectionMaterialService materials;
    private final MysqlTableDiscovery discovery;

    public TableController(ConnectionMaterialService materials, MysqlTableDiscovery discovery) {
        this.materials = materials;
        this.discovery = discovery;
    }

    @GetMapping("/{id}/tables")
    public TableListResponse list(@PathVariable UUID id, @RequestParam UUID ownerId) {
        var material = materials.loadMysql(id, ownerId);
        return discovery.list(material.config(), material.password());
    }

    @GetMapping("/{id}/tables/{schema}/{table}")
    public TableDetailResponse describe(
            @PathVariable UUID id,
            @PathVariable String schema,
            @PathVariable String table,
            @RequestParam UUID ownerId
    ) {
        var material = materials.loadMysql(id, ownerId);
        return discovery.describe(material.config(), material.password(), schema, table);
    }
}
