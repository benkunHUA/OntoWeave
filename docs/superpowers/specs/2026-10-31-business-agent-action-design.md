# 业务 Agent 行动执行设计

- 日期：2026-10-31
- 状态：已确认，待实施
- 范围：在现有业务问答 Agent 内增加行动发现、参数装配、提交校验、人工确认、执行、审计与回读
- 上游：P4 Agent 闭环、动作层定义、业务行动编辑覆盖层
- 不在范围：独立执行面、独立 Action Agent、物化快照、动作定义编辑

## 0. 结论先行

1. 现有 Query Agent 改名为 Business Agent（业务 Agent），仍然只有一个会话入口、一个 Agent、一个执行面。
2. 行动执行不是新服务入口，而是业务 Agent 的一个受控工具：`execute_action`。
3. 图卡增加“可执行动作”区段，图向量继续由图卡生成，因此 `search_graphs` 能召回包含行动的图；不再新增独立 `search_graph_actions`。
4. 选中图后，`get_graph_detail` 返回行动摘要；需要填参数时再调 `get_action_detail` 取完整定义。
5. `execute_action` 内部固定执行：参数校验 → 提交校验 → edit set → 数据服务 preview → 人工确认 → apply → effective readback。
6. 行动产生的数据变更只写数据服务的编辑覆盖层，不写基础索引表，也不回写源库。
7. 人工确认复用现有 LangGraph interrupt 通道，但使用结构化 `action_confirmation` 载荷，展示对象与字段前后值。

## 1. 已确认约束

- 严格采用 Palantir 式编辑语义。
- 覆盖全部五种规则：`create_object`、`modify_object`、`delete_object`、`create_link`、`delete_link`。
- 覆盖层按本体身份挂载，是本体级，不按 `graph_id`；图只进入执行审计上下文。
- 主键不可修改；标题可修改，清空时回退到主键。
- 绑定或映射变化时覆盖行标记 `stale`，stale 不参与查询。
- v1 不支持修改数组属性。
- `ontology_edit_execution` 与 `ontology_edit_change` 必做。
- P4 不做 effective 物化快照。
- 不新增独立执行面，行动能力挂在现有业务 Agent 内。
- Agent 不直接拼 SQL，不直接调用数据服务写接口；只能调用受控工具。

## 2. 总体链路

```text
用户提出业务请求
  → 业务 Agent 判断是查询还是行动
  → search_graphs（图卡已含行动）
  → get_graph_detail（对象、关系、行动摘要）
  → 需要时 get_action_detail（完整参数、规则、提交校验）
  → query_objects / get_related_objects 解析对象参数
  → execute_action
       → 参数与提交校验
       → 规则装配 edit set
       → 数据服务 preview
       → interrupt 人工确认
       → 数据服务 apply（单事务、幂等）
       → effective readback
  → Agent 用回读结果回答
```

行动执行与查询共用同一个 Agent 会话、同一个 checkpointer、同一个消息流；不创建 `actionTask`，不创建 Action 子 Agent。

## 3. Agent 改名

### 3.1 命名

对外名称统一为“业务助手 / 业务工作台”，代码统一为 `business`。

| 旧 | 新 |
|---|---|
| `QueryAgentDefinition` | `BusinessAgentDefinition` |
| `QueryRunContext` | `BusinessRunContext` |
| `MAIN_QUERY_AGENT_PROMPT` | `MAIN_BUSINESS_AGENT_PROMPT` |
| `agent/tools/query/` | `agent/tools/business/` |
| `ConversationKind.QUERY = "query"` | `ConversationKind.BUSINESS = "business"` |
| `POST /query/conversations` | `POST /business/conversations` |
| 前端 `/query`、`features/query` | `/business`、`features/business` |

`query_objects`、`get_related_objects`、`rewrite_query` 这些工具名保留：它们描述的是具体动作，不承担 Agent 命名。

### 3.2 持久化兼容

- 新增 Alembic 迁移：把 `conversation.kind = 'query'` 更新为 `'business'`，并替换 `ck_conversation_kind`。
- 会话 ID 不变，checkpointer 线程仍按 `conversation_id` 读取，历史消息与断点不受影响。
- `/query/*` 路由保留一个版本的 301/307 重定向或兼容入口，指向 `/business/*`；前端 `/query` 同样重定向。
- 读取接口接受 `kind=query` 作为兼容别名，写入一律落 `business`。

