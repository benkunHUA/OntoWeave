// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '../../../test/render'
import type { ActionConfirmation, ChatMessage, EvidenceGraph } from '../../../shared/types'
import { QUERY_COPY } from '../lib/constants'
import { QueryMessageBubble } from './QueryMessageBubble'

const graph: EvidenceGraph = {
  graphId: 'graph-1',
  graphName: '燃气业务图',
  objectTypes: [{ id: 'owner', name: '业主', properties: [] }],
  relations: [],
  nodes: [
    {
      id: 'owner:C-1',
      objectTypeId: 'owner',
      primaryKey: 'C-1',
      title: '王建国',
      properties: {},
    },
  ],
  edges: [],
  warnings: [],
}

const message: ChatMessage = {
  id: 'message-1',
  role: 'assistant',
  blocks: [
    {
      id: 'tool-1',
      kind: 'tool',
      callId: 'tool-1',
      name: 'query_objects',
      args: {},
      status: 'ok',
      durationMs: 800,
      summary: '业主 · 搜索「王建国」，最多 20 条',
      result: '{"total":1}',
    },
    { id: 'text-1', kind: 'text', text: '找到 1 位业主。', done: true },
    { id: 'evidence-1', kind: 'evidence', graph },
  ],
}

const confirmation: ActionConfirmation = {
  kind: 'action_confirmation',
  title: '执行「派工」？',
  summary: '修改 1 个对象',
  action: { name: '派工' },
  preview_hash: 'hash',
  execution_id: 'execution-1',
  object_changes: [],
  relation_changes: [],
  warnings: [],
  options: [],
}

afterEach(cleanup)

describe('QueryMessageBubble', () => {
  it('renders execution, answer and evidence in reading order', () => {
    const { container } = render(
      <QueryMessageBubble
        message={message}
        pendingInterruptId={null}
        isRunning={false}
        onAnswer={vi.fn()}
      />,
    )

    const text = container.textContent ?? ''
    expect(text.indexOf(`${QUERY_COPY.executionTraceTitle} · 1 步`)).toBeLessThan(
      text.indexOf('找到 1 位业主。'),
    )
    expect(text.indexOf('找到 1 位业主。')).toBeLessThan(
      text.indexOf('燃气业务图'),
    )
    expect(screen.getByText('王建国')).toBeTruthy()
  })

  it('hides the evidence graph when the action turn suppresses it', () => {
    render(
      <QueryMessageBubble
        message={{
          ...message,
          blocks: [
            {
              id: 'i1',
              kind: 'interrupt',
              question: confirmation.title,
              options: [],
              freeText: null,
              confirmation,
            },
            { id: 'evidence-1', kind: 'evidence', graph },
          ],
        }}
        pendingInterruptId="i1"
        isRunning={false}
        onAnswer={vi.fn()}
        hideEvidence
      />,
    )

    expect(screen.getByText('执行「派工」？')).toBeTruthy()
    expect(screen.queryByText('王建国')).toBeNull()
  })
})
