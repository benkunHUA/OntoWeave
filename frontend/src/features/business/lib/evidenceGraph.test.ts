import { describe, expect, it } from 'vitest'
import type { ActionConfirmation, ChatMessage, EvidenceGraph } from '../../../shared/types'
import { evidenceGraphFromMessage, isActionTurnMessage } from './evidenceGraph'

const graph: EvidenceGraph = {
  graphId: 'graph-1',
  graphName: '销售图',
  objectTypes: [],
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

function message(
  id: string,
  role: ChatMessage['role'],
  blocks: ChatMessage['blocks'],
): ChatMessage {
  return { id, role, blocks }
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

describe('evidenceGraphFromMessage', () => {
  it('reads the persisted evidence block directly', () => {
    expect(
      evidenceGraphFromMessage(
        message('m1', 'assistant', [{ id: 'e1', kind: 'evidence', graph }]),
      ),
    ).toBe(graph)
  })

  it('does not infer a graph from tool text', () => {
    expect(
      evidenceGraphFromMessage(
        message('m1', 'assistant', [
          {
            id: 'tool-1',
            kind: 'tool',
            callId: 'c1',
            name: 'query_objects',
            args: {},
            status: 'ok',
            result: '{"items":[]}',
          },
        ]),
      ),
    ).toBeNull()
  })
})

describe('isActionTurnMessage', () => {
  it('keeps a pure query message outside the action flow', () => {
    expect(
      isActionTurnMessage(
        [
          message('u1', 'user', []),
          message('a1', 'assistant', [{ id: 'e1', kind: 'evidence', graph }]),
        ],
        1,
      ),
    ).toBe(false)
  })

  it('marks a message waiting for action confirmation', () => {
    expect(
      isActionTurnMessage(
        [
          message('a1', 'assistant', [
            {
              id: 'i1',
              kind: 'interrupt',
              question: confirmation.title,
              options: [],
              freeText: null,
              confirmation,
            },
            { id: 'e1', kind: 'evidence', graph },
          ]),
        ],
        0,
      ),
    ).toBe(true)
  })

  it('marks an action execution message', () => {
    expect(
      isActionTurnMessage(
        [
          message('a1', 'assistant', [
            {
              id: 'tool-1',
              kind: 'tool',
              callId: 'c1',
              name: 'execute_action',
              args: {},
              status: 'ok',
            },
            { id: 'e1', kind: 'evidence', graph },
          ]),
        ],
        0,
      ),
    ).toBe(true)
  })

  it('marks the result after a confirmation answer as part of the action turn', () => {
    expect(
      isActionTurnMessage(
        [
          message('a1', 'assistant', [
            {
              id: 'i1',
              kind: 'interrupt',
              question: confirmation.title,
              options: [],
              freeText: null,
              confirmation,
            },
          ]),
          message('u1', 'user', []),
          message('a2', 'assistant', [{ id: 'e1', kind: 'evidence', graph }]),
        ],
        2,
      ),
    ).toBe(true)
  })

  it('does not mark a later query after the action result', () => {
    expect(
      isActionTurnMessage(
        [
          message('a1', 'assistant', [
            {
              id: 'i1',
              kind: 'interrupt',
              question: confirmation.title,
              options: [],
              freeText: null,
              confirmation,
            },
          ]),
          message('u1', 'user', []),
          message('a2', 'assistant', [{ id: 'e1', kind: 'evidence', graph }]),
          message('u2', 'user', []),
          message('a3', 'assistant', [{ id: 'e2', kind: 'evidence', graph }]),
        ],
        4,
      ),
    ).toBe(false)
  })
})
