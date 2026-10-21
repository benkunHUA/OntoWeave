import type { AgentEvent, AgentRunInput } from '../types'
import { apiUrl } from './client'

/**
 * 跑一次智能体回合，边跑边把 SSE 事件交给调用方。
 *
 * 事件按空行分帧、`data: ` 前缀取载荷；最后残留的一段也要冲出来，否则收尾事件会丢。
 */
export async function streamAgentRun(
  conversationId: string,
  body: AgentRunInput,
  onEvent: (event: AgentEvent) => void,
  signal?: AbortSignal,
  turnId?: string,
): Promise<void> {
  const isResume = 'answer' in body
  const suffix = isResume ? 'resume' : 'messages'
  const headers: Record<string, string> = { 'content-type': 'application/json' }
  if (turnId) headers['X-Turn-Id'] = turnId
  const response = await fetch(apiUrl(`/conversations/${conversationId}/${suffix}`), {
    method: 'POST',
    headers,
    body: JSON.stringify(
      isResume ? { answer: body.answer, text: body.display } : { text: body.text },
    ),
    signal,
  })
  if (!response.ok) {
    throw new Error((await response.text()) || `Agent run failed: ${response.status}`)
  }
  if (!response.body) {
    throw new Error('Agent stream returned no body')
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
      const data = block
        .split('\n')
        .filter((line) => line.startsWith('data: '))
        .map((line) => line.slice(6))
        .join('\n')
      if (data) {
        onEvent(JSON.parse(data) as AgentEvent)
      }
    }
  }

  if (buffer.trim()) {
    for (const line of buffer.split('\n')) {
      if (line.startsWith('data: ')) {
        onEvent(JSON.parse(line.slice(6)) as AgentEvent)
      }
    }
  }
}
