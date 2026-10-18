import type { ConversationEvent } from '../types'
import { apiUrl, request } from './client'

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
      const payload = block
        .split('\n')
        .filter((line) => line.startsWith('data: '))
        .map((line) => line.slice(6))
        .join('\n')
      if (payload) {
        onEvent(JSON.parse(payload) as ConversationEvent)
      }
    }
  }
}
