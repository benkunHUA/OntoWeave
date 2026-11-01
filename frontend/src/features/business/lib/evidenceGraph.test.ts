import { describe, expect, it } from 'vitest'
import type { ChatMessage, EvidenceGraph } from '../../../shared/types'
import { evidenceGraphFromMessage } from './evidenceGraph'

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

function message(blocks: ChatMessage['blocks']): ChatMessage {
  return { id: 'm1', role: 'assistant', blocks }
}

describe('evidenceGraphFromMessage', () => {
  it('reads the persisted evidence block directly', () => {
    expect(evidenceGraphFromMessage(message([{ id: 'e1', kind: 'evidence', graph }]))).toBe(graph)
  })

  it('does not infer a graph from tool text', () => {
    expect(
      evidenceGraphFromMessage(
        message([
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