### 3.3 工具目录

```text
backend/app/agent/tools/business/
  context.py          # 业务工具运行上下文
  selection.py        # search_graphs / get_graph_detail
  read.py             # query_objects / get_related_objects
  action.py           # get_action_detail / execute_action
  summary.py          # 工具调用的一行摘要
```

`app/services/query` 保持纯查询原语，不因 Agent 改名而移动。

## 4. 行动发现

### 4.1 图卡增加行动

图卡仍由图快照生成，增加固定区段：

```text
图：售后运营

对象类型：
- 工单（Ticket，id=...）：工单号(主键)、状态、解决说明
- 配件（Part，id=...）：配件编号(主键)、名称

关系：
- 工单_使用_配件（id=...）：工单 -> 配件，many_to_many，使用配件

可执行动作：
- 关闭工单（id=...）：关闭处理中的工单并记录解决说明。
  参数：工单（对象：工单，必填）、解决说明（文本，可选）
  影响：修改 工单
  校验：工单.状态 是 处理中
- 登记配件使用（id=...）：把配件关联到工单。
  参数：工单（对象：工单，必填）、配件（对象：配件，必填）
  影响：新增 工单_使用_配件
```

图向量继续对整张图卡做 embedding，因此行动名称、描述、参数和影响范围都会进入召回文本。**不新增 action 向量表，也不新增 `search_graph_actions`。**

图卡由 `render_graph_card(snapshot, action_summaries)` 生成；行动摘要由统一的 `action.presentation` 模块产出，避免图卡、图详情、工具三处各拼一遍。

### 4.2 发布校验

图发布时新增行动校验：

- 图内每个行动及其参数、规则、赋值、条件节点必须是 `confirmed` 或 `approved`。
- 行动涉及的对象类型和关系必须都在当前图内。
- 不满足时阻断发布，并返回具体行动/元素名称。

### 4.3 `get_graph_detail` 的行动摘要

`get_graph_detail` 增加顶层 `actions`，每个行动只放“判断是否该选它”所需的信息，不返回完整规则树。

```json
{
  "actions": [
    {
      "id": "uuid",
      "name": "关闭工单",
      "description": "关闭处理中的工单并记录解决说明",
      "status": "confirmed",
      "parameters": [
        {
          "id": "uuid",
          "name": "工单",
          "description": "要关闭的工单",
          "kind": "object_ref",
          "datatype": null,
          "target_object_type_id": "uuid",
          "target_object_type_name": "工单",
          "is_array": false,
          "is_required": true,
          "default_value": null,
          "enum_values": null
        },
        {
          "id": "uuid",
          "name": "解决说明",
          "description": "关闭原因",
          "kind": "value",
          "datatype": "text",
          "target_object_type_id": null,
          "target_object_type_name": null,
          "is_array": false,
          "is_required": false,
          "default_value": null,
          "enum_values": null
        }
      ],
      "effects": {
        "objects": [
          {
            "object_type_id": "uuid",
            "object_type_name": "工单",
            "rule_kinds": ["modify_object"]
          }
        ],
        "relations": []
      },
      "criteria": [
        {
          "condition_id": "uuid",
          "text": "工单.状态 是 处理中",
          "failure_message": "只有处理中的工单才能关闭"
        }
      ]
    }
  ]
}
```

字段含义：

| 字段 | 内容 |
|---|---|
| `id` | 行动类型 ID，后续调用 `get_action_detail` / `execute_action` 使用 |
| `name` / `description` / `status` | 行动基本信息与发布状态 |
| `parameters` | 参数 ID、名称、说明、值/对象类型、目标对象类型、是否必填、是否数组、默认值、枚举值 |
| `effects.objects` | 被对象规则作用的对象类型及规则种类 |
| `effects.relations` | 被链接规则作用的关系及规则种类 |
| `criteria` | 条件 ID、代码渲染的一行条件文本、失败提示 |

不放进摘要的内容：字段赋值明细、完整条件操作数、规则内部 ID、时间戳。这些由 `get_action_detail` 返回。

### 4.4 `get_action_detail`

