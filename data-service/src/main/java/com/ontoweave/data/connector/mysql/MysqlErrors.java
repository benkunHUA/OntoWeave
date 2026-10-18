package com.ontoweave.data.connector.mysql;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.Locale;

public final class MysqlErrors {

    private MysqlErrors() {
    }

    public static MysqlFailure map(SQLException exception) {
        String sqlState = exception.getSQLState() == null ? "" : exception.getSQLState();
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        if (exception.getErrorCode() == 1049) {
            return new MysqlFailure("DATABASE_NOT_FOUND", "数据库不存在");
        }
        if (sqlState.startsWith("28") || exception.getErrorCode() == 1045) {
            return new MysqlFailure("AUTH_FAILED", "用户名或密码错误");
        }
        if (message.contains("ssl") || message.contains("tls")) {
            return new MysqlFailure("TLS_ERROR", "TLS 连接失败");
        }
        if (hasCause(exception, SocketTimeoutException.class) || message.contains("timeout")) {
            return new MysqlFailure("TIMEOUT", "连接源库超时");
        }
        if (sqlState.startsWith("08") || exception.getClass().getSimpleName().contains("Communications")) {
            return new MysqlFailure("HOST_UNREACHABLE", "无法连接源库");
        }
        return new MysqlFailure("UNKNOWN", "连接测试失败");
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
