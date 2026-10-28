# P4 Agent 使用

- 状态：🚧 只读查询已实施（前后端与 Java 查询原语完成；动作执行后置）
- 目标：先让 Agent 基于已发布业务图完成对象/关系查询和证据回答；查询跑通后再扩展动作执行、人工确认、审计与回读。
- 当前子阶段完成标准：能改写问题、召回并选择图、解析对象、执行只读查询并回答；动作执行不在本轮范围。

## 任务拆分

| 子任务 | 状态 | 说明 |
|---|---|---|
| 4.0 只读查询技术设计 | ✅ 已完成 | 图发布、图向量、图卡、主 Agent 查询工具契约 |
| 4.1 查询改写工具 | ✅ 已完成 | `rewrite_query` 输出一句业务查询，实体标注类型 |
| 4.2 图发布与图向量 | ✅ 已完成 | 整图发布、图卡生成、图向量生成，不做多版本 |
| 4.3 图选择与对象解析 | ✅ 已完成 | 图向量召回、LLM 选图、代码校验、主 Agent 解析锚点 |
| 4.4 受控查询执行 | ✅ 已完成 | `query_objects`、`get_related_objects`、关系遍历、排序/筛选、证据返回 |
| 4.5 动作执行技术设计 | ⏳ 未开始 | 查询闭环跑通后启动；执行入口、参数装配、校验、事务与回滚 |
| 4.6 动作实例与审计 | ⏳ 未开始 | 执行记录、操作者、时间、变更前后值 |
| 4.7 执行后验证 | ⏳ 未开始 | 重新查询并汇报实际结果；失败原因回传 |

## 已确认约束

- P4 先做只读查询，查询链路跑通后再做动作执行。
- 图采用整图发布，不做多版本；重新发布覆盖当前发布产物。
- 发布只生成图卡和图向量；图卡是 LLM 推理文本，图向量由图卡生成。
- P4 不使用 RDF/OWL；未来需要标准本体交换或外部推理器时再导出。
- 图选择采用“图向量召回 + LLM 看图卡判断 + 代码校验”，代码不做语义过滤。
- 选中图后，才解析对象类型、对象实例和查询路径；不跨图拼接。
- 查询改写只输出一句话，实体写成 `名称（类型）`，不返回 concepts 或候选对象类型。
- 只读当前用户可见的已发布对象实例和当前版本，不读取数据集、源库或映射草稿。
- Agent 不直接拼 SQL，只调用受控查询工具。
- 查询由主 Agent 直接规划与执行，不设查询子 Agent，不建 `queryTask` 表。
- 主 Agent 读取图详情后，根据对象、属性、关系与方向自行推理下一步查询；图详情只含类型层信息，不含对象实例。
- 实例查询只提供两个工具：`query_objects` 与 `get_related_objects`。
- `get_related_objects` 沿关系批量返回对端对象引用；多对多关系由数据服务内部查询关系实例，不向 LLM 暴露中间表细节。
- 拿到关系对端主键后，统一用 `query_objects(primary_keys=...)` 批量读取完整对象。
- 动作执行、人工确认、审计和回读本次不启动；原动作层约束继续有效。
- 动作只能通过已定义的动作类型、参数、规则和提交校验执行。
- 提交前必须校验；失败不能只改一半。
- 修改真实数据前需要人工确认。
- 每次执行留下审计记录。
- Agent 不直接拼 SQL 修改数据，只调用受控工具。

## 待讨论议题

1. 大型图的图卡组织方式与局部展开交互。
2. 图选择时 LLM 判断与用户确认的边界。
3. 属性、关系是否需要补充描述和别名。
4. 大型图详情传给主 Agent 时的规模控制。
5. 查询会话长上下文的压缩策略。
6. 查询证据在前端的展示方式。
7. 动作执行、人工确认、审计和回读（查询闭环之后）。

## 实施结果

- 前后端只读查询闭环已落地：图发布、图向量召回、图详情、`rewrite_query`、主 Agent 直接查询、两个实例查询工具和查询工作台。
- 查询会话使用 `kind='query'` 与随机 `resource_id`；主 Agent 状态存在 checkpoint，不建 `queryTask`，也不创建查询子 Agent 线程。
- Java 增加 `/internal/v1/ontology/object-types/{id}/objects/query` 与 `/internal/v1/ontology/relation-types/{id}/related-objects`，过滤、排序、投影、关系批量去重和 `total/truncated` 均在数据服务完成。
- 前端独立入口为 `/query` 与 `/query/:conversationId`，左侧会话、右侧对话，支持新建、切换、删除、搜索、中断恢复和证据展开。
- 建模画布顶部新增图发布入口，显示未发布/已发布时间；发布成功后查询工作台才可召回该图。
- 校验：后端 662 个测试通过，ruff/mypy 通过；前端 302 个测试、lint 与生产构建通过；Java 查询定向测试通过。
- Java 全量 `555` 项中仍有 `3` 个失败、`8` 个错误，集中在 Iceberg/Spark 预览与调度时间戳用例（Java 26 `getSubject` 限制、Iceberg 写入失败、调度时间戳为空），与查询链路无关；查询定向用例 `Ontology*Query*`、`FilterParser*` 全绿。

## 已知限制

