import type { ViewSpec } from './view'

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
    }

/** 一条渲染出来的消息：用户输入，或助手的一次运行。 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  blocks: MessageBlock[]
  kind?: 'message' | 'error'
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
