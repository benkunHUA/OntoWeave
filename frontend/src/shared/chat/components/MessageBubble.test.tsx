// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '../../../test/render'
import type { ChatMessage, MessageBlock } from '../../types'
import { MessageBubble } from './MessageBubble'

const toolBlock: Extract<MessageBlock, { kind: 'tool' }> = {
  id: 'tool-1',
  kind: 'tool',
  callId: 'tool-1',
  name: 'query_objects',
  args: {},
  status: 'ok',
  durationMs: 800,
  summary: '业主 · 搜索「王建国」，最多 20 条',
  result: '{"total":1}',
}

const blocks: MessageBlock[] = [
  toolBlock,
  { id: 'reasoning-1', kind: 'reasoning', text: '内部推理不应直接显示', done: true },
  { id: 'text-1', kind: 'text', text: '找到 1 位业主。', done: true },
]

const message: ChatMessage = {
  id: 'message-1',
  role: 'assistant',
  blocks,
}

afterEach(cleanup)

describe('MessageBubble execution layout', () => {
  it('shows the folded trace before the final answer and hides reasoning', () => {
    const { container } = render(
      <MessageBubble
        message={message}
        pendingInterruptId={null}
        isRunning={false}
        onAnswer={vi.fn()}
        executionLabel="查询过程"
      />,
    )

    const text = container.textContent ?? ''
    expect(text.indexOf('查询过程 · 1 步 · 800ms')).toBeLessThan(text.indexOf('找到 1 位业主。'))
    expect(screen.queryByText('内部推理不应直接显示')).toBeNull()
  })

  it('treats only the latest trace as live while the message is running', () => {
    const runningMessage: ChatMessage = {
      id: 'message-2',
      role: 'assistant',
      blocks: [
        { ...toolBlock, id: 'tool-a', callId: 'tool-a' },
        { id: 'text-mid', kind: 'text', text: '先查第一阶段。', done: true },
        { ...toolBlock, id: 'tool-b', callId: 'tool-b', summary: '客户 · 搜索「张三」' },
      ],
    }

    render(
      <MessageBubble
        message={runningMessage}
        pendingInterruptId={null}
        isRunning
        onAnswer={vi.fn()}
        executionLabel="查询过程"
        active
      />,
    )

    const headings = screen
      .getAllByRole('button', { name: /查询过程/ })
      .map((node) => node.getAttribute('aria-label') ?? node.textContent ?? '')
    expect(headings.filter((name) => name.includes('执行中'))).toHaveLength(1)
  })
})
