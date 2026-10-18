package com.ontoweave.data.connection.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.domain.ConnectionKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysqlMaterialValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MysqlConfigValidator configValidator = new MysqlConfigValidator();
    private final MysqlSecretValidator secretValidator = new MysqlSecretValidator();

    @Test
    void fillsMysqlDefaults() throws Exception {
        var config = configValidator.validate(objectMapper.readTree("""
                {"host":"127.0.0.1","database":"demo","username":"reader"}
                """));

        assertThat(config.path("port").asInt()).isEqualTo(3306);
        assertThat(config.path("useSsl").asBoolean()).isFalse();
    }

    @Test
    void rejectsIncompleteConfig() throws Exception {
        assertThatThrownBy(() -> configValidator.validate(objectMapper.readTree("""
                {"host":"127.0.0.1"}
                """))).isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsMissingPassword() throws Exception {
        assertThatThrownBy(() -> secretValidator.validate(objectMapper.readTree("{}")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validatesThroughMaterialValidator() throws Exception {
        ConnectionMaterialValidator materials = new ConnectionMaterialValidator(
                java.util.List.of(configValidator),
                java.util.List.of(secretValidator)
        );
        var secret = materials.validateSecret(
                ConnectionKind.MYSQL,
                objectMapper.readTree("""
                        {"password":"secret"}
                        """)
        );

        assertThat(secret.path("password").asText()).isEqualTo("secret");
    }
}
