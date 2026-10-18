package com.ontoweave.data.connection.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_connection", schema = "data")
public class DataConnectionEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private ConnectionKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode config;

    @Column(name = "config_version", nullable = false)
    private int configVersion;

    @Column(nullable = false)
    private ConnectionStatus status;

    @Column(name = "last_test_status", nullable = false)
    private TestStatus lastTestStatus;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_error")
    private String lastTestError;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_test_details")
    private JsonNode lastTestDetails;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private UUID conversationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DataConnectionEntity() {
    }

    public DataConnectionEntity(UUID id, UUID ownerId, UUID conversationId, String name, ConnectionKind kind) {
        this.id = id;
        this.ownerId = ownerId;
        this.conversationId = conversationId;
        this.name = name;
        this.kind = kind;
        this.status = ConnectionStatus.DRAFT;
        this.lastTestStatus = TestStatus.UNTESTED;
        this.configVersion = 1;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void initializeConfig(JsonNode config) {
        this.config = config;
    }

    public void changeConfig(JsonNode config) {
        this.config = config;
        this.configVersion += 1;
        resetTest();
    }

    public void changeSecret() {
        this.configVersion += 1;
        resetTest();
    }

    public void recordTest(TestStatus status, Instant testedAt, String error, JsonNode details) {
        this.lastTestStatus = status;
        this.lastTestAt = testedAt;
        this.lastTestError = error;
        this.lastTestDetails = details;
        if (status == TestStatus.SUCCESS) {
            this.status = ConnectionStatus.ACTIVE;
        }
    }

    private void resetTest() {
        this.lastTestStatus = TestStatus.UNTESTED;
        this.lastTestAt = null;
        this.lastTestError = null;
        this.lastTestDetails = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public ConnectionKind getKind() {
        return kind;
    }

    public JsonNode getConfig() {
        return config;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public TestStatus getLastTestStatus() {
        return lastTestStatus;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public String getLastTestError() {
        return lastTestError;
    }

    public JsonNode getLastTestDetails() {
        return lastTestDetails;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
