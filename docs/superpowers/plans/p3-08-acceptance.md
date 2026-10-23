# P3 W3-08：端到端与性能验收

> 依赖：W1-01 至 W3-07 全部合入。
> 写作用域：`docs/checks/2026-10-23-p3-ontology-mapping-smoke.md`、`data-service/src/test/java/com/ontoweave/data/ontology/acceptance/**`、`scripts/p3-*`（如需）。不要改业务代码；发现问题回到对应流的文档修复。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §14

## 目标

用真实 Docker 数据面和真实 Iceberg 数据跑通 P3 主链路，并给出可复现的验收记录与性能数据。

## 验收环境

| 组件 | 要求 |
|---|---|
| PostgreSQL | Docker，schema `app` / `data`，启用 `pg_trgm` |
| MySQL | Docker，造 `customer` / `orders` / `order_item` 三表 |
| Flink + Iceberg + RabbitMQ | 复用 P1/P2 compose |
| Python API | 本地 uvicorn |
| Java 数据服务 | Docker 或本地 |
| 模型 | DeepSeek 兼容接口，Agent 用例需要 |

数据量：

- 功能 e2e：2 万行 `orders`、2000 行 `customer`、2 万行 `order_item`。
- 性能：单对象类型 50 万行、8 个属性。

## 场景清单

### 1. 建模与绑定

- 在建模页创建 `Customer`、`Order` 两个对象类型，属性含主键、标题、必填、可选、枚举、数值、日期时间。
- 创建 `placed` 外键关系（Order → Customer，外键属性 `customer_id`）。
- 创建 `contains` 多对多关系（Order ↔ Product，连接表）。
- 进入对象工作台，确认左侧三类资源都出现。

### 2. 映射与首次生成

- 自动建议后人工修正，保存草稿，确认 revision 递增。
- 校验出现阻断时不能生成；修复后可生成。
- 预览对象和关系，确认不产生对象数据。
- 生成成功，列表、总数、详情、关系分组可查。
- 运行记录显示来源版本、计数、耗时。

### 3. 查询

- 主键搜索、标题搜索。
- 每种操作符至少一例：text/enum 的 eq/ne/contains/判空，数值/日期/时间的大小比较，布尔 eq。
- 三条件 AND 结果与 SQL 手算一致。
- 数字主键按数值排序。
- 双向关系查询各返回正确方向。

### 4. 新版本自动重建

- 给源表追加/修改数据，触发 P1 同步新版本。
- 确认自动入队、生成、原子切换；列表出现新数据。
- 旧版本 5 分钟后被清理，查询不受影响。

### 5. 失败保留旧对象

- 构造主键重复或必填为空的数据版本。
- 生成失败，run 记录错误样例；列表仍是上一版。
- 修复数据后重试成功。

### 6. 字段变化

- 删除或改名映射中的列，发布新版本。
- 确认 `NEEDS_REPAIR`、自动重建暂停、旧对象可查。
- 修复映射并重新生成后恢复。

### 7. 换源与解绑

- 换到另一个数据集，生成前旧对象不变；成功后切换。
- 解绑对象类型，确认对象与关联关系实例删除，本体定义、数据集、运行记录保留。

### 8. 并发与取消

- 两个页面同时保存，后写者收到 409 并重载。
- 排队任务取消立即生效；运行中取消在下一批退出并清理 stage。

### 9. Agent

- 建议映射、解释校验错误、预览。
- 修改主键/标题、生成、换源、解绑、取消都触发确认。
- 拒绝确认时不落库。
- 409 后 Agent 重新读取，不自动覆盖。

### 10. 性能

- 50 万行、8 属性，批大小 1k / 10k / 50k 各跑一次。
- 记录：总耗时、读取耗时、写入耗时、峰值内存、实例与派生值行数。
- 列表首屏（page=1，pageSize=20）P95。
- 三条件筛选 P95。
- 给出推荐批大小。

## 验收记录

`docs/checks/2026-10-23-p3-ontology-mapping-smoke.md` 必须包含：

- 环境、镜像版本、基线 commit。
- 每个场景的请求/响应摘要或截图路径。
- 失败场景的 run id、错误码、错误样例。
- 性能表格与推荐批大小。
- 已知限制与未覆盖项。
- 最终结论：通过 / 有条件通过 / 不通过。

## 实施步骤

- [ ] 1. 写数据造数与清理脚本。
- [ ] 2. 跑通建模 + 绑定 + 首次生成 + 查询。
- [ ] 3. 跑通多对多和外键关系。
- [ ] 4. 跑通自动重建与失败保留。
- [ ] 5. 跑通字段变化、换源、解绑。
- [ ] 6. 跑通并发 409 与取消。
- [ ] 7. 跑通 Agent 确认链路。
- [ ] 8. 跑性能三组并记录。
- [ ] 9. 写验收报告并更新阶段文档状态。

## 验收命令

```bash
cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app
cd data-service && ./mvnw -q test
cd frontend && npx tsc -b && npm run lint && npm test && npm run build
docker compose up -d
```

## Definition of Done

- 十个场景全部有记录，失败场景也有可复现步骤。
- 性能数据可重复，推荐批大小有依据。
- 报告中的 run id、版本 id、对象主键都能在数据库或 API 中复查。
- 发现的问题已回到对应 W 文档修复，而不是在验收脚本里绕过。
