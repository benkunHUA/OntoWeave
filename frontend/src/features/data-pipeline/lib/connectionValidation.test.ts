import { describe, expect, it } from 'vitest'
import { validateConnection } from './connectionValidation'
import { FILTER_A, FILTER_B, INPUT_ID, catalog, draftFixture } from './testFixtures'

describe('validateConnection', () => {
  it('rejects an occupied single-input port', () => {
    const draft = draftFixture()
    draft.definition.nodes.push({
      id: FILTER_B,
      type: 'filter',
      name: '筛选 B',
      config: {},
      position: { x: 320, y: 340 },
    })
    draft.definition.edges.push({
      id: '30000000-0000-4000-8000-000000000009',
      sourceNodeId: INPUT_ID,
      sourcePort: 'out',
      targetNodeId: FILTER_B,
      targetPort: 'in',
    })

    const result = validateConnection(
      draft,
      {
        sourceNodeId: INPUT_ID,
        sourcePort: 'out',
        targetNodeId: FILTER_B,
        targetPort: 'in',
      },
      { ...catalog, nodes: catalog.nodes },
    )

    expect(result.ok).toBe(false)
    if (result.ok) throw new Error('expected occupied-port rejection')
    expect(result.reason).toBe('INPUT_OCCUPIED')
  })

  it('rejects a connection that closes a cycle', () => {
    const draft = draftFixture()
    draft.definition.nodes.push({
      id: FILTER_B,
      type: 'filter',
      name: '筛选 B',
      config: {},
      position: { x: 320, y: 340 },
    })
    draft.definition.edges.push({
      id: '30000000-0000-4000-8000-000000000007',
      sourceNodeId: FILTER_B,
      sourcePort: 'out',
      targetNodeId: FILTER_A,
      targetPort: 'in',
    })

    const result = validateConnection(
      draft,
      {
        sourceNodeId: FILTER_A,
        sourcePort: 'out',
        targetNodeId: FILTER_B,
        targetPort: 'in',
      },
      catalog,
    )

    expect(result.ok).toBe(false)
    if (result.ok) throw new Error('expected cycle rejection')
    expect(result.reason).toBe('CYCLE')
  })

  it('allows multiple edges only when the target port declares multiple', () => {
    const draft = draftFixture()
    draft.definition.nodes.push({
      id: FILTER_B,
      type: 'union',
      name: '合并',
      config: {},
      position: { x: 360, y: 340 },
    })
    draft.definition.edges.push({
      id: '30000000-0000-4000-8000-000000000008',
      sourceNodeId: INPUT_ID,
      sourcePort: 'out',
      targetNodeId: FILTER_B,
      targetPort: 'inputs',
    })

    const result = validateConnection(
      draft,
      {
        sourceNodeId: FILTER_A,
        sourcePort: 'out',
        targetNodeId: FILTER_B,
        targetPort: 'inputs',
      },
      catalog,
    )

    expect(result.ok).toBe(true)
  })
})
