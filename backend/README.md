# OntoWeave Backend

会话驱动的企业本体建模工具后端。当前进度：本体数据层（对象类型 / 属性 / 关系 / 图成员 /
状态机）+ 动作层定义 + 数据连接与同步（MySQL → Flink CDC → Iceberg 数据集）+ 数据连接
Agent。动作层只定义「一次能做哪些改动」，不执行实例，执行层留给下一版。

## 用容器跑整套

```bash
cd ..
docker compose up -d --build
curl http://127.0.0.1:18000/health
```

应用容器启动时会先跑 `alembic upgrade head`，再起 uvicorn。接口文档在
http://127.0.0.1:18000/docs。

> 宿主机 8000 常被别的项目占用，所以这里映射到 **18000**。

## 本地开发

前置：安装 [uv](https://docs.astral.sh/uv/)（依赖与虚拟环境由 `uv.lock` 统一锁定）。

```bash
cd ..
docker compose up -d db
cd backend
uv sync --extra dev
uv run alembic upgrade head
uv run uvicorn app.main:app --reload --port 18000
```

## 配置

复制 `.env.example` 成 `.env`（已在 `.gitignore` 里，不会进版本库），把 key 填进去：

```bash
cp .env.example .env
```

Compose 会把 `backend/.env` 注入 `app` 容器。修改配置后需要重建容器才能生效：

```bash
docker compose up -d --force-recreate app
```

六组配置：

| 组 | 变量 | 说明 |
|---|---|---|
| 数据库 | `DATABASE_URL` / `POSTGRES_*` | 本地默认值可直接用 |
| 对话模型 | `CHAT_BASE_URL` / `CHAT_API_KEY` / `CHAT_MODEL` | 智能体用；OpenAI 兼容接口，私有化环境换成自建网关 |
| 嵌入模型 | `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL` / `EMBEDDING_DIM` | 相似检索用 |
| 可观测性 | `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` / `LANGFUSE_BASE_URL` | 可选；审计追踪用，两个 key 都填才开启 |
| Java 数据服务 | `DATA_SERVICE_URL` / `DATA_SERVICE_TOKEN` / `DATA_SERVICE_TIMEOUT_SECONDS` | 内部 REST，默认 `http://localhost:18001`；token 必须与 Java 一致 |
| 事件消费 | `RABBITMQ_URL` / `DATA_EVENT_EXCHANGE` / `DATA_EVENT_QUEUE` / `EVENT_CONSUMER_ENABLED` | 消费 Java Outbox 的连接与同步事件；测试默认关闭 |

两个坑先说清楚：

- **`EMBEDDING_DIM` 必须和数据库里 `object_type.embedding` 列的维度一致（当前 1536）。** 换成原生维度不是 1536 的模型时，要么打开 `EMBEDDING_SEND_DIMENSIONS` 让服务端按 1536 输出，要么改列维度并生成一次迁移。
- **没有配 `EMBEDDING_API_KEY` 时，服务会回落到本地的 `HashingEmbedder`**（词面相似度，不需要网络）。开发够用，但语义质量远不如真实模型，上生产前记得配上。

`CHAT_API_KEY` 由智能体运行时消费；不配置时普通 REST 功能仍可运行。

## 跑测试

```bash
cd ..
docker compose up -d db
cd backend
uv run pytest -v
```

测试连 `TEST_DATABASE_URL`（默认本地的 `ontoweave_test`），每次会话重建该库的 schema。

**保护机制：** 库名不以 `_test` 结尾时，pytest 直接拒绝启动，避免误清开发库。测试库由
根目录 Compose 挂载的 `scripts/init-test-db.sql` 在数据卷首次初始化时创建；已有数据卷需要手工
在项目根目录执行
`docker compose exec db psql -U ontoweave -d postgres -c "CREATE DATABASE ontoweave_test OWNER ontoweave;"`
或 `docker compose down -v` 后重建。

## 静态检查

```bash
uv run ruff check .
uv run ruff format --check .
uv run mypy app
```

CI 在 `.github/workflows/backend.yml`，起一个 pgvector 服务容器后依次跑 lint、格式检查、
类型检查和测试。依赖由 `uv.lock` 锁定，CI 与 Docker 构建都使用冻结锁文件。

## 目录

| 路径 | 作用 |
|---|---|
| `app/models/` | SQLAlchemy 模型，按领域拆分（本体、图、会话、动作、改动集） |
| `app/schemas/` | 按领域拆分的 Pydantic 出入参；`schemas/ontology/` 按资源再分组 |
| `app/services/ontology/` | 对象类型 / 属性 / 关系的领域逻辑与状态机 |
| `app/services/action/` | 动作类型、参数、规则、赋值、brief、detail 与引用保护 |
| `app/services/criteria/` | 提交校验条件树：读取顺序、单条形状、建树、整树不变量 |
| `app/services/element_kinds/` | 元素种类注册表（9 种）：create 分派、删除顺序、引用检查都按它走 |
| `app/services/element_edit/` | 改动集写入：操作形状、建草稿、更新与删除、条件树整棵替换 |
| `app/services/graph/` | 图的成员管理、发布、检索与关系端点完整性约束 |
| `app/services/changeset/` | 改动集存储、操作登记、确认 / 撤销与回执 |
| `app/services/conversation/` | 会话生命周期、消息、事件幂等落库与 SSE |
| `app/services/data_connection/` | 数据连接编排：Python 会话 + Java 数据服务 |
| `app/clients/data_service/` | Java 内部 REST 客户端、传输层与 camelCase → snake_case 映射 |
| `app/messaging/consumer.py` | RabbitMQ 消费者与重连 |
| `app/api/data_connections/` | 数据连接、同步任务与数据集的 REST 边界 |
| `app/api/runtime_dependencies.py` | 智能体运行时外部依赖的装配入口 |
| `app/api/` | 按领域拆分的路由，统一挂在 `/api/v1` 下 |
| `app/deps.py` | 依赖注入；请求事务边界在这里（成功提交、异常回滚） |
| `app/constants.py` | 跨层共享常量（如向量维度），避免 ORM 反向依赖服务层 |
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
| POST | `/api/v1/graphs/{id}/edits` | 按元素种类写入一批编辑（新增 / 更新 / 删除） |
| GET | `/api/v1/actions` | 动作类型列表（分页） |
| GET | `/api/v1/actions/{id}` | 动作详情（参数 / 规则 / 赋值 / 提交校验） |
| POST | `/api/v1/data-connections` | 创建草稿连接与对应会话 |
| GET | `/api/v1/data-connections` | 连接列表（分页） |
| GET / PATCH | `/api/v1/data-connections/{id}` | 连接详情 / 修改名称、配置或密码 |
| POST | `/api/v1/data-connections/{id}/test` | 测试已保存的连接 |
| GET | `/api/v1/data-connections/{id}/tables` | 源库表列表 |
| GET | `/api/v1/data-connections/{id}/tables/{schema}/{table}` | 表字段与主键 |
| GET | `/api/v1/data-connections/{id}/state` | 连接、任务、运行、数据集与缺失步骤 |
| POST / GET | `/api/v1/data-connections/{id}/sync-tasks` | 保存本次选表 / 查询同步任务 |
| POST | `/api/v1/data-connections/{id}/sync-runs` | 触发同步（202） |
| GET | `/api/v1/data-connections/{id}/sync-runs` | 最近同步运行 |
| GET / POST | `/api/v1/sync-runs/{id}` · `/stop` | 查询运行 / 停止运行（202） |
| GET | `/api/v1/data-connections/{id}/datasets` | 连接下的数据集 |
| GET | `/api/v1/datasets/{id}` | 数据集详情与版本 |
| GET | `/api/v1/conversations/{id}/events` | 事件历史与增量拉取 |
| GET | `/api/v1/conversations/{id}/events/stream` | SSE 推送事件卡片 |

### 数据连接事件

同步执行是异步的：Python 受理写操作后调 Java 内部接口，Java 通过 Outbox 把
`connection.tested`、`sync.run.*`、`dataset.version.published` 等事件投到 RabbitMQ。
Python 消费后按 `event_id` 幂等写入 `app.conversation_event`，再通过 SSE 推给打开该会话的
前端。事件只更新操作卡片，不自动触发模型；失败后由用户发消息让 Agent 解释。

每个响应都带 `X-Request-ID`；请求里带了就透传，没带就生成一个，并出现在日志里。

### 数据集向量检索

- 对象和属性带可空 `name_en`。对象英文名在创建时生成，属性英文名在定稿时按对象批量补齐；
  只补 `NULL`，之后改中文名不会回写，用户手工填写的英文名也不会被覆盖。
- Java 发布 `dataset.version.published` / `pipeline.build.succeeded` 后，Python 消费事件，
  取当前版本的名称与字段写入 `app.dataset_search_index`（pgvector）。向量只负责召回，
  主键、必填字段与类型兼容仍由确定性规则复核。
- 检索只查 `is_current = true AND status = 'ready'` 的当前版本。embedding 失败会落成
  `failed`，不会阻塞数据集发布。
- 历史数据用 `cd backend && uv run python scripts/backfill_dataset_search.py --owner-id <uuid>`
  回填；不传 owner 时使用 `DEFAULT_USER_ID`。索引表可随时按 Java 当前版本重建。

### 改动集的语义

- `create`：元素在草稿阶段就已入库（`status='draft'`），确认时翻成 `confirmed`，撤销时删除。
- `update`：`payload.before` 里带上改动前的字段快照，撤销时按它还原。
- `delete`：草稿阶段**只登记不真删**（界面上表现为幽灵态），确认时才真删，撤销则无事发生。

确认前会先对所有操作做一遍全量校验，避免改到一半失败留下半生不熟的状态。

可写元素种类集中在 `app/services/element_kinds/registry.py`：对象类型 / 属性 / 关系 / 动作类型 /
动作参数 / 动作规则 / 规则赋值 / 条件节点 / 条件操作数，共 9 种。新增一种元素 =
在注册表加一条（模型、字段白名单、删除顺序阶梯、引用检查都在这里），不再各自维护分支。

## 智能体工具层

工具层在 `app/agent/tools/`，领域函数是普通 Python 函数，不依赖大模型，可以直接单测：

- `ontology/`：主智能体的建模工具。读 `search` / `get_subgraph`，
  写 `propose_type` / `reuse_type` / `propose_property` / `propose_relation` /
  `update_element` / `remove_element`，以及中断提问 `ask_user`。`ops.py` 只做参数与对外
  形状的转换，规则都在 service 层；`binding.py` 把它们绑成模型可调用的工具。
- `action/`：动作子智能体。`ops.py` 是动作定义（参数 / 规则 / 赋值 / 提交校验）的读写与
  形状转换，`args.py` 放嵌套入参模型，`binding.py` 绑成 `search_actions` / `get_action` /
  `get_object_type` / `propose_action` / `reuse_action` / `propose_action_parameter` /
  `propose_action_rule` / `set_action_criteria`，`subagent.py` 包成 `action_modeler` 子智能体。
- `search.py`：只读检索，主智能体与动作子智能体共用一份——对象类型与动作一个入口，
  不传 `query` 即按名称列出全部（含草稿，看 `status`），`limit` 是每类各自的上限。
- `view/`：绘图子智能体。`ops.py` 是视图规格的纯变换，`registry.py` 登记每种视图能力
  （名字、描述、参数、对应的纯变换），`binding.py` 按表生成工具，`subagent.py` 包成子智能体。
  新增一种视图操作 = 在注册表加一条。
- `graph_read.py`：图切片的读取与对外形状。`get_subgraph` 与 `get_ontology_slice` 共用它，
  免得同一份节点/关系字段各写一遍。
- `context.py`：`ToolContext`（一次运行的外部依赖）与工具调用的错误兜底、并发串行化。

护栏都放在工具内部，不靠调用方自觉：`propose_type` 之前必须先 `search_ontology` 查重、
命中候选要 `ask_user` 让用户决定复用还是新建；`update_element` 只允许白名单字段、
`before` 快照由 service 采集；`remove_element` 只标记不真删。相似检索走 pgvector，
嵌入模型可替换，默认是本地确定性实现（`HashingEmbedder`），不需要网络。

## 智能体运行时

`app/agent/` 按职责分层：

- `llm.py`：模型装配。**`deepseek-flash` 是推理模型，`CHAT_MAX_TOKENS` 给小了会得到
  「HTTP 200 但正文为空」的响应**（推理把 token 吃光了），默认给到 4096。
- `prompts.py`：系统提示词与子智能体描述。提示词是产品行为的一部分，只放这一处。
- `graph.py`：用 deepagents 的 `create_deep_agent` 装配，不自己拼 StateGraph；
  同时挂上 `view_designer` 绘图子智能体与 `action_modeler` 动作子智能体。
- `tools/`：模型可调用的工具（见上）。
- `runtime/`：一次运行的编排。`runs.py` 管运行的生命周期（一条会话一个模型线程、
  一个打开的改动集），`assembly.py` 把模型与工具装配成可运行的图，`events.py` 是对外的
  事件协议与消息内容块，`interrupts.py` 把两种中断折成同一份形状，`stream.py` 是流式编排。
- `observability.py`：按配置给一轮 run 挂 Langfuse 审计回调（可选，见下）。

绘图子智能体只拥有视图工具：读取图切片和当前视图，修改布局、高亮、分组、折叠、
过滤、视野与标注，并持久化到 `view_state`。它没有创建或修改本体的工具。
`view_state.name` 不再独立编辑，始终镜像对应图的名称。

动作子智能体只拥有动作定义工具（外加共用的只读 `search`）：新建 / 复用一个动作，
补参数、补规则与字段赋值、整棵替换提交校验。它不碰实例，也不管画布怎么画。

**子智能体的过程默认不外露，动作是例外。** 视觉整理是短任务，前端只看到委派行与结果；
动作要连着调十几次工具，看不见过程就是「一直等待中」。`runtime/subagent_trace.py`
把子图命名空间对回发起它的那次 `task` 调用，登记的可见子智能体的工具调用会带
`agent` / `parentCallId` 流到前端，在委派行下面缩进显示；放宽或收紧只改
`VISIBLE_SUBAGENTS` 一处。

工具按关注点分类：建模工具与只读检索由主智能体直接持有（`build_model_tools`），
视图与动作两类包成子智能体**按需加载**——主智能体只看到子智能体的名字与描述，
用到哪一类才把那一类工具展开，避免工具清单一次全铺给模型。

视图规格的形状定义在 `app/schemas/view_state.py`（`ViewSpec`），写入前校验：前端画布
与绘图子智能体共写这份 JSONB，坏形状在落库前就挡下，别等前端算布局时才炸。

接 SSE 的接口挂在会话下——线程 ID 直接复用 `conversation_id`，不另引线程表：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/conversations/{id}/messages` | 发一句，返回 SSE 事件流 |
| POST | `/api/v1/conversations/{id}/resume` | 回答中断提问，返回 SSE 事件流 |

事件类型：`run.start` / `block.start` / `block.delta` / `block.end` / `tool.start` /
`tool.end` / `interrupt` / `state.update` / `run.error` / `run.end`。协议在后端一侧
定义于 `app/schemas/agent_events.py`（消息内容块见 `app/schemas/message_blocks.py`），
前端 `shared/types` 里的 `AgentEvent` / `MessageBlock` 与它一一对应；加字段不用抬
`PROTOCOL_VERSION`，改语义必须抬。

用户发言（包括 `/resume` 的回答）在运行开始时先单独提交；助手正文在回合结束时
写入 `conversation_message`。模型或工具失败也会留下带 `kind='error'` 的助手消息。

**`run.end` 带 `paused` 字段。** 中断和完成都以 `run.end` 收尾，前端靠它区分：
`paused: true` 表示卡在中途等人回答，该调 `/resume`；`false` 表示本轮做完了。

### 审计追踪（Langfuse）

可观测性走**开源自托管**的 [Langfuse](https://langfuse.com)，不依赖云服务，契合私有化诉求。

- 装配在 `app/agent/observability.py`：`LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY`
  两个都填上才启用；留空则整条链路零开销（不 import、不建客户端）。
- 回调挂在主图一轮 run 的 `config` 上（`runtime/stream.py`）：`conversation_id` 作 session、
  `owner_id` 作 user，前端一次对话正好对应 Langfuse 一个 session，方便回看。
- **子智能体内部的模型与工具调用也会被记录。** langgraph 把父 run 的 callbacks 透传给
  子智能体，deepagents 还会给它打 `ls_agent_type=subagent` 标签——这正是 SSE 里被
  `_is_streamable` 挡掉、`conversation_message` 也不落库的那部分内部过程。
- 用云端：填 `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` 即可。自托管：把
  `LANGFUSE_BASE_URL` 指向本地实例（如 `http://127.0.0.1:3000`）。自托管完整栈较重
  （web + worker + ClickHouse + Redis + S3），按官方 compose 单独起，本项目只负责把
  trace 发过去。

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
  编译而失败。当前 `deepagents==0.7.22` 的应用镜像已完整安装该依赖，
  `cryptography==50.0.2` 命中 Linux x86_64 的 manylinux 预编译 wheel，无需 Rust
  工具链。**升级 deepagents 时仍要重新验证镜像构建。**

## 数据库连接

Postgres 只监听本机（`127.0.0.1:5432`），不会暴露到局域网。凭据可用 `.env` 覆盖
`POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB`。
