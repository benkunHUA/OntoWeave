import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../../../shared/types'
import { evidenceFromMessage, evidenceFromQueryObjectsResult, latestEvidence } from './evidence'

const result = JSON.stringify({
  object_type_id: 'type-1',
  index_version_id: 'index-1',
  items: [
    {
      object_type_id: 'type-1',
      primary_key: 'O-100',
      title: '订单 100',
      properties: { 'prop-amount': 1280 },
    },
  ],
})

describe('query evidence parsing', () => {
  it('parses evidence from a query_objects result', () => {
    expect(evidenceFromQueryObjectsResult(result)).toEqual([
      {
        objectTypeId: 'type-1',
        objectTypeName: null,
        primaryKey: 'O-100',
        title: '订单 100',
        properties: { 'prop-amount': 1280 },
        propertyNames: {},
        indexVersionId: 'index-1',
      },
    ])
  })

  it('keeps parsing failures empty', () => {
    expect(evidenceFromQueryObjectsResult('not json')).toEqual([])
    expect(evidenceFromQueryObjectsResult(JSON.stringify({ items: 'bad' }))).toEqual([])
  })

  it('uses the latest assistant query_objects result', () => {
    const messages: ChatMessage[] = [
      {
        id: 'm1',
        role: 'assistant',
        blocks: [{ id: 'tool-1', kind: 'tool', callId: '1', name: 'query_objects', args: {}, status: 'ok', result }],
      },
    ]
    expect(evidenceFromMessage(messages[0])).toHaveLength(1)
    expect(latestEvidence(messages)).toHaveLength(1)
  })

  it('only keeps the last query_objects result in one assistant message', () => {
    const earlier = JSON.stringify({
      index_version_id: 'index-1',
      items: [{ primary_key: '112', title: '苏泽宁' }],
    })
    const message: ChatMessage = {
      id: 'm2',
      role: 'assistant',
      blocks: [
        { id: 'tool-1', kind: 'tool', callId: '1', name: 'query_objects', args: {}, status: 'ok', result: earlier },
        { id: 'tool-2', kind: 'tool', callId: '2', name: 'query_objects', args: {}, status: 'ok', result },
      ],
    }

    expect(evidenceFromMessage(message).map((item) => item.primaryKey)).toEqual(['O-100'])
  })
})
