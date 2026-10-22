#!/usr/bin/env bash
# 向 Docker 中的 retail_sales 库追加测试数据；每次运行从当前最大 id 往后递增，可重复执行。
#
# 用法：
#   ./scripts/seed-demo-data.sh                 # 1000 客户 + 5000 订单
#   CUSTOMERS=500 ORDERS=2000 ./scripts/seed-demo-data.sh
#   MYSQL_CONTAINER=my-mysql ./scripts/seed-demo-data.sh

set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-ontoweave-mysql-1}"
DATABASE="${MYSQL_DATABASE:-retail_sales}"
DB_USER="${MYSQL_USER:-ontoweave}"
DB_PASSWORD="${MYSQL_PASSWORD:-ontoweave}"
CUSTOMERS="${CUSTOMERS:-1000}"
ORDERS="${ORDERS:-5000}"

if ! [[ "$CUSTOMERS" =~ ^[0-9]+$ ]] || ! [[ "$ORDERS" =~ ^[0-9]+$ ]]; then
  echo "CUSTOMERS 和 ORDERS 必须是非负整数" >&2
  exit 1
fi
if (( CUSTOMERS == 0 && ORDERS > 0 )); then
  echo "新增订单需要同时新增客户，请把 CUSTOMERS 设为大于 0" >&2
  exit 1
fi

run_sql() {
  docker exec -i -e MYSQL_PWD="$DB_PASSWORD" "$CONTAINER" \
    mysql --default-character-set=utf8mb4 --batch --skip-column-names \
    -u"$DB_USER" "$DATABASE" "$@"
}

customer_start=$(run_sql -e "SELECT COALESCE(MAX(id), 0) + 1 FROM customers")
order_start=$(run_sql -e "SELECT COALESCE(MAX(id), 0) + 1 FROM orders")

if (( CUSTOMERS > 0 )); then
  run_sql -e "
    START TRANSACTION;
    SET SESSION cte_max_recursion_depth = $((CUSTOMERS + 10));
    INSERT INTO customers (id, name, level, created_at)
    WITH RECURSIVE seq(n) AS (
      SELECT 0
      UNION ALL
      SELECT n + 1 FROM seq WHERE n + 1 < $CUSTOMERS
    )
    SELECT
      $customer_start + n,
      CONCAT('测试客户-', $customer_start + n),
      ELT(1 + (n % 3), 'gold', 'silver', 'bronze'),
      NOW() - INTERVAL (n * 17 % 365) DAY - INTERVAL (n * 31 % 86400) SECOND
    FROM seq;
    COMMIT;
  "
fi

if (( ORDERS > 0 )); then
  run_sql -e "
    START TRANSACTION;
    SET SESSION cte_max_recursion_depth = $((ORDERS + 10));
    INSERT INTO orders (id, customer_id, amount, created_at)
    WITH RECURSIVE seq(n) AS (
      SELECT 0
      UNION ALL
      SELECT n + 1 FROM seq WHERE n + 1 < $ORDERS
    )
    SELECT
      $order_start + n,
      $customer_start + ((n * 37) % $CUSTOMERS),
      ROUND(9.9 + ((n * 7919) % 490000) / 100, 2),
      NOW() - INTERVAL (n * 11 % 120) DAY - INTERVAL (n * 53 % 86400) SECOND
    FROM seq;
    COMMIT;
  "
fi

run_sql -e "
  SELECT CONCAT('customers: ', COUNT(*), ' 行') FROM customers;
  SELECT CONCAT('orders: ', COUNT(*), ' 行') FROM orders;
"
