# 后端加固修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**来源：** `docs/superpowers/plans/2026-10-07-ontology-backend-foundation.md` 实现后的代码审查。
**范围：** 只修地基问题，不加新功能。目标是在开工 Plan 2（会话 / 改动集 / 视图）之前，把库、测试、事务和交付配套摆正。
**原则：** P0 不做完不进入 Plan 2。每一项都要有可执行的验收命令，不能靠"看着像修好了"。

## 优先级说明

| 级别 | 判据 | 数量 |
|---|---|---|
| P0 | 会丢数据、会漏安全 | 2 |
| P1 | 会让测试说谎、会产生错误数据 | 5 |
| P2 | 阻碍工业化交付（架构、部署、可观测、CI） | 5 |
| P3 | 整洁与一致性 | 4 |

## 进度

| 项 | 状态 |
|---|---|
| P0-1 测试库与开发库隔离 | ✅ 已完成（`d408dc2`） |
| P0-2 数据库端口收敛到本机 | ✅ 已完成（`d408dc2`） |
| P1-1 测试改跑迁移并加一致性检查 | ✅ 已完成（`d408dc2`） |
| P1-2 非空列的数据库级默认值 | ✅ 已完成（`d408dc2`） |
| P1-3 关系查重 | ✅ 已完成（第二轮） |
| P1-4 补测零覆盖的服务函数 | ✅ 已完成（第二轮） |
| P1-5 事务边界与可注入引擎 | ✅ 已完成（第二轮） |
| P2-1 加索引 | ✅ 已完成（第三轮） |
| P2-2 路由与 schema 按领域拆分 + `/api/v1` | ✅ 已完成（第三轮） |
| P2-3 日志与请求 ID | ✅ 已完成（第三轮） |
| P2-4 应用容器化 | ✅ 已完成（第三轮） |
| P2-5 CI、lint、类型检查、依赖锁 | ✅ 已完成（第三轮） |
| P3 整洁项 | ✅ 已完成（第三、四轮） |

第一轮顺带发现并修掉一个计划外的真实 bug：`alembic/env.py` 原先无条件用应用配置覆盖
`sqlalchemy.url`，导致测试夹具指定的目标库被忽略、迁移实际跑在开发库上。现已改成
优先读 `config.attributes["sqlalchemy_url"]`。

P1-5 的验收标准有一处调整：原计划写的是「把 `DATABASE_URL` 指向不存在的库，集成测试
失败」，但集成测试是**注入** session factory 的，绕过了应用配置，所以改成两条来覆盖：
一条注入 factory 验证真实链路落库，另一条 `test_default_app_wires_factory_from_settings`
验证不带参数装配时连接串确实来自应用配置。P1-3 另外更正了此前审查中「加 source ≠ target
约束」的错误建议——自反关系合法，只做查重。

第三轮的两处环境调整：

1. 宿主机 8000 端口被别的项目占用，应用容器映射到 **18000**。
2. Docker Hub 在本网络不可达，`python:3.12-slim` 与 `pgvector/pgvector:pg16` 都是从
   镜像源拉取后打回原标签的，`docker compose up --build` 现在可离线工作。

P3 里「删死代码」一项随 P2-2 的重构一并做掉了（`STATUS_VALUES`、`ErrorBody` 都已移除）。

---

## P0-1 测试库与开发库隔离

**问题：** [conftest.py:10](../../../backend/tests/conftest.py) 的 `TEST_DATABASE_URL` 默认指向开发库 `ontoweave`，[:20](../../../backend/tests/conftest.py) 直接 `drop_all`。对着有数据的库跑一次 `pytest` 就是数据丢失。

**改动：**
1. 默认连接串改成 `.../ontoweave_test`。
2. 加放行校验：库名不以 `_test` 结尾就直接退出，不给跑。
3. `docker-compose.yml` 挂一个初始化脚本，容器首次启动时建出 `ontoweave_test`。

```python
# backend/tests/conftest.py
import os

import pytest
from sqlalchemy.engine import make_url

TEST_DATABASE_URL = os.environ.get(
    "TEST_DATABASE_URL",
    "postgresql+psycopg://ontoweave:ontoweave@localhost:5432/ontoweave_test",
)

_db_name = make_url(TEST_DATABASE_URL).database or ""
if not _db_name.endswith("_test"):
    pytest.exit(f"拒绝在非测试库上跑测试：{_db_name}", returncode=1)
```

```sql
-- backend/scripts/init-test-db.sql
CREATE DATABASE ontoweave_test OWNER ontoweave;
```

```yaml
# backend/docker-compose.yml（db 服务下新增挂载）
    volumes:
      - ontoweave_db:/var/lib/postgresql/data
      - ./scripts/init-test-db.sql:/docker-entrypoint-initdb.d/10-test-db.sql:ro
```

**验收：** `TEST_DATABASE_URL` 指向 `ontoweave` 时跑 `pytest` 被拒绝并提示库名；指向 `ontoweave_test` 时全套测试通过。

**注意：** 初始化脚本只在数据卷为空时执行。已有卷需要 `docker compose down -v` 重建，或手工 `createdb`。

---

