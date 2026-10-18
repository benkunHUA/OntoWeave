package com.ontoweave.data.connection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_connection_secret", schema = "data")
public class DataConnectionSecretEntity {

    @Id
    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(nullable = false)
    private String ciphertext;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DataConnectionSecretEntity() {
    }

    public DataConnectionSecretEntity(UUID connectionId, String ciphertext, int keyVersion) {
        this.connectionId = connectionId;
        this.ciphertext = ciphertext;
        this.keyVersion = keyVersion;
    }

    public void replace(String ciphertext, int keyVersion) {
        this.ciphertext = ciphertext;
        this.keyVersion = keyVersion;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
