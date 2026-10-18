package com.ontoweave.data.sync.domain;

import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum SyncMode {
    SNAPSHOT("snapshot"),
    CDC("cdc");

    private final String value;

    SyncMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SyncMode from(String value) {
        String normalized = value == null || value.isBlank() ? "cdc" : value.toLowerCase(Locale.ROOT);
        for (SyncMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "syncMode 只支持 snapshot 或 cdc");
    }
}
