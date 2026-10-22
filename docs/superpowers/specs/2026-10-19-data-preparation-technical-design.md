# 数据整理（可视化处理）技术设计

- 日期：2026-10-19
- 状态：技术设计已确认并实施完成（2026-10-22 端到端验收通过；功能设计 8/8、技术实现细节 8/8，来源追踪已降级）
- 范围：P2「数据整理」的可视化处理、预览、构建、调度与来源元数据
- 输入：P1 同步生成的数据集版本（只读）
- 输出：新的派生数据集版本（不覆盖原始数据集）
- 上游依据：`2026-10-17-data-integration-and-agent-loop-design.md`、`2026-10-17-data-connection-sync-technical-design.md`
- 不含：对象映射（P3）、动作执行与 Agent 闭环（P4）、完整血缘图与影响分析（后置）

## 1. 目标与边界

P2 要把「同步进来的原始数据集」整理成「可直接用于对象映射的派生数据集」：

`数据集版本 → 处理流程 → 预览 → 构建 → 派生数据集版本`

已确认边界：

- 处理流程只读取 P1 生成的数据集版本，不直接访问外部数据库。
- 输出新的派生数据集版本，不覆盖原始数据集。
- 数据集版本不可变；失败不改变当前版本。
- 每次构建都有运行记录；无变化不创建构建，记录在调度运行表并标明跳过原因。
- 沿用 Agent 对话 + 三栏工作台骨架，不另做一套菜单表单。
- 画布和右侧面板操作通过结构化事件回流到对话，复用 `ConversationEvent`。

## 2. 交互模型与设计议题

### 2.1 已确认交互模型

- 交互采用「对话 + 画布」：对话负责引导、解释、确认；画布负责可视化流程和直接操作。
- 一个对话对应一条处理流程（一次 Pipeline Builder 会话）；新建流程时同时创建对话，打开已有流程时恢复原对话。
- 用户可以从对话开始，也可以直接在画布上拖节点；两边共享同一份流程状态。
- 画布操作以结构化事件写入对话；Agent 读取流程状态和事件历史，判断缺失步骤并给出下一步引导。
- 复用 P1 的三栏工作台骨架、`ConversationEvent`、SSE 和 Agent 运行时，不另做一套菜单表单。

### 2.2 议题顺序

一次只推进一个议题：

| # | 议题 | 要回答的问题 |
|---|---|---|
| 1 | 对话 + 画布的交互骨架与资源绑定 | 一个对话如何绑定一条流程？三栏怎么排？对话和画布谁主谁辅？新建/打开/删除怎么走？ |
| 2 | 处理流程的数据模型 | 流程、草稿、节点、连线、运行、派生版本怎么存？对话事件怎么关联？ |
| 3 | Agent 引导与流程状态机 | Agent 怎么知道缺什么？下一步问什么？用户手动改画布后怎么同步？哪些操作要确认？ |
| 4 | 画布节点、连线与配置 | 能拖哪些节点？怎么连线？配置面板怎么组织？表达式和转换怎么分层？ |
| 5 | 预览与字段级错误 | 什么时候预览？看多少行？错误怎么定位到节点和字段？预览和构建怎么保证一致？ |
| 6 | 执行、构建与派生版本 | 用什么引擎跑？保存/部署/构建怎么区分？成功/失败/无变化怎么记？数据期望怎么校验？ |
| 7 | 定时调度与无变化跳过 | 什么触发？暂停/恢复？运行记录？和 P1 同步怎么衔接？ |
| 8 | 来源追踪与影响分析 | 展示到什么粒度？怎么和 P1/P3 衔接？ |

议题 8 已按降级方案确认：P2 只做来源元数据和单跳上游列表，完整血缘与影响分析后置，见 §11。

### 2.3 与旧议题的调整

- 新增议题 1「交互骨架」和议题 3「Agent 状态机」，因为对话 + 画布是核心交互。
- 原「MVP 处理算子范围」并入议题 4，不再单独作为第一议题；已有结论作为议题 4 的输入。
- 原「前端复用」拆入议题 1 和议题 4，不再单独设议题。
- 执行引擎并入议题 6，避免把产品语义和技术选型拆散。
- 数据模型提前到 Agent 之前，Agent 才能基于稳定的流程状态做引导。

## 3. 已有结论：MVP 处理算子范围（已并入议题 4）

状态：已确认（2026-10-20）；议题 4 已按画布节点模型复核，最终节点目录见 §7.2。

本节结论基于 Palantir 官方文档核对，作为画布节点目录的输入，不再作为独立议题。

### 3.1 结论

MVP 候选节点如下，外加「数据源」和「输出」两个图节点：

| 类别 | 算子 | MVP 行为 |
|---|---|---|
| 行级 | 筛选 | 按列与常量比较过滤行，支持 AND/OR 组合 |
| 列级 | 选列 | 保留或删除字段，至少保留一个输出字段 |
| 列级 | 改名 | 修改输出字段名，要求非空且唯一 |
| 列级 | 列计算 | 用表达式完成类型转换（Cast）、条件分支、比较、与/或/非、空值判断 |
| 多表 | 合并 | 多路按字段名合并，要求字段结构一致 |
| 多表 | 关联 | 两路等值关联，支持左、右、内、全外关联 |
| 行级 | 去重 | 按字段子集或整行去重 |
| 行级 | 汇总 | 按分组字段做求和、计数等聚合（待确认） |

排序、窗口、透视、派生字段、用户自定义函数和自定义 SQL/脚本不进入 MVP。

### 3.2 算子边界

**筛选**

- 支持 `=`、`!=`、`>`、`>=`、`<`、`<=`、`IS NULL`、`IS NOT NULL`、`IN`。
- 支持 `AND` / `OR` 组合，可用括号表达分组。
- 只比较「字段与常量」；不做字段间比较、函数调用、子查询和正则。
- 条件只有为真才保留；空值结果视为不满足（Palantir Filter 将 null 视为 false）。

**选列**

- 输出输入字段的子集；至少保留一个字段。
- 只做列裁剪，不生成计算字段。

**改名**

- 只改输出字段名，不改数据。
- 输出字段名必须非空且在当前节点内唯一；只做大小写变化也视为改名。
- 改名不改变字段类型和下游引用。

**列计算（表达式）**

- 类型转换（Cast）是表达式，不是独立转换节点；通过「列计算」节点落到输出字段。
- 第一版支持 `string`、`int`、`bigint`、`decimal`、`double`、`boolean`、`date`、`timestamp`。
- 同时支持条件分支（Case）、比较、与/或/非、为空/不为空等行级表达式。
- 显式转换，不做隐式推断；源类型与目标类型不兼容时在节点上直接报错。
- 转换失败按字段级错误处理，具体定位与展示在议题 5 确定。

**合并（Union by name）**

- 支持两路及以上输入，保留全部行（含重复行）。
- 输入字段结构必须一致，按字段名对齐；缺失字段在节点上报错并列出缺失项；不做按位置对齐，不做类型自动提升。
- 输出字段名按字段名对齐；需要不同名称时在下游「改名」处理。
- 去重不是合并的一部分，需要时在下游单独加「去重」节点。

- 只支持两路输入、等值关联键，MVP 支持左、右、内、全外关联。
- 关联键可多列；不支持非等值条件、反连接、半连接和交叉连接。
- 一个关联节点只做一次两表关联；多表关联拆成多个节点。
- 不做关联基数校验；多对多会产生行放大，预览中必须能看出来（具体展示在议题 5）。
- 两侧同名字段在输出中自动加来源前缀；前缀规则在数据模型议题确定。

**去重**

- 按指定字段子集或整行判定重复；默认保留每组重复中的一行。
- 支持 Batch；是否支持多字段组合去重由画布节点配置决定。

**汇总（待确认）**

- 支持按字段分组，并配置求和、计数、最小值、最大值、平均值等基础聚合。
- 汇总会改变数据粒度；是否进入 MVP 需在议题 4 确认。

**数据源与输出**

- 数据源节点引用一个 P1 数据集；预览和构建时解析当前已发布版本，构建时冻结精确版本。同一流程可引用多个数据集。
- 输出节点是流程的发布出口；一个流程可以有多个输出节点，每个输出生成一个派生数据集版本。
- 数据源与输出不承载计算逻辑。

### 3.3 为什么这样切

- 这组节点覆盖了从原始数据到 P3 映射的最小闭环：裁剪字段、过滤无效行、统一命名与类型、补充关联信息、合并同构数据、去除重复数据。
- 除「关联」外，其余算子都是单表行级或列级操作，预览成本低、结果确定，适合边改边看。
- 「汇总」是 Palantir 的一等转换；纳入与否取决于是否需要在 P2 直接产出指标型数据，需在议题 4 确认。
- 排序、窗口、透视、用户自定义函数和自定义 SQL 要么依赖更复杂的执行与权限模型，要么不是对象映射的前置条件，后续可以按真实需求追加，不会推翻当前图模型。

### 3.4 对后续议题的约束

- 数据模型必须能表达「多输入 DAG」：数据源、处理节点、输出节点，以及节点间的字段级 schema。
- 预览必须按节点执行，并能定位到「节点 + 字段」；关联后的行放大要能在预览中观察到。
- 构建必须整条流程原子成功或原子失败；任一节点失败不发布新版本。
- 前端提供算子面板和每个算子的配置表单，不提供自由 SQL 编辑器。
- P3 只消费输出节点生成的派生版本，不读取中间节点。

### 3.5 暂不纳入与触发条件

| 能力 | 处理 | 重新评估的触发条件 |
|---|---|---|
| 排序 | 后置 | 预览或输出需要稳定顺序时 |
| 窗口函数 | 后置 | 需要排名、累计、同比等计算时 |
| 透视 | 后置 | 出现明确的宽表转换需求 |
| 复杂表达式 / 自定义函数 | 后置 | 内置列计算表达式无法覆盖真实计算时 |
| 自定义 SQL / 脚本 | 后置 | 算子组合无法覆盖真实场景，且完成安全与沙箱设计后 |

## 4. 议题一：对话 + 画布的交互骨架与资源绑定

状态：已确认（2026-10-20）。

### 4.1 布局

- 沿用在建模工作台已经验证过的三栏结构：**左侧流程列表、中间对话、右侧画布**。
- 对话是操作入口和解释区，画布是流程的可视化结果；两者不是主次关系，而是同一份流程状态的两种视图。
- 节点配置和结果预览放在画布内的抽屉或底部面板，不占用对话栏。
- 数据连接工作台的连接列表、对话面板、事件卡片和画布容器可以直接复用，只替换业务组件。

### 4.2 资源绑定

- 一个对话严格对应一条处理流程：`Conversation.kind = data_pipeline`，`Conversation.resource_id = pipeline_id`。
- 新建流程时同时创建草稿流程和对话；打开已有流程时恢复原对话；不创建“无流程的闲聊对话”。
- 删除流程时，流程和对话一起归档；对话保留只读历史，不能继续追加新的 Agent 回合。
- 复制流程时创建新的流程和新的对话，不复制原对话的消息与事件。

### 4.3 对话与画布同步

- Agent 可以通过工具直接修改草稿，不必每一步都等待用户确认；修改结果立即反映到画布。
- 用户在画布上的增删节点、连线、配置修改、移动和命名，都以结构化事件写入当前对话。
- 所有操作持久化并记录：操作者类型（Agent / 用户）、操作前后内容、`operation_id`、`turn_id` 和时间。
- 对话上下文由当前流程定义 + 结构化操作事件 + 用户消息组成；刷新页面后可以从数据库恢复。

### 4.4 保存、构建与撤销

- 草稿自动保存，用户不需要手动“保存”。
- 构建必须由用户显式触发；构建时冻结当前草稿，生成不可变派生版本，失败不改变当前版本。
- 支持“撤销本轮 Agent 改动”和“回退到上次构建”。
- 删除节点、清空流程、覆盖已有输出等破坏性操作必须二次确认。
- 流程状态至少区分：草稿、可构建、构建中、成功、失败；具体状态机见 §6。

### 4.5 数据分层

```text
Agent / 画布
   ↓
pipeline_draft      当前草稿，可修改
pipeline_edit_op    操作历史，只追加
   ↓ 构建
pipeline_version    不可变版本
```

草稿是工作副本，操作历史用于撤销、审计和对话回放，版本是构建和下游消费的唯一依据。三者的具体表结构见 §5。

### 4.6 技术实现（2026-10-20 补充）

状态：已确认。

- 采用 Python 统一入口（BFF，后端聚合层）+ Java 内部接口：浏览器只调用 Python 的 `/pipelines`，Python 负责会话与事件，Java 负责流程、草稿、版本、构建和派生数据集。
- 公共创建接口要求 `X-Operation-Id`；Python 以所有者、操作类型和 `operation_id` 为输入，通过固定命名空间派生稳定的 `pipeline_id` 和 `conversation_id`。重复请求复用同一组 ID，不新建资源。
- 创建顺序：Python 先写入 `Conversation(kind=data_pipeline, resource_id=pipeline_id)`，再调用 Java 按 `pipeline_id` 和 `conversation_id` 幂等创建流程。Java 发现同所有者且同绑定的记录时返回已有流程；绑定被其他所有者占用时返回 409。
- 跨服务事务采用“幂等创建 + 补偿/对账”，不引入分布式事务：明确的 4xx 失败回滚 Python 会话；超时或 5xx 不立即删除会话，客户端用同一 `operation_id` 重试，由 Python 和 Java 的幂等键收敛。
- 删除流程时先由 Java 归档流程，再由 Python 归档会话，两个操作都幂等；`conversation` 增加可空的 `archived_at`，归档会话保留消息和事件但只读，默认列表不返回。P1 的既有删除行为不变。
- 复制流程生成新的 `pipeline_id` 和 `conversation_id`，复制当前草稿和输出声明，不复制消息、事件、构建历史和派生版本历史。
- 前端新增 `/pipelines` 和 `/pipelines/:pipelineId`。左侧流程列表以 Java 的流程列表为准；打开流程时用响应中的 `conversationId` 加载消息和事件流，避免用会话反向决定流程。
- 抽取数据连接工作台已经出现的三栏骨架、可拖拽分栏和会话面板外壳，供数据连接与流程工作台共用；事件卡片、空状态和画布仍由各自 feature 提供渲染器，不复制整套页面。
- 所有流程变更由 Java 在业务事务内同时写流程数据和发件箱（outbox）事件，Python 消费后按 `event_id` 幂等写入 `conversation_event`；浏览器和 Agent 读取同一条事件流。创建响应同时返回事件 ID，供 Python 立即落库并避免与发件箱重复。
- 权限以 Python 当前用户为入口，Java 内部接口仍校验 `ownerId`；创建前校验会话所有者和流程所有者一致，读取归档会话只允许只读操作。
- 创建和归档必须幂等；草稿编辑的并发控制使用后续议题确定的 `base_revision` 乐观锁。

## 5. 议题二：处理流程的数据模型

状态：已确认（2026-10-20）。

### 5.1 结论

