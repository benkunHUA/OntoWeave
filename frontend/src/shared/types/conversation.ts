import type { MessageBlock } from './agent'

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
