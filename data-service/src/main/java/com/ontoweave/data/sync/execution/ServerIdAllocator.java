package com.ontoweave.data.sync.execution;

import com.ontoweave.data.common.config.SyncProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ServerIdAllocator {

    private final JdbcTemplate jdbc;
    private final SyncProperties properties;

    public ServerIdAllocator(JdbcTemplate jdbc, SyncProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public long[] allocate() {
        int size = Math.max(properties.serverIdBlockSize(), properties.parallelism() + 2);
        Long base = jdbc.queryForObject("select nextval('data.sync_server_id_seq')", Long.class);
        long start = (base == null ? 1L : base) * size;
        return new long[]{start, start + size - 1L};
    }
}
