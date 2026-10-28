# P4 只读查询 Agent 设计

> **2026-10-29 决议：** 已取消查询子 Agent 与 `execute_query_step`。主 Agent 直接使用 `query_objects` 和 `get_related_objects`；多对多关系由后者在数据服务内部查询关系实例。本文以下涉及查询子 Agent 的内容为历史设计。

- 日期：2026-10-27
- 状态：设计已确认，待实施
- 范围：已发布业务图、对象类型、关系类型与对象实例的只读问答
- 不在范围：动作执行、审计、写回、RDF/OWL、图发布历史版本
- 上游：P3 对象/关系查询；建模阶段的业务图与本体定义

## 1. 产品目标

P4 的长期目标仍是让 Agent 在动态本体上完成查询、动作执行和回读闭环。当前先只做查询，验证以下能力：

> 用户用自然语言提问，Agent 选择最合适的业务图，在图内解析对象与关系，查询真实对象数据，并给出带证据的回答。

产品要求：

- 不针对固定业务场景写死 Agent。
- 图的发布、对象类型和关系变化后，不改 Agent 代码即可生效。
- Agent 不直接拼 SQL，只通过受控工具查询。
- 当前阶段只读，动作执行后续单独设计。

## 2. 已确认决策

- P4 先做查询场景，查询链路跑通后再做动作执行。
- 图采用整图发布，不做多版本；重新发布覆盖当前发布产物。
- 发布只生成两样东西：图卡文本、图向量。不生成 RDF/OWL。
- 图卡是给 LLM 推理的文本；图向量由图卡文本生成，用于候选图召回。
- 图选择采用“图向量粗召回 + LLM 看图卡判断 + 代码校验”。代码不做语义过滤，只校验 LLM 给出的图、对象、关系、路径是否真实存在。
- 选中图后，才把问题落到对象类型、对象实例和查询路径。
- 不跨图拼接；图内不存在目标或路径时，明确报告模型缺口。
- 只读当前用户可见的已发布对象实例和当前版本；不读取数据集、源库或映射草稿。
- Agent 不能编造对象类型、关系、属性或对象主键。
- 查询任务由一个独立的查询子 Agent 负责规划与执行，不建 `queryTask` 表。
- 查询子 Agent 使用 `CompiledSubAgent`，拥有独立 checkpoint 线程；中间消息和工具结果通过该线程持久化。
- 查询子 Agent 的输入是图详情与 `rewrite_query` 的结果，不接收自然语言原问题。
- 取消 `match_question_to_graph` 和 `plan_next_query_step`；锚点解析、路径规划和下一步选择都由查询子 Agent 自己决定。
- 查询子 Agent 只拿两个实例查询工具：`query_objects`、`execute_query_step`。

## 3. 发布产物

### 3.1 图卡

图卡从模型库直接生成，作为 LLM 的推理文本。建议包含：

- 图名称、描述
- 对象类型：稳定 ID、名称、英文名、描述、主键/标题键、关键属性
- 关系：稳定 ID、名称、起点、终点、方向、基数、描述
- 动作：后续阶段追加，当前不进入查询链路

图卡是固定投影，不针对单个问题临时裁剪。图较大时，LLM 可以指定元素 ID，再由代码按 ID 取局部。

示例：

```text
图：销售运营

对象类型：
- Customer 客户：姓名、等级
- Order 订单：订单号、状态、创建时间、发货时间
- Product 商品：名称、SKU
- Supplier 供应商：名称

关系：
- Customer_places_Order：Customer -> Order，1:N，客户下单
- Order_contains_Product：Order -> Product，N:M，订单包含商品
- Product_suppliedBy_Supplier：Product -> Supplier，N:1，商品由供应商提供
```

### 3.2 图向量

- 每张图生成一个图向量。
- 向量输入由图卡中的对象类型、关系名称和描述拼接生成。
- 重新发布时覆盖旧图向量，不保留历史版本。
- 发布前要求图内对象类型、属性和关系均为 `confirmed` 或 `approved`；存在草稿时阻断发布。

### 3.3 明确不使用 RDF/OWL

P4 的 LLM 推理链路不需要 RDF/OWL：

- LLM 不执行 SPARQL 或 OWL 推理。
- 图卡比 RDF 三元组更紧凑，对象和关系保持在同一上下文里。
- 若未来需要标准本体交换、外部推理器或图谱工具，再从模型库导出 RDF，不影响当前主链路。