- P2 领域归 Java 数据服务：流程、草稿、输出、版本、构建和派生数据集由 Java 持有；Python 只持有对话、事件和 Agent。
- P1 的 `data.dataset` / `data.dataset_version` 保持同步数据集语义，不修改、不复用为 pipeline 输出数据集。
- P2 新增 `pipeline_dataset` / `pipeline_dataset_version`，表示流程自己的派生数据集和不可变版本。
- 草稿和版本使用整图 JSONB；节点和连线不单独建表，节点/边 ID 在 JSON 内保持稳定。
- 输出采用「通用输出 + 类型扩展」：`pipeline_output` 只放所有输出类型共有字段；dataset 输出由 `pipeline_dataset` 承载类型配置和数据集身份。未来增加文件、虚拟表、Ontology 输出时新增对应类型表，不改通用表。
- 一次构建由 `pipeline_build` 表示；每个输出节点的执行结果由 `pipeline_build_output` 表示；dataset 输出真正物化出的数据版本由 `pipeline_dataset_version` 表示。
- 定时调度由 `pipeline_schedule` + `pipeline_schedule_run` 表示；调度只运行已发布版本，无变化不创建构建。
- MVP 只做单分支，`pipeline` 即 main 分支；未来引入分支时再增加 `pipeline_branch`。
- 预览是临时计算，不生成版本、不写输出；按 `draft_revision + node_id` 缓存。
- P3 通过只读的统一数据集视图或查询服务消费 sync / pipeline 两类数据集，不直接共享物理表。

### 5.2 ER 图

```mermaid
erDiagram
    PIPELINE ||--|| PIPELINE_DRAFT : has
    PIPELINE ||--o{ PIPELINE_EDIT_OP : logs
    PIPELINE ||--o{ PIPELINE_OUTPUT : declares
    PIPELINE_OUTPUT ||--o| PIPELINE_DATASET : dataset_output
    PIPELINE ||--o{ PIPELINE_DATASET : owns
    PIPELINE ||--o{ PIPELINE_VERSION : freezes
    PIPELINE ||--o| PIPELINE_SCHEDULE : schedules
    PIPELINE_SCHEDULE ||--o{ PIPELINE_SCHEDULE_RUN : triggers
    PIPELINE_SCHEDULE_RUN ||--o| PIPELINE_BUILD : dispatches
    PIPELINE_VERSION ||--o{ PIPELINE_BUILD : executes
    PIPELINE_BUILD ||--o{ PIPELINE_BUILD_OUTPUT : produces
    PIPELINE_BUILD_OUTPUT ||--o| PIPELINE_DATASET_VERSION : yields
    PIPELINE_DATASET ||--o{ PIPELINE_DATASET_VERSION : has
    CONVERSATION ||--o{ CONVERSATION_EVENT : emits
    CONVERSATION ||--|| PIPELINE : binds

    PIPELINE {
        uuid id PK
        uuid owner_id
        uuid conversation_id UK
        text name
        text status
        uuid current_version_id FK
        timestamptz archived_at
        timestamptz created_at
        timestamptz updated_at
    }
    PIPELINE_DRAFT {
        uuid pipeline_id PK
        jsonb definition
        bigint revision
        text updated_by_type
        uuid updated_by_id
        timestamptz updated_at
    }
    PIPELINE_EDIT_OP {
        uuid id PK
        uuid pipeline_id FK
        bigint revision
        int seq
        text op_type
        text target_type
        uuid target_id
        jsonb before
        jsonb after
        text actor_type
        uuid actor_id
        uuid operation_id
        uuid turn_id
        timestamptz created_at
    }
    PIPELINE_OUTPUT {
        uuid id PK
        uuid pipeline_id FK
        uuid node_id
        text output_type
        text name
        text status
        timestamptz archived_at
        timestamptz created_at
        timestamptz updated_at
    }
    PIPELINE_DATASET {
        uuid id PK
        uuid output_id UK
        uuid pipeline_id FK
        text name
        text write_mode
        jsonb primary_key_columns
        jsonb expectations
        uuid current_version_id FK
        timestamptz archived_at
        timestamptz created_at
        timestamptz updated_at
    }
    PIPELINE_VERSION {
        uuid id PK
        uuid pipeline_id FK
        int version_number
        jsonb definition
        jsonb outputs
        text definition_hash
        jsonb input_refs
        bigint source_draft_revision
        uuid created_by
        timestamptz created_at
    }
    PIPELINE_BUILD {
        uuid id PK
        uuid pipeline_id FK
        uuid pipeline_version_id FK
        text status
        text stage
        text trigger
        uuid operation_id
        jsonb resolved_inputs
        text logic_hash
        text log_uri
        timestamptz started_at
        timestamptz heartbeat_at
        timestamptz cancel_requested_at
        timestamptz finished_at
        text error_code
        text error_message
        jsonb stats
        timestamptz created_at
    }
    PIPELINE_SCHEDULE {
        uuid id PK
        uuid pipeline_id FK UK
        text name
        text status
        text trigger_type
        text cron_expression
        text timezone
        timestamptz next_run_at
        timestamptz last_run_at
        timestamptz paused_at
        uuid paused_by
        uuid created_by
        timestamptz created_at
        timestamptz updated_at
    }
    PIPELINE_SCHEDULE_RUN {
        uuid id PK
        uuid schedule_id FK
        uuid pipeline_id FK
        text status
        text trigger_type
        timestamptz scheduled_for
        timestamptz started_at
        timestamptz finished_at
        uuid build_id FK
        text logic_hash
        jsonb resolved_inputs
        text reason_code
        text message
        timestamptz created_at
    }
    PIPELINE_BUILD_OUTPUT {
        uuid id PK
        uuid build_id FK
        uuid output_id FK
        text output_type
        text status
        text stage
        text engine_job_id
        text log_uri
        jsonb result
        text error_code
        text error_message
        jsonb stats
        timestamptz started_at
        timestamptz finished_at
        timestamptz created_at
    }
    PIPELINE_DATASET_VERSION {
        uuid id PK
        uuid dataset_id FK
        uuid build_output_id FK
        int version_number
        text iceberg_catalog
        text iceberg_namespace
        text iceberg_table
        bigint iceberg_snapshot_id
        jsonb schema_json
        bigint row_count
        boolean row_count_estimated
        bigint byte_size
        int column_count
        text storage_path
        timestamptz committed_at
        timestamptz published_at
    }
    CONVERSATION {
        uuid id PK
        uuid owner_id
        text kind
        uuid resource_id
        text title
        timestamptz archived_at
        timestamptz created_at
        timestamptz updated_at
    }
    CONVERSATION_EVENT {
        uuid id PK
        uuid conversation_id FK
        uuid event_id
        uuid operation_id
        uuid run_id
        text event_type
        text summary
        jsonb payload
        timestamptz created_at
    }
```

`CONVERSATION → PIPELINE` 是应用层绑定：对话在 `app` schema，流程在 `data` schema，不建跨 schema 外键。

### 5.3 表职责与关键字段

| 表 | 关键字段 | 职责 |
|---|---|---|
| `pipeline` | `id`、`owner_id`、`conversation_id`、`name`、`status`、`current_version_id`、`archived_at`、时间戳 | 流程元数据；MVP 单分支 |
| `pipeline_draft` | `pipeline_id`、`definition`、`revision`、`updated_by_type`、`updated_by_id`、`updated_at` | 当前可编辑草稿；整图 JSONB |
| `pipeline_edit_op` | `pipeline_id`、`revision`、`seq`、`op_type`、`target_type`、`target_id`、`before`、`after`、`actor_type`、`actor_id`、`operation_id`、`turn_id`、`created_at` | 只追加的编辑历史；支持撤销和审计 |
| `pipeline_output` | `pipeline_id`、`node_id`、`output_type`、`name`、`status`、`archived_at`、时间戳 | 通用输出声明，不绑定具体输出类型 |
| `pipeline_dataset` | `output_id`、`pipeline_id`、`name`、`write_mode`、`primary_key_columns`、`expectations`、`current_version_id`、`archived_at`、时间戳 | dataset 输出配置 + pipeline 自己的派生数据集身份 |
| `pipeline_version` | `pipeline_id`、`version_number`、`definition`、`outputs`、`definition_hash`、`input_refs`、`source_draft_revision`、`created_by`、`created_at` | 不可变流程版本；冻结图和输出配置 |
| `pipeline_build` | `pipeline_id`、`pipeline_version_id`、`status`、`stage`、`trigger`、`operation_id`、`resolved_inputs`、`logic_hash`、`log_uri`、`started_at`、`heartbeat_at`、`cancel_requested_at`、`finished_at`、错误、`stats` | 一次构建的整体记录与编排状态 |
| `pipeline_build_output` | `build_id`、`output_id`、`output_type`、`status`、`stage`、`engine_job_id`、`log_uri`、`result`、错误、`stats`、起止时间 | 一次构建中每个输出节点的执行结果与引擎作业信息 |
| `pipeline_dataset_version` | `dataset_id`、`build_output_id`、`version_number`、Iceberg 位置与快照、schema、行数、文件大小、存储路径、提交/发布时间 | dataset 输出物化出的不可变数据版本 |
| `pipeline_schedule` | `pipeline_id`、`name`、`status`、`trigger_type`、`cron_expression`、`timezone`、`next_run_at`、`last_run_at`、`paused_at`、`paused_by`、`created_by`、时间戳 | 一条流程一个定时调度；MVP 只支持时间触发 |
| `pipeline_schedule_run` | `schedule_id`、`pipeline_id`、`status`、`trigger_type`、`scheduled_for`、`build_id`、`logic_hash`、`resolved_inputs`、`reason_code`、`message`、起止时间 | 每次调度触发及跳过/派发记录；无变化时不创建构建 |
| `conversation` | `owner_id`、`kind`、`resource_id`、`title`、`archived_at`、时间戳 | 对话；`kind=data_pipeline` 时 `resource_id=pipeline_id` |
| `conversation_event` | `conversation_id`、`event_id`、`operation_id`、`run_id`、`event_type`、`summary`、`payload`、`created_at` | 对话结构化事件；`run_id` 指向构建 ID |

### 5.4 关键约束

- `pipeline_output`：`(pipeline_id, node_id)` 唯一；一个输出节点对应一条通用输出记录。
- `pipeline_dataset`：`output_id` 唯一，表示一个 dataset 输出对应一个派生数据集；`(pipeline_id, name)` 唯一。
- `pipeline_build_output`：`(build_id, output_id)` 唯一；一次构建里每个输出只有一条执行结果。
- `pipeline_dataset_version`：`(dataset_id, version_number)` 唯一；`build_output_id` 唯一；`(dataset_id, iceberg_table, iceberg_snapshot_id)` 唯一。
- `pipeline_draft`：语义写操作携带 `base_revision`，成功后 `revision` 递增；冲突返回 409，由前端或 Agent 重新读取草稿。坐标移动不参与该并发控制。
- `pipeline_edit_op`：`(pipeline_id, revision, seq)` 唯一；建议增加 `(pipeline_id, operation_id, seq)` 唯一以保证重试幂等。
- `pipeline_version`：不可变；`(pipeline_id, version_number)` 唯一。
- `pipeline_build`：成功不自动重跑；失败不切换 `pipeline.current_version_id`；无变化不创建构建，记录在 `pipeline_schedule_run`。
- `pipeline_build`：同一流程同一时间只允许一个 `queued` 或 `running` 构建；`operation_id` 在同一流程内唯一，重复请求返回已有构建。
- `pipeline_version`：同一 `pipeline_id + definition_hash` 可复用已冻结版本，不重复创建。
- 一次构建的多个输出共享同一个构建 ID 和 `resolved_inputs`；整体成功要求所有输出成功，任一输出失败则整体失败。
- `pipeline_dataset_version.published_at` 只有整体构建成功后才写入；失败构建可能留下临时产物，但不发布、不切换当前版本。
- `pipeline_schedule`：`pipeline_id` 唯一，一条流程最多一个调度；`status=active` 时 `next_run_at` 必填，`status=paused` 时为空。
- `pipeline_schedule_run`：`(schedule_id, scheduled_for)` 唯一；同一调度最多一条 `status=pending` 记录，用部分唯一索引 `(schedule_id) WHERE status='pending'` 保证；`build_id` 唯一，一个调度运行最多派发一个构建。

### 5.5 关键语义

**草稿、操作和版本**

- `pipeline_draft.definition` 是画布和 Agent 的共同工作副本，结构为 `{"nodes": [...], "edges": [...]}`；输出节点引用 `pipeline_output.id`。
- `revision` 是草稿语义修订号；每次成功的节点、连线或配置修改递增，用于乐观锁。节点坐标移动属于展示变更，不递增 `revision`。
- `pipeline_edit_op` 只追加语义修改；`revision` 是修改后的草稿修订号，`seq` 是同一批修改内的顺序。坐标移动不写编辑操作。
- `operation_id` 表示一次具体操作或命令；`turn_id` 表示一轮对话。一个 `turn_id` 可以包含多个 `operation_id`，用于“撤销本轮 Agent 改动”。
- `pipeline_version` 是构建时冻结的不可变快照；`definition` 保存流程，`outputs` 保存通用输出和类型专用配置的冻结副本。

**输入版本**

- `pipeline_version.input_refs` 记录流程声明的输入引用，例如 `{datasetKind, datasetId}`；MVP 只允许 `sync` 数据集。
- `pipeline_build.resolved_inputs` 记录本次实际读取的精确版本，例如 `{datasetKind, datasetId, datasetVersionId, schemaHash}`；这是复现和来源追踪的依据。来源展示额外保存 `displaySnapshot`，只用于历史展示，不参与版本解析；详见 §11.6。

**输出和数据集**

- `pipeline_output` 是通用输出声明；`output_type=dataset` 时由 `pipeline_dataset` 承载写入模式、主键、数据期望和目标派生数据集。
- `pipeline_dataset` 是 pipeline 自己的派生数据集，不复用 P1 `dataset`，也不带连接/源表语义。
- `pipeline_dataset.write_mode`：MVP 只允许 `snapshot`，表示每次构建用新结果整体替换输出；`append`、`update`、`delete` 后续再开放。
- `pipeline_build_output` 是“这次构建里某个输出跑得怎么样”；`pipeline_dataset_version` 是“这个 dataset 输出实际产出了哪份数据”。
- `pipeline_dataset.expectations` 是数据质量规则配置；构建时检查，任一规则失败则构建失败，不发布新版本。检查结果记录在 `pipeline_build_output` 的结果或统计中。
- 未来增加文件、虚拟表、Ontology 等输出时，新增对应类型表（如 `pipeline_file_output`），均以 `output_id` 关联 `pipeline_output`；通用输出表和构建表不变。

**调度**

