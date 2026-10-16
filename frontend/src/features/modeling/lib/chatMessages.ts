import type { ChatMessage, ConversationMessage, MessageBlock } from '../../../shared/types'
import { blocksFromMessage } from './messageBlocks'

/** 本地新建消息用的 id。服务端在 run.start 里会给出权威 id 并替换掉它。 */
export function newId(): string {
  return crypto.randomUUID()
}

/** 把服务端的历史转成界面渲染用的消息列表。 */
export function messagesFromHistory(history: ConversationMessage[]): ChatMessage[] {
  return history.map((message) => ({
    id: message.id,
    role: message.role,
    blocks: blocksFromMessage(message),
    kind: message.kind,
  }))
}

/** 用户刚说的一句话，落成一条带文本块的用户消息。 */
export function userMessage(text: string): ChatMessage {
  const block: MessageBlock = { id: newId(), kind: 'text', text, done: true }
  return { id: newId(), role: 'user', blocks: [block] }
}
