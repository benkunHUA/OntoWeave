package com.ontoweave.data.sync.domain;

public enum VersionSource {
    SNAPSHOT_END("snapshot_end"),
    SCHEDULED("scheduled"),
    STOP("stop");

    private final String value;

    VersionSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
