// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '../../../test/render'
import type { ChatMessage, ConversationEvent } from '../../types'
import { ConversationTimeline } from './ConversationTimeline'

const messages: ChatMessage[] = [
  {
    id: 'message-1',
    role: 'user',
    blocks: [{ id: 'block-1', kind: 'text', text: '映射客户名称', done: true }],
    created_at: '2026-10-24T10:00:00Z',
  },
]

const events: ConversationEvent[] = [
  {
    id: 'event-1',
    event_id: 'event-1',
    operation_id: null,
    run_id: null,
    turn_id: null,
    event_type: 'mapping.saved',
    summary: '草稿已保存',
    payload: {},
    created_at: '2026-10-24T10:01:00Z',
  },
  {
    id: 'event-2',
    event_id: 'event-2',
    operation_id: null,
    run_id: null,
    turn_id: null,
    event_type: 'internal.state',
    summary: '内部状态',
    payload: {},
    created_at: '2026-10-24T10:02:00Z',
  },
]

function timeline(eventFilter?: (event: ConversationEvent) => boolean) {
  return (
    <ConversationTimeline
      messages={messages}
      events={events}
      isRunning={false}
      isLoading={false}
      interrupt={null}
      onAnswer={vi.fn()}
      eventFilter={eventFilter}
      eventIcon={<span>icon</span>}
      eventClassName="test-event"
      eventIconClassName="test-event__icon"
      emptyIcon={<span>empty-icon</span>}
      emptyTitle="还没有对话"
      emptyBody="描述你想映射的字段"
    />
  )
}

describe('ConversationTimeline', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn()
  })

  afterEach(() => {
    cleanup()
  })

  it('renders messages and domain events in chronological order', () => {
    const { container } = render(timeline())

    expect(screen.getByText('映射客户名称')).toBeTruthy()
    expect(screen.getByText('草稿已保存')).toBeTruthy()
    expect(screen.getByText('内部状态')).toBeTruthy()
    expect(container.querySelectorAll('.test-event')).toHaveLength(2)
    expect(container.querySelector('.test-event__icon')?.textContent).toBe('icon')
  })

  it('applies the event filter without disturbing messages', () => {
    const { container } = render(timeline((event) => event.event_type !== 'internal.state'))

    expect(screen.getByText('映射客户名称')).toBeTruthy()
    expect(screen.getByText('草稿已保存')).toBeTruthy()
    expect(screen.queryByText('内部状态')).toBeNull()
    expect(container.querySelectorAll('.test-event')).toHaveLength(1)
  })

  it('shows the configured empty state', () => {
    render(
      <ConversationTimeline
        messages={[]}
        events={[]}
        isRunning={false}
        isLoading={false}
        interrupt={null}
        onAnswer={vi.fn()}
        emptyIcon={<span>empty-icon</span>}
        emptyTitle="还没有对话"
        emptyBody="描述你想映射的字段"
      />,
    )

    expect(screen.getByText('还没有对话')).toBeTruthy()
    expect(screen.getByText('描述你想映射的字段')).toBeTruthy()
  })
})