入参 `graph_id`、`action_type_id`，返回完整、可执行的行动定义：

- 参数全量字段；
- 每条规则及其赋值，包含目标属性名称、值来源、来源参数/属性、静态值；
- 完整条件树，包含左右操作数与失败提示；
- 行动定义快照版本所需的全部字段。

工具返回的是结构化 JSON，LLM 不自行推断缺失的赋值或条件。

## 5. `execute_action` 工具

### 5.1 模型可见入参

```json
{
  "graph_id": "uuid",
  "action_type_id": "uuid",
  "parameters": {
    "<parameter_id>": {"object_type_id": "uuid", "primary_key": "T-1001"},
    "<parameter_id>": "已更换硒鼓"
  }
}
```

- `parameters` 以参数 ID 为键，避免同名参数歧义。
- 对象参数传 `{object_type_id, primary_key}`；值参数传标量。
- 模型不能传 `execution_id`、`idempotency_key`、`owner_id`、`expected`，这些由工具生成。

### 5.2 工具内部流程

```text
1. 读取并校验行动：归属、在图内、已发布、定义完整
2. 解析参数：默认值、必填、类型、枚举、对象存在性
3. 执行提交校验：条件树不通过则直接返回 CRITERIA_FAILED
4. 按规则生成 edit set 草稿
5. 调数据服务 preview
6. preview 有冲突 → 返回 PREVIEW_CONFLICT，不进入确认
7. interrupt(action_confirmation)，等待用户确认
8. 用户确认后重新 preview，并比对 preview_hash
9. 未变化 → apply；变化 → 返回 PREVIEW_STALE，要求重新确认
10. apply 成功 → effective readback
11. 返回 APPLIED / CANCELLED / EDIT_CONFLICT / UNCERTAIN
```

工具重新执行时，`execution_id` 和 `idempotency_key` 必须保持不变。二者由会话 ID 与 LangChain 注入的 `tool_call_id` 确定性生成，不依赖模型输入，也不在 interrupt 前后重新随机。

### 5.3 确认载荷

确认卡片使用 `kind = "action_confirmation"` 的结构化载荷，不再让模型用 `ask_user` 拼接确认信息。

```json
{
  "kind": "action_confirmation",
  "title": "执行「关闭工单」？",
  "summary": "将修改 1 个对象",
  "action": {"id": "uuid", "name": "关闭工单", "description": "..."},
  "preview_hash": "sha256...",
  "execution_id": "uuid",
  "object_changes": [
    {
      "kind": "OBJECT_MODIFY",
      "object_type_id": "uuid",
      "object_type_name": "工单",
      "primary_key": "T-1001",
      "title": "打印机故障",
      "fields": [
        {"property_id": "uuid", "name": "状态", "before": "处理中", "after": "已关闭"},
        {"property_id": "uuid", "name": "解决说明", "before": null, "after": "已更换硒鼓"}
      ]
    }
  ],
  "relation_changes": [],
  "warnings": [],
  "options": [
    {"label": "确认执行", "answer": {"decision": "approve"}},
    {"label": "取消", "answer": {"decision": "reject"}}
  ]
}
```

确认载荷只包含用户做决定所需的信息，不包含完整规则树或内部 edit set。

`PREVIEW_HASH` 覆盖行动定义哈希、解析后的参数、对象/关系变更和 before 版本。用户确认的是这份预览；预览变化后不能静默沿用旧确认。

## 6. 执行服务分层

新增 `backend/app/services/action/execution/`，与动作定义层分开：

| 模块 | 职责 |
|---|---|
| `context.py` | 读取行动、图、归属、对象/关系名称；校验行动在图内且已发布；生成行动快照与哈希 |
| `parameters.py` | 参数默认值、必填、类型、枚举、对象引用解析；v1 拒绝数组 |
| `criteria.py` | 运行时提交校验求值，返回失败条件与失败提示 |
| `planner.py` | 五种规则 → `EditSetDraft`；主键生成；重复影响检测 |
| `preview.py` | 生成 `EditSetRequest`、调用 preview、构造确认载荷、计算 preview hash |
| `executor.py` | interrupt 确认、apply、幂等重试、状态与错误封装 |
| `readback.py` | 把 apply 结果与 preview 转成对象/字段/关系回读 |
| `errors.py` | 结构化错误码与用户可读消息 |

