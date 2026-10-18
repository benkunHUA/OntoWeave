package com.ontoweave.data.sync.service;

import com.ontoweave.data.common.web.ApiException;
import com.ontoweave.data.connection.service.ConnectionMaterialService;
import com.ontoweave.data.connector.mysql.MysqlConnectionTester;
import com.ontoweave.data.connector.mysql.MysqlTestResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MysqlCdcPreflight {

    private final MysqlConnectionTester tester;

    public MysqlCdcPreflight(MysqlConnectionTester tester) {
        this.tester = tester;
    }

    public void validate(ConnectionMaterialService.MysqlConnectionMaterial material) {
        MysqlTestResult result = tester.test(material.config(), material.password());
        if (!result.success()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    result.errorCode() == null ? "SOURCE_UNAVAILABLE" : result.errorCode(),
                    result.message()
            );
        }
        Object binlog = result.details().get("binlog");
        if (!(binlog instanceof Map<?, ?> values)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BINLOG_DISABLED", "无法确认 binlog 配置");
        }
        if (!Boolean.TRUE.equals(values.get("enabled"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BINLOG_DISABLED", "CDC 模式要求 MySQL 开启 binlog");
        }
        if (!"ROW".equalsIgnoreCase(String.valueOf(values.get("format")))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BINLOG_FORMAT_UNSUPPORTED",
                    "CDC 模式要求 binlog_format=ROW"
            );
        }
        if (!"FULL".equalsIgnoreCase(String.valueOf(values.get("rowImage")))) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "BINLOG_ROW_IMAGE_UNSUPPORTED",
                    "CDC 模式要求 binlog_row_image=FULL"
            );
        }
        Object replication = result.details().get("replication");
        if (!(replication instanceof Map<?, ?> replicationValues)
                || !Boolean.TRUE.equals(replicationValues.get("client"))
                || !Boolean.TRUE.equals(replicationValues.get("slave"))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "REPLICATION_PERMISSION_DENIED",
                    "账号缺少 REPLICATION CLIENT 或 REPLICATION SLAVE 权限"
            );
        }
    }
}
