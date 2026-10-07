# OntoWeave Backend

会话驱动的企业本体建模工具后端。当前进度：本体数据层（对象类型 / 属性 / 关系 / 图成员 / 状态机）。

## 用容器跑整套

```bash
docker compose up -d --build
curl http://127.0.0.1:18000/health
```

应用容器启动时会先跑 `alembic upgrade head`，再起 uvicorn。接口文档在
http://127.0.0.1:18000/docs。

> 宿主机 8000 常被别的项目占用，所以这里映射到 **18000**。

## 本地开发

```bash
docker compose up -d db
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head
uvicorn app.main:app --reload --port 18000
```

## 配置

复制 `.env.example` 成 `.env`（已在 `.gitignore` 里，不会进版本库），把 key 填进去：

```bash
cp .env.example .env
```

三组配置：

| 组 | 变量 | 说明 |
|---|---|---|
| 数据库 | `DATABASE_URL` / `POSTGRES_*` | 本地默认值可直接用 |
| 对话模型 | `CHAT_BASE_URL` / `CHAT_API_KEY` / `CHAT_MODEL` | 智能体用；OpenAI 兼容接口，私有化环境换成自建网关 |
| 嵌入模型 | `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL` / `EMBEDDING_DIM` | 相似检索用 |

两个坑先说清楚：

- **`EMBEDDING_DIM` 必须和数据库里 `object_type.embedding` 列的维度一致（当前 1536）。** 换成原生维度不是 1536 的模型时，要么打开 `EMBEDDING_SEND_DIMENSIONS` 让服务端按 1536 输出，要么改列维度并生成一次迁移。
- **没有配 `EMBEDDING_API_KEY` 时，服务会回落到本地的 `HashingEmbedder`**（词面相似度，不需要网络）。开发够用，但语义质量远不如真实模型，上生产前记得配上。

`CHAT_API_KEY` 目前还没有消费方——智能体编排才会用到它。先填上不影响现有功能。

## 跑测试

```bash
docker compose up -d db
source .venv/bin/activate
pytest -v
```

测试连 `TEST_DATABASE_URL`（默认本地的 `ontoweave_test`），每次会话重建该库的 schema。

**保护机制：** 库名不以 `_test` 结尾时，pytest 直接拒绝启动，避免误清开发库。测试库由
`scripts/init-test-db.sql` 在数据卷首次初始化时创建；已有数据卷需要手工
`docker compose exec db psql -U ontoweave -d postgres -c "CREATE DATABASE ontoweave_test OWNER ontoweave;"`
或 `docker compose down -v` 后重建。

## 静态检查

```bash
ruff check .
ruff format --check .
mypy app
```

CI 在 `.github/workflows/backend.yml`，起一个 pgvector 服务容器后依次跑 lint、格式检查、
类型检查和测试。依赖用 `uv.lock` 锁定。

## 目录

| 路径 | 作用 |
|---|---|
| `app/models.py` | SQLAlchemy 模型（本体、图、视图、改动集） |
| `app/schemas/` | 按领域拆分的 Pydantic 出入参 |
| `app/services/ontology.py` | 对象类型 / 属性 / 关系的领域逻辑与状态机 |
| `app/services/graph.py` | 图的成员管理与关系端点完整性约束 |
| `app/api/` | 按领域拆分的路由，统一挂在 `/api/v1` 下 |
| `app/deps.py` | 依赖注入；事务边界在这里（成功提交、异常回滚） |
| `app/middleware.py` | 请求 ID 与访问日志 |
| `alembic/versions/` | 数据库迁移 |

## 接口

业务接口都在 `/api/v1` 下；`/health` 不版本化，方便探活。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/health` | 健康检查 |
| POST | `/api/v1/object-types` | 新建对象类型（草案状态） |
| GET | `/api/v1/object-types` | 对象类型列表（分页） |
| POST | `/api/v1/object-types/{id}/confirm` | 草案 → 已确认 |
| POST | `/api/v1/graphs` | 新建图 |
| POST | `/api/v1/graphs/{id}/objects` | 把对象加入图 |
| POST | `/api/v1/graphs/{id}/relations` | 把关系加入图（两端必须已在图内） |
| POST | `/api/v1/conversations` | 新建会话（同时建出对应的图） |
| GET | `/api/v1/conversations` | 会话列表（分页） |
| GET | `/api/v1/conversations/{id}` | 会话详情 |
| POST | `/api/v1/graphs/{id}/changesets` | 新建改动集 |
| GET | `/api/v1/graphs/{id}/changesets` | 改动集列表 |
| GET | `/api/v1/changesets/{id}` | 改动集详情（含操作明细） |
| POST | `/api/v1/changesets/{id}/ops` | 往改动集里记一条操作 |
| POST | `/api/v1/changesets/{id}/confirm` | 确认：草案 → 已确认，标记删除的真删 |
| POST | `/api/v1/changesets/{id}/reject` | 撤销：删掉本次新建的元素、还原改动 |
| GET | `/api/v1/graphs/{id}/view-state` | 读当前用户的视图状态 |
| PUT | `/api/v1/graphs/{id}/view-state` | 覆盖写入视图状态 |

每个响应都带 `X-Request-ID`；请求里带了就透传，没带就生成一个，并出现在日志里。

### 改动集的语义

- `create`：元素在草稿阶段就已入库（`status='draft'`），确认时翻成 `confirmed`，撤销时删除。
- `update`：`payload.before` 里带上改动前的字段快照，撤销时按它还原。
- `delete`：草稿阶段**只登记不真删**（界面上表现为幽灵态），确认时才真删，撤销则无事发生。

确认前会先对所有操作做一遍全量校验，避免改到一半失败留下半生不熟的状态。

## 智能体工具层

工具是普通 Python 函数，位于 `app/agent/`，不依赖大模型，可以直接单测：

- `ontology_tools.py`：主智能体的建模工具。读工具 `search_ontology` / `list_types` /
  `get_subgraph`；写工具 `propose_type` / `propose_property` / `propose_relation` /
  `update_element` / `remove_element`。
- `view_tools.py`：绘图子智能体的视图工具，全部是对视图规格的纯函数操作，不碰本体。
- `schemas.py`：工具清单元数据，用它导出 tool schema。

护栏都放在工具内部，不靠调用方自觉：`propose_type` 先查近邻，同名或足够像就复用
已有的类型；`update_element` 强制要求 `before` 快照，且只允许白名单字段；
`remove_element` 只标记不真删。相似检索走 pgvector，嵌入模型可替换，默认是本地
确定性实现（`HashingEmbedder`），不需要网络。

## 数据库连接

Postgres 只监听本机（`127.0.0.1:5432`），不会暴露到局域网。凭据可用 `.env` 覆盖
`POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB`。
