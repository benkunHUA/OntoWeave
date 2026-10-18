package com.ontoweave.data.connection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.connection.api.dto.ConnectionTestResponse;
import com.ontoweave.data.connection.domain.DataConnectionEntity;
import com.ontoweave.data.connection.domain.TestStatus;
import com.ontoweave.data.connector.mysql.MysqlConnectionTester;
import com.ontoweave.data.connector.mysql.MysqlTestResult;
import com.ontoweave.data.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ConnectionTestService {

    private final ConnectionMaterialService materials;
    private final MysqlConnectionTester tester;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public ConnectionTestService(
            ConnectionMaterialService materials,
            MysqlConnectionTester tester,
            OutboxWriter outbox,
            ObjectMapper objectMapper
    ) {
        this.materials = materials;
        this.tester = tester;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConnectionTestResponse test(UUID id, UUID ownerId, UUID operationId) {
        ConnectionMaterialService.MysqlConnectionMaterial material = materials.loadMysql(id, ownerId);
        DataConnectionEntity connection = material.connection();
        MysqlTestResult result = tester.test(material.config(), material.password());
        Instant testedAt = Instant.now();
        TestStatus status = result.success() ? TestStatus.SUCCESS : TestStatus.FAILED;
        connection.recordTest(
                status,
                testedAt,
                result.success() ? null : result.message(),
                objectMapper.valueToTree(result.details())
        );
        OutboxWriterPayload payload = new OutboxWriterPayload(
                connection.getId(),
                connection.getOwnerId(),
                connection.getConversationId(),
                operationId,
                result.success() ? "success" : "failed",
                result.latencyMs(),
                result.errorCode(),
                result.serverVersion(),
                result.success() ? "连接测试成功" : "连接测试失败：" + result.message()
        );
        outbox.write("data_connection", id, "connection.tested", payload);
        return new ConnectionTestResponse(
                result.success() ? "success" : "failed",
                result.latencyMs(),
                result.serverVersion(),
                result.errorCode(),
                result.message(),
                testedAt,
                result.details()
        );
    }

    private record OutboxWriterPayload(
            UUID connectionId,
            UUID ownerId,
            UUID conversationId,
            UUID operationId,
            String status,
            long latencyMs,
            String errorCode,
            String serverVersion,
            String summary
    ) {
    }
}
