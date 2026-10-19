import type { ConversationEvent } from '../types'
import { apiUrl, request } from './client'

const CONVERSATION_EVENT_NAME = 'conversation.event'

interface SseFrame {
  event: string
  data: string
}

export function parseSseFrame(block: string): SseFrame | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  return data.length ? { event, data: data.join('\n') } : null
}

export function listConversationEvents(
  conversationId: string,
  afterEventId?: string,
): Promise<ConversationEvent[]> {
  const params = new URLSearchParams({ limit: '200' })
  if (afterEventId) params.set('after_event_id', afterEventId)
  return request<ConversationEvent[]>(
    `/conversations/${conversationId}/events?${params.toString()}`,
  )
}

/** 订阅会话事件；每个 `data:` 载荷都是一条 ConversationEvent。 */
export async function streamConversationEvents(
  conversationId: string,
  onEvent: (event: ConversationEvent) => void,
  signal?: AbortSignal,
  afterEventId?: string,
): Promise<void> {
  const params = new URLSearchParams()
  if (afterEventId) params.set('after_event_id', afterEventId)
  const suffix = params.size ? `?${params.toString()}` : ''
  const response = await fetch(
    apiUrl(`/conversations/${conversationId}/events/stream${suffix}`),
    { signal },
  )
  if (!response.ok) {
    throw new Error((await response.text()) || `Event stream failed: ${response.status}`)
  }
  if (!response.body) {
    throw new Error('Event stream returned no body')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const frame = parseSseFrame(block)
      if (frame?.event !== CONVERSATION_EVENT_NAME) continue
      onEvent(JSON.parse(frame.data) as ConversationEvent)
    }
  }
}
