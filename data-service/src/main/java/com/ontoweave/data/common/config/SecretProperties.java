package com.ontoweave.data.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "ontoweave.secret")
public record SecretProperties(
        @Min(1) int currentVersion,
        @NotEmpty Map<Integer, String> keys
) {
}
