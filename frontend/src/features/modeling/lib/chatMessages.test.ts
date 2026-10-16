import { describe, expect, it } from 'vitest'
import type { ConversationMessage } from '../../../shared/types'
import { messagesFromHistory, userMessage } from './chatMessages'

describe('messagesFromHistory', () => {
  it('把服务端消息翻成渲染用的消息，正文缺失时回落到 content', () => {
    const history: ConversationMessage[] = [
      {
        id: 'm1',
        role: 'user',
        content: '客户下订单',
        kind: 'message',
        blocks: null,
        created_at: '2026-01-01T00:00:00Z',
      },
      {
        id: 'm2',
        role: 'assistant',
        content: '',
        kind: 'message',
        blocks: [{ id: 'b1', kind: 'text', text: '好', done: false }],
        created_at: '2026-01-01T00:00:01Z',
      },
    ]

    expect(messagesFromHistory(history)).toEqual([
      {
        id: 'm1',
        role: 'user',
        kind: 'message',
        blocks: [{ id: 'm1', kind: 'text', text: '客户下订单', done: true }],
      },
      {
        id: 'm2',
        role: 'assistant',
        kind: 'message',
        blocks: [{ id: 'b1', kind: 'text', text: '好', done: true }],
      },
    ])
  })
})

describe('userMessage', () => {
  it('包成一条带文本块的用户消息，正文与块 id 各自独立', () => {
    const message = userMessage('把工单指派给员工')

    expect(message.role).toBe('user')
    expect(message.blocks).toEqual([
      { id: expect.any(String), kind: 'text', text: '把工单指派给员工', done: true },
    ])
    expect(message.blocks[0].id).not.toBe(message.id)
  })
})