每个模块只做一件事，单文件控制在约 300 行以内。`executor.py` 不负责规则装配，`planner.py` 不负责 HTTP。

## 7. 参数与提交校验

### 7.1 参数

- 未知参数 ID → `UNKNOWN_PARAMETER`。
- 必填缺失 → `MISSING_PARAMETER`，返回参数 ID、名称、类型，供 Agent 追问。
- 默认值只在该参数未出现时生效；显式 `null` 视为清空，不套默认值。
- 值参数按 `datatype`、`enum_values`、`is_array` 校验，复用现有 `value_types`。
- 对象参数必须属于参数声明的对象类型，且 effective 可见；不存在 → `OBJECT_NOT_FOUND`。
- v1 所有参与执行的参数都按单值处理；`is_array=true` 直接返回 `UNSUPPORTED_ARRAY`。

### 7.2 提交校验

条件树在 Python 侧求值，不交给 LLM：

- `parameter`：取解析后的值参数。
- `object_property`：读取对象参数当前 effective 属性值。
- `static`：使用定义里的静态值。
- 运算符沿用定义层：`is`、`is not`、`is null`、`is not null`、`<`、`<=`、`>`、`>=`。
- 日期/时间/数值比较前统一解析；解析失败按 `INVALID_CRITERIA_VALUE` 返回。
- 任一条件不通过 → `CRITERIA_FAILED`，携带条件 ID、`failure_message`、实际值与期望值。
- 校验不通过时不会 preview，也不会弹确认卡片。

## 8. 规则到 edit set

数据服务只理解 edit set，不理解 Action 规则。Python 负责把规则翻译成对象/关系变更。

| 规则 | edit set | 说明 |
|---|---|---|
| `create_object` | `objectCreates` | 赋值转 `values`；主键缺失时按规则生成 |
| `modify_object` | `objectUpdates` | 赋值转 `set`；解析为 null 的赋值转 `clear` |
| `delete_object` | `objectDeletes` | 对象参数定位目标 |
| `create_link` | `relationCreates` | 仅 `many_to_many`；两端对象必须存在 |
| `delete_link` | `relationDeletes` | 仅 `many_to_many`；两端对象必须存在 |

### 8.1 主键

- 行动显式给主键赋值 → 使用该值。
- 未给主键且主键类型为 `text` → 生成 UUID 字符串；`integer` / `decimal` → 生成同类型的数值。
- 主键生成按会话与 `tool_call_id` 确定，同一次执行重试沿用同一个值。
- 未给主键且主键类型为 `boolean` / `date` / `timestamp` / `enum` → `PRIMARY_KEY_REQUIRED`，要求用户或模型补主键。
- 主键一旦创建不可修改；`modify_object` 的赋值命中主键直接拒绝。

### 8.2 赋值

- `parameter`：取解析后的值参数。
- `object_property`：从对象参数的 effective 属性取值。
- `current_time`：执行时按目标属性类型取当前日期 / 时间。
- `auto_generate`：新建对象时生成主键，仅用于 `create_object` 的主键赋值。
- `static`：使用静态值。
- 目标属性 `is_array=true` → `UNSUPPORTED_ARRAY`。
- 同一行动内同一目标对象出现两条冲突变更 → `DUPLICATE_EFFECT`。
- 同一关系端点同时创建和删除 → `DUPLICATE_EFFECT`。

### 8.3 外键关系

外键关系不生成 `relationCreates` / `relationDeletes`，而是通过 `modify_object` 修改外键属性；关系查询从 effective 外键派生。与编辑覆盖层设计一致。

## 9. preview、确认与 apply

### 9.1 Edit set 请求

```json
{
  "execution_id": "uuid",
  "owner_id": "uuid",
  "conversation_id": "uuid",
  "graph_id": "uuid",
  "action_type_id": "uuid",
  "action_definition_hash": "sha256",
  "action_snapshot": {},
  "parameters": {},
  "idempotency_key": "conversation_id:tool_call_id",
  "expected": {
    "objects": [{"object_type_id": "uuid", "primary_key": "T-1001", "version": 3}],
    "relations": []
  },
  "object_creates": [],
  "object_updates": [],
  "object_deletes": [],
  "relation_creates": [],
  "relation_deletes": []
}
```

