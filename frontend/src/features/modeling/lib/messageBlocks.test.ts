import { describe, expect, it } from 'vitest'
import type { AgentEvent, ConversationMessage, MessageBlock } from '../../../shared/types'
import {
  blocksFromMessage,
  isNoticeMessage,
  pendingInterruptFrom,
  reduceBlocks,
  textFromBlocks,
} from './messageBlocks'

function event(payload: AgentEvent): AgentEvent {
  return payload
}

describe('reduceBlocks', () => {
  it('keeps reasoning, text and tools in arrival order', () => {
    let blocks: MessageBlock[] = []
    blocks = reduceBlocks(
      blocks,
      event({ v: 1, type: 'block.start', messageId: 'm1', blockId: 'b1', kind: 'reasoning' }),
    )
    blocks = reduceBlocks(
      blocks,
      event({ v: 1, type: 'block.delta', messageId: 'm1', blockId: 'b1', text: '先想想' }),
    )
    blocks = reduceBlocks(
      blocks,
      event({ v: 1, type: 'block.end', messageId: 'm1', blockId: 'b1' }),
    )
    blocks = reduceBlocks(
      blocks,
      event({
        v: 1,
        type: 'tool.start',
        messageId: 'm1',
        callId: 'c1',
        name: 'search_ontology',
        args: { query: '客户' },
      }),
    )
    blocks = reduceBlocks(
      blocks,
      event({
        v: 1,
        type: 'tool.end',
        messageId: 'm1',
        callId: 'c1',
        status: 'ok',
        result: '命中 2 个',
        truncated: false,
        durationMs: 180,
      }),
    )
    blocks = reduceBlocks(
      blocks,
      event({ v: 1, type: 'block.start', messageId: 'm1', blockId: 'b2', kind: 'text' }),
    )
    blocks = reduceBlocks(
      blocks,
      event({ v: 1, type: 'block.delta', messageId: 'm1', blockId: 'b2', text: '建好了' }),
    )

    expect(blocks.map((block) => block.kind)).toEqual(['reasoning', 'tool', 'text'])
    expect(blocks[0]).toMatchObject({ text: '先想想', done: true })
    expect(blocks[1]).toMatchObject({ status: 'ok', result: '命中 2 个' })
    expect(textFromBlocks(blocks)).toBe('建好了')
  })

  it('tags steps that come from inside a subagent', () => {
    const blocks = reduceBlocks(
      [],
      event({
        v: 1,
        type: 'tool.start',
        messageId: 'm1',
        callId: 'c2',
        name: 'propose_action_rule',
        args: {},
        agent: 'action_modeler',
        parentCallId: 'c1',
      }),
    )

    expect(blocks[0]).toMatchObject({
      kind: 'tool',
      agent: 'action_modeler',
      parentCallId: 'c1',
    })
  })
})

describe('blocksFromMessage', () => {
  it('falls back to plain content for legacy rows', () => {
    const legacy = {
      id: 'msg-1',
      role: 'assistant',
      content: '旧消息',
      kind: 'message',
      blocks: null,
      created_at: '2026-10-10T00:00:00Z',
    } satisfies ConversationMessage
    expect(blocksFromMessage(legacy)).toEqual([
      { id: 'msg-1', kind: 'text', text: '旧消息', done: true },
    ])
  })

  it('reuses stored blocks and marks text as done', () => {
    const row = {
      id: 'msg-2',
      role: 'assistant',
      content: '你好',
      kind: 'message',
      blocks: [
        { id: 'b1', kind: 'reasoning', text: '想想', done: true },
        { id: 'b2', kind: 'text', text: '你好' },
      ],
      created_at: '2026-10-10T00:00:00Z',
    } as ConversationMessage
    expect(blocksFromMessage(row)).toEqual([
      { id: 'b1', kind: 'reasoning', text: '想想', done: true },
      { id: 'b2', kind: 'text', text: '你好', done: true },
    ])
  })
})

