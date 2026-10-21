import type { MessageBlock } from './agent'

export const CONVERSATION_KIND_MODELING = 'modeling' as const
export const CONVERSATION_KIND_DATA_CONNECTION = 'data_connection' as const
export const CONVERSATION_KIND_DATA_PIPELINE = 'data_pipeline' as const

export type ConversationKind =
  | typeof CONVERSATION_KIND_MODELING
  | typeof CONVERSATION_KIND_DATA_CONNECTION
  | typeof CONVERSATION_KIND_DATA_PIPELINE

export interface Conversation {
  id: string
  kind: ConversationKind
  resource_id: string
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
