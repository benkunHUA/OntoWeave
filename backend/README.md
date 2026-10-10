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

`CHAT_API_KEY` 由智能体运行时消费；不配置时普通 REST 功能仍可运行。

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
| GET | `/api/v1/graphs/{id}/snapshot` | 读取画布快照（节点、属性、关系） |
| POST | `/api/v1/conversations` | 新建会话（同时建出对应的图） |
| GET | `/api/v1/conversations` | 会话列表（分页） |
| GET | `/api/v1/conversations/{id}` | 会话详情 |
| PATCH | `/api/v1/conversations/{id}` | 修改会话名称（同步图名称与视图名称） |
| GET | `/api/v1/conversations/{id}/messages` | 会话消息历史 |
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

## 智能体运行时

`app/agent/` 分四层：

- `llm.py`：模型装配。**`deepseek-flash` 是推理模型，`CHAT_MAX_TOKENS` 给小了会得到
  「HTTP 200 但正文为空」的响应**（推理把 token 吃光了），默认给到 4096。
- `tools.py`：把普通的建模函数用闭包绑上运行上下文，包成 LangChain 工具。
- `graph.py`：用 deepagents 的 `create_deep_agent` 装配，不自己拼 StateGraph；
  同时挂上 `view_designer` 绘图子智能体。
- `runtime.py`：一次运行的装配，以及「一次对话一个打开的改动集」的生命周期。

绘图子智能体只拥有视图工具：读取图切片和当前视图，修改布局、高亮、分组、折叠、
过滤、视野与标注，并持久化到 `view_state`。它没有创建或修改本体的工具。
`view_state.name` 不再独立编辑，始终镜像对应图的名称。

接 SSE 的接口挂在会话下——线程 ID 直接复用 `conversation_id`，不另引线程表：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/conversations/{id}/messages` | 发一句，返回 SSE 事件流 |
| POST | `/api/v1/conversations/{id}/resume` | 回答中断提问，返回 SSE 事件流 |

事件类型：`run.start` / `message.delta` / `tool.start` / `tool.end` / `interrupt` /
`state.update` / `run.error` / `run.end`。

用户发言（包括 `/resume` 的回答）在运行开始时先单独提交；助手正文在回合结束时
写入 `conversation_message`。模型或工具失败也会留下带 `kind='error'` 的助手消息。

**`run.end` 带 `paused` 字段。** 中断和完成都以 `run.end` 收尾，前端靠它区分：
`paused: true` 表示卡在中途等人回答，该调 `/resume`；`false` 表示本轮做完了。

### 踩过的坑

这几条都是实际跑起来才暴露的，不是读文档能看出来的：

- **工具异常会打挂整轮。** deepagents 的 ToolNode 默认把工具异常直接抛出，
  而我们的领域错误（找不到、状态非法）在设计上是预期内的。所以工具统一返回
  结构化错误 `{"error": ..., "message": ...}`，让模型自己纠正。
  注意异常兜底要包住**参数构造**（`uuid.UUID("坏 id")` 也算），不能只包函数调用。
- **工具会并发跑，而它们共用一个 Session。** deepagents 在同一轮里用线程池并发执行
  工具调用，SQLAlchemy Session 不是线程安全的。`ToolContext` 上挂了一把每次运行
  一把的锁来串行化——这样既保住「一次运行一个事务边界」，又不会并发踩 Session。
- **规划要手动加。** `write_todos`（`TodoListMiddleware`）不在 deepagents 的默认
  中间件栈里，得自己挂。
- **`interrupt_on` 和 `ask_user` 都需要 checkpointer。** 没有检查点时审批和提问都
  不会工作，所以 `build_agent` 在没检查点时会自动关掉审批（测试用）。
- **`PostgresSaver.from_conn_string` 返回的是上下文管理器**，不是实例；长期存活的
  进程要自己建连接池，且 conninfo 要用 libpq 格式（去掉 SQLAlchemy 的 `+psycopg`），
  关闭时记得 `pool.close()`。
- **安装有个坑。** deepagents 把 `langchain-google-genai` 声明成硬依赖，但代码里
  从没 import 过它；它拖来的 `google-auth → cryptography` 在部分机器上要从源码
  编译而失败。目前的处理是跳过它。**升级 deepagents 时要重新验证这条。**

## 数据库连接

Postgres 只监听本机（`127.0.0.1:5432`），不会暴露到局域网。凭据可用 `.env` 覆盖
`POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB`。
