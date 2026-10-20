package com.ontoweave.data.sync.repository;

import com.ontoweave.data.sync.domain.SyncRunStatus;

import java.time.Instant;
import java.util.UUID;

public record ConnectionRunSummary(UUID connectionId, SyncRunStatus status, Instant at) {
}
