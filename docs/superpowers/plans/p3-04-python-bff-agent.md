# P3 W1-04：Python BFF 与映射 Agent

> 依赖：W0（schema、`ConversationKind.ONTOLOGY_WORKBENCH`、Alembic）。Java 接口按设计稿 §8.1 的契约开发，可用 `httpx.MockTransport` 先跑通。
> 写作用域：`backend/app/api/ontology.py`、`backend/app/services/ontology.py`、`backend/app/services/data_service/ontology_client.py`、`backend/app/schemas/ontology.py`、`backend/app/agent/definitions/ontology_workbench.py`、`backend/app/agent/prompts_ontology_workbench.py`、`backend/app/agent/tools/ontology/`、`backend/app/api/router.py`、`backend/app/agent/definitions/registry.py` 与对应测试。
> 设计来源：[技术设计](../specs/2026-10-23-ontology-mapping-technical-design.md) §8.2、§9

## 目标

交付浏览器统一 API 和映射 Agent：所有 P3 请求经 Python 转发到 Java；Agent 能读映射、给建议、校验、预览、改草稿，关键动作走确认。

## 交付物

### Java 客户端

`services/data_service/ontology_client.py`

- 复用 `DataServiceClient` 的 token、超时、错误转换。
- 方法覆盖 §8.1 的全部接口。
- camelCase ↔ snake_case 只在这一层做。
- 409 映射为 `RevisionConflict`，422 映射为 `UnprocessableEntityError`。

### BFF

`api/ontology.py`：

```text
GET    /api/v1/ontology/resources
GET    /api/v1/ontology/conversations
GET    /api/v1/ontology/bindable-datasets
GET    /api/v1/ontology/bindable-datasets/{kind}/{dataset_id}/columns

GET    /api/v1/ontology/object-types/{object_type_id}/binding
PUT    /api/v1/ontology/object-types/{object_type_id}/binding
DELETE /api/v1/ontology/object-types/{object_type_id}/binding
POST   /api/v1/ontology/object-types/{object_type_id}/binding/validate
POST   /api/v1/ontology/object-types/{object_type_id}/binding/preview
POST   /api/v1/ontology/object-types/{object_type_id}/binding/suggest
POST   /api/v1/ontology/object-types/{object_type_id}/binding/generate
GET    /api/v1/ontology/object-types/{object_type_id}/binding/runs

GET    /api/v1/ontology/relation-types/{relation_id}/binding
PUT    /api/v1/ontology/relation-types/{relation_id}/binding
DELETE /api/v1/ontology/relation-types/{relation_id}/binding
POST   /api/v1/ontology/relation-types/{relation_id}/binding/preview
POST   /api/v1/ontology/relation-types/{relation_id}/binding/suggest
POST   /api/v1/ontology/relation-types/{relation_id}/binding/generate
GET    /api/v1/ontology/relation-types/{relation_id}/binding/runs
GET    /api/v1/ontology/relation-types/{relation_id}/overview

GET    /api/v1/ontology/runs/{run_id}
POST   /api/v1/ontology/runs/{run_id}/cancel

GET    /api/v1/ontology/object-types/{object_type_id}/objects
GET    /api/v1/ontology/object-types/{object_type_id}/object
GET    /api/v1/ontology/object-types/{object_type_id}/object-relations
GET    /api/v1/ontology/object-types/{object_type_id}/object-relations/{relation_id}
GET    /api/v1/ontology/relation-types/{relation_id}/instances
```

- `resources` 汇总对象类型、外键关系、多对多关系与绑定摘要，供左侧栏一次加载。
- `suggest` 在 Python 侧按名称相似度与类型兼容度计算，不写 Java。
- `conversations` 按 `(owner, resource_kind, resource_id)` 确定性生成会话并确保存在。
- `owner_id` 只从登录态取，不接受前端传入。
- 详情返回前用本体定义补齐未映射属性并排序。
- 关系分组补可读名和方向标签。

### 关系定义同步

- 打开对象/关系工作台、保存绑定、预览、生成前，把关系定义 upsert 到 Java。
- Python 是唯一来源；Java 只存快照。
- 同步失败不阻塞读取，但生成前必须成功，否则返回 503。

### Agent

`agent/definitions/ontology_workbench.py`

- `kind = ontology_workbench`，`resource_id` 为对象类型或关系 ID。
- 每轮加载：当前资源、绑定、映射草稿、最近 run。
- 工具：
  - 读：`list_bindable_datasets`、`get_dataset_schema`、`get_subject_definition`、`get_mapping`、`suggest_mapping`、`validate_mapping`、`preview_mapping`、`list_index_runs`、`get_index_run`。
  - 写：`update_mapping`、`start_generation`、`rebind_dataset`、`unbind`、`cancel_index_run`。
- 写工具复用 `interrupt`；改主键/标题键时在 `update_mapping` 内部再确认。
- 409 返回冲突和当前 revision，不自动重试。
- 不创建本体定义，不写对象实例，不在轮次内等待生成完成。

`prompts_ontology_workbench.py`

- 说明画布、字段映射、主键/标题键、阻断/告警。
- 明确生成、换源、解绑、取消必须确认。
- 明确失败先看运行记录，再解释错误。

## 实施步骤

- [ ] 1. `ontology_client` 全量方法 + `MockTransport` 测试。
- [ ] 2. BFF 路由与错误映射 + 测试。
- [ ] 3. 详情合并本体定义 + 测试。
- [ ] 4. 关系定义同步 + 测试。
- [ ] 5. Agent 读工具 + 测试。
- [ ] 6. Agent 写工具 + 确认中断测试。
- [ ] 7. 注册 definition 与对话 kind + 集成测试。
- [ ] 8. 全量后端校验。

## 验收命令

```bash
cd backend && .venv/bin/python -m pytest -q && .venv/bin/ruff check . && .venv/bin/ruff format --check . && .venv/bin/mypy app
```

## Definition of Done

- 所有路由都从登录态取 owner，不接受客户端 owner 参数。
- Java 409/422/404 都映射成现有错误类型，响应结构保持一致。
- Agent 写工具在没有确认时返回 `interrupted`，不落库。
- Agent 改主键/标题键会二次确认。
- 详情中未映射属性为 `null`，顺序与本体定义一致。
- 关系定义同步失败时生成返回明确错误，不静默继续。

## 交接说明

- 前端只依赖本流的 `/api/v1/ontology/**`。
- Java 接口未就绪时用 `MockTransport` 锁定契约，不修改 Java 代码绕过。
- 不改现有建模 Agent 的 `definitions/ontology.py`，新定义单独注册。