Python 客户端调用：

- `POST /internal/v1/ontology/edit-sets/preview`
- `POST /internal/v1/ontology/edit-sets/apply`

字段按现有客户端统一转 camelCase。

### 9.2 expected

- 对象修改/删除：preview 返回的 `before.version`。
- 关系创建/删除：preview 返回的 `before_version`；不存在时为 0。
- 对象创建：不设 expected，由数据服务检查 effective 对象是否已存在。

apply 必须重新校验 expected，不能信任 preview。

### 9.3 确认

- 使用 `interrupt`，不使用 `ask_user`。
- 用户点“确认执行”返回 `{"decision": "approve"}`；点“取消”返回 `{"decision": "reject"}`。
- 不允许自由文本确认。
- 取消后不写任何覆盖行，不创建 execution。
- 确认后重新 preview 并比对 `preview_hash`；不一致返回 `PREVIEW_STALE`，让用户重新确认。

### 9.4 apply 与幂等

- `execution_id`、`idempotency_key` 在工具调用内确定性生成。
- 网络超时后使用同一幂等键重试 apply；数据服务返回已有 execution 的结果。
- apply 失败不自动降级为部分写入；数据服务单事务回滚。
- 版本冲突 → `EDIT_CONFLICT`，携带冲突对象/关系，让 Agent 解释并让用户决定是否重试。

### 9.5 readback

apply 成功后重新读取 effective state，返回：

```json
{
  "status": "APPLIED",
  "execution_id": "uuid",
  "action": {"id": "uuid", "name": "关闭工单"},
  "objects": [
    {
      "object_type_id": "uuid",
      "object_type_name": "工单",
      "primary_key": "T-1001",
      "title": "打印机故障",
      "exists": true,
      "version": 4,
      "properties": [{"property_id": "uuid", "name": "状态", "value": "已关闭"}]
    }
  ],
  "relations": [],
  "changes": [
    {"kind": "VALUE_SET", "object": "工单/T-1001", "property": "状态", "before": "处理中", "after": "已关闭"}
  ]
}
```

Agent 最终回答必须引用 readback 的实际值，不能复述 preview 的预期值。

## 10. 回写位置与审计

### 10.1 回写

行动变更只写数据服务编辑覆盖层：

- 对象状态、标题、字段覆盖：`data.ontology_object_edit`、`data.ontology_object_value_edit`。
- 多对多关系覆盖：`data.ontology_relation_edit`。
- 基础索引表保持只读。
- 源数据库、数据集、映射草稿都不因行动执行而改变。
- 图卡、图向量、发布产物不因行动执行而更新。

### 10.2 审计

- `data.ontology_edit_execution`：执行 ID、owner、conversation、graph、action、定义快照、定义哈希、参数、幂等键、基础版本、结果、状态、时间。
- `data.ontology_edit_change`：逐条对象/字段/关系变更前后值，追加式写入。
- 会话消息中的工具调用与确认卡片保留交互级记录；数据服务审计是事实源。
- v1 不提供用户可见的执行历史页面；`execution_id` 在 readback 中返回，供后续查询与排障。

## 11. 前端

### 11.1 工作台改名

- 路由：`/business`、`/business/:conversationId`；`/query` 兼容重定向。
- 文案：查询工作台 → 业务工作台；只读问答 → 业务问答与行动。
- 左侧会话、消息流、证据展开、中断恢复继续复用现有组件。

### 11.2 确认卡片

新增结构化确认渲染，使用 `action_confirmation` 块：

- 标题：行动名称 + “确认执行”。
- 摘要：修改/新建/删除的对象数，新增/删除的关系数。
- 对象变更：对象类型、标题、主键、变更类型、字段前后值表。
- 关系变更：关系名称、两端对象、创建/删除。
- 警告：preview 返回的非阻断提示。
- 按钮：确认执行 / 取消。
- 提交中禁用按钮并显示进度；失败后在消息流中展示结构化错误。

类型与组件：

