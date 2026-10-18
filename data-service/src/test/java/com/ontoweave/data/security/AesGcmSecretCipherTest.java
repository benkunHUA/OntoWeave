package com.ontoweave.data.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ontoweave.data.common.config.SecretProperties;
import com.ontoweave.data.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AesGcmSecretCipher cipher;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) index;
        }
        SecretProperties properties = new SecretProperties(
                1,
                Map.of(1, Base64.getEncoder().encodeToString(key))
        );
        cipher = new AesGcmSecretCipher(properties, objectMapper);
        cipher.loadKeys();
    }

    @Test
    void encryptsAndDecryptsWithConnectionAndKindAsAad() {
        UUID connectionId = UUID.randomUUID();
        ObjectNode secret = objectMapper.createObjectNode().put("password", "p@ssword");

        EncryptedSecret encrypted = cipher.encrypt(connectionId, "mysql", secret);
        var decrypted = cipher.decrypt(connectionId, "mysql", encrypted.ciphertext(), encrypted.keyVersion());

        assertThat(decrypted.path("password").asText()).isEqualTo("p@ssword");
        assertThat(encrypted.ciphertext()).doesNotContain("p@ssword");
        assertThat(encrypted.keyVersion()).isEqualTo(1);
    }

    @Test
    void refusesCiphertextCopiedToAnotherConnection() {
        ObjectNode secret = objectMapper.createObjectNode().put("password", "p@ssword");
        EncryptedSecret encrypted = cipher.encrypt(UUID.randomUUID(), "mysql", secret);

        assertThatThrownBy(() -> cipher.decrypt(
                UUID.randomUUID(),
                "mysql",
                encrypted.ciphertext(),
                encrypted.keyVersion()
        )).isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("SECRET_ERROR");
    }
}
