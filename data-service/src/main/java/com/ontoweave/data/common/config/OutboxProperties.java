package com.ontoweave.data.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ontoweave.outbox")
public record OutboxProperties(
        boolean enabled,
        @NotBlank String exchange,
        @Min(100) long pollIntervalMs
) {
}