- `shared/types/agent.ts`：增加 `ActionConfirmationPreview` 与 `InterruptBlock.confirmation`。
- `shared/chat/components/InterruptCard.tsx`：有 `confirmation` 时渲染行动确认布局。
- `shared/chat/lib/messageBlocks.ts`、`useAgentRun.ts`：透传确认载荷。
- `features/business/lib/actionCopy.ts`：变更类型、字段、按钮文案。

### 11.3 回读展示

v1 先用工具结果块展示 readback JSON，并在助手正文中给出业务语言总结；不单独做执行历史页。

## 12. 错误与状态

| 状态/错误 | 含义 | Agent 行为 |
|---|---|---|
| `MISSING_PARAMETER` | 必填参数缺失 | 用 `ask_user` 只问最阻塞的一个 |
| `INVALID_PARAMETER` | 类型、枚举、对象类型不匹配 | 说明哪个参数不合法，请求重填 |
| `UNKNOWN_PARAMETER` | 参数 ID 不在行动定义中 | 重新读取行动详情，不猜测 |
| `UNSUPPORTED_ARRAY` | v1 不支持数组执行 | 明确说明限制 |
| `OBJECT_NOT_FOUND` | 对象参数不存在或不可见 | 重新查询对象或请用户确认 |
| `CRITERIA_FAILED` | 提交校验不通过 | 展示失败提示与实际值，不执行 |
| `PREVIEW_CONFLICT` | preview 不可应用 | 展示冲突，不弹确认 |
| `PREVIEW_STALE` | 确认后预览变化 | 重新展示预览并再次确认 |
| `EDIT_CONFLICT` | apply 版本冲突 | 说明冲突对象，让用户决定是否重试 |
| `APPLY_FAILED` | 数据服务明确的执行失败 | 说明失败原因，不声称成功 |
| `UNCERTAIN` | 网络超时且无法确认结果 | 用同一幂等键重试，或让用户稍后核对 |
| `CANCELLED` | 用户取消 | 不写任何数据 |

所有错误都以结构化对象返回给 Agent，不把异常直接抛给用户。

## 13. 测试

### 13.1 后端

- Agent 改名与迁移：`test_business_agent_definition.py`、迁移升级/降级测试。
- 图发布：行动进入图卡、草稿阻断、行动涉及元素必须在图内；`test_graph_publication_actions.py`。
- 图详情：`actions` 字段逐项契约；`test_graph_detail_actions.py`。
- 参数：默认值、必填、类型、枚举、对象引用、数组拒绝。
- 提交校验：各运算符、失败提示、对象属性读取。
- 规则装配：五种规则、主键生成、重复影响、外键限制。
- preview：确认载荷、preview hash、expected 版本。
- apply：幂等重试、版本冲突、取消、readback。
- interrupt：`action_confirmation` 解析与前端事件形状。
- 客户端：edit-set preview/apply 的 camelCase 请求与 409 错误映射。

### 13.2 前端

- 确认卡片：对象变更、字段前后值、关系变更、取消、提交中禁用。
- 中断透传：`confirmation` 落库并回放。
- 路由兼容：`/query` 重定向到 `/business`。
- 业务工作台文案与证据展示。

### 13.3 端到端

```text
提问 → 召回含行动的图 → 读取行动摘要 → 读取行动详情 → 查询对象参数
→ execute_action → preview 卡片 → 确认 → apply → readback → 回答
```

至少覆盖：修改对象、创建对象、删除对象、创建多对多关系、删除多对多关系、提交校验失败、用户取消、版本冲突。

## 14. 实施顺序

1. Agent 与 `ConversationKind` 改名，迁移与兼容路由。
2. 行动展示层：图卡行动区段、发布校验、`get_graph_detail.actions`、`get_action_detail`。
3. 执行服务：参数、提交校验、规则装配。
4. 数据服务客户端：preview/apply 与错误映射。
5. `execute_action` 工具：interrupt 确认、preview hash、幂等 apply、readback。
6. 前端确认卡片与业务工作台改名。
7. 测试、文档与端到端验收。

## 15. P4 不做

- 独立 Action Agent、独立执行页面、独立 `actionTask` 表。
- `search_graph_actions` 与独立 action 向量索引。
- 动作执行历史页面。
- 数组属性修改。
- 物化 effective 快照。
- 回写源数据库或重建基础索引。
