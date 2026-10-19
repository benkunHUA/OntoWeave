package com.ontoweave.data.sync.domain;

import java.util.List;
import java.util.Locale;

public enum SyncRunStatus {
    QUEUED("queued"),
    PREPARING("preparing"),
    SUBMITTED("submitted"),
    RUNNING("running"),
    STOPPING("stopping"),
    SUCCEEDED("succeeded"),
    STOPPED("stopped"),
    FAILED("failed");

    public static final List<SyncRunStatus> ACTIVE = List.of(
            QUEUED, PREPARING, SUBMITTED, RUNNING, STOPPING
    );

    public static final List<SyncRunStatus> FLINK_ACTIVE = List.of(
            PREPARING, SUBMITTED, RUNNING, STOPPING
    );

    private final String value;

    SyncRunStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean terminal() {
        return this == SUCCEEDED || this == STOPPED || this == FAILED;
    }

    public static SyncRunStatus from(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (SyncRunStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }
        return FAILED;
    }
}
