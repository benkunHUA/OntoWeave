package com.ontoweave.data.security;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface SecretCipher {

    EncryptedSecret encrypt(UUID connectionId, String kind, JsonNode secret);

    JsonNode decrypt(UUID connectionId, String kind, String ciphertext, int keyVersion);
}
