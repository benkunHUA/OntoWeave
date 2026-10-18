package com.ontoweave.data.sync.domain;

import java.util.Locale;

public enum SyncTaskStatus {
    ENABLED("enabled"),
    DISABLED("disabled");

    private final String value;

    SyncTaskStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SyncTaskStatus from(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return "disabled".equals(normalized) ? DISABLED : ENABLED;
    }
}