## P0-2 数据库端口收敛到本机

**问题：** [docker-compose.yml:9](../../../backend/docker-compose.yml) 写的是 `"5432:5432"`，绑定 `0.0.0.0`，同一局域网内任何人都能连，口令是 `ontoweave`。

**改动：**

```yaml
    ports:
      - "127.0.0.1:5432:5432"
```

顺带把凭据从字面量改成读环境变量，`.env` 已在 `.gitignore` 里：

```yaml
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-ontoweave}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-ontoweave}
      POSTGRES_DB: ${POSTGRES_DB:-ontoweave}
```

**验收：** 在另一台机器上 `nc -z <本机局域网IP> 5432` 连不上；本机 `nc -z 127.0.0.1 5432` 通。

---

## P1-1 测试改跑迁移，并加一致性检查

**问题：** [conftest.py:21](../../../backend/tests/conftest.py) 用 `Base.metadata.create_all` 建表，模型和 Alembic 迁移一旦漂移，测试全绿而线上建表失败。

**改动：**
1. `conftest.py` 改成用 Alembic 建 schema：`command.downgrade(cfg, "base")` 再 `command.upgrade(cfg, "head")`。
2. 新增一条测试，跑 `alembic check`（Alembic 1.9+），有未落库的模型改动就失败。

```python
# backend/tests/test_migrations.py
from alembic import command
from alembic.config import Config

from tests.conftest import TEST_DATABASE_URL


def test_migrations_match_models():
    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", TEST_DATABASE_URL)
    command.check(cfg)  # 有 pending 的 autogenerate 变更会抛异常
```

**验收：** 故意在 `models.py` 加一个字段但不生成迁移，`pytest` 失败；生成迁移后恢复通过。

---

## P1-2 补上非空列的数据库级默认值

**问题：** [models.py:111-112](../../../backend/app/models.py) 的 `datatype` / `required`、[:142](../../../backend/app/models.py) 的 `direction` 只有 Python 端 `default=`，没有 `server_default=`；而 `status` 两个都有。任何绕开 ORM 的写入都会因为 NOT NULL 失败。

**改动：**

```python
# app/models.py
datatype: Mapped[str] = mapped_column(Text, nullable=False, server_default="text")
required: Mapped[bool] = mapped_column(
    Boolean, nullable=False, server_default=text("false")
)
direction: Mapped[str] = mapped_column(Text, nullable=False, server_default="forward")
```

然后生成迁移 `0002_defaults`，用 `alter_column` 带上 `server_default`。**不要改已应用的 `0001`。**

**验收：** `docker compose exec db psql -U ontoweave -d ontoweave -c "insert into property (id, owner_type_id, name) values (gen_random_uuid(), '<某个 object_type id>', '测试');"` 能插入成功，`datatype` 自动是 `text`。

---

## P1-3 关系查重

**问题：** [ontology.py:97](../../../backend/app/services/ontology.py) 的 `create_relation` 直接插入，同一对类型可以插任意多条同名同向关系。设计文档里写的「内部查重」没有实现。

**改动：** 给 `relation` 加唯一约束，并让 `create_relation` 幂等。

```python
# app/models.py
__table_args__ = (
    CheckConstraint(...),
    UniqueConstraint(
        "source_type_id", "target_type_id", "name", "direction",
        name="uq_relation_shape",
    ),
)
```

```python
# app/services/ontology.py
def create_relation(session, *, owner_id, source_type_id, target_type_id, name,
                    direction="forward", home_graph_id=None) -> Relation:
    get_object_type(session, source_type_id)
    get_object_type(session, target_type_id)
    existing = session.scalar(
        select(Relation).where(
            Relation.source_type_id == source_type_id,
            Relation.target_type_id == target_type_id,
            Relation.name == name,
            Relation.direction == direction,
        )
    )
    if existing is not None:
        return existing
    ...
```

**更正一条我上次审查里的错误建议：** 我提过要加「source ≠ target」约束，这是错的。本体里自反关系（公司—控股→公司、员工—管理→员工）是合理且常见的，不该禁止。只做查重，不做自反限制。

**验收：** 同一个形状调两次 `create_relation`，只产生一行，且两次返回同一个 id。

---

## P1-4 未覆盖的服务函数

**问题：** [ontology.py:87](../../../backend/app/services/ontology.py) 的 `list_properties` 和 [:130](../../../backend/app/services/ontology.py) 的 `transition_relation` 既没有测试，也没有任何调用方。留着会让人误以为它被验证过。

**改动：** 二选一——要么补测试并接上调用（关系的状态机显然是要用的，选这条），要么删掉。建议：
- 给 `list_properties` 补一条按 `owner_type_id` 过滤、按名称排序的测试。
- 给 `transition_relation` 补状态机测试（草案→已确认→已批准、跳级拒绝、已批准不可回退），和对象类型那套对齐。

**验收：** `pytest tests/test_ontology_service.py -v` 里能看到这两个函数的用例，新增用例数 ≥ 4。

---

## P1-5 事务边界与可注入引擎

