export type Status = 'draft' | 'confirmed' | 'approved'

/** 删会话 / 删图时，对共享池里对象与关系的处理范围。 */
export type DeleteScope =
  | 'graph-only'
  | 'graph-and-drafts'
  | 'graph-and-unreferenced'

export interface Conversation {
  id: string
  graph_id: string
  title: string | null
}

export interface ConversationMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  kind: 'message' | 'error'
  blocks: MessageBlock[] | null
  created_at: string
}

export interface GraphProperty {
  id: string
  name: string
  datatype: string
  required: boolean
  is_array: boolean
  is_primary_key: boolean
  is_title_key: boolean
  status: Status
}

export interface GraphNode {
  id: string
  name: string
  description: string | null
  status: Status
  properties: GraphProperty[]
}

export interface GraphRelation {
  id: string
  name: string
  source_type_id: string
  target_type_id: string
  direction: string
  cardinality: string
  forward_label: string | null
  reverse_label: string | null
  /** 一对一 / 一对多：承载这条链接的外键属性 id（存对端主键）；多对多为 null。 */
  foreign_key_property_id: string | null
  status: Status
}

/** 抽屉里能直接编辑的元素类型：对象、字段与关系。 */
export type EditableElementKind = 'object_type' | 'property' | 'relation'

/** 改已有元素：只带真正变了的字段。 */
export interface ElementUpdateEdit {
  op: 'update'
  element_kind: EditableElementKind
  element_id: string
  patch: Record<string, string | null | boolean>
}

/** 给对象类型新增一个字段。 */
export interface PropertyCreateEdit {
  op: 'create_property'
  owner_type_id: string
  name: string
  datatype: string
  is_array: boolean
  is_primary_key: boolean
  is_title_key: boolean
}

/** 标记删除一个元素（抽屉里只删字段）。 */
export interface ElementDeleteEdit {
  op: 'delete'
  element_kind: EditableElementKind
  element_id: string
}

/** 新建一个草稿对象类型，并挂进当前图。 */
export interface ObjectTypeCreateEdit {
  op: 'create_object_type'
  name: string
  description?: string | null
}

/** 把共享池里已确认的对象类型并进当前图。 */
export interface ObjectTypeReuseEdit {
  op: 'reuse_object_type'
  object_type_id: string
}

/** 新建一条草稿关系；两端类型不存在时会一并挂进当前图。 */
export interface RelationCreateEdit {
  op: 'create_relation'
  source_type_id: string
  target_type_id: string
  name: string
  direction?: string
  cardinality?: string
  forward_label?: string | null
  reverse_label?: string | null
  foreign_key_property_id?: string | null
}

/** 抽屉一次保存里的全部编辑，整批落进同一个改动集。 */
export type GraphEditOp =
  | ElementUpdateEdit
  | PropertyCreateEdit
  | ElementDeleteEdit
  | ObjectTypeCreateEdit
  | ObjectTypeReuseEdit
  | RelationCreateEdit

export interface GraphSnapshot {
  graph: {
    id: string
    name: string | null
  }
  nodes: GraphNode[]
  relations: GraphRelation[]
}

export interface ChangesetOp {
  id: string
  /** view 是绘图子智能体对视图状态的改动：即时生效，撤销时一并回滚。 */
  op: 'create' | 'update' | 'delete' | 'view'
  target_id: string | null
  payload: {
    kind?: 'object_type' | 'property' | 'relation' | 'view'
    before?: Record<string, unknown>
    after?: Record<string, unknown>
    [key: string]: unknown
  }
}

export interface Changeset {
  id: string
  graph_id: string
  status: 'open' | 'confirmed' | 'rejected'
  ops: ChangesetOp[]
}

export interface ViewState {
  graph_id: string
  name: string | null
  spec: ViewSpec
}

export interface ViewSpec {
  nodes?: Record<string, { x: number; y: number }>
  layout?: {
    kind?: 'hierarchical' | 'force' | 'domain-grouped' | 'grid'
    options?: Record<string, unknown>
  }
  groups?: Array<{ id: string; label: string; nodeIds: string[] }>
  collapsed?: string[]
  highlight?: Array<{ nodeIds: string[]; style: string }>
  dim?: string[]
  filters?: Record<string, unknown>
  annotations?: Array<{ nodeId: string; text: string }>
  [key: string]: unknown
}

export type ToolStatus = 'running' | 'ok' | 'error'

/** 自由文本回答怎么回填给图：raw 直接给字符串，respond 包成工具审批的 respond 决策。 */
export type FreeTextMode = null | { wrap: 'raw' | 'respond' }

export interface InterruptOption {
  label: string
  answer: unknown
}

/** 确认卡片里的补充信息，用于展示候选对象的名称/描述/状态等。 */
export interface InterruptDetail {
  label: string
  value: string
}

/** 助手回合里按时间排列的内容块；思考 / 正文 / 工具调用共用一套渲染。 */
export type MessageBlock =
  | { id: string; kind: 'text'; text: string; done?: boolean }
  | { id: string; kind: 'reasoning'; text: string; done?: boolean }
  | { id: string; kind: 'notice'; text: string }
  | {
      id: string
      kind: 'tool'
      callId: string
      name: string
      args: Record<string, unknown>
      status: ToolStatus
      result?: string | null
      truncated?: boolean
      durationMs?: number | null
    }
  | {
      id: string
      kind: 'interrupt'
      question: string
      options: InterruptOption[]
      freeText: FreeTextMode
      details?: InterruptDetail[]
    }

/** POST /messages 或 /resume 的请求体。 */
export type AgentRunInput = { text: string } | { answer: unknown; display?: string }

export type AgentEvent =
  | { v: number; type: 'run.start'; runId: string; conversationId: string; messageId: string }
  | { v: number; type: 'block.start'; messageId: string; blockId: string; kind: 'text' | 'reasoning' }
  | { v: number; type: 'block.delta'; messageId: string; blockId: string; text: string }
  | { v: number; type: 'block.end'; messageId: string; blockId: string }
  | {
      v: number
      type: 'tool.start'
      messageId: string
      callId: string
      name: string
      args: Record<string, unknown>
    }
  | {
      v: number
      type: 'tool.end'
      messageId: string
      callId: string
      status: ToolStatus
      result: string
      truncated: boolean
      durationMs: number
    }
  | {
      v: number
      type: 'interrupt'
      messageId: string
      blockId: string
      question: string
      options: InterruptOption[]
      freeText: FreeTextMode
      details?: InterruptDetail[]
    }
  | {
      v: number
      type: 'state.update'
      changesetId?: string
      ops?: Array<{ op: string; kind?: string }>
      view?: ViewSpec
    }
  | { v: number; type: 'run.error'; message?: string }
  | { v: number; type: 'run.end'; messageId?: string; paused: boolean }

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  blocks: MessageBlock[]
  kind?: 'message' | 'error'
}