### 3.4 发布入口

- 建模工作台右侧画布顶部提供「发布图 / 重新发布」按钮，旁显「未发布」或「已发布 + 时间」。
- 存在待确认的本体改动、草稿对象/属性/关系或图内没有对象类型时，前端禁用按钮；空改动集和纯视图改动不拦截，后端仍做最终校验。
- 发布成功直接刷新状态，不重取整块工作区；发布失败由页面错误提示展示后端原因。

## 4. 在线查询链路

```text
用户问题
  → rewrite_query 改写成一句业务查询
  → 改写句 embedding
  → 图向量召回 Top-5
  → 主 Agent 看候选图卡选图
  → 代码校验选中的图 ID、发布状态和当前用户可见性
  → get_graph_detail 读取对象类型与关系类型
  → task query_agent(graph_detail, rewritten_query)
       → query_objects 解析锚点对象
       → 子 Agent 基于图详情、改写句和历史结果规划下一步
       → execute_query_step 执行一步关系遍历
       → 结果进入子 Agent 上下文，继续或结束
  → 主 Agent 读取子 Agent 的结构化结果，回答用户
```

### 4.1 查询改写

工具：`rewrite_query(question, context?) -> str`

- 只输出一句改写后的业务查询。
- 具体实体名标注业务类型，例如 `张三（客户）`。
- 不返回 concepts、对象类型 ID、候选列表或 JSON。
- 实体类型不确定时保留多候选，例如 `张三（客户/员工）`。
- 不提前使用模型内部英文类型名。

完整提示词见附录 A。

### 4.2 图召回与选择

1. 用改写句生成 embedding。
2. 检索图向量，取 Top-5 候选图。
3. 将问题与候选图卡交给 LLM，由 LLM 判断哪张图合适。
4. LLM 只输出选中的图；代码校验图 ID、发布状态和当前用户可见性。
5. 多个图都合理时让用户选择；没有合适图时报告模型缺口。
6. 代码不基于猜测的概念绑定做语义过滤。

选中图后，主 Agent 通过 `get_graph_detail(graph_id)` 读取对象类型、属性、关系类型、方向和基数。图详情不包含对象实例值。

主 Agent 运行在 `query` 会话中。查询会话的 `resource_id` 只作为会话身份，不绑定固定图；图在每次问题中动态选择。这样同一会话可以先查销售图，再查员工图，不需要为每张图单独建会话。

### 4.3 查询子 Agent

查询子 Agent 是一个独立的 `CompiledSubAgent`，名称固定为 `query_agent`。它负责：

- 把改写句落到对象类型和对象实例
- 解析锚点对象，处理多候选与零匹配
- 基于图详情规划查询路径
- 逐步执行关系遍历，并根据真实结果决定下一步
- 汇总证据，返回答案、限制和数据缺口

主 Agent 不参与单步规划，也不直接调用实例查询工具。

#### 4.3.1 运行形态

- 使用 `CompiledSubAgent`，由 `task(subagent_type="query_agent", ...)` 调用。
- `task` 的 `description` 使用固定 JSON 信封，只包含 `graph_id` 和 `rewritten_query`；包装器读取 `graph_id` 生成线程 ID，不解析自然语言。
- 图详情由包装器按 `graph_id` 从服务端重新读取，不接收主 Agent 复制或压缩后的版本。这样属性/关系 UUID 不会在委派过程中丢失，代码也会再次校验图的发布状态和归属。
- 信封缺字段或 `graph_id` 非法时，包装器直接返回 `BLOCKED` 结构化结果，不写查询线程。
- 复用应用的 `PostgresSaver`，但使用独立线程，例如：

```text
<conversation_id>:query:<graph_id>
```

