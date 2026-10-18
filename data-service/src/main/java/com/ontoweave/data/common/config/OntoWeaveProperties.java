package com.ontoweave.data.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ontoweave")
public record OntoWeaveProperties(@NotBlank String internalToken) {
}