- `pipeline_schedule` 表示一条流程的定时配置；MVP 只支持 `trigger_type=time`，使用 5 段 Unix cron 和 IANA 时区。
- `pipeline_schedule_run` 表示一次触发；`pending` 等待当前构建结束，`dispatched` 已创建构建，`ignored` 未创建构建，`failed` 调度自身失败。
- `pipeline_schedule_run.status=dispatched` 只表示构建已创建，不代表构建成功；构建结果看 `pipeline_build.status`。
- 无变化不创建 `pipeline_build`：由 `pipeline_schedule_run.status=ignored`、`reason_code=NO_CHANGE` 记录；不改变流程状态。
- `pipeline_build.trigger` 取值：`manual`、`schedule`。

**P3 消费边界**

- P1 `dataset` 和 P2 `pipeline_dataset` 保持独立存储。
- P3 通过只读 `dataset_catalog` 视图或查询服务获取统一字段：`dataset_kind`、`dataset_id`、`owner_id`、`name`、`current_version_id`、时间戳。
- 版本查询通过对应的统一版本视图或服务完成；不在 P2 中把两类数据集物理合并。

**来源追踪（降级版）**

- P2 只保留来源元数据：`pipeline_build.resolved_inputs` 记录本次构建读取的精确输入版本；`pipeline_dataset_version.build_output_id` 关联到 `pipeline_build_output`，再关联到 `pipeline_build` 和 `pipeline_version`。
- 数据集详情只展示直接上游：输入数据集、输入版本、P1 来源连接/源表、构建 ID、构建时间、流程版本和构建状态。
- P2 不新增 `pipeline_lineage_edge`，不做多跳血缘遍历、血缘图、下游影响分析和字段级血缘。
- 现有来源元数据足以支撑后续补完整血缘；后续增加血缘功能时不需要重新计算历史构建。

### 5.6 技术实现（2026-10-20 补充）

状态：已确认。

- 实际 schema：所有 `pipeline*` 表放 Java 的 `data` schema；`conversation` 和 `conversation_event` 保持 Python 现有的 `public` schema。文档中的“app schema”统一改为“public schema”，不建跨 schema 外键。
- 迁移归属：Java 用 Flyway 新增 `V8+` 迁移创建流程表；Python 用 Alembic 单独增加 `conversation.kind=data_pipeline`、`conversation.archived_at` 和 `conversation_event.turn_id`。两边迁移不共享事务，部署顺序不要求同时完成。
- 草稿 JSON 固定为 `{"schemaVersion":1,"nodes":[...],"edges":[...]}`。节点包含 `id`、`type`、`name`、`config`、`position`；连线包含 `id`、`sourceNodeId`、`sourcePort`、`targetNodeId`、`targetPort`。`pipeline_draft.definition` 和 `pipeline_version.definition` 使用同一结构。
- 输出节点只在 `config` 中保存 `outputId`；`pipeline_output` 是输出身份和类型的唯一来源，草稿只引用它。构建冻结时把通用输出声明和类型专用配置复制进 `pipeline_version.outputs`，避免历史版本被后续输出修改影响。
- 输入节点保存 `datasetKind` 和 `datasetId`，不在草稿中固定版本；预览和构建时由 Java 解析当前已发布版本。解析结果写入 `pipeline_build.resolved_inputs`。
- 语义草稿写入使用单条条件更新：`WHERE pipeline_id=? AND revision=?`，成功后 `revision` 递增；同一事务插入 `pipeline_edit_op` 和发件箱事件。更新 0 行返回 409，由调用方重新读取草稿。坐标移动单独更新，不递增 `revision`，不写编辑操作和事件。
- 冻结流程版本时锁 `pipeline` 行分配 `version_number`；对去掉节点坐标后的规范化 `definition + outputs + input_refs` 计算 SHA-256，写入 `definition_hash`。`(pipeline_id, definition_hash)` 唯一，同一定义复用已有版本。
- `pipeline_dataset.id` 是稳定的派生数据集身份；`pipeline_dataset_version` 在 Iceberg 快照提交后先写入，`published_at` 暂为空。统一发布事务成功后才写 `published_at` 并切换 `pipeline_dataset.current_version_id` 和 `pipeline.current_version_id`。
- 补充审计字段：`pipeline_build.requested_by` 记录触发构建的用户；`pipeline_build.heartbeat_at` 用于 worker 存活检测，`pipeline_build.cancel_requested_at` 记录持久化取消请求；`pipeline_dataset_version.created_at` 用于清理未发布产物；`conversation_event.turn_id` 可空，用于把同一轮 Agent 改动归组并支持“撤销本轮”。
- 关键约束：同一流程最多一个 `queued/running` 构建（部分唯一索引）；`(build_id, output_id)` 唯一；`(dataset_id, version_number)` 唯一；`(dataset_id, iceberg_table, iceberg_snapshot_id)` 唯一；`(schedule_id, scheduled_for)` 唯一；同一调度最多一条 `pending` 运行（部分唯一索引）；`(pipeline_id, operation_id)` 在构建表内唯一。
- 关键索引：`pipeline(owner_id, archived_at, updated_at DESC)`、`pipeline_edit_op(pipeline_id, turn_id)`、`pipeline_build(pipeline_id, created_at DESC)`、`pipeline_build(pipeline_id, pipeline_version_id, created_at DESC)`、`pipeline_dataset_version(dataset_id, version_number DESC)`、`pipeline_schedule(status, next_run_at)`（只索引 active）。
- `pipeline_dataset.expectations` 使用带 `type` 的判别联合 JSON，Java 在写入和构建前校验；主键单独保存在 `primary_key_columns`，不在 expectations 中重复存储。
- P3 使用 `data.dataset_catalog` 和 `data.dataset_version_catalog` 只读视图统一 P1/P2 数据集；视图只暴露已发布版本，不物理合并两类数据集。

Palantir 依据（均已实际访问，返回 200）：