- 线程上保存完整的 message、工具调用、工具结果和最终结构化结果，因此不需要 `queryTask` 表。
- 线程状态除 `messages` 外，还保存结构化的步骤摘要：`steps`（step_id → 输入/输出对象引用）、`anchor_refs`、`frontier`、`evidence` 和 `step_counter`。
- 查询子 Agent 优先在模型绑定层关闭并行工具调用；即使 provider 忽略该参数，`steps` / `evidence` / `frontier` 也用 reducer 合并增量，并行步骤按 `tool_call_id` 生成唯一 step_id，不会互相覆盖或触发并发写冲突。
- 返回主 Agent 时由包装器显式投影为只含 `messages` 和 `structured_response`；不能依赖 `CompiledSubAgent` 自动裁剪，因为它会把子 Agent 的自定义 state 字段并回父 Agent。`steps`、`frontier` 等查询状态留在子 Agent 线程，不污染主 Agent 状态。
- 同一会话、同一张图的后续追问继续使用同一线程，可以直接引用上一轮结果。
- 换图时线程 ID 变化，自动得到新的查询上下文。
- 子 Agent 不继承主 Agent 的对话；它只看到主 Agent 传入的图详情、改写句和之后追加的任务消息。

#### 4.3.2 输入

```json
{
  "graph_id": "uuid",
  "rewritten_query": "一句业务查询"
}
```

- 上述 JSON 就是 `task(description=...)` 的全文；主 Agent 只负责选图和改写，不复制图详情。
- 包装器按 `graph_id` 读取 canonical `graph_detail`，其中只包含对象类型、属性、关系类型、方向、基数和主键/标题键。
- 不传图卡、不传对象实例、不传数据源信息。
- 主 Agent 传入的是选中图的结构快照；查询子 Agent 仍以图 ID 校验查询范围。

#### 4.3.3 输出

查询子 Agent 通过 `response_format` 返回结构化结果：

```text
status: OK | EMPTY | NOT_INDEXED | NEEDS_USER | BLOCKED
answer: str | null
evidence: list[Evidence]
question: str | null
options: list[str]
limitations: list[str]
```

- `OK`：回答完成，附带证据
- `EMPTY`：当前索引无匹配数据
- `NOT_INDEXED`：所需对象类型或属性未建索引
- `NEEDS_USER`：同名多对象、缺少业务时间字段、范围过大等需要用户确认
- `BLOCKED`：图内无可行路径、超过步数上限或连续无进展

`Evidence` 至少包含：

```text
step_id
object_type_id
primary_key
title
properties
index_version_id
```

主 Agent 只转述子 Agent 的答案与证据，不自行补造字段。遇到 `NEEDS_USER` 时，由主 Agent 调现有 `ask_user`；用户回答后，主 Agent 再把答案作为新的 `task` 输入送回同一个查询线程。

#### 4.3.4 上下文规则

- 子 Agent 的上下文就是查询历史，不另建任务表。
- 每次工具结果都带 `step_id`、`status`、`total`、`index_version_id`，便于后续步骤引用。
- 中间结果只保留回答所需字段，不返回整对象。
- 子 Agent 自己维护当前锚点、当前 frontier 和证据；不需要主 Agent 传入历史。
- 发生上下文压缩时，已执行步骤的摘要仍保留；压缩不能改变已返回的事实。

#### 4.3.5 提示词约束

- 一次只规划并执行一步，不生成完整路径后一次性执行。
- 只能使用图详情中存在的对象类型、属性、关系 ID 和方向。
- 先解析锚点，再遍历关系；不允许跳过锚点直接猜对象主键。
- 多个同名对象时返回 `NEEDS_USER`，不要自行选择。
- “最新”“最近”必须落到模型声明的业务时间字段；没有声明时返回 `NEEDS_USER`。
- 任一步 `FAILED` 或 `NOT_INDEXED` 就停止该分支并报告位置。
- 结果被截断时，不假设已拿到全集；提示用户缩小范围或显式扩大条数。
- 不跨图查询，不编造数据，不直接拼 SQL。

### 4.4 查询工具

实例数据只通过以下两个工具读取。图详情负责 schema，工具负责数据。

#### 4.4.1 `query_objects`

对象实例的检索与读取入口。

输入：

```text
object_type_id
primary_key?             # 给了就按主键取单个对象
q?                       # 主键/标题包含搜索，用来解析实例名
filters?                 # [{property_id, op, value}]，多条件 AND
sort?                    # [{property_id, direction}]
limit
project?                 # 回答需要的属性 ID；默认主键 + 标题
```

输出：

```text
step_id
object_type_id
index_version_id
items: [{primary_key, title, properties}]
total
status: OK | EMPTY | TRUNCATED | NOT_INDEXED | FAILED
truncated
```

规则：