**问题：** 两件事绑在一起。一是 `session.commit()` 写在路由里（[routes.py:33](../../../backend/app/api/routes.py) 那一类），事务边界属于 HTTP 层，Plan 3 让智能体直接调服务时会到处漏提交。二是引擎在导入时就创建（[deps.py:8-10](../../../backend/app/deps.py)），`create_app()` 没法接不同配置，导致 [test_api.py:15](../../../backend/tests/test_api.py) 只能把 `get_session` 整个替换掉，「依赖注入 → 真实 Postgres」这段从没被验证过。

**改动：**
1. 让 `create_app()` 接收一个 session factory，挂到 `app.state`，`get_session` 从 `request.app.state` 取。
2. `get_session` 承担单元事务：成功提交、异常回滚。
3. 路由里删掉所有 `session.commit()`。
4. 新增一条**不覆盖依赖**的集成测试：用测试库的 factory 建 app，走一遍「建对象类型 → 确认 → 建图 → 加对象」，断言真实数据库里有对应行。

```python
# app/deps.py
def get_session(request: Request) -> Iterator[Session]:
    factory = request.app.state.session_factory
    with factory() as session:
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
```

**验收：** 新的集成测试在没有 `dependency_overrides` 的情况下通过；把 `DATABASE_URL` 指向一个不存在的库，该测试失败。

---

## P2-1 加索引

**问题：** 迁移里 `create_index` 出现 0 次。Postgres 不会给外键自动建索引，这些列都会全表扫：`graph.owner_id`、`conversation.owner_id`、`object_type.owner_id`、`relation.source_type_id`、`relation.target_type_id`、`graph_object.object_type_id`、`graph_relation.relation_id`。

**改动：** 在对应 `mapped_column(..., index=True)`，生成迁移 `0003_indexes`。

**验收：** `docker compose exec db psql -U ontoweave -d ontoweave -c "\di"` 里能看到这七个索引。

---

## P2-2 路由与 schema 按领域拆分

**问题：** 现在 5 个接口挤在 [routes.py](../../../backend/app/api/routes.py)，Plan 2 要加会话、改动集、视图状态，Plan 3 还要 SSE，很快就撑爆。

**改动：** 按领域拆，和 `services/` 对齐。

```
app/api/
  router.py        # 汇总子路由
  object_types.py
  graphs.py
app/schemas/
  ontology.py
  graph.py
```

同时加上 `/api/v1` 前缀——接口一旦对外就有兼容义务，早加成本最低。

**验收：** `/docs` 里接口路径带 `/api/v1`；`pytest` 全绿；`routes.py` 不再是单文件。

---

## P2-3 日志与请求 ID

**问题：** 全项目 0 处 `logging`。私有化部署出问题时无法定位。

**改动：** 加一个最小中间件，为每个请求生成/透传 `X-Request-ID`，通过 `contextvars` 带进日志；配 `logging` 的 JSON formatter；把领域异常的发生也记一条 warn。

**验收：** 起服务打一个会 409 的请求，日志里能看到 request id、路径、状态码和领域错误原因。

---

## P2-4 应用容器化

**问题：** 只有数据库的 compose，应用本身没有 `Dockerfile`，私有化交付落不了地。

**改动：** 加多阶段 `backend/Dockerfile`（builder 装依赖、runtime 只带 venv），给应用也加进 compose（和 `db` 同网络），入口脚本先跑 `alembic upgrade head` 再起 uvicorn。加 `.dockerignore`。

**验收：** `docker compose up --build` 后 `curl http://127.0.0.1:8000/health` 返回 200，且自动完成了迁移。

---

## P2-5 CI、lint、类型检查、依赖锁

**问题：** 没有 CI，没有 ruff / mypy，没有锁文件。

**改动：**
- 配 `ruff`（lint + format）和 `mypy`，加进 `pyproject.toml`。
- 加 `.github/workflows/backend.yml`：起 Postgres 服务容器 → 装依赖 → `ruff check` → `mypy` → `pytest`。
- 用 `uv lock`（或 `pip-compile`）产出锁文件并提交。

**验收：** 本地 `ruff check . && mypy app && pytest` 全过；推上去 CI 绿。

---

## P3 整洁项

- [x] 删死代码：`STATUS_VALUES`、`ErrorBody`（第三轮随重构移除）。
- [x] `remove_object_from_graph` 补上 `get_graph` 校验，和 `add_object_to_graph` 对齐。
- [x] `GET /api/v1/object-types` 加分页（`limit` 默认 50、上限 200，`offset`），
      排序加 `id` 作次级键保证翻页稳定。
- [x] `alembic.ini` 的连接串清空，运行时由 `env.py` 从环境变量 / 应用配置填入（第三轮）。
- [x] 加根 `README.md`。

---

## 建议的执行节奏

1. **第一轮（半天）**：P0-1、P0-2、P1-1、P1-2。做完这套，库是安全的、测试是可信的。
2. **第二轮（一天）**：P1-3、P1-4、P1-5。做完这套，数据是对的、事务边界是对的、真实链路有测试。
3. **第三轮（一天到两天）**：P2 全部。做完这套，可以往私有化环境交付。
4. **P3** 随时顺手做。

第三轮结束后再开 Plan 2。
