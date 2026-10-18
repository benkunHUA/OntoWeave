package com.ontoweave.data.connector.mysql;

import com.ontoweave.data.connection.config.MysqlConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;

@Component
public class MysqlJdbc {

    public Connection open(MysqlConfig config, String password) throws SQLException {
        return DriverManager.getConnection(url(config), config.username(), password);
    }

    String url(MysqlConfig config) {
        return String.format(
                Locale.ROOT,
                "jdbc:mysql://%s:%d/%s?connectTimeout=5000&socketTimeout=5000&useSSL=%s"
                        + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8&useUnicode=true",
                config.host(),
                config.port(),
                config.database(),
                config.useSsl()
        );
    }
}
