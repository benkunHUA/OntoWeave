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
  status: Status
}

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
