package com.ontoweave.data.security;

public record EncryptedSecret(String ciphertext, int keyVersion) {
}
