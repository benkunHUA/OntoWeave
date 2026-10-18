package com.ontoweave.data.connection.domain;

import com.ontoweave.data.common.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum ConnectionKind {
    MYSQL("mysql");

    private final String value;

    ConnectionKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ConnectionKind from(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (ConnectionKind kind : values()) {
            if (kind.value.equals(normalized)) {
                return kind;
            }
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_CONNECTION_KIND",
                "暂不支持的连接类型",
                java.util.Map.of("kind", value == null ? "" : value)
        );
    }
}
