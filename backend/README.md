# OntoWeave Backend

会话驱动的企业本体建模工具后端。当前进度：本体数据层（对象类型 / 属性 / 关系 / 图成员 / 状态机）。

## 本地起服务

```bash
docker compose up -d db
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head
uvicorn app.main:app --reload
```

打开 http://127.0.0.1:8000/docs 看接口文档。

## 跑测试

```bash
docker compose up -d db
source .venv/bin/activate
python -m pytest -v
```

测试连 `TEST_DATABASE_URL`（默认是本地的 `ontoweave_test`），每次会话重建该库的 schema。

**保护机制：** 库名不以 `_test` 结尾时，pytest 会直接拒绝启动，避免误清开发库。
测试库由 `scripts/init-test-db.sql` 在数据卷首次初始化时创建；已有数据卷需要手工
`docker compose exec db psql -U ontoweave -d postgres -c "CREATE DATABASE ontoweave_test OWNER ontoweave;"`
或 `docker compose down -v` 后重建。

## 数据库连接

Postgres 只监听本机（`127.0.0.1:5432`），不会暴露到局域网。凭据可用 `.env` 覆盖
`POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB`。

## 目录

| 路径 | 作用 |
|---|---|
| `app/models.py` | SQLAlchemy 模型（本体、图、视图、改动集） |
| `app/services/ontology.py` | 对象类型 / 属性 / 关系的领域逻辑与状态机 |
| `app/services/graph.py` | 图的成员管理与关系端点完整性约束 |
| `app/api/routes.py` | REST 路由 |
| `alembic/versions/` | 数据库迁移 |

## 已实现的接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/object-types` | 新建对象类型（草案状态） |
| POST | `/object-types/{id}/confirm` | 草案 → 已确认 |
| POST | `/graphs` | 新建图 |
| POST | `/graphs/{id}/objects` | 把对象加入图 |
| POST | `/graphs/{id}/relations` | 把关系加入图（两端必须已在图内） |
