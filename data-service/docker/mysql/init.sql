SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '客户名',
    level VARCHAR(20) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT = '客户表';

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY ix_orders_customer (customer_id)
) COMMENT = '订单表';

INSERT IGNORE INTO customers (id, name, level) VALUES
    (1, 'alice', 'gold'),
    (2, 'bob', 'silver');

INSERT IGNORE INTO orders (id, customer_id, amount) VALUES
    (1, 1, 128.50),
    (2, 2, 42.00);

GRANT SELECT, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'ontoweave'@'%';
FLUSH PRIVILEGES;
