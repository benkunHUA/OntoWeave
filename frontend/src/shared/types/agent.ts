import type { ViewSpec } from './view'
import type { PipelineCheckResult } from './dataPipeline'

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

/** 行动确认卡里的字段前后值。 */
export interface ActionFieldChange {
  property_id?: string | null
  name?: string | null
  before: unknown
  after: unknown
}

/** 行动确认卡里的对象变更。 */
export interface ActionObjectChange {
  kind: string
  object_type_id?: string | null
  object_type_name?: string | null
  primary_key: string
  title: string
  fields: ActionFieldChange[]
}

/** 行动确认卡里的关系变更。 */
export interface ActionRelationChange {
  kind: string
  relation_id?: string | null
  relation_name?: string | null
  from_object_type_id?: string | null
  from_object_type_name?: string | null
  from_primary_key: string
  to_object_type_id?: string | null
  to_object_type_name?: string | null
  to_primary_key: string
  before_exists: boolean
  after_exists: boolean
}

/** `execute_action` 在 preview 后返回的结构化确认载荷。 */
export interface ActionConfirmation {
  kind: 'action_confirmation'
  title: string
  summary: string
  action: { id?: string | null; name: string; description?: string | null }
  preview_hash: string
  execution_id: string
  object_changes: ActionObjectChange[]
  relation_changes: ActionRelationChange[]
  warnings: unknown[]
  options: InterruptOption[]
}

export interface EvidenceObjectType {
  id: string
  name: string
  properties: Array<Record<string, unknown>>
}

export interface EvidenceRelation {
  id: string
  name: string
  sourceTypeId: string
  targetTypeId: string
  cardinality: string
  forwardLabel: string | null
  reverseLabel: string | null
}

export interface EvidenceNode {
  id: string
  objectTypeId: string
  primaryKey: string
  title: string | null
  properties: Record<string, unknown>
}

export interface EvidenceEdge {
  id: string
  relationId: string
  source: string
  target: string
  label: string
}

/** 一轮查询最终命中的对象实例子图。 */
export interface EvidenceGraph {
  graphId: string | null
  graphName: string | null
  objectTypes: EvidenceObjectType[]
  relations: EvidenceRelation[]
  nodes: EvidenceNode[]
  edges: EvidenceEdge[]
  warnings: string[]
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
      /** 面向用户的一行说明，例如“客户 · 搜索「张三」”。 */
      summary?: string | null
      /** 子智能体内部的调用：子智能体名；主智能体自己的调用没有这个字段。 */
      agent?: string | null
      /** 发起这次子智能体调用的是哪次 `task` 工具调用。 */
      parentCallId?: string | null
    }
  | {
      id: string
      kind: 'interrupt'
      question: string
      options: InterruptOption[]
      freeText: FreeTextMode
      details?: InterruptDetail[]
      /** 行动执行的富确认载荷；普通提问中断没有。 */
      confirmation?: ActionConfirmation | null
    }
  | { id: string; kind: 'evidence'; graph: EvidenceGraph }

/** 一条渲染出来的消息：用户输入，或助手的一次运行。 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  blocks: MessageBlock[]
  kind?: 'message' | 'error'
  created_at?: string
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
      summary?: string | null
      agent?: string | null
      parentCallId?: string | null
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
      confirmation?: ActionConfirmation | null
    }
  | {
      v: number
      type: 'state.update'
      changesetId?: string
      ops?: Array<{ op: string; kind?: string }>
      view?: ViewSpec
      dataConnection?: unknown
      pipelineId?: string
      draftRevision?: number | null
      check?: PipelineCheckResult | null
      loadError?: string | null
    }
  | {
      v: number
      type: 'evidence.update'
      messageId: string
      blockId: string
      graph: EvidenceGraph
    }
  | { v: number; type: 'run.error'; message?: string }
  | { v: number; type: 'run.end'; messageId?: string; paused: boolean }

export type StateUpdateEvent = Extract<AgentEvent, { type: 'state.update' }>