- [Datasets](https://palantir.com/docs/foundry/data-integration/datasets/)：数据集事务支持 `SNAPSHOT`、`APPEND`、`UPDATE`、`DELETE`；MVP 只实现 `SNAPSHOT`。
- [Add a dataset output](https://palantir.com/docs/foundry/pipeline-builder/outputs-add-dataset-output)：数据集输出在部署/构建后物化，输出配置包含写入方式。
- [Branches overview](https://palantir.com/docs/foundry/pipeline-builder/branches-overview)：分支作用域在流程内；MVP 只保留 main。

## 6. 议题三：Agent 引导与流程状态机

状态：已确认（2026-10-20）。

### 6.1 产品行为

- Agent 是流程向导，不是一次性生成整条流程的聊天机器人；它按当前画布逐步推进。
- Agent 每回合重新读取当前草稿和流程检查结果，不依赖上一轮聊天记忆。
- 一次只问一个最阻塞的问题，优先给具体选项，不把全部问题一起抛给用户。
- 普通、非破坏性改动由 Agent 直接写草稿并立即反映到画布。
- 删除节点、清空流程、删除或改指向输出、覆盖已有输出等破坏性操作需要确认。
- 构建必须由用户明确点击或明确说“构建”；Agent 不能把构建作为其他操作的后台副作用。
- 用户手动改画布后，Agent 以最新画布为准继续引导；Agent 与用户同时修改时，不能静默覆盖用户改动。

### 6.2 三类状态

三层状态分开维护：

| 层 | 存储 | 取值 | 作用 |
|---|---|---|---|
| 流程生命周期 | `pipeline.status` | `draft`、`active`、`archived` | 流程是否正在使用、是否归档 |
| 可构建状态 | 派生，不落库 | `empty`、`needs_input`、`needs_output`、`invalid`、`ready`、`building`、`failed`、`succeeded`、`stale` | Agent 判断下一步说什么、做什么 |
| 单次构建 | `pipeline_build.status` | `queued`、`running`、`succeeded`、`failed`、`canceled` | 某次构建的运行结果 |

可构建状态含义：

| 状态 | 含义 | Agent 下一步 |
|---|---|---|
| `empty` | 新流程，没有节点 | 询问输入数据集 |
| `needs_input` | 没有有效输入 | 让用户选数据集和版本 |
| `needs_output` | 有输入，但没有完整输出 | 询问最终要产出的数据集 |
| `invalid` | 结构、配置或 schema 有阻塞错误 | 定位具体节点/字段并修复 |
| `ready` | 阻塞错误清零 | 建议预览，再询问是否构建 |
| `building` | 构建运行中 | 展示进度；说明构建使用冻结版本 |
| `failed` | 最近一次构建失败 | 解释失败输出和原因，提出修复方案，不自动重试 |
| `succeeded` | 最近构建成功，草稿未变 | 查看结果或继续修改 |
| `stale` | 构建成功后草稿又变了 | 说明产物对应旧版本，建议重新构建 |

构建期间允许继续编辑草稿，但构建使用 `pipeline_version` 的冻结快照；构建结束后如果草稿又变，进入 `stale`。无变化不创建构建，记录在 `pipeline_schedule_run.status=ignored`，不改变流程的可构建状态；调度状态见 §10。

### 6.3 流程检查结果

Java 在每次需要时计算流程检查结果，Agent 读取结构化结果后决定回复和工具调用：

```json
{
  "revision": 12,
  "stage": "invalid",
  "blockingIssues": [
    {
      "code": "FIELD_NOT_FOUND",
      "nodeId": "...",
      "field": "amount",
      "message": "上游输出没有 amount 字段"
    }
  ],
  "warnings": [],
  "nextActions": [
    { "action": "configure_node", "nodeId": "..." }
  ]
}
```

至少覆盖以下问题码：

- `INPUT_MISSING`
- `INPUT_VERSION_UNRESOLVED`
- `OUTPUT_MISSING`
- `OUTPUT_NOT_CONNECTED`
- `NODE_CONFIG_INCOMPLETE`
- `CYCLE_DETECTED`
- `SCHEMA_MISMATCH`
- `FIELD_NOT_FOUND`
- `TYPE_MISMATCH`
- `PRIMARY_KEY_INVALID`
- `EXPECTATION_INVALID`
- `PREVIEW_FAILED`（警告，不阻塞构建）
- `BUILD_FAILED`

### 6.4 下一步询问顺序

按阻塞程度排序，一次只问一个：

1. 最近构建失败：先解释失败输出和原因。
2. 缺输入：让用户选数据集和版本。
3. 缺输出或输出未连接：询问最终要产出什么。
4. 节点配置、schema 或字段错误：定位到具体节点和字段，给出修复选项。
5. 输出数据集未配置：询问数据集名称、写入模式、主键。
6. 预览失败：提示风险，但不直接阻止构建。
7. 可构建：建议预览或构建。
8. 草稿已变更：建议重新构建。

### 6.5 画布同步与并发

- 画布操作由 Java 写入 `pipeline_edit_op`，通过 Outbox 投递到 Python，写成 `ConversationEvent`。
- Agent 每回合先读取最新 `pipeline_draft` 和流程检查结果，再回复或调用工具。
- Agent 写草稿时携带 `base_revision`；如果用户同时改了画布，Java 返回冲突，Agent 重新读取并重新规划，不能静默覆盖。
- 用户删除 Agent 正要操作的节点时，Agent 以最新草稿为准，重新询问或重新生成。

### 6.6 确认策略

| 操作 | 是否确认 |
|---|---|
| 新增节点、改配置、连线、改名、移动 | 不确认，直接写草稿 |
| 删除单条连线 | 可以不确认，保留撤销能力 |
| 删除节点 | 需要确认 |
| 清空流程 | 需要确认 |
| 删除、改指向或覆盖输出数据集 | 需要确认 |
| 触发构建 | 必须由用户明确操作；Agent 只发起确认，不自动构建 |
| 自动重试失败构建 | 不自动，先解释再询问 |

确认请求记录为 `ConversationEvent`，带 `operation_id`；用户确认后才写 `pipeline_edit_op`，刷新页面后仍能恢复待确认操作。

### 6.7 技术实现（2026-10-20 补充）

状态：已确认。

- 新增 `DataPipelineAgentDefinition` 并注册到 `data_pipeline`；每轮 `prepare_run` 从 Java 重新读取流程状态和检查结果，写入 `DataPipelineRunContext`；`build_graph` 再用本轮状态构造 `DataPipelineToolContext`，不把上一轮状态当成事实。读取失败时保留 `load_error`，写入工具返回 `PIPELINE_STATE_UNAVAILABLE`，不伪造修订号。
- `build_graph` 使用单 Agent + 工具，MVP 不启用规划子智能体；提示词只注入紧凑状态和最近事件，完整草稿通过工具按需读取，控制上下文体积。
- Java 提供唯一的 `PipelineCheckService`，UI、Agent 和构建入口共用。派生状态优先级固定为：`building` → `empty/needs_input/needs_output/invalid` → `failed` → `stale` → `succeeded` → `ready`。
- `failed` 仅在最近一次终态构建失败且草稿 revision 未变化时成立；草稿已修改则回到 `ready` 或 `invalid`，但界面保留失败记录。`canceled` 不改变派生状态。
- 读取工具：流程状态、检查结果、草稿、输入数据集、字段结构、事件。写入工具：加节点、改节点、删节点、连线、断线、设置输出。动作工具：预览节点、构建流程。
- 每轮生成 `turn_id`，每次工具调用生成 `operation_id`；写入工具使用上下文中的 `base_revision`，成功后更新 revision。Java 返回 409 时转成 `REVISION_CONFLICT`，Agent 停止写入、重新读取状态并告知用户，不静默覆盖。
- 每轮最多一个澄清问题，由 `ask_user` 工具和上下文的 `questionsAsked` 计数强制；第二个问题返回 `QUESTION_LIMIT_REACHED`。确认中断单独计数，不占用澄清问题额度。
- 删除节点、清空流程、修改或覆盖输出、触发构建必须在工具内部调用 LangGraph `interrupt`；模型不能自己传 `confirmed=true`，只有用户 resume 的确认结果能继续执行。
- 提供 `POST /pipelines/{id}/turns/{turnId}/undo`：只允许撤销最近一轮 Agent 编辑，且当前草稿 revision 必须等于该轮结束 revision；否则返回 409。撤销本身写新的编辑记录，不物理删除历史。
- `conversation_event` 增加可空 `turn_id` 并建索引，用于把同一轮 Agent 改动归组到对话时间线。

每轮系统提示词骨架（动态段由 Java 状态填充，不写入检查点）：

```text
你是 OntoWeave 的数据整理助手，负责陪用户逐步搭建一条数据处理流程。

## 当前流程（每轮重新读取）
{{pipeline_state_json}}

## 最近事件
{{recent_events}}

## 工作规则
1. 先调用工具确认事实，不编造节点、字段、数据集或构建状态。
2. 每轮最多问一个最阻塞的问题，提问必须调用 ask_user。
3. 普通修改直接写草稿；删除节点、清空流程、修改或覆盖输出、构建必须确认。
4. 所有写操作使用当前 draft_revision 作为 base_revision；冲突时停止写入并重新读取。
5. 只能使用节点目录中的节点和配置，不能生成自由 SQL。
6. stage=ready 时先建议预览；用户没有明确要求时不要自动构建。
7. 用简短、直白的中文回答，不暴露内部接口和服务名。

## 本轮优先动作
{{top_next_action}}
```

### 6.8 Palantir 依据

Palantir 的 AIP Generate 会基于选中的输入节点和 prompt 返回转换节点及说明，并允许继续下一步、查看历史 prompt 和重试；Explain 用于解释选中的连通子图。我们的 Agent 同样输出具体节点和变更说明，而不是只给建议。

Palantir 的构建解析会检测循环、校验输入和 schema、锁定输出；因此流程检查结果先做同样的阻塞检查，再让 Agent 引导用户构建。

官方页面（均已实际访问，返回 200）：

- [Pipeline Builder AIP features](https://www.palantir.com/docs/foundry/pipeline-builder/pipeline-builder-aip/)
- [Pipeline Builder 核心概念](https://www.palantir.com/docs/foundry/pipeline-builder/core-concepts)
- [构建生命周期](https://www.palantir.com/docs/foundry/data-integration/builds/)
- [预览](https://www.palantir.com/docs/foundry/pipeline-builder/outputs-preview-pipeline/)
- [交付流程](https://www.palantir.com/docs/foundry/pipeline-builder/outputs-deliver-pipeline/)
- [数据期望](https://www.palantir.com/docs/foundry/pipeline-builder/dataexpectations-overview)

## 7. 议题四：画布节点、连线与配置

状态：已确认（2026-10-20）。

### 7.1 产品行为

- 画布按「输入 → 转换 → 输出」搭积木；每个节点只做一种转换。
- 筛选、选列、改名等都是独立节点；需要多个操作时拖多个节点并连线，不把多个功能塞进一个节点。
- 点击节点后在画布右侧打开配置抽屉；抽屉底部提供「预览」按钮，点击后关闭抽屉并在画布底部打开预览面板；配置与预览同一时间只显示一个。
- 错误标在节点、端口或字段上，并能从错误列表跳回对应配置位置。
- Agent 与用户共用同一套节点目录、配置 schema 和校验规则；Agent 不能自己生成 SQL 或另一套算子。

### 7.2 MVP 节点目录

| 节点 | 输入 → 输出 | 作用 |
|---|---|---|
| 输入 | 0 → 1 | 选择同步数据集；预览/构建时解析当前已发布版本 |
| 筛选 | 1 → 1 | 按条件过滤行 |
| 选列 | 1 → 1 | 保留或删除字段 |
| 改名 | 1 → 1 | 修改字段名 |
| 列计算 | 1 → 1 | 用表达式生成字段，包含类型转换 |
| 去重 | 1 → 1 | 按整行或字段子集去重 |
| 关联 | 2 → 1 | 左右两表等值关联 |
| 合并 | N → 1 | 多表按字段名纵向合并 |
| 汇总 | 1 → 1 | 分组后做计数、求和、最值、平均值 |
| 输出 | 1 → 0 | 写入 pipeline 自己的派生数据集 |

MVP 纳入基础汇总（Aggregate）；不做排序、窗口、透视、拆分（Split）、用户自定义函数和自由 SQL。

### 7.3 端口与连线

| 节点类型 | 输入端口 | 输出端口 |
|---|---|---|
| 输入 | 无 | `out` |
| 筛选、选列、改名、列计算、去重、汇总 | `in` | `out` |
| 关联 | `left`、`right` | `out` |
| 合并 | `inputs[]`，至少两个 | `out` |
| 输出 | `in` | 无 |

连线规则：

- 只能从输出端口连到输入端口。
- 一个输入端口只能接一条边；一个输出端口可以分叉给多个下游。
- 不允许环。
- 允许存在未连接到输出的节点，但构建时不会执行；只执行通往输出节点的路径。
- 关联和合并的输入端口数量和角色固定，不能把普通单输入节点连到 `left`/`right` 以外的位置。
- 端口类型不兼容时禁止创建连线，或在连线上显示错误。

### 7.4 配置抽屉与预览面板

- 画布左侧是节点面板，支持拖入节点。
- 选中节点后，在画布右侧打开配置抽屉；不占用中间对话栏。
- 配置抽屉结构：节点名称/类型/状态、输入字段、操作配置、输出字段、错误提示。
- 配置抽屉底部固定「预览」按钮；点击后先关闭配置抽屉，再从画布底部打开预览面板并执行预览。
- 配置与预览互斥：打开配置会关闭预览；打开预览会关闭配置。需要改配置时先关闭预览，再点节点打开抽屉；预览打开时点击其他节点，会关闭预览并打开该节点的配置抽屉。
- 预览面板结构：节点名称、草稿修订号、「基于当前草稿」标识、结果行数、耗时、结果表格、错误列表，以及「刷新」「关闭」操作。
- 配置修改自动保存；连续快速输入合并为一次编辑操作，失焦或约 400 毫秒空闲时提交。
- 配置一旦修改，已有预览立即标记为「已过期」；不自动重跑，用户再次点击「预览」或「刷新」时重新计算。
- 节点至少展示节点级、端口级和字段级三类错误；错误能定位到具体节点和字段。点击错误列表中的字段错误时，关闭预览并打开对应节点的配置抽屉，聚焦到该字段。

### 7.5 节点配置规则

- **输入**：选择数据集；MVP 不固定版本，预览和构建时解析当前已发布版本，构建时冻结精确输入版本。
- **筛选**：列、操作符、值，支持 AND/OR；空值按不满足条件处理，空值判断显式使用 `IS NULL`。
- **选列**：支持保留或删除字段，至少保留一个输出字段。
- **改名**：旧字段名到新字段名；新名称非空且在当前节点内唯一，只改大小写也视为改名。
- **列计算**：配置输出字段名和表达式；`Cast` 是表达式不是节点；MVP 不做隐式类型推断。
- **关联**：两路等值关联，支持左、右、内、全外；关联键输出一份，右侧同名非关联字段默认加 `right_` 前缀，仍冲突时追加序号。
- **合并**：按字段名对齐，所有输入 schema 必须一致；缺失字段报错；保留重复行，去重必须单独接去重节点。
- **去重**：按整行或字段子集判断，保留任意一行，不承诺顺序。
- **汇总**：配置分组字段和聚合字段；不配置分组字段时为全局汇总；MVP 支持 count、sum、min、max、avg。
- **输出**：配置目标 `pipeline_dataset`、写入模式、主键和数据期望；输出 schema 由上游推导，MVP 不允许手工改字段类型。

### 7.6 三端共用节点定义

- Java 维护节点目录：类型 ID、显示名称、分类、端口、配置 schema、校验规则、预览/构建实现。
- Python Agent 通过内部接口读取节点目录和校验错误，不维护第二套节点枚举。
- 前端通过 Python 获取节点目录，用配置 schema、UI schema 和少量专用编辑器渲染表单，不在组件里硬编码算子清单。
- Agent 添加节点时使用与画布相同的结构化配置；不能绕过 schema 直接写自由 SQL。
- Agent 可以根据当前选中的节点和上游 schema 生成具体配置建议，但配置必须经过同一套校验。

### 7.7 Palantir 依据

以下页面均已实际访问并返回 200：

- [转换总览：表达式 vs 转换](https://www.palantir.com/docs/foundry/pipeline-builder/transforms-overview)
- [配置并添加转换](https://www.palantir.com/docs/foundry/pipeline-builder/transforms-transform-data/)
- [筛选 Filter](https://www.palantir.com/docs/foundry/pb-functions-transform/filterV1)
- [选列 Select columns](https://www.palantir.com/docs/foundry/pb-functions-transform/selectV1)
- [删列 Drop columns](https://www.palantir.com/docs/foundry/pb-functions-transform/dropV1)
- [改名 Rename columns](https://www.palantir.com/docs/foundry/pb-functions-transform/renameColumnsV1)
- [类型转换 Cast](https://www.palantir.com/docs/foundry/pb-functions-expression/castV2)
- [关联 Join data](https://www.palantir.com/docs/foundry/pipeline-builder/transforms-join-data)
- [合并 Union data](https://www.palantir.com/docs/foundry/pipeline-builder/transforms-union-data)
- [去重 Drop duplicates](https://www.palantir.com/docs/foundry/pb-functions-transform/dropDuplicatesV1)
- [汇总 Aggregate](https://www.palantir.com/docs/foundry/pb-functions-transform/aggregateV1)

Palantir 的关联高级配置支持选择右表字段和自定义前缀；MVP 固定使用 `right_` 前缀，选列交给下游「选列」节点，保持一个节点只做一种转换。Palantir 的筛选条件可以接受通用布尔表达式；MVP 按已确认边界，只允许字段与常量比较和与/或组合。

### 7.8 技术实现（2026-10-20 补充）

状态：已确认。

**节点目录（node catalog）**

- Java 用代码维护 `PipelineNodeCatalog`，不把节点目录存数据库。节点行为、端口和校验规则随代码发布，目录落库容易和执行器实现漂移。
- Java 提供 `GET /internal/v1/pipeline-node-catalog`；Python 通过 `GET /pipelines/node-catalog` 向浏览器暴露同一份目录，并对 Agent 提供读取工具。
- 目录响应包含 `catalogVersion` 和节点数组；每个节点声明 `type`、`version`、`label`、`category`、`ports`、`configSchema`、`uiSchema`。浏览器按 ETag 缓存。
- MVP 所有数据端口的数据种类统一为 `table`；端口兼容性主要体现在连线拓扑，字段级兼容性在 schema 推导阶段校验。
- 草稿不记录目录版本。MVP 只允许向后兼容的目录变更；破坏性变更必须提升草稿 `definition.schemaVersion` 并提供迁移，不直接改旧草稿语义。

节点配置的规范形状如下：

| 节点 | `config` |
|---|---|
| 输入 | `{datasetKind, datasetId}` |
| 筛选 | `{condition}` |
| 选列 | `{mode: "include" \| "exclude", columns: string[]}` |
| 改名 | `{renames: [{from, to}]}` |
| 列计算 | `{outputField, expression}` |
| 去重 | `{mode: "all" \| "subset", columns: string[]}` |
| 关联 | `{joinType: "left" \| "right" \| "inner" \| "full", keys: [{left, right}]}` |
| 合并 | `{}`，输入由端口提供 |
| 汇总 | `{groupBy: string[], aggregations: [{op, field?, outputField}]}` |
| 输出 | `{outputId}` |

`configSchema` 使用 JSON Schema，开启 `additionalProperties: false`，但顶层业务字段不设为 `required`。这样未完成的草稿可以保存，由 Java 语义校验返回 `NODE_CONFIG_INCOMPLETE`；结构类型错误则直接拒绝写入。

**表达式树（AST）**

- 筛选条件和列计算共用受限表达式树，不允许自由 SQL、子查询、正则或用户自定义函数。
- 允许的节点类型：`field`、`literal`、`cast`、`case`、`compare`、`logical`、`isNull`。
- 比较运算的 `op` 固定为 `eq`、`ne`、`gt`、`gte`、`lt`、`lte`、`is_null`、`is_not_null`、`in`；`is_null` / `is_not_null` 不带值字段，`in` 使用 `values`。
- 字面量带显式 `dataType`，例如：

```json
{"kind":"compare","field":"status","op":"eq","value":{"kind":"literal","dataType":"string","value":"active"}}
```

- 列计算的 `outputField` 已存在时覆盖该字段，不存在时追加；字段名必须非空且在本节点内唯一。
- 关联的键可以是多列；MVP 固定右侧同名字段加 `right_` 前缀，仍冲突时追加序号。
- 合并节点不接收配置，所有输入必须字段结构完全一致并保留重复行；去掉重复行由下游「去重」节点负责。

**有向无环图（DAG）校验**

- Java 的 `PipelineGraphValidator` 按固定顺序执行：JSON 结构 → 节点/连线身份和端口 → 环检测与必需输入 → 输出可达性 → 拓扑序 schema 推导和语义校验。
- 规则：一个输入端口只能有一条边；输出端口可以分叉；不允许自环和环；关联固定两个输入；合并至少两个输入；输出节点必须至少有一个输入。
- 只有连接到输出节点的路径参与构建。孤立节点记为警告，不阻塞构建；预览仍可单独计算该节点的上游路径。
- schema 推导从输入节点开始，逐节点计算输出字段。错误统一沿用 §6.3 的检查结果，并补充 `port`、`field`、`blocking`；新增 `EDGE_PORT_INVALID`、`PORT_OCCUPIED`、`DUPLICATE_FIELD`、`AGGREGATION_INVALID` 等问题码。
- UI、Agent、预览和构建都调用同一个 `PipelineCheckService`，不各自实现一套规则。

**前端画布**

- `PipelineCanvas` 使用 `@xyflow/react`，节点和连线由草稿派生；`useNodesState` 只保存拖动中的临时位置和选中态，不维护第二份图数据。
- 从节点面板拖入时用 `screenToFlowPosition` 计算坐标，生成 UUID 和默认配置，发送新增命令；成功后打开配置抽屉。
- `isValidConnection` 在客户端即时检查自环、环、端口占用和端口角色；`onConnect` 仍发送命令，由 Java 重新校验并最终裁决。
- 画布修改走 `PipelineDraftCommandService` 的命令队列，一次只允许一个写请求在途；每个命令携带 `baseRevision`。返回 409 时停止队列、重新读取草稿并提示，不静默覆盖。
- 初始布局和「整理布局」使用 `dagre`；用户拖动后的坐标持久化，Agent 新增节点时放在上游节点右侧，找不到上游时使用自动布局。
- 坐标是展示元数据：移动节点不递增语义 `revision`，不写 `pipeline_edit_op`，不触发重新检查，不使构建变为 `stale`，也不参与 `definition_hash`。多个客户端同时移动同一节点时后写覆盖前写。

**配置抽屉**

- 采用「通用字段组件 + 专用编辑器」，不实现完整的 JSON Schema 自动表单引擎。
- 通用组件由 `uiSchema.widget` 选择文本、数字、布尔、单选、多选、字段选择器或操作符选择器；筛选条件树、关联键、汇总聚合列表和表达式树使用专用编辑器。
- 抽屉的输入字段、输出字段和节点错误直接使用 `PipelineCheckService` 的结果，前端不自行推导 schema。
- 配置修改按失焦或约 400 毫秒空闲合并提交；结构合法但语义不完整的配置允许保存，并在抽屉和节点上显示错误。

**Agent 复用**

- Agent 读取工具新增 `get_node_catalog`；提示词只注入节点类型、名称和用途的紧凑摘要，完整配置 schema 按需读取，控制上下文体积。
- 写入工具只接受结构化节点操作：`add_pipeline_node`、`update_pipeline_node`、`delete_pipeline_node`、`connect_pipeline_nodes`、`disconnect_pipeline_nodes`、`set_pipeline_output`；不接受整图 JSON 或自由 SQL。
- 工具参数先通过节点 `configSchema`，再调用 Java 校验。结构不合法返回 `CONFIG_INVALID`；结构合法但配置不完整时允许写入，并返回检查结果让 Agent 继续询问用户。
- Agent 新增节点时可以不传坐标；Java 根据上游节点分配位置。所有写入继续使用议题 3 的 `base_revision`、`turn_id` 和 `operation_id`。

**输入版本**

- 输入节点只保存 `datasetKind` 和 `datasetId`，不保存版本。检查、预览和构建时由 Java 解析当前已发布版本；`pipeline_version.input_refs` 记录输入声明，精确版本写入 `pipeline_build.resolved_inputs`。
- MVP 不提供「固定到某个版本」的输入配置；需要固定版本时，后续在输入节点增加版本策略并提升草稿 schema 版本。

## 8. 议题五：预览与字段级错误

状态：已确认（2026-10-20）。

### 8.1 结论

- 预览是显式操作，不随选中节点或修改配置自动执行；避免每次点击和输入都触发计算。
- 预览按单个选中节点执行，并自动计算从原始输入到该节点的完整上游；输出节点预览其输入结果，不执行写入。
- 预览最多展示 500 行；服务端另有内部安全读取上限，触达上限时明确提示「结果不完整」，不能把部分结果当成全量。
- MVP 不提供用户自定义采样；内部安全上限只用于保护服务，不作为采样策略暴露给用户。
- 预览使用当前草稿；构建使用冻结的 `pipeline_version`。预览和构建共用同一套节点计划与执行实现，但预览受读取上限约束，不能替代构建验证。
- 预览结果不落库，不创建 `pipeline_build`、`pipeline_version` 或 `pipeline_dataset_version`，也不写输出数据集。
- 预览失败本身不阻塞构建，只作为警告；如果失败原因同时是节点配置、字段或 schema 错误，构建检查仍会按对应错误阻塞。
- 错误必须定位到节点、端口和字段；节点、连线端口和配置字段上都能看到对应错误标记。

### 8.2 交互流程

1. 用户点击节点，画布右侧打开配置抽屉；如果预览面板正开着，先关闭预览。
2. 用户点击配置抽屉底部的「预览」，配置抽屉关闭，画布底部打开预览面板并显示加载状态。
3. 预览完成后展示节点名称、草稿修订号、「基于当前草稿」标识、结果行数、耗时、结果表格和错误列表。
4. 用户修改配置后，当前预览标记为「已过期」并停止作为最新结果展示；系统不自动重跑。
5. 用户点击「刷新」重新预览；或关闭预览面板，点击节点重新打开配置抽屉继续修改。
6. 预览面板打开时点击其他节点，关闭预览并打开新节点的配置抽屉；同一时间只显示配置或预览中的一个。

### 8.3 数据量与执行边界

- 返回结果最多 500 行；前端不分页加载更多，超过部分通过构建查看。
- 服务端按内存和超时预算设置固定安全读取上限；响应携带 `scannedRows`、`truncated` 和 `truncationReason`。
- `truncated=true` 时，行数和统计只代表已读取部分，界面必须显示「非全量结果」提示。
- 预览请求带 `pipeline_id`、`draft_revision`、`node_id`；服务端校验修订号，避免用旧草稿返回误导结果。
- 预览结果只进入进程内短期缓存；可选使用 Redis 短期缓存，但不写数据库、不写对象存储。缓存键为 `pipeline_id + draft_revision + node_id + 节点配置哈希`，草稿修订变化后旧缓存不再命中。
- 预览失败不改变流程状态，不写任何持久化运行记录；需要展示的错误通过响应返回。

### 8.4 错误结构

预览和流程检查共用同一套错误码与定位字段：

```json
{
  "code": "FIELD_NOT_FOUND",
  "phase": "compile",
  "nodeId": "3c8f0d9e-2f0e-4e8b-8f4d-1f7f9c6a2b11",
  "portId": "in",
  "field": "amount",
  "message": "上游输出没有 amount 字段",
  "hint": "检查上游的选列或改名配置"
}
```

- 节点级错误：节点边框和状态徽标变红。
- 端口级错误：对应输入或输出端口变红，连线显示错误。
- 字段级错误：配置表单中的字段高亮，预览错误列表同步展示。
- 点击错误列表中的字段错误，关闭预览并打开对应节点配置抽屉，滚动并聚焦到该字段。
- 预览错误码至少覆盖 `NODE_CONFIG_INCOMPLETE`、`SCHEMA_MISMATCH`、`FIELD_NOT_FOUND`、`TYPE_MISMATCH`、`PREVIEW_TIMEOUT`、`PREVIEW_LIMIT_REACHED`。

### 8.5 Palantir 依据

Palantir 的预览面板按单个选中节点运行，并计算从原始数据集到该节点的逻辑；默认最多处理 500 行，筛选、关联和去重等操作可能读取超过 500 行输入才能产出 500 行结果。输入采样策略只影响预览，不影响构建。我们的 MVP 不提供用户采样，改为服务端内部安全上限。

Palantir 在选中节点时自动打开预览并执行；我们的配置抽屉与预览面板互斥，因此改为显式点击「预览」触发，避免选中节点就产生计算。

官方页面（均已实际访问，返回 200）：

- [预览 Pipeline](https://www.palantir.com/docs/foundry/pipeline-builder/outputs-preview-pipeline/)
- [输入采样策略](https://www.palantir.com/docs/foundry/pipeline-builder/management-input-sampling)
- [构建生命周期](https://www.palantir.com/docs/foundry/data-integration/builds/)

### 8.6 技术实现（2026-10-20 补充）

状态：已确认。

**请求、校验与并发**

- 新增 `POST /pipelines/{pipelineId}/previews`，请求体为 `{draftRevision, nodeId, requestId}`；Python 透传到 Java，用户仍只访问 Python 的 `/pipelines`。
- 新增 `POST /pipelines/{pipelineId}/previews/cancel`（Java 内部路径为 `/internal/v1/pipelines/{pipelineId}/previews/cancel`），无请求体，成功返回 204；取消当前流程正在运行的预览，重复取消按幂等处理。
- Java 先校验 `draftRevision`，不一致返回 409；再调用 `PipelineCheckService`，存在阻塞错误返回 422，不启动查询。
- 预览使用独立执行通道和独立并发限制，不占用 `pipeline_build` 的构建锁，不创建构建、版本或数据集版本记录。
- 建议默认：同一流程同时最多 1 个预览，同一用户最多 2 个；预览超时 20 秒。构建优先，资源冲突时取消预览并返回 `PREVIEW_CANCELED`。

**数据来源**

- 预览不查业务库，也不查 `pipeline_draft` 表；它查输入节点解析出的 P1 已发布数据集版本对应的 Iceberg 快照。
- 请求开始时，Java 解析每个输入节点的当前已发布版本，得到 `iceberg_catalog`、`iceberg_namespace`、`iceberg_table` 和 `iceberg_snapshot_id`。
- 目标节点是输入节点时，复用 P1 的 `IcebergPreviewReader.read(...)` 快速路径，直接按快照读取；这是唯一不启动 Flink 的预览路径。
- 目标节点是转换节点时，`PreviewPlanBuilder` 从目标节点反向遍历，只保留其上游子图；`PipelinePlanCompiler` 生成内部逻辑计划，再编译为 Flink 批处理 Table API 作业。

**查询计划**

- Flink 作业注册 Hadoop Iceberg catalog，为每个输入创建表，并通过 Iceberg 的 `snapshot-id` 读取选项固定快照，避免预览期间上游发布新版本导致结果漂移。
- 每个输入设置扫描上限，例如默认 50,000 行；通过 Iceberg connector 的 `limit` 读取选项或等价的内部算子限制实现。
- 按拓扑顺序应用节点计划中的筛选、选列、改名、列计算、去重、关联、合并和汇总。
- 目标节点是输出节点时，只执行它的上游，不执行写入。
- 最终查询 `LIMIT 501`：返回前 500 行；读到第 501 行时标记 `truncated=true`，原因 `OUTPUT_ROW_LIMIT`。
- 如果任一输入版本的总行数超过扫描上限，也标记 `truncated=true`，原因 `SCAN_ROW_LIMIT`。关联、去重等场景可能因此漏掉部分结果，界面必须显示“非全量预览”；构建仍读取完整输入。
- 不使用用户可控的采样比例；扫描上限只用于保护预览服务。

**结果与缓存**

- Flink 以批模式执行，`TableResult.collect()` 收集最多 501 行，转换成本地预览结果后立即释放作业；结果不写 Iceberg、不写数据库、不写对象存储。
- 响应包含 `previewId`、`draftRevision`、`nodeId`、`columns`、`rows`、`returnedRows`、`scannedRows`、`truncated`、`truncationReason`、`elapsedMs`、`inputVersions`、`errors` 和 `warnings`。
- `scannedRows` 从 Flink source 指标汇总；指标不可用时返回 `null`，不使用估算值冒充实际扫描行数。
- 预览结果可放进程内短期缓存；缓存键包含 `ownerId + pipelineId + draftRevision + nodeId + 排序后的输入版本 ID + planHash`，TTL 建议 5 分钟。修订或输入版本变化后自然失效。
- 关闭预览面板或发起同一流程的新预览时，取消仍在运行的 Flink 作业。
- 前端关闭面板、切换节点或切换流程时调用取消接口；取消失败不阻塞界面切换，但保留可重试提示。构建开始时仍由 Java 主动取消并等待预览释放。

**错误定位**

- 编译期错误由 `PipelineCheckService` 产生，直接携带 `nodeId`、`portId`、`field`，不启动 Flink。
- 执行期类型转换在预览模式使用 `TRY_CAST`，并生成隐藏错误标记列；结果返回前剥离标记列，把失败字段、行号和原值摘要写入 `errors`。
- 其他执行期错误通过计划节点元数据映射回 `nodeId` 和 `portId`；无法确定字段时保留节点级错误，不伪造字段名。
- 预览执行期错误默认是 warning，不阻塞构建；编译期 schema、字段和配置错误仍然阻塞构建。超时返回 `PREVIEW_TIMEOUT`，资源繁忙返回 `PREVIEW_BUSY`。

**前端行为**

- 点击「预览」后关闭配置抽屉、打开底部预览面板并进入加载态；预览期间显示节点名称、草稿修订号和取消入口。
- 配置修改后，当前结果标记为「已过期」，不自动重跑；「刷新」重新请求，「关闭」取消作业并收起面板。
- 面板展示输入版本摘要、返回行数、扫描行数、是否截断、耗时、结果表格和错误列表；点击字段错误跳到对应节点配置抽屉。

实现参考：

- [Iceberg Flink queries](https://iceberg.apache.org/docs/latest/flink-queries/)
- [Iceberg Flink configuration](https://iceberg.apache.org/docs/latest/flink-configuration/)

## 9. 议题六：执行、构建与派生版本

状态：已确认（2026-10-20）。

### 9.1 结论

- 构建是唯一的发布动作：草稿自动保存，不单独做“保存”；MVP 不单独做“部署”，构建成功即发布新版本。
- 构建先把当前草稿冻结成不可变 `pipeline_version`，再把所有输入解析成精确的 `dataset_version_id`，整次构建固定使用这组输入版本。
- MVP 复用 P1 的 Flink 1.20 批处理 + Iceberg，不引入 Spark、Trino 或 DuckDB；节点图先编译成内部逻辑计划，再生成 Flink Table API 作业，Agent 不能生成自由 SQL。
- MVP 只支持全量快照写入（`SNAPSHOT`）：每次构建用新结果整体替换输出，不做增量追加、更新或主键合并。
- 一个构建可以包含多个输出；每个输出独立执行并记录 `pipeline_build_output`，但只有全部输出成功且数据期望通过后才统一发布。
- 多输出采用“先写 Iceberg 暂存快照、后统一发布指针”的逻辑原子语义：任一输出失败或取消，所有输出都不发布，下游不会看到半套数据。
- 数据期望是输出后置检查；MVP 支持主键、行数区间、取值集合、非空四类，任一失败则构建失败、不发布新版本。
- 手动构建总是执行，不做无变化跳过；定时构建的无变化判定见 §10，无变化不创建构建。
- 同一流程同一时间只允许一个构建；重复触发返回冲突，不排队。

### 9.2 产品行为

- MVP 只保留一个构建入口：画布顶部工具栏的「构建」。输出节点配置抽屉展示构建状态，不重复放构建按钮。
- 点击「构建」先弹出确认，展示本次使用的草稿修订号、输出列表和“构建期间修改草稿不会进入本次构建”的提示。
- 构建启动后，顶部显示阶段进度；点击进度打开构建详情，展示整体状态、每个输出状态、耗时、行数、数据期望结果和日志。
- 构建期间允许继续编辑草稿；构建始终使用冻结的 `pipeline_version`，结束后如果草稿又变，流程进入 `stale`。
- 运行中的构建可以取消；取消后不发布、不切换当前版本，已完成输出的暂存快照不对外可见。
- 失败构建不自动重试；Agent 只解释失败原因和给出修复建议，不自动发起构建。

### 9.3 构建生命周期

1. **请求校验**：校验 `draft_revision`、流程阻塞错误和当前是否已有运行中构建。修订号不一致返回 409；存在阻塞错误返回 422，不创建构建记录。
2. **冻结版本**：按 `definition_hash` 查找可复用的 `pipeline_version`，没有则创建；版本记录 `source_draft_revision`、冻结图和输出配置。
3. **创建构建**：创建 `pipeline_build`，状态 `queued`，写入 `operation_id`、`trigger=manual` 和发起人；同一 `operation_id` 重试返回已有构建。
4. **解析输入**：把每个输入节点解析为精确版本，读取构建开始时该数据集的已发布最新版本。结果写入 `resolved_inputs`，包含 `datasetVersionId` 和 `schemaHash`。
5. **锁定输出**：同一流程只允许一个活跃构建；输出只由本次构建发布，其他构建不能同时写同一输出。
6. **执行输出**：按拓扑顺序执行通往输出节点的路径；每个输出提交一个独立 Flink 作业，记录 `engine_job_id`。
7. **检查期望**：输出写入 Iceberg 后执行数据期望检查，结果写入 `pipeline_build_output.result.expectations`。
8. **统一发布**：全部输出成功且期望通过后，在一个数据库事务里写入 `pipeline_dataset_version.published_at`、切换各 `pipeline_dataset.current_version_id` 和 `pipeline.current_version_id`；首次成功构建把流程状态置为 `active`。Iceberg `main` 分支快进在事务提交后尽力执行，不作为发布事实。

任一执行步骤失败，`pipeline_build.status=failed`，按失败码记录原因；已经完成的输出保持在 `pipeline_build_output` 中，但不发布。

### 9.4 执行引擎

- 逻辑计划（Logical Plan）是图与引擎之间的稳定中间层：节点类型、配置、输入引用、输出映射都先转成计划，再由计划生成 Flink Table API 作业。
- 预览与构建共用同一个逻辑计划；预览只把输入扫描替换为有界读取并加输出行数限制，构建读取完整输入。
- MVP 每个输出一个 Flink 作业，便于独立记录日志、独立取消和定位失败；上游公共计算可能重复执行，后续再合并为单作业优化。
- 作业名包含 `pipeline_id`、`build_id`、`output_id`，用于在 Flink 侧定位；`engine_job_id` 写入 `pipeline_build_output`。
- 不把 Flink SQL 暴露给前端或 Agent；内部即使使用 SQL 生成，也只能由受控逻辑计划生成，不能让 Agent 拼接 SQL。

### 9.5 版本、输入与当前版本

- `pipeline_version` 不可变；同一 `pipeline_id + definition_hash` 复用，避免每次构建都生成新版本。
- `pipeline_version` 只冻结逻辑和输出配置，不冻结输入数据版本；输入版本在每次构建开始时解析，写入 `pipeline_build.resolved_inputs`。
- 构建失败或取消不改变 `pipeline.current_version_id`；成功后才把当前版本切到本次构建使用的 `pipeline_version`。
- 定时构建后续只运行 `pipeline.current_version_id` 指向的已发布版本，不直接运行草稿；具体调度规则在议题 7 确认。
- 构建成功后如果 `pipeline_draft.revision` 大于 `pipeline_version.source_draft_revision`，派生状态为 `stale`。

### 9.6 发布原子性

- 每个 dataset 输出写入自己的 Iceberg 表；每次输出生成一个新的 Iceberg 快照，并创建一条 `pipeline_dataset_version`，此时 `published_at=NULL`。
- 只有整体构建成功，才在数据库事务中把本次所有 `pipeline_dataset_version` 置为已发布，并更新 `pipeline_dataset.current_version_id`；任一输出失败或取消则全部保持未发布。
- 这是“逻辑原子发布”，不是跨 Iceberg 表的分布式事务：失败构建可能在 Iceberg 表里留下未被任何已发布版本引用的快照，由后续清理任务删除。
- P3 只通过 `pipeline_dataset.current_version_id` 和已发布版本读取数据，不直接读 Iceberg 表的当前快照，因此不会读到未发布数据。
- 发布事务失败记为 `PUBLISH_FAILED`；已经写入的 Iceberg 快照保留为未发布状态，可重试发布或清理。

### 9.7 数据期望

- 数据期望只作用于输出，且在输出写入之后、统一发布之前执行。
- MVP 支持四类：主键（所选列非空且组合唯一）、行数区间（最小值和/或最大值）、取值集合（每行都在允许值内）、非空（指定列无空值）。
- 主键期望读取 `pipeline_dataset.primary_key_columns`；未配置主键时不能使用主键期望。
- 浮点非 NaN 和行级表达式期望后置，不进入 MVP。
- 任一期望失败，`pipeline_build_output.status=failed`、`pipeline_build.status=failed`，不发布任何输出；失败详情记录实际值、期望值和失败行数摘要。

### 9.8 状态、进度、日志与取消

- 构建状态：`queued`、`running`、`succeeded`、`failed`、`canceled`；无变化不创建构建，记录为 `pipeline_schedule_run.status=ignored`。
- 构建阶段：`resolving_inputs`、`submitting`、`running`、`checking_expectations`、`publishing`、`finished`。
- 每个输出同样记录 `stage`，并记录 `engine_job_id` 和 `log_uri`；整体构建记录 `stage` 和 `log_uri`。
- 生命周期事件（排队、开始、输出成功/失败、取消、发布完成）写入 `ConversationEvent`，`run_id=build_id`；原始日志通过 SSE 实时推送并写入对象存储，不逐行写入对话事件。
- 取消构建时先写 `cancel_requested_at`，由执行 worker 调用 Flink cancel，等待引擎确认后标记 `canceled`；取消中界面显示“取消中”，但 `status` 只使用最终状态。
- 失败构建不自动重试；手动重试创建新的 `pipeline_build` 和新的 `operation_id`。

### 9.9 输出 Schema 变化

- 首次构建自动创建输出的 Iceberg 表。
- 允许新增列和类型放宽（例如整数扩为长整数）；这些变化写入新的 `schema_json`。
- 删除列、改名、类型收窄视为破坏性变化，构建失败并返回 `OUTPUT_SCHEMA_CHANGED`，不自动修改输出表结构。
- MVP 不允许手工覆盖输出 schema；输出 schema 始终由上游推导。需要破坏性变更时，后续再设计显式迁移流程。

### 9.10 接口轮廓

| 操作 | 接口 | 说明 |
|---|---|---|
| 发起构建 | `POST /pipelines/{pipelineId}/builds` | 请求体包含 `draftRevision` 和 `operationId`；返回构建 ID 和状态 |
| 查询构建 | `GET /pipelines/{pipelineId}/builds/{buildId}` | 返回整体状态、阶段、各输出状态和统计 |
| 取消构建 | `POST /pipelines/{pipelineId}/builds/{buildId}/cancel` | 取消运行中的 Flink 作业，不发布 |
| 构建事件 | `GET /pipelines/{pipelineId}/builds/{buildId}/events` | SSE 推送阶段、输出状态和日志摘要 |
| 读取日志 | `GET /pipelines/{pipelineId}/builds/{buildId}/log` | 读取已持久化的完整日志 |

### 9.11 与 Palantir 的差异

- Palantir 区分 Deploy（部署逻辑）和 Build（物化数据）；我们 MVP 不设单独部署，构建成功即发布，减少一次用户操作。
- Palantir 文档说明构建中某个 job 失败时，之前完成的 job 可能已经写入输出；我们采用暂存后统一发布，保证下游不会看到部分更新。
- Palantir 默认使用 Spark 计算引擎；我们复用 P1 已有的 Flink + Iceberg，避免新增一套计算基础设施。
- Palantir 支持多种写入模式和部分输出构建；我们 MVP 只做全量快照和整条流程构建。

### 9.12 Palantir 依据

Palantir 的构建解析会检测循环、校验输入数据集和 schema、锁定输出事务，并在输入被其他构建改变时排队。Job 状态包括等待、运行、取消、失败和完成；构建完成后输出才算更新。数据期望支持主键、行数、取值集合、非空、浮点非 NaN 和行级表达式，任一失败则构建失败。数据集事务中 `SNAPSHOT` 会用全新文件整体替换当前数据集视图。

官方页面（均已实际访问，返回 200）：

- [Builds](https://palantir.com/docs/foundry/data-integration/builds)
- [Data expectations](https://palantir.com/docs/foundry/pipeline-builder/dataexpectations-overview)
- [Dataset transactions](https://palantir.com/docs/foundry/data-integration/datasets/)
- [Deliver pipeline / Deploy vs. build](https://palantir.com/docs/foundry/pipeline-builder/outputs-deliver-pipeline)
- [Add a dataset output](https://palantir.com/docs/foundry/pipeline-builder/outputs-add-dataset-output)

### 9.13 技术实现（2026-10-20 补充）

状态：已确认。

**入队与执行 worker**

- `POST /pipelines/{pipelineId}/builds` 只做修订号、检查结果和活跃构建校验，然后写入 `pipeline_build(status=queued)` 并返回。
- 同一流程的活跃构建由部分唯一索引保证；同一 `operation_id` 重试返回已有构建，不重复创建。
- Java 新增数据库驱动的 `PipelineBuildWorker`，用 `FOR UPDATE SKIP LOCKED` 领取 `queued` 构建，复用 P1 的 worker 模式，不新增消息队列。
- worker 领取构建后写入 `status=running`、`started_at`，执行期间定期更新 `heartbeat_at`；启动时把心跳超时的 `running` 构建标记为 `BUILD_WORKER_LOST`，并取消已记录的 Flink 作业。
- 取消接口只写 `cancel_requested_at`；worker 读取到后取消 Flink 作业，最终将 `status` 置为 `canceled`。`status` 不增加“取消中”状态。

**冻结版本与解析输入**

- 锁 `pipeline` 行分配 `version_number`；对去掉节点坐标后的规范化 `definition + outputs + input_refs` 计算 `definition_hash`，相同则复用已有 `pipeline_version`。
- 解析每个输入节点的当前已发布版本，写入 `pipeline_build.resolved_inputs`，包含 `datasetVersionId`、`schemaHash` 和 Iceberg 快照位置。
- 输入缺失、没有已发布版本或 schema 无法读取时，构建失败，不提交 Flink 作业。

**Flink 计划与输出执行**

- `PipelinePlanCompiler` 生成内部逻辑计划，`FlinkPlanCompiler` 将其转换为 Flink Table API；不使用自由 SQL，不在运行时拼接用户输入。
- 每个输出节点提交一个独立 Flink 批作业，MVP 默认串行执行；任一输出失败或取消，停止后续输出并取消仍在运行的作业。
- 输入表通过 Iceberg `snapshot-id` 固定到本次构建解析的精确版本；节点运算保持与预览同一套语义。
- 输出写入每个 `pipeline_dataset` 对应的稳定 Iceberg 表的临时分支 `build_<buildId>`，使用覆盖写实现全量快照；不直接写 `main`。
- 每个输出记录 `pipeline_build_output.status`、`stage`、`engine_job_id`、`log_uri`、行数、schema 和统计；任一步失败都保留失败详情。
- 构建详情和逐输出响应增加 `datasetId`、`datasetVersionId`：构建执行中为 `null`，发布成功后指向本次生成的 `pipeline_dataset` 和不可变版本；历史上已发布输出始终保留这两个 ID，供来源区域直接跳转。
- `log_uri` 由配置的 Flink REST 基址和已提交的 JobID 生成作业诊断入口；成功、失败和取消路径都写入。写回只更新 `log_uri` 字段，不能整实体保存，以免覆盖并发写入的 `cancel_requested_at`、`stage` 和 `heartbeat_at`。没有可用 REST 基址时保持 `null`，界面展示诊断 fallback。

**数据期望**

- 每次输出作业完成后，读取临时分支的快照执行数据期望；行数优先读取 Iceberg 快照摘要，主键唯一性、非空和取值集合通过 Flink 批查询计算。
- 期望结果写入 `pipeline_build_output.result.expectations`，包含实际值、期望值、失败行数摘要和是否通过。
- 任一期望失败，当前输出和整个构建失败；临时分支不发布，后续清理。

**统一发布**

- 全部输出成功且期望通过后，在一个数据库事务内写入各 `pipeline_dataset_version`、切换各 `pipeline_dataset.current_version_id` 和 `pipeline.current_version_id`、更新构建状态并写发件箱事件。
- `pipeline_dataset_version` 记录临时分支的精确 `iceberg_snapshot_id`；数据库指针是 P2/P3 的唯一发布事实。
- 事务提交后，尽力将每个 Iceberg 表的 `main` 分支快进到已发布快照，并删除临时分支。快进失败不回滚数据库发布，由对账任务按数据库指针重试。
- 发布事务失败记为 `PUBLISH_FAILED`，保留临时分支供重试或清理；未发布分支不通过数据集目录暴露。

**输出 schema 变化**

- 首次构建自动创建 Iceberg 表。
- 允许新增列和 Iceberg 支持的类型放宽；更新表 schema 后再提交输出作业。
- 删除列、改名、类型收窄返回 `OUTPUT_SCHEMA_CHANGED`，构建在提交 Flink 作业前失败。
- 每次发布把实际 schema 写入新的 `pipeline_dataset_version.schema_json`，历史版本仍按自己的快照和 schema 读取。

**取舍**

- MVP 每个输出独立作业会重复计算共享上游，但失败隔离清晰、状态记录简单；后续可在不改数据模型的前提下合并共享子图。
- 临时分支 + 数据库指针避免下游看到半套输出；`main` 快进只作为兼容性维护，不承担原子发布职责。

实现参考：

- [Iceberg Flink writes and branch writes](https://iceberg.apache.org/docs/latest/flink-writes/)
- [Iceberg branching and tagging](https://iceberg.apache.org/docs/latest/branching/)

## 10. 议题七：定时调度与无变化跳过

状态：已确认（2026-10-20）。

### 10.1 结论

- MVP 只支持时间触发：5 段 Unix cron + IANA 时区；数据更新触发、逻辑更新触发和复合触发后置。
- 一条流程最多一个调度；调度只运行 `pipeline.current_version_id` 指向的已发布版本，不运行草稿。
- 到点先判断是否需要执行：没有已发布版本则跳过；输入版本和逻辑都没变且上次构建成功则跳过。
- 无变化不创建 `pipeline_build`，只写一条 `pipeline_schedule_run`，状态 `ignored`、原因 `NO_CHANGE`；`pipeline_build.status=skipped` 不再使用。
- P1 同步与 P2 调度不直接串联：调度到点读取 P1 当前已发布版本；没有新版本就跳过，P1 失败不会直接让 P2 调度失败。
- 同一流程仍只允许一个构建；调度触发时如果有构建在跑，保留一条 `pending` 运行，多次触发合并，当前构建结束后再判断是否执行。
- 支持暂停、恢复、下次运行时间、运行历史和跳过原因；MVP 不做自动重试和自动暂停。
- 暂停会清空下次运行时间，并把待执行运行标记为 `ignored`；恢复从当前时间重新计算，不补跑历史。

### 10.2 产品行为

- 流程顶部工具栏增加「调度」入口，打开调度抽屉；一条流程只有一个调度。
- 调度配置包含：启用/暂停、频率预设（每小时、每天、每周）或自定义 cron、时区、下次运行时间。
- 运行历史展示每次触发的时间、状态、跳过原因、对应构建链接和耗时。
- 草稿已修改但尚未成功构建时，抽屉显示「调度仍使用已发布版本 vN」警告；没有已发布版本时显示「首次构建后才能定时运行」。
- 暂停后不再产生新的触发；恢复后只从当前时间向后计算，不补跑暂停期间错过的触发。
- Agent 可以建议调度配置，但创建、修改、暂停、恢复调度必须由用户确认。

### 10.3 数据模型

新增两张表，ER 见 §5.2。

| 表 | 关键字段 | 职责 |
|---|---|---|
| `pipeline_schedule` | `pipeline_id`、`name`、`status`、`trigger_type`、`cron_expression`、`timezone`、`next_run_at`、`last_run_at`、`paused_at`、`paused_by`、`created_by`、时间戳 | 一条流程一个定时配置；MVP 只支持 `trigger_type=time` |
| `pipeline_schedule_run` | `schedule_id`、`pipeline_id`、`status`、`trigger_type`、`scheduled_for`、`build_id`、`logic_hash`、`resolved_inputs`、`reason_code`、`message`、起止时间 | 每次触发、跳过或派发记录 |

关键约束：

- `pipeline_schedule.pipeline_id` 唯一；`status` 取值 `active`、`paused`。
- `pipeline_schedule_run.status` 取值 `pending`、`dispatched`、`ignored`、`failed`。
- `(schedule_id, scheduled_for)` 唯一，防止重启或多实例重复触发。
- 同一调度最多一条 `pending` 记录，后续触发合并到这条记录。
- `build_id` 唯一且可空；`dispatched` 时必填，`ignored`、`pending` 时为空。
- `ignored` 和 `failed` 必须写 `reason_code`；`dispatched` 不写跳过原因。
- `pipeline_build.trigger` 取值为 `manual` 或 `schedule`。

### 10.4 调度生命周期

1. **扫描到期调度**：Java 后台调度器定期扫描 `status=active` 且 `next_run_at <= now()` 的 `pipeline_schedule`，用 `FOR UPDATE SKIP LOCKED` 领取，支持多实例部署。
2. **计算触发时刻**：按 cron 和时区计算本次 `scheduled_for`。停机恢复后只补最近一次到期触发，不补全部历史，避免一次性堆出大量构建。
3. **创建或合并运行**：按 `(schedule_id, scheduled_for)` 幂等创建 `pipeline_schedule_run`；如果已有 `pending`，不新建，合并到已有记录。
4. **检查构建占用**：如果该流程已有 `queued` 或 `running` 构建，保持 `pending`，等当前构建结束后重新评估。
5. **检查已发布版本**：没有 `pipeline.current_version_id` 时，标记 `ignored / NO_ACTIVE_VERSION`，不创建构建。
6. **解析输入并计算 hash**：读取输入节点对应的当前已发布版本，写入 `resolved_inputs`，计算 `logic_hash`。MVP 不支持固定输入版本。
7. **无变化判断**：如果最近一次构建成功、使用的是当前已发布版本、且 `logic_hash` 与本次一致，标记 `ignored / NO_CHANGE`，不创建构建。
8. **派发构建**：否则调用与手动构建相同的内部服务，创建 `pipeline_build`，`trigger=schedule`，把 `build_id` 写回调度运行并标记 `dispatched`。
9. **推进下一次**：无论本次是 `pending`、`dispatched`、`ignored` 还是 `failed`，都更新 `last_run_at`，并按 cron 计算下一次 `next_run_at`；失败也推进，避免同一触发反复重试；暂停时 `next_run_at` 置空。

调度运行进入 `dispatched` 后，构建失败不回写调度运行为 `failed`；构建结果通过 `build_id` 查看。`pipeline_schedule_run.status=failed` 只表示调度自身没有成功派发构建，例如权限不足、cron 计算错误或内部异常。

### 10.5 无变化判定

- `logic_hash = pipeline_version.definition_hash + 排序后的 resolved input version IDs`；输出配置已包含在冻结的 `pipeline_version.definition_hash` 中。
- 判断对象是 `pipeline.current_version_id` 指向的已发布版本，不是草稿。
- 最近一次构建成功、版本一致且 `logic_hash` 相同 → 跳过，`reason_code=NO_CHANGE`。
- 最近一次构建失败或取消 → 不跳过，下一次调度触发重新尝试；MVP 不做构建内自动重试。
- 没有历史构建 → 不跳过，正常派发。
- 输入版本变化但结果数据可能相同，仍然执行；MVP 不比较结果行，只比较逻辑和输入版本。
- 跳过的运行同样保存 `logic_hash` 和 `resolved_inputs`，便于解释为什么没有执行。

### 10.6 与 P1 同步的衔接

- P1 同步独立发布 `dataset_version`；P2 调度在每次触发时解析最新已发布版本，不直接监听 P1 的 Flink 作业。
- P1 没有产生新版本时，输入版本 ID 不变，`logic_hash` 不变，P2 调度跳过并记录 `NO_CHANGE`。
- P1 同步失败时，P2 仍读取最后一个已发布版本；如果没有新版本就跳过，不把 P1 故障升级成 P2 调度失败。
- P1 和 P2 的故障边界保持独立：P2 不触发、不重试、不暂停 P1 同步；P1 也不直接触发 P2 构建。
- 调度抽屉展示每个输入的最新已发布版本时间，帮助用户把调度时间放在同步完成之后。
- 未来增加 `input_updated` 事件触发时，可以订阅 P1 的版本发布事件，再复用同一套无变化判定和构建派发逻辑。

### 10.7 并发、暂停与恢复

- 同一流程只允许一个活跃构建；调度触发时如果有构建在跑，只保留一条 `pending`，不排队多条。
- 当前构建结束后，`pending` 运行重新走“已发布版本 → 输入解析 → 无变化判断 → 派发”流程；如果当前构建已经把最新输入构建掉，则 `pending` 会转为 `ignored / NO_CHANGE`。
- 暂停调度：`pipeline_schedule.status=paused`、`next_run_at=NULL`；已有 `pending` 运行标记 `ignored / SCHEDULE_PAUSED`。
- 恢复调度：`status=active`，从当前时间计算下一次触发；不补跑暂停期间的触发。
- 修改 cron 或时区：立即重算 `next_run_at`，不立即触发一次构建。
- 停机期间错过的多个触发只补最近一次；夏令时前拨跳过不存在的墙钟时间，回拨导致的重复墙钟时间由 `(schedule_id, scheduled_for)` 唯一约束去重。
- MVP 不做自动暂停：连续失败只在运行历史中显示，由用户决定是否暂停；Palantir 的自动暂停后置。

### 10.8 状态与运行记录

| 状态 | 含义 |
|---|---|
| `pending` | 已触发，但当前有构建在跑或调度器尚未完成派发；同一调度最多一条 |
| `dispatched` | 已创建 `pipeline_build`；不代表构建成功 |
| `ignored` | 没有创建构建；原因包括 `NO_CHANGE`、`NO_ACTIVE_VERSION`、`SCHEDULE_PAUSED` |
| `failed` | 调度自身失败；原因包括 `PERMISSION_DENIED`、`TRIGGER_INVALID`、`DISPATCH_FAILED` |

运行记录只追加，不删除；MVP 不做历史保留清理。无变化跳过不会进入构建列表，只在调度运行历史中展示。

### 10.9 权限

- 创建、修改、暂停、恢复调度需要流程所有者或编辑权限。
- 调度以流程所有者身份运行；运行时权限不足则记录 `failed / PERMISSION_DENIED`，不创建构建。
- MVP 只做用户范围的调度，不做 Palantir 的 Project-scoped 调度；后续需要团队级调度时再增加执行身份和项目范围。

### 10.10 接口轮廓

| 操作 | 接口 | 说明 |
|---|---|---|
| 查询调度 | `GET /pipelines/{pipelineId}/schedule` | 返回配置、状态、下次/上次运行时间 |
| 创建或修改调度 | `PUT /pipelines/{pipelineId}/schedule` | 一条流程一个调度；请求包含 cron、时区、启用状态 |
| 暂停调度 | `POST /pipelines/{pipelineId}/schedule/pause` | 清空 `next_run_at`，处理待执行运行 |
| 恢复调度 | `POST /pipelines/{pipelineId}/schedule/resume` | 从当前时间计算下一次，不补跑 |
| 运行历史 | `GET /pipelines/{pipelineId}/schedule/runs` | 分页返回触发、跳过、派发记录和构建链接 |

### 10.11 与 Palantir 的差异

- Palantir 支持时间、数据更新、逻辑更新及复合触发；MVP 只做时间触发。
- Palantir 默认同一调度不重叠，触发会等待前一次完成；我们保留一条 `pending` 并合并后续触发，行为接近但不做多调度并行。
- Palantir 有 `Force build` 覆盖新鲜度；我们不提供强制调度构建，手动构建本身就是强制执行。
- Palantir 会自动暂停连续失败的调度；MVP 只记录连续失败，由用户手动暂停。
- Palantir 调度可以是用户范围或 Project 范围；MVP 只做用户范围。

### 10.12 Palantir 依据

Palantir 的调度用触发器定义构建条件，支持时间、事件和复合触发；默认同一调度不重叠，触发会等待前一次运行结束。暂停会重置触发状态并忽略已观察事件。调度运行分为成功启动构建、因数据已新鲜而忽略、以及调度失败；已新鲜的数据集不会在后续构建中重复计算，强制构建可以覆盖新鲜度。P1 同步和 P2 调度通过数据集版本解耦，正好对应 Palantir 的新鲜度判断。

官方页面（均已实际访问，返回 200）：

- [Schedules](https://palantir.com/docs/foundry/data-integration/schedules/)
- [Pipeline Builder schedules overview](https://palantir.com/docs/foundry/pipeline-builder/schedules-overview)
- [Trigger types reference](https://palantir.com/docs/foundry/building-pipelines/triggers-reference)
- [View and modify schedules](https://palantir.com/docs/foundry/building-pipelines/view-modify-schedules)
- [Scheduling best practices](https://palantir.com/docs/foundry/building-pipelines/scheduling-best-practices)
- [Staleness](https://palantir.com/docs/foundry/data-integration/builds/#staleness)

### 10.13 技术实现（2026-10-20 补充）

状态：已确认。

**调度器与抢占**

- Java 新增 `PipelineScheduleWorker`，用 Spring `@Scheduled` 定期扫描，默认间隔 15 秒，可配置；每批最多处理 100 条。
- 扫描条件为 `status=active AND next_run_at <= now()`，使用 `FOR UPDATE SKIP LOCKED` 领取，支持多实例部署。
- 不引入 Quartz 或消息队列；数据库是调度状态的唯一来源。
- 使用 `cron-utils` 解析 5 段 Unix cron，并绑定 IANA 时区计算下一次执行时间；停机恢复只补最近一次到期触发。
- 领取时用 `latestDueAt` 计算 `<= now()` 的最后一个触发时刻，避免停机后一次性补跑全部历史。

**运行记录与触发合并**

- 按 `(schedule_id, scheduled_for)` 幂等创建 `pipeline_schedule_run`。
- 如果流程已有 `queued/running` 构建，只保留一条 `pending` 运行；后续触发只推进 `next_run_at`，不重复插入。部分唯一索引 `(schedule_id) WHERE status='pending'` 保证合并。
- `pending` 运行不绑定构建；等当前构建结束后，由 `PipelineScheduleDispatcher` 重新执行“解析输入 → 无变化判断 → 派发”。
- 调度器自身异常写 `failed` 和 `reason_code`；调度失败不自动重试，下一次 cron 触发重新判断。

**无变化判断**

- 调度只读取 `pipeline.current_version_id` 指向的已发布 `pipeline_version`，不读取草稿。
- 从冻结定义中解析输入节点，读取每个输入当前已发布版本，写入运行记录的 `resolved_inputs`。
- 计算 `logic_hash = pipeline_version.definition_hash + 排序后的输入版本 ID`。
- 查询该 `pipeline_version_id` 最近一次终态构建：
  - 最近一次成功且 `logic_hash` 相同 → `ignored / NO_CHANGE`
  - 最近一次失败或取消 → 正常派发
  - 没有历史构建 → 正常派发
- 其他流程版本的失败构建不影响当前已发布版本的判断；新增索引 `pipeline_build(pipeline_id, pipeline_version_id, created_at DESC)`。
- 跳过运行同样保存 `logic_hash` 和 `resolved_inputs`，便于解释。

**派发**

- 需要执行时调用内部 `enqueueScheduledBuild`，传入 `pipelineVersionId=current_version_id`、`trigger=schedule`、`resolved_inputs`、`logic_hash` 和由调度运行 ID 派生的 `operation_id`。
- 构建服务只创建 `queued` 构建；调度运行写 `build_id` 并标记 `dispatched`。
- `dispatched` 只表示构建已创建，不代表构建成功；构建状态以 `pipeline_build` 为准。

**暂停、恢复与修改**

- 暂停在单个事务内完成：`status=paused`、`next_run_at=null`、所有 `pending` 运行标记 `ignored / SCHEDULE_PAUSED`；不取消已经在运行的构建。
- 恢复时 `status=active`，从当前时间计算下一次触发，不补跑暂停期间的触发。
- 修改 cron 或时区立即重算 `next_run_at`，不立即触发构建，也不补跑历史。
- 夏令时按 `cron-utils` 和 `ZoneId` 处理；回拨产生的重复墙钟时间由 `(schedule_id, scheduled_for)` 唯一约束去重。

**P1 解耦与接口**

- P1 同步失败或没有新版本时，输入版本 ID 不变，调度按 `NO_CHANGE` 跳过，不把 P1 故障升级为调度失败。
- 接口继续使用 §10.10 的 `GET/PUT /schedule`、`pause`、`resume` 和 `runs`；Python 作为浏览器统一入口。
- 运行记录同时返回 `startedAt` 和 `finishedAt`；`ignored` 或未进入执行的 `failed` 运行可以只有 `finishedAt`。前端只有两者都存在时才展示耗时，否则展示跳过/失败状态，不能把 `scheduled_for` 当开始时间。
- `pipeline` 列表和详情响应增加 `currentVersionNumber` 与 `currentVersionDraftRevision`：前者标识调度实际运行的已发布版本号，后者记录该发布对应的草稿修订号。前端据此显示“调度仍使用已发布版本 vN”，不会把当前草稿误认为已发布。
- 运行历史只追加，不随构建清理；MVP 不做自动暂停和自动重试。

## 11. 议题八：来源追踪与影响分析（降级）

状态：已确认（2026-10-20，降级）。

### 11.1 结论

- 完整血缘图和影响分析不是 P3/P4 的前置依赖，P2 不实现。
- P2 只保留来源元数据，并在输出数据集详情展示**单跳上游列表**。
- 不新增血缘边表，不做多跳遍历、血缘图、下游影响分析、字段级血缘、过期诊断图、保存图形和权限模拟。
- 现有 `resolved_inputs`、`build_id`、`pipeline_version_id` 和 `pipeline_dataset_version` 继续保留，后续补完整血缘时不需要重新计算历史构建。

### 11.2 P2 保留的功能

输出数据集详情增加「来源」区域，展示直接来源：

| 展示项 | 来源 |
|---|---|
| 输入数据集名称和版本 | `pipeline_build.resolved_inputs`（身份字段 + `displaySnapshot`；旧构建回退 P1 元数据） |
| P1 连接和源表 | P1 数据集元数据接口 |
| 构建 ID、构建时间和状态 | `pipeline_build` |
| 使用的流程版本 | `pipeline_version` |
| 输出版本号、行数、发布时间 | `pipeline_dataset_version` |

只展示直接输入，不做递归祖先展开；不展示下游和影响范围。

### 11.3 后置的功能

- `pipeline_lineage_edge` 版本级血缘边表。
- 多跳上游/下游遍历和交互式血缘图。
- 修改流程或输出前的下游影响分析。
- 字段级血缘、过期原因图、构建甘特图。
- 保存/共享血缘图、权限模拟和回滚。

这些功能放到 P3/P4 之后，或作为 P2 后续增强；P3 只需要读 P2 的已发布数据集版本，P4 只依赖对象和动作数据，不受影响。

### 11.4 验收边界

- 打开一个输出数据集，能看到它来自哪些输入数据集和版本、由哪次构建和哪个流程版本产生。
- 点击构建能看到构建状态和日志入口。
- 不出现「下游影响」页签，不出现可展开的血缘图。

### 11.5 Palantir 依据

Palantir 的 Data Lineage 支持数据集/表/列搜索、祖先/后代展开、节点详情、过期着色和影响分析；这些是完整的分析能力。P2 只保留其中的来源追踪子集，先把构建和版本链路做扎实，完整血缘后置。

官方页面（均已实际访问，返回 200）：

- [Data Lineage](https://palantir.com/docs/foundry/data-lineage/overview)
- [Explore lineage](https://palantir.com/docs/foundry/data-lineage/explore-lineage)
- [View build timeline](https://palantir.com/docs/foundry/data-lineage/build-timeline)
- [Understand out-of-date datasets](https://palantir.com/docs/foundry/data-lineage/stale-datasets)
- [Analyze the impact of changes](https://palantir.com/docs/foundry/code-repositories/analyze-impact)

### 11.6 技术实现（2026-10-20 补充）

状态：已确认。

**结论**

不新增血缘边表、图数据库或递归查询。P2 的「来源」是已发布输出版本上的只读投影：构建时把精确输入版本和展示快照写入 `resolved_inputs`；读取来源时按 `pipeline_dataset_version.build_output_id` 固定到那次构建，不按当前输入版本回推。`resolved_inputs` 是 JSONB，不需要新增表或数据库迁移，只扩展结构。

**数据链路**

`pipeline_dataset_version.build_output_id`
→ `pipeline_build_output(build_id, output_id)`
→ `pipeline_build.resolved_inputs`
→ `pipeline_version`

- `pipeline_dataset_version` 提供输出版本、发布时间、行数和 `build_output_id`。
- `pipeline_build_output` 提供该输出的执行状态、日志入口和构建 ID。
- `pipeline_build` 提供构建状态、触发方式、时间和精确输入版本。
- `pipeline_version` 提供构建使用的流程版本号和时间。
- `SourceBuild` 返回 `buildId`、状态、触发方式、起止时间和 `logUri`；`SourceInput` 返回构建时冻结的 `datasetId` 与 `datasetVersionId`。来源面板可打开构建详情和日志，但仍是单跳，不展开祖先/后代。
- `pipeline_output` / `pipeline_dataset` 只用于校验输出身份和名称，不参与历史版本固定。

**输入快照**

`resolved_inputs` 在现有身份字段上增加 `displaySnapshot`：

```json
{
  "datasetKind": "sync",
  "datasetId": "...",
  "datasetVersionId": "...",
  "schemaHash": "...",
  "displaySnapshot": {
    "datasetName": "orders",
    "datasetVersionNumber": 12,
    "connectionId": "...",
    "connectionName": "prod-mysql",
    "sourceSchemaName": "sales",
    "sourceTableName": "orders"
  }
}
```

- 身份字段用于复现和后续完整血缘；`displaySnapshot` 只用于历史展示，不参与 hash、权限判断或版本解析。
- 构建和调度运行共用一个 `ResolvedInput` 结构；调度跳过的 `pipeline_schedule_run.resolved_inputs` 也保存同一结构。
- worker 冻结 `pipeline_version` 并解析输入版本时，在同一事务写入 `resolved_inputs`，必须在提交 Flink 作业前完成。
- 历史构建没有 `displaySnapshot` 时，读取 P1 元数据回填；P1 元数据被改名、删除或无权限时，仍能显示已保存的展示快照和精确 ID，并标记 `metadataStatus`。

**查询接口**

浏览器通过 Python 统一入口读取，Java 提供内部查询：

`GET /pipelines/{pipelineId}/datasets/{datasetId}/sources?versionId={versionId}`

- `versionId` 可省略，默认 `pipeline_dataset.current_version_id`。
- 只允许查询已发布版本；版本必须属于该 dataset，dataset 必须属于该 pipeline，否则返回 404。
- 返回输出、构建、流程版本和直接输入列表；`inputs` 只做单跳，不递归。
- 读取路径固定为：版本 → 构建输出 → 构建 → 流程版本；输入元数据按 `datasetKind` 批量解析，MVP 只有 `sync`，一次批量查询 P1 元数据，避免 N+1。
- 输入元数据不可用时返回已保存的展示快照和 `metadataStatus=missing`；无权限时返回 `metadataStatus=restricted` 并隐藏名称、连接和源表。不得跨所有者暴露 P1 元数据。
- Agent 增加只读工具 `get_output_sources`，复用同一个 DTO；不提供写工具，也不允许 Agent 查询其他流程的来源。

响应结构示例：

```json
{
  "pipelineId": "...",
  "datasetId": "...",
  "versionId": "...",
  "output": {
    "outputId": "...",
    "name": "orders_clean",
    "versionNumber": 3,
    "rowCount": 1200,
    "publishedAt": "2026-10-20T10:00:00Z"
  },
  "build": {
    "buildId": "...",
    "status": "succeeded",
    "trigger": "manual",
    "startedAt": "...",
    "finishedAt": "..."
  },
  "pipelineVersion": {
    "versionId": "...",
    "versionNumber": 7,
    "createdAt": "..."
  },
  "inputs": [
    {
      "datasetKind": "sync",
      "datasetId": "...",
      "datasetVersionId": "...",
      "datasetVersionNumber": 12,
      "schemaHash": "...",
      "datasetName": "orders",
      "connectionName": "prod-mysql",
      "sourceSchemaName": "sales",
      "sourceTableName": "orders",
      "metadataStatus": "available"
    }
  ]
}
```

**服务分层**

- Java 新增 `PipelineSourceService`，只负责组装来源视图；不把逻辑塞进构建 worker 或 controller。
- `ResolvedInput` 的解析和展示快照由 `PipelineInputResolver` 统一产出，构建和调度共用，避免两处各拼一份。
- P1 元数据通过 `DatasetCatalogQueryService` 批量读取；返回 DTO 做脱敏和缺失降级。
- Python 只做鉴权透传和响应转发，不拼接来源数据。
- 前端在输出数据集详情增加「来源」区域，只渲染直接输入；不出现展开祖先/后代、影响分析、字段级血缘或血缘图入口。

**验收**

- 同一输出数据集的历史版本在输入表改名、删除或重新同步后，来源仍指向构建时的精确输入版本。
- 来源接口只返回直接输入，查询次数不随输入数量线性增长。
- 无权限或元数据缺失时有明确状态，不泄露连接和源表信息。
- 旧构建没有展示快照时仍可读取；新构建必须写入展示快照。
- P2 不新增血缘边表，不提供多跳、下游影响、字段级血缘或血缘图。

**Palantir 依据**

- Data Lineage Overview（数据血缘概览）支持祖先/后代展开、过期着色和影响分析；P2 只取「直接上游 + 构建来源」子集。
- Explore data lineage（探索数据血缘）支持搜索资源、展开直接父节点和递归展开；P2 只做直接父节点。
- View build timeline（查看构建时间线）提供构建历史甘特图和作业详情；P2 只展示单次构建的链接和状态。
- Understand out-of-date datasets（理解过期数据集）通过祖先展开和构建状态着色诊断过期；P2 不做该诊断。
- Analyze the impact of changes（分析变更影响）默认展示直接影响的数据集，并可扩展到派生数据集；P2 不做影响分析。

官方页面（2026-10-20 实际访问，均返回 200）：

- [Data Lineage Overview](https://www.palantir.com/docs/foundry/data-lineage/overview)
- [Explore data lineage](https://www.palantir.com/docs/foundry/data-lineage/explore-lineage)
- [View build timeline](https://www.palantir.com/docs/foundry/data-lineage/build-timeline)
- [Understand out-of-date datasets](https://www.palantir.com/docs/foundry/data-lineage/stale-datasets)
- [Analyze the impact of changes](https://www.palantir.com/docs/foundry/code-repositories/analyze-impact)

## 12. 决策记录

| 日期 | 议题 | 结论 | 状态 |
|---|---|---|---|
| 2026-10-19 | MVP 处理算子范围 | 候选：筛选、选列、改名、列计算、按字段名合并、关联、去重、汇总；后续已在议题四确认并扩展为 §7.2 的节点目录。 | 已由议题四确认 |
| 2026-10-20 | 交互模型 | 采用对话 + 画布；一个对话对应一条处理流程；Agent 通过对话引导用户逐步创建；画布操作写入对话事件。 | 已确认 |
| 2026-10-20 | 议题重组 | 调整为 8 个议题：交互骨架、数据模型、Agent 状态机、画布节点、预览、构建、调度、来源追踪。 | 已调整 |
| 2026-10-20 | 议题一：交互骨架与资源绑定 | 左侧流程列表、中间对话、右侧画布；一个对话严格绑定一条流程；Agent 直接写草稿，操作留痕；草稿自动保存，构建显式触发；支持撤销本轮 Agent 改动与回退到上次构建；破坏性操作需确认。 | 已确认 |
| 2026-10-20 | 议题二：处理流程的数据模型 | P2 领域归 Java；P1 `dataset` 不动；新增 `pipeline_dataset` / `pipeline_dataset_version`；草稿和版本用整图 JSONB；通用 `pipeline_output` + dataset 类型表；`pipeline_build_output` 记录逐输出执行结果；P3 通过统一数据集视图消费。 | 已确认 |
| 2026-10-20 | 议题三：Agent 引导与流程状态机 | Agent 每回合读取最新草稿和流程检查结果；一次只问一个最阻塞的问题；普通改动直接写草稿，破坏性操作和构建需要确认；画布变更通过事件同步；可构建状态派生不落库。 | 已确认 |
| 2026-10-20 | 议题四：画布节点、连线与配置 | MVP 节点为输入、筛选、选列、改名、列计算、去重、关联、合并、汇总、输出；一个节点一种转换；点击节点打开右侧配置抽屉；Java 统一节点目录，前端和 Agent 共用。 | 已确认 |
| 2026-10-20 | 议题五：预览与字段级错误 | 预览由配置抽屉底部按钮显式触发；预览打开时关闭配置抽屉，两者互斥；最多展示 500 行，服务端有内部安全读取上限；不提供用户采样；预览使用当前草稿、不落库、不阻塞构建；错误定位到节点、端口和字段。 | 已确认 |
| 2026-10-20 | 议题六：执行、构建与派生版本 | 构建是唯一发布动作；冻结 `pipeline_version` 并锁定输入版本；复用 Flink 批处理 + Iceberg；MVP 只做全量快照；多输出暂存后统一发布；数据期望支持主键、行数、取值集合、非空；构建表补 `stage`、`log_uri`，输出表补 `stage`、`engine_job_id`、`log_uri`。 | 已确认 |
| 2026-10-20 | 议题七：定时调度与无变化跳过 | MVP 只做时间触发；一条流程一个调度；调度只运行已发布版本；无变化不创建构建，记录 `pipeline_schedule_run=ignored`；P1/P2 通过数据集版本解耦；构建中触发保留一条 `pending`；支持暂停/恢复，不做自动重试和自动暂停。 | 已确认 |
| 2026-10-20 | 议题八：来源追踪与影响分析 | 降级：P2 只保留来源元数据和单跳上游列表；不新增血缘边表，不做多跳血缘图、下游影响分析、字段级血缘、过期诊断图和权限模拟；完整血缘后置到 P3/P4 之后。 | 已确认（降级） |
| 2026-10-20 | 议题一技术实现：资源创建与绑定 | Python 统一入口 + Java 内部接口；`operation_id` 派生稳定双 ID；Java 按 `pipeline_id`/`conversation_id` 幂等创建；4xx 回滚、超时重试对账；会话归档增加 `archived_at`；复制只复制草稿；前端 `/pipelines/:pipelineId`；抽取共享三栏和会话外壳；Java 业务事务与发件箱事件同步。 | 已确认 |
| 2026-10-20 | 议题二技术实现：数据模型落地 | `pipeline*` 放 `data` schema，`conversation` 放 `public` schema；草稿 JSON 带 `schemaVersion`；输出节点只引用 `outputId`；草稿条件更新 + 编辑历史 + 发件箱同事务；版本按规范化哈希复用；dataset 版本发布后才切换当前版本；补 `requested_by`、`created_at`、`conversation_event.turn_id`；P3 用只读目录视图消费。 | 已确认 |
| 2026-10-20 | 议题三技术实现：Agent 运行与状态机 | 新增 `DataPipelineAgentDefinition`；每轮重读状态；Java 统一计算派生状态；单 Agent + 工具；写入带 `base_revision`；`turn_id`/`operation_id` 留痕；每轮最多一个澄清问题；破坏性操作和构建通过 `interrupt` 确认；支持按 turn 撤销；提示词只注入紧凑状态和最近事件。 | 已确认 |
| 2026-10-20 | 议题四技术实现：画布节点、连线与配置 | Java 代码维护版本化节点目录；前端通过 Python 获取目录并按 ETag 缓存；配置用结构化 JSON 和受限表达式树，JSON Schema 管形状、Java 管语义；DAG 按固定顺序校验并共用 `PipelineCheckService`；前端使用受控 React Flow、命令队列和 `base_revision`；配置抽屉采用通用组件加专用编辑器；Agent 只调用结构化节点工具；输入节点不固定版本；坐标不参与语义修订、过期判断和版本哈希。 | 已确认 |
| 2026-10-20 | 议题五技术实现：预览与字段级错误 | 预览接口校验草稿修订号并共用 `PipelineCheckService`；输入节点复用 P1 Iceberg 快照读取，转换节点编译为 Flink 批处理 Table API 作业；每个输入固定 `snapshot-id` 并设置扫描上限，最终查询 501 行返回 500 行；预览使用独立通道、不占构建锁；结果只在内存短期缓存；编译期错误定位到节点/端口/字段，执行期转换用 `TRY_CAST` 和隐藏标记收集字段级警告。 | 已确认 |
| 2026-10-20 | 议题六技术实现：执行、构建与派生版本 | 构建接口只入队，数据库 worker 用 `SKIP LOCKED` 领取并心跳；新增 `heartbeat_at`、`cancel_requested_at`；冻结版本并固定输入快照；每个输出一个 Flink Table API 批作业，按顺序执行；输出写 Iceberg `build_<buildId>` 临时分支并覆盖写；期望验证通过后用一个数据库事务写版本并切换指针；事务后尽力快进 `main`；schema 只允许新增和放宽。 | 已确认 |
| 2026-10-20 | 议题七技术实现：定时调度与无变化跳过 | Spring 定时扫描 + `FOR UPDATE SKIP LOCKED`；`cron-utils` 解析 5 段 Unix cron 和 IANA 时区；按 `(schedule_id, scheduled_for)` 幂等、同一调度最多一条 `pending`；调度只读已发布版本并解析当前输入，按 `logic_hash` 判断无变化；派发复用构建入队服务；暂停清空 `next_run_at` 并忽略 pending，恢复不补跑。 | 已确认 |
| 2026-10-20 | 议题八技术实现：来源追踪与影响分析 | 不新增血缘边表或图查询；按 `pipeline_dataset_version.build_output_id → pipeline_build_output → pipeline_build.resolved_inputs → pipeline_version` 投影单跳来源；构建时保存 `displaySnapshot`，历史版本固定到构建时输入；新增只读来源接口和 Agent 工具；P1 元数据批量读取，缺失/无权限降级；不做多跳、下游影响和字段级血缘。 | 已确认 |
