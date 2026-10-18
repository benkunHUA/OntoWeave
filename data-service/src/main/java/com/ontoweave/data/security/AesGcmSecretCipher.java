package com.ontoweave.data.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.SecretProperties;
import com.ontoweave.data.common.web.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, SecretKeySpec> keys = new HashMap<>();

    public AesGcmSecretCipher(SecretProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadKeys() {
        properties.keys().forEach((version, encoded) -> {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            if (keyBytes.length != 32) {
                throw new IllegalStateException("secret key version " + version + " must be 32 bytes");
            }
            keys.put(version, new SecretKeySpec(keyBytes, "AES"));
        });
        if (!keys.containsKey(properties.currentVersion())) {
            throw new IllegalStateException("current secret key version is not configured");
        }
    }

    @Override
    public EncryptedSecret encrypt(UUID connectionId, String kind, JsonNode secret) {
        byte[] plaintext = null;
        try {
            plaintext = objectMapper.writeValueAsBytes(secret);
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey(), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(connectionId, kind));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return new EncryptedSecret(Base64.getEncoder().encodeToString(combined), properties.currentVersion());
        } catch (JsonProcessingException | GeneralSecurityException exception) {
            throw secretError(exception);
        } finally {
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    @Override
    public JsonNode decrypt(UUID connectionId, String kind, String ciphertext, int keyVersion) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(keyVersion), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(connectionId, kind));
            return objectMapper.readTree(cipher.doFinal(encrypted));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw secretError(exception);
        }
    }

    private SecretKeySpec currentKey() {
        return key(properties.currentVersion());
    }

    private SecretKeySpec key(int version) {
        SecretKeySpec key = keys.get(version);
        if (key == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SECRET_KEY_UNAVAILABLE",
                    "凭据密钥版本不可用"
            );
        }
        return key;
    }

    private byte[] aad(UUID connectionId, String kind) {
        return (connectionId + ":" + kind).getBytes(StandardCharsets.UTF_8);
    }

    private ApiException secretError(Exception cause) {
        return new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SECRET_ERROR",
                "凭据加解密失败: " + cause.getClass().getSimpleName()
        );
    }
}