describe('pendingInterruptFrom', () => {
  function message(
    id: string,
    role: 'user' | 'assistant',
    blocks: ConversationMessage['blocks'],
  ): ConversationMessage {
    return {
      id,
      role,
      content: '',
      kind: 'message',
      blocks,
      created_at: '2026-10-10T00:00:00Z',
    }
  }

  it('rebuilds the waiting state from the last assistant message', () => {
    const history = [
      message('m1', 'user', null),
      message('m2', 'assistant', [
        { id: 'b1', kind: 'text', text: '拿不准，问一下。' },
        {
          id: 'b2',
          kind: 'interrupt',
          question: '客户还是供应商？',
          options: [
            { label: '客户', answer: '客户' },
            { label: '供应商', answer: '供应商' },
          ],
          freeText: { wrap: 'raw' },
        },
      ]),
    ]
    expect(pendingInterruptFrom(history)).toEqual({
      blockId: 'b2',
      question: '客户还是供应商？',
      options: [
        { label: '客户', answer: '客户' },
        { label: '供应商', answer: '供应商' },
      ],
      freeText: { wrap: 'raw' },
    })
  })

  it('is not pending once the user has answered', () => {
    const history = [
      message('m1', 'assistant', [
        { id: 'b1', kind: 'interrupt', question: '问一下', options: [], freeText: null },
      ]),
      message('m2', 'user', null),
      message('m3', 'assistant', [{ id: 'b2', kind: 'text', text: '好', done: true }]),
    ]
    expect(pendingInterruptFrom(history)).toBeNull()
  })

  it('stays pending when a system notice lands after the interrupt', () => {
    const history = [
      message('m1', 'assistant', [
        { id: 'b1', kind: 'interrupt', question: '客户还是供应商？', options: [], freeText: null },
      ]),
      message('m2', 'assistant', [{ id: 'n1', kind: 'notice', text: '已撤销本次改动：新增 1。' }]),
    ]
    expect(pendingInterruptFrom(history)).toEqual({
      blockId: 'b1',
      question: '客户还是供应商？',
      options: [],
      freeText: null,
    })
  })

  it('ignores a history made only of notices', () => {
    const history = [
      message('m1', 'assistant', [{ id: 'n1', kind: 'notice', text: '已确认本次改动。' }]),
    ]
    expect(pendingInterruptFrom(history)).toBeNull()
  })
})

describe('reduceBlocks interrupt', () => {
  it('keeps a single interrupt block and stores resume payloads', () => {
    let blocks: MessageBlock[] = []
    const event: AgentEvent = {
      v: 1,
      type: 'interrupt',
      messageId: 'm1',
      blockId: 'i1',
      question: '要执行吗',
      options: [{ label: '允许', answer: { decisions: [{ type: 'approve' }] } }],
      freeText: { wrap: 'respond' },
    }
    blocks = reduceBlocks(blocks, event)
    blocks = reduceBlocks(blocks, { ...event, blockId: 'i2' })
    expect(blocks).toEqual([
      {
        id: 'i2',
        kind: 'interrupt',
        question: '要执行吗',
        options: [{ label: '允许', answer: { decisions: [{ type: 'approve' }] } }],
        freeText: { wrap: 'respond' },
        details: [],
      },
    ])
  })

  it('carries candidate details through to the block', () => {
    const blocks = reduceBlocks([], {
      v: 1,
      type: 'interrupt',
      messageId: 'm1',
      blockId: 'i1',
      question: '要复用已有的「客户」吗？',
      options: [
        { label: '复用这个', answer: '复用这个' },
        { label: '新建单独的', answer: '新建单独的' },
      ],
      freeText: { wrap: 'raw' },
      details: [
        { label: '名称', value: '客户' },
        { label: '状态', value: 'confirmed' },
      ],
    })
    expect(blocks).toEqual([
      {
        id: 'i1',
        kind: 'interrupt',
        question: '要复用已有的「客户」吗？',
        options: [
          { label: '复用这个', answer: '复用这个' },
          { label: '新建单独的', answer: '新建单独的' },
        ],
        freeText: { wrap: 'raw' },
        details: [
          { label: '名称', value: '客户' },
          { label: '状态', value: 'confirmed' },
        ],
      },
    ])
  })
})

describe('isNoticeMessage', () => {
  it('is true only when every block is a system notice', () => {
    expect(isNoticeMessage([{ id: 'n1', kind: 'notice', text: '已撤销本次改动。' }])).toBe(true)
    expect(
      isNoticeMessage([
        { id: 'n1', kind: 'notice', text: '已撤销本次改动。' },
        { id: 't1', kind: 'text', text: '还有正文' },
      ]),
    ).toBe(false)
    expect(isNoticeMessage([])).toBe(false)
  })
})
