import type {
  AgentEvent,
  ConversationMessage,
  FreeTextMode,
  InterruptOption,
  MessageBlock,
} from '../../../shared/types'

type TextLikeBlock = Extract<MessageBlock, { kind: 'text' | 'reasoning' }>

/** 把一条 SSE 事件合并进助手消息的块列表，返回新的列表。 */
export function reduceBlocks(blocks: MessageBlock[], event: AgentEvent): MessageBlock[] {
  switch (event.type) {
    case 'block.start':
      return [...blocks, { id: event.blockId, kind: event.kind, text: '' }]
    case 'block.delta':
      return blocks.map((block) =>
        isTextLike(block) && block.id === event.blockId
          ? { ...block, text: block.text + event.text }
          : block,
      )
    case 'block.end':
      return blocks.map((block) =>
        isTextLike(block) && block.id === event.blockId ? { ...block, done: true } : block,
      )
    case 'interrupt':
      return [
        ...blocks.filter((block) => block.kind !== 'interrupt'),
        {
          id: event.blockId,
          kind: 'interrupt',
          question: event.question,
          options: event.options ?? [],
          freeText: event.freeText ?? null,
          details: event.details ?? [],
        },
      ]
    case 'tool.start':
      return [
        ...blocks,
        {
          id: event.callId,
          kind: 'tool',
          callId: event.callId,
          name: event.name,
          args: event.args ?? {},
          status: 'running',
          result: null,
          summary: event.summary ?? null,
          agent: event.agent ?? null,
          parentCallId: event.parentCallId ?? null,
        },
      ]
    case 'tool.end':
      return blocks.map((block) =>
        block.kind === 'tool' && block.callId === event.callId
          ? {
              ...block,
              status: event.status,
              result: event.result,
              truncated: event.truncated,
              durationMs: event.durationMs,
            }
          : block,
      )
    default:
      return blocks
  }
}

function isTextLike(block: MessageBlock): block is TextLikeBlock {
  return block.kind === 'text' || block.kind === 'reasoning'
}

/** 历史消息（REST）转成块；旧消息没有 blocks 时回退到纯文本。 */
export function blocksFromMessage(message: ConversationMessage): MessageBlock[] {
  const stored = message.blocks
  if (Array.isArray(stored) && stored.length > 0) {
    return stored.map((block) =>
      block.kind === 'text' || block.kind === 'reasoning' ? { ...block, done: true } : block,
    )
  }
  if (!message.content) return []
  return [{ id: message.id, kind: 'text', text: message.content, done: true }]
}

export function textFromBlocks(blocks: MessageBlock[]): string {
  return blocks
    .filter((block): block is Extract<MessageBlock, { kind: 'text' }> => block.kind === 'text')
    .map((block) => block.text)
    .join('')
}

/** 一条消息是不是纯系统提示（确认 / 撤销的回执）。 */
export function isNoticeMessage(blocks: MessageBlock[]): boolean {
  return blocks.length > 0 && blocks.every((block) => block.kind === 'notice')
}

export interface PendingInterrupt {
  blockId: string
  question: string
  options: InterruptOption[]
  freeText: FreeTextMode
}

/** 会话还停在等人回答的状态吗：看最后一条助手消息的末尾是不是中断块。 */
export function pendingInterruptFrom(
  history: ConversationMessage[],
): PendingInterrupt | null {
  // 从后往前找最后一条「真实发言」，跳过确认 / 撤销这类系统回执：
  // 回执是插进对话里的旁白，不代表用户已经回答了中断里的问题。
  for (let index = history.length - 1; index >= 0; index -= 1) {
    const message = history[index]
    const blocks = blocksFromMessage(message)
    if (isNoticeMessage(blocks)) continue
    if (message.role !== 'assistant') return null
    const tail = blocks.at(-1)
    if (!tail || tail.kind !== 'interrupt') return null
    return {
      blockId: tail.id,
      question: tail.question,
      options: tail.options,
      freeText: tail.freeText,
    }
  }
  return null
}