- 查询工作台尚未做浏览器级 E2E；交互由共享组件测试、前端构建和后端契约测试覆盖。
- 大型图详情与图卡的 token 规模控制尚未做专项验收；查询过程保留在主 Agent 会话上下文中，未额外实现定制压缩策略。
- Java 全量测试受本机 Java 26 / Iceberg 环境影响，仍有 11 个既有失败；本轮只把查询相关用例纳入必过范围。
- 动作执行、人工确认、审计与执行后回读仍后置。

## 沟通记录

| 日期 | 议题 | 结论/记录 |
|---|---|---|
| 2026-10-27 | P4 阶段顺序 | 先做只读查询，查询闭环跑通后再做动作执行。 |
| 2026-10-27 | 图发布与推理格式 | 整图发布、不做多版本；发布图卡 + 图向量，不使用 RDF/OWL。 |
| 2026-10-27 | 在线查询链路（已被 2026-10-29 决议替代） | 改写问题 → 图向量召回 → LLM 看图卡选图 → 读取图详情 → 委派持久化查询子 Agent → 逐步执行 → 证据回答。详见[查询设计](../specs/2026-10-27-agent-query-design.md)。 |
| 2026-10-27 | 图选择与代码边界 | LLM 负责语义判断，代码只校验图/对象/关系/路径是否存在及数据是否可用，不做语义过滤。 |
| 2026-10-27 | 查询改写工具 | `rewrite_query(question, context?) -> str`，只输出一句查询；实体写成 `名称（类型）`。提示词见[独立文档](../specs/prompts/2026-10-27-rewrite-query-prompt.md)。 |
| 2026-10-27 | 候选图检索工具 | `search_graphs(query, limit=5)` 返回图 ID、余弦相似度和发布图卡；不选图、不返回对象实例。 |
| 2026-10-27 | 图详情工具 | `get_graph_detail(graph_id)` 返回结构化对象类型与关系索引，不重复返回图卡、对象实例或数据，减少 token 消耗。 |
| 2026-10-27 | 问题-图匹配工具（已取消） | `match_question_to_graph` 原设计只输出 `anchor` 和 `goal`；后续决议取消该工具，由查询子 Agent 自己解析锚点。 |
| 2026-10-27 | 单步查询规划（已取消） | `plan_next_query_step(task_id)` 原设计每次只规划下一步；后续决议取消该工具，由查询子 Agent 在上下文中逐步规划。 |
| 2026-10-27 | 查询子 Agent 边界（已取消） | 查询任务改由独立 `CompiledSubAgent` 负责；使用独立 checkpoint 线程，不建 `queryTask`。取消 `match_question_to_graph` 和 `plan_next_query_step`。 |
| 2026-10-27 | 查询子 Agent 输入输出（已取消） | 输入为图详情 + `rewrite_query` 结果；图详情只含对象类型、属性、关系类型。输出 `answer + evidence + status + limitations`，需要追问时返回 `NEEDS_USER` 由主 Agent 调 `ask_user`。 |
| 2026-10-27 | 查询工具集（已被 2026-10-29 决议替代） | 实例数据只通过 `query_objects` 和 `execute_query_step` 读取；不单独提供 `get_object`、`get_object_relations`。 |
| 2026-10-29 | 取消查询子 Agent | 主 Agent 直接持有 `query_objects` 与 `get_related_objects`；关系路径由主 Agent 基于图详情推理。`get_related_objects` 负责批量取对端引用，多对多关系在数据服务内部查询关系实例；再用 `query_objects(primary_keys=...)` 读取完整对象。删除查询子 Agent、`execute_query_step`、`/query-steps` 及配套状态/测试。 |
| 2026-10-27 | 设计与计划 | 设计稿已更新；实施步骤见[只读查询 Agent 计划](../plans/2026-10-27-agent-query-plan.md)。 |
| 2026-10-17 | 动作执行与 Agent 闭环能力 | [总体设计 §3.4](../specs/2026-10-17-data-integration-and-agent-loop-design.md#34-第四层agent-使用) |
| 2026-10-17 | 待讨论事项 | [总体设计 §6](../specs/2026-10-17-data-integration-and-agent-loop-design.md#6-待讨论) 第 6-7 项 |
| 2026-10-16 | 动作层定义 | [动作层设计](../specs/2026-10-16-action-layer-design.md) |

## 文档索引

- 现有设计：[总体设计 §3.4、§6](../specs/2026-10-17-data-integration-and-agent-loop-design.md)
- 当前查询设计：[2026-10-27-agent-query-design.md](../specs/2026-10-27-agent-query-design.md)
- 当前查询计划：[2026-10-27-agent-query-plan.md](../plans/2026-10-27-agent-query-plan.md)
- 动作定义层：[2026-10-16-action-layer-design.md](../specs/2026-10-16-action-layer-design.md)
- 后续动作设计：`../specs/2026-10-XX-action-execution-technical-design.md`（查询闭环后）
- 可复用实现：Agent 运行时、SSE、中断卡片、动作定义与提交校验

## 下一步

下一步进入动作执行设计：选择动作、填写参数、人工确认、执行、审计与回读。只读查询的浏览器级 E2E 和大型图上下文压缩作为独立收尾项。