- `primary_key` 与 `q`、`filters`、`sort` 互斥；只允许同时带 `project`。
- `q` 只做文本包含搜索，不负责语义匹配。
- `filters`、`sort`、`project` 的字段必须属于该对象类型且当前可查。
- 只读当前索引版本，不读历史版本、数据集或源库。
- 不自动决定锚点；多个匹配原样返回，由子 Agent 判断是否需要追问。
- 零匹配返回 `EMPTY`，不判定为图不匹配。
- `status=TRUNCATED` 时必须带 `total` 和 `truncated=true`。
- 第一次锚点解析由代码分配 `step_id=s0`；后续读取详情也分配 step ID，并写入查询子 Agent 的 `steps` 状态。

用途：

- `query_objects(Customer, q="张三")` 解析锚点
- `query_objects(Order, filters=[status eq "未发货"])` 列对象
- `query_objects(Customer, primary_key="C001", project=["name", "level"])` 读详情

#### 4.4.2 `execute_query_step`

唯一的关系遍历执行器。

输入：

```text
input_step_id            # 上一步结果；锚点步骤固定为 s0
relation_id
direction                # outgoing | incoming
filters?                 # 作用于目标对象
sort?                    # 作用于目标对象；代码自动追加主键为稳定次级键
limit
project?                 # 目标对象需要返回的属性
expected_cardinality     # one | many
```

输出：

```text
step_id
status: OK | EMPTY | TRUNCATED | NOT_INDEXED | FAILED
items: [{object_type_id, primary_key, title, properties}]
total
truncated
index_version_id
```

规则：

- 目标对象类型由关系定义和方向推导，不接受模型填写。
- 代码从查询子 Agent 的 `steps` 状态解析 `input_step_id`；找不到或类型不匹配时返回结构化错误。
- 代码校验关系属于当前图、方向合法、输入类型与关系端点一致。
- `filters`、`sort`、`project` 只作用于目标对象。
- 一对多返回集合；多对多去重。
- `limit` 设上限；结果超过上限时返回 `TRUNCATED`，不静默截断。
- `step_id` 由代码分配，模型只引用，不自行编造。
- 执行结果同时写入 `steps`、`frontier` 和 `evidence` 状态，供下一步和最终回答引用。
- 不规划下一步，不执行完整路径；一次只走一条关系。

### 4.5 锚点解析与查询语义

- 锚点优先按主键、标题键搜索；姓名不是标题键时，用对应属性过滤。
- 多个匹配：返回候选对象类型和标题，由子 Agent 返回 `NEEDS_USER`。
- 零匹配：报告数据缺口，不判定为图不匹配。
- “最新/最近”必须使用模型声明的业务时间属性；没有声明时追问，不能默认 `created_at`。
- 同一时间值需要稳定次级排序键，代码统一追加主键。
- 中间结果超过上限时返回 `TRUNCATED`，由子 Agent 请求用户缩小范围或显式展开。
- 任一步失败就停止该分支并报告失败位置，不编造后续结果。
- 完整计划由子 Agent 在上下文中逐步累积，不一次生成。

### 4.6 查询层能力补齐

P4 查询层需要补齐的能力：

- 对象列表按属性排序
- 关系遍历按对端属性过滤和排序
- 按主键批量取对象（供 `execute_query_step` 内部补齐投影字段，不单独暴露模型工具）
- 明确区分“无数据”“未建索引”“结果被截断”

实现入口：

- `POST /internal/v1/ontology/object-types/{object_type_id}/objects/query`：一次完成对象过滤、排序、投影与截断统计。
- `POST /internal/v1/ontology/query-steps`：接收一组输入对象引用，按关系方向和目标属性完成遍历、过滤、排序、投影与多对多去重。
- Python 只负责图范围、关系端点和输入类型校验；不在 BFF 重做过滤、排序或截断。

## 5. 非目标

- 不做动作执行、写回、审计和回读。
- 不做跨图查询。
- 不做 RDF/OWL 或 SPARQL。
- 不做历史版本或图发布历史。
- 不保存查询任务表；查询状态由子 Agent 的 checkpoint 线程持有。

## 附录 A：rewrite_query 提示词

完整提示词见 [2026-10-27-rewrite-query-prompt.md](prompts/2026-10-27-rewrite-query-prompt.md)。
