import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '../../../shared/types'
import { evidenceFromMessage, evidenceFromTaskResult, latestEvidence } from './evidence'

const result = JSON.stringify({
  status: 'OK',
  answer: '订单 A',
  evidence: [
    {
      step_id: 's0',
      object_type_id: 'type-1',
      object_type_name: '订单',
      primary_key: 'O-100',
      title: '订单 100',
      properties: { 'prop-amount': 1280 },
      property_names: { 'prop-amount': 'amount' },
      index_version_id: 'index-1',
    },
  ],
})

describe('query evidence parsing', () => {
  it('parses evidence from a task result', () => {
    expect(evidenceFromTaskResult(result)).toEqual([
      {
        stepId: 's0',
        objectTypeId: 'type-1',
        objectTypeName: '订单',
        primaryKey: 'O-100',
        title: '订单 100',
        properties: { 'prop-amount': 1280 },
        propertyNames: { 'prop-amount': 'amount' },
        indexVersionId: 'index-1',
      },
    ])
  })

  it('defaults missing readable metadata for older evidence', () => {
    const legacy = JSON.stringify({
      evidence: [{ step_id: 's0', primary_key: '1', properties: {} }],
    })

    expect(evidenceFromTaskResult(legacy)[0]).toMatchObject({
      objectTypeName: null,
      propertyNames: {},
    })
  })

  it('keeps raw results when parsing fails', () => {
    expect(evidenceFromTaskResult('not json')).toEqual([])
  })

  it('uses the latest assistant task evidence', () => {
    const messages: ChatMessage[] = [
      {
        id: 'm1',
        role: 'assistant',
        blocks: [{ id: 'tool-1', kind: 'tool', callId: '1', name: 'task', args: {}, status: 'ok', result }],
      },
    ]
    expect(evidenceFromMessage(messages[0])).toHaveLength(1)
    expect(latestEvidence(messages)).toHaveLength(1)
  })

  it('only keeps the last task evidence in one assistant message', () => {
    const earlier = JSON.stringify({
      status: 'OK',
      answer: '旧结果',
      evidence: [{ step_id: 's0', primary_key: '112', title: '苏泽宁' }],
    })
    const message: ChatMessage = {
      id: 'm2',
      role: 'assistant',
      blocks: [
        { id: 'tool-1', kind: 'tool', callId: '1', name: 'task', args: {}, status: 'ok', result: earlier },
        { id: 'tool-2', kind: 'tool', callId: '2', name: 'task', args: {}, status: 'ok', result },
      ],
    }

    expect(evidenceFromMessage(message).map((item) => item.primaryKey)).toEqual(['O-100'])
  })
})
